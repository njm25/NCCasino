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
            return new ItemDeliveryOutcome(amount, 0L, 0L, 0L, amount);
        }

        Material material = resolveMaterial(currency);
        if (material == null) {
            // Without a material nothing can be handed over, but the debt is
            // real -- bank it so it survives for manual reconciliation
            // rather than evaporating.
            boolean banked = store.credit(player.getUniqueId(), currency, amount);
            return new ItemDeliveryOutcome(amount, 0L, 0L, banked ? amount : 0L, banked ? 0L : amount);
        }

        OverflowSettings config = settings();
        OverflowPreference preference = config.effectivePreference(playerPreference(player.getUniqueId()));
        int stackSize = maxStackSize(material);
        long dropCapUnits = (long) config.maxDropStacks() * stackSize;

        long capacity = freeCapacityUnits(player.getInventory(), material, stackSize);
        ItemDeliveryPlan plan = ItemDeliveryPlanner.plan(amount, capacity, preference, dropCapUnits);

        long inserted = insert(player, material, plan.toInventory(), stackSize);
        // The capacity probe and the actual insert run in the same tick, so a
        // shortfall should be impossible; if Bukkit still refuses part of it,
        // push the difference back into the overflow rather than losing it.
        long shortfall = plan.toInventory() - inserted;

        long dropTarget = plan.toDrop();
        long bankTarget = plan.toBank();
        if (shortfall > 0) {
            if (preference == OverflowPreference.DROP) {
                long extraDropRoom = Math.max(0L, dropCapUnits - dropTarget);
                long extraDrop = Math.min(shortfall, extraDropRoom);
                dropTarget += extraDrop;
                shortfall -= extraDrop;
            }
            bankTarget += shortfall;
        }

        long dropped = drop(player, material, dropTarget, stackSize);
        long undropped = dropTarget - dropped;
        bankTarget += undropped;

        long banked = 0L;
        long unsettled = 0L;
        if (bankTarget > 0) {
            if (store.credit(player.getUniqueId(), currency, bankTarget)) {
                banked = bankTarget;
            } else {
                unsettled = bankTarget;
                plugin.getLogger().severe("[NCCasino] Overflow bank write failed for " + player.getUniqueId()
                    + "; " + bankTarget + " " + currency.storageKey() + " remains UNSETTLED and the paying game"
                    + " must retain the obligation.");
            }
        }

        return new ItemDeliveryOutcome(amount, inserted, dropped, banked, unsettled);
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
