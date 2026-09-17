package org.nc.nccasino.payout;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.nc.nccasino.Nccasino;
import org.nc.nccasino.currency.CurrencyMode;

import java.util.Map;
import java.util.UUID;

/**
 * The single place item winnings are physically handed over.
 *
 * <p>Every item payout in NCCasino should route through
 * {@link #deliver}: it fills the inventory, applies the configured
 * Bank/Drop preference to what is left, caps physical drops, and persists
 * any final remainder to the {@link OverflowBankStore}. Nothing is ever
 * deleted, and nothing is reported settled unless it genuinely reached the
 * player, the ground, or durable storage.
 *
 * <p>Claiming (as opposed to paying) deliberately never drops. The bank
 * blocks wagering precisely to make the player free up real inventory
 * space; satisfying a claim by scattering the balance on the ground would
 * clear the block while leaving the winnings exactly as losable as before.
 */
public class OverflowBankService {

    /** Vanilla default, used when a material's real stack size cannot be resolved. */
    static final int DEFAULT_STACK_SIZE = 64;

    private final Nccasino plugin;
    private final OverflowBankStore store;

    public OverflowBankService(Nccasino plugin, OverflowBankStore store) {
        this.plugin = plugin;
        this.store = store;
    }

    public OverflowBankStore store() {
        return store;
    }

    private OverflowSettings settings() {
        return OverflowSettings.load(plugin);
    }

    /**
     * Pays {@code amount} whole units of {@code currency} to {@code player},
     * banking whatever cannot be delivered.
     *
     * @return what reached each destination; check
     *     {@link ItemDeliveryOutcome#settled()} before treating the payout
     *     as complete
     */
    public ItemDeliveryOutcome deliver(Player player, BankedCurrency currency, long amount) {
        if (amount <= 0) {
            return ItemDeliveryOutcome.nothing();
        }
        if (player == null || currency == null) {
            return ItemDeliveryOutcome.allUnsettled(amount);
        }

        UUID playerId = player.getUniqueId();
        Material material = resolveMaterial(currency);
        if (material == null) {
            // Nothing can be handed over, but the debt is real. Bank it so it
            // survives for reconciliation rather than evaporating.
            return store.credit(playerId, currency, amount)
                ? new ItemDeliveryOutcome(amount, 0L, 0L, amount, 0L)
                : ItemDeliveryOutcome.allUnsettled(amount);
        }

        OverflowSettings config = settings();
        OverflowPreference preference = config.effectivePreference(playerPreference(playerId));
        int stackSize = maxStackSize(material);
        long dropCapUnits = (long) config.maxDropStacks() * stackSize;

        long capacity = freeCapacityUnits(player.getInventory(), material, stackSize);
        ItemDeliveryPlan plan = ItemDeliveryPlanner.plan(amount, capacity, preference, dropCapUnits);

        if (!plan.hasOverflow()) {
            return deliverEntirelyIntoInventory(player, playerId, currency, material, amount, stackSize);
        }
        return deliverWithOverflow(player, playerId, currency, material, plan, amount, stackSize);
    }

    /**
     * The common case: the whole payout fits, so no durable state is needed at
     * all and the player simply receives it.
     */
    private ItemDeliveryOutcome deliverEntirelyIntoInventory(
        Player player, UUID playerId, BankedCurrency currency, Material material, long amount, int stackSize) {

        long inserted = insert(player, material, amount, stackSize);
        long shortfall = amount - inserted;
        if (shortfall <= 0) {
            return new ItemDeliveryOutcome(amount, inserted, 0L, 0L, 0L);
        }

        // The capacity probe said this would fit and Bukkit disagreed. Bank
        // the difference; only report it unsettled if even that fails.
        if (store.credit(playerId, currency, shortfall)) {
            return new ItemDeliveryOutcome(amount, inserted, 0L, shortfall, 0L);
        }
        return new ItemDeliveryOutcome(amount, inserted, 0L, 0L, shortfall);
    }

    /**
     * The overflow case, ordered so a failed bank write can never cause a
     * double payment.
     *
     * <p>The entire overflow is reserved in the bank <em>before</em> a single
     * item moves. If that write fails nothing physical has happened, so the
     * caller is told the whole payout is unsettled and a retry pays it exactly
     * once. Once the reservation is on disk the value is durably the player's,
     * and every subsequent step can only move it out of the bank -- never lose
     * it. Dropping likewise debits first and drops second, so a failed write
     * simply leaves the value banked instead of scattering items the bank
     * still thinks it holds.
     */
    private ItemDeliveryOutcome deliverWithOverflow(
        Player player, UUID playerId, BankedCurrency currency, Material material,
        ItemDeliveryPlan plan, long amount, int stackSize) {

        long reserved = plan.toDrop() + plan.toBank();
        if (!store.credit(playerId, currency, reserved)) {
            plugin.getLogger().severe("[NCCasino] Could not reserve " + reserved + " " + currency.storageKey()
                + " in the overflow bank for " + playerId + "; no items were moved and the paying game still owes"
                + " the full " + amount + ". It must retry rather than treat this as settled.");
            return ItemDeliveryOutcome.allUnsettled(amount);
        }

        long inserted = insert(player, material, plan.toInventory(), stackSize);
        long unsettled = 0L;
        long shortfall = plan.toInventory() - inserted;
        if (shortfall > 0) {
            if (store.credit(playerId, currency, shortfall)) {
                reserved += shortfall;
            } else {
                // Never delivered and never banked -- the caller still owes
                // exactly this much, and nothing more.
                unsettled = shortfall;
            }
        }

        long dropped = 0L;
        long dropTarget = Math.min(plan.toDrop(), reserved);
        if (dropTarget > 0 && player.getWorld() != null
            && store.debitBeforeDelivery(playerId, currency, dropTarget)) {
            dropped = drop(player, material, dropTarget, stackSize);
            if (dropped < dropTarget) {
                // Put back whatever never actually hit the ground.
                store.credit(playerId, currency, dropTarget - dropped);
            }
        }

        long banked = reserved - dropped;
        return new ItemDeliveryOutcome(amount, inserted, dropped, banked, unsettled);
    }

    /**
     * Delivers a payout and, if any part of it cannot be handed over, retains
     * that remainder as a {@link PendingPayout} exactly once.
     *
     * <p>This is the single-owner entry point for money-bearing callers that
     * have no settlement state machine of their own. It exists specifically to
     * remove the "helper retains, caller retains again" hazard: because the
     * retention happens here and the caller receives a
     * {@link PayoutDisposition} rather than a bare boolean, there is no
     * failure signal a caller could misread as "nothing was recorded, I should
     * record it myself" -- which would create two obligations for one payout.
     *
     * @param context the encoded {@link PayoutMessages} reason to store on the
     *     retained record, so a refund is not later shown to the player as an
     *     ordinary winning; {@code null} records it as a committed result
     * @return {@link PayoutDisposition#DELIVERED} if it all reached the
     *     player, {@link PayoutDisposition#RETAINED} if the remainder is
     *     durably recorded for retry, or {@link PayoutDisposition#UNRESOLVED}
     *     if it is neither -- the only state that must never be reported to a
     *     player as a completed payout.
     */
    public PayoutDisposition deliverAndRetain(
        Player player,
        BankedCurrency currency,
        long amount,
        String gameType,
        String dealerInternalName,
        String context
    ) {
        if (amount <= 0) {
            return PayoutDisposition.DELIVERED;
        }
        if (player == null || currency == null) {
            return PayoutDisposition.UNRESOLVED;
        }

        ItemDeliveryOutcome outcome = deliver(player, currency, amount);
        if (outcome.settled()) {
            return PayoutDisposition.DELIVERED;
        }

        boolean retained = UnsettledPayouts.retain(
            plugin,
            player.getUniqueId(),
            gameType,
            dealerInternalName,
            currency.mode(),
            currency.material(),
            currency.name(),
            outcome.unsettled(),
            context);
        return PayoutDisposition.of(false, retained);
    }

    /**
     * Attempts to hand back everything currently banked for {@code player}.
     * Only inventory space is used -- see the class note on why a claim
     * never drops.
     *
     * @return how many units are still banked afterwards; {@code 0} means the
     *     player is clear to wager again
     */
    public long claimAll(Player player) {
        if (player == null) {
            return 0L;
        }
        UUID playerId = player.getUniqueId();

        for (OverflowBankStore.Entry entry : store.entriesFor(playerId)) {
            Material material = resolveMaterial(entry.currency());
            if (material == null) {
                continue;
            }
            int stackSize = maxStackSize(material);
            long capacity = freeCapacityUnits(player.getInventory(), material, stackSize);
            long deliverable = Math.min(entry.amount(), capacity);
            if (deliverable <= 0) {
                continue;
            }
            long inserted = insert(player, material, deliverable, stackSize);
            if (inserted > 0) {
                store.debit(playerId, entry.currency(), inserted);
            }
        }

        return store.totalUnits(playerId);
    }

    /**
     * The universal pre-wager gate. Any nonzero balance in any currency
     * blocks any wager, so this first tries to clear the whole bank and only
     * then reports whether play may continue.
     *
     * @return {@code 0} when the player may wager, otherwise how many units
     *     still need room
     */
    public long clearForWager(Player player) {
        if (player == null) {
            return 0L;
        }
        if (!store.hasAnyBalance(player.getUniqueId())) {
            return 0L;
        }
        if (!settings().clearBankBeforeWager()) {
            // The automatic attempt is disabled, but a banked balance still
            // blocks play -- the player claims manually instead.
            return store.totalUnits(player.getUniqueId());
        }
        return claimAll(player);
    }

    public boolean isBlocked(UUID playerId) {
        return store.hasAnyBalance(playerId);
    }

    public long bankedUnits(UUID playerId) {
        return store.totalUnits(playerId);
    }

    // ---- physical movement ------------------------------------------------

    /**
     * Room for {@code material} across the storage grid, counting empty slots
     * at a full stack each and partial stacks of the same material by what
     * they can still take. Armor/offhand are excluded deliberately: currency
     * must not be stuffed into equipment slots.
     */
    static long freeCapacityUnits(PlayerInventory inventory, Material material, int stackSize) {
        if (inventory == null || material == null) {
            return 0L;
        }
        ItemStack[] contents = inventory.getStorageContents();
        if (contents == null) {
            return 0L;
        }
        long capacity = 0L;
        for (ItemStack stack : contents) {
            if (stack == null || stack.getType() == Material.AIR) {
                capacity += stackSize;
            } else if (stack.getType() == material) {
                capacity += Math.max(0, stack.getMaxStackSize() - stack.getAmount());
            }
        }
        return capacity;
    }

    /** @return how many units actually went in */
    private long insert(Player player, Material material, long units, int stackSize) {
        if (units <= 0) {
            return 0L;
        }
        PlayerInventory inventory = player.getInventory();
        long inserted = 0L;
        long remaining = units;

        while (remaining > 0) {
            int chunk = (int) Math.min(remaining, stackSize);
            Map<Integer, ItemStack> leftovers = inventory.addItem(new ItemStack(material, chunk));
            int rejected = 0;
            for (ItemStack leftover : leftovers.values()) {
                rejected += leftover.getAmount();
            }
            inserted += chunk - rejected;
            remaining -= chunk;
            if (rejected > 0) {
                // Inventory is full; further chunks would only bounce back.
                break;
            }
        }
        return inserted;
    }

    /** @return how many units actually hit the ground */
    private long drop(Player player, Material material, long units, int stackSize) {
        if (units <= 0 || player.getWorld() == null) {
            return 0L;
        }
        long dropped = 0L;
        long remaining = units;
        while (remaining > 0) {
            int chunk = (int) Math.min(remaining, stackSize);
            player.getWorld().dropItemNaturally(player.getLocation(), new ItemStack(material, chunk));
            dropped += chunk;
            remaining -= chunk;
        }
        return dropped;
    }

    private Material resolveMaterial(BankedCurrency currency) {
        if (currency.mode() == CurrencyMode.VAULT) {
            // Vault balances are numeric and never need physical space, so
            // they can never overflow into this system.
            return null;
        }
        return currency.material() == null ? null : Material.matchMaterial(currency.material());
    }

    /**
     * The currency's real stack size. The drop cap is expressed in stacks, so
     * a currency that stacks to 16 must not be treated as stacking to 64 --
     * that would quadruple the entity burst the cap exists to bound.
     */
    static int maxStackSize(Material material) {
        try {
            int size = material.getMaxStackSize();
            return size > 0 ? size : DEFAULT_STACK_SIZE;
        } catch (Throwable t) {
            // Resolving a stack size goes through the item registry, which is
            // not guaranteed to be available for every material on every
            // server build. A payout must never fail because of that, so fall
            // back to the vanilla default rather than propagating.
            return DEFAULT_STACK_SIZE;
        }
    }

    private OverflowPreference playerPreference(UUID playerId) {
        try {
            return plugin.getPreferences(playerId).getOverflowPreference();
        } catch (RuntimeException e) {
            // Preferences are best-effort here; a lookup failure must never
            // block a payout.
            return null;
        }
    }
}
