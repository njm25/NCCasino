package org.nc.nccasino.games.Blackjack;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFactory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.nc.nccasino.Nccasino;
import org.nc.nccasino.currency.CurrencyManager;
import org.nc.nccasino.currency.CurrencyMode;
import org.nc.nccasino.currency.VaultCurrencyProvider;
import org.nc.nccasino.helpers.Preferences;
import org.nc.nccasino.localization.LocalizationService;
import org.nc.nccasino.payout.PendingPayoutStore;

import java.io.File;
import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Minimal, real-controller integration-test infrastructure for
 * {@link BlackjackInventory}. Every pure-logic delegate (BlackjackRules,
 * BlackjackSplitQueue, the animation-plan classes, etc.) already has its own
 * focused unit tests -- what none of those can catch is a defect in
 * {@code BlackjackInventory}'s own stateful control flow: phase transitions,
 * click routing, scheduler sequencing, view bootstrap, and dealer/controller
 * teardown. This harness constructs a genuine {@code BlackjackInventory}
 * against Mockito static mocks of {@code Bukkit}/{@code JavaPlugin} and a
 * deterministic fake scheduler (see {@link FakeScheduler}) -- MockBukkit was
 * not usable here (its published releases don't target the
 * {@code 1.21.11-R0.1-SNAPSHOT} Spigot API this project compiles against),
 * and this project has no other Bukkit test harness to build on.
 *
 * <p>The scheduler is the load-bearing piece: every {@code runTaskLater}/
 * {@code scheduleSyncRepeatingTask} call in production code is captured into
 * a tick-ordered queue instead of actually running on a background thread,
 * and only fires when a test calls {@link FakeScheduler#advance(long)} --
 * so a test can assert "nothing happened yet at tick 4" and "the expected
 * thing happened by tick 5" without any real sleep or timing dependency.
 *
 * <p>Deliberately not a full Bukkit simulation: only the surface
 * {@code BlackjackInventory}/{@code DealerInventory}/{@code BlackjackView}
 * actually touch is stubbed. Sounds are always off and messages are always
 * suppressed (see {@link #newPreferences()}) so tests assert on canonical
 * state and scheduler behavior, not on chat/sound side effects.
 */
final class BlackjackControllerTestSupport {

    private BlackjackControllerTestSupport() {
    }

    /** Full duration (with slack) of the game-reset white-tile sweep {@code resetGame()}/{@code cancelGame()} now play -- advance the scheduler past this before asserting canonical post-reset board state. */
    static final long RESET_SWEEP_TOTAL_TICKS = BlackjackResetSweepPlan.totalDurationTicks(
        BlackjackResetSweepPlan.build(BlackjackTiming.RESET_SWEEP_STEP_TICKS, BlackjackTiming.RESET_SWEEP_HOLD_DIAGONALS)
    ) + 2;

    /** One assembled test table: the live controller, its plugin/currency doubles, and the fake scheduler driving it. */
    static final class Harness implements AutoCloseable {
        final Nccasino plugin;
        final BlackjackInventory inventory;
        final FakeScheduler scheduler;
        final FakeVaultCurrencyProvider currencyProvider;
        final PendingPayoutStore pendingPayoutStore;
        /** Shared preferences mock every player resolves through {@code plugin.getPreferences(...)} -- re-stub {@code getMessageSetting()}/{@code getSoundSetting()} in a test that needs to observe messages/sounds (both default suppressed). */
        final Preferences preferences;
        final UUID dealerId;
        final String internalName;
        private final MockedStatic<Bukkit> bukkitStatic;
        private final MockedStatic<JavaPlugin> javaPluginStatic;
        private final Map<UUID, Player> onlinePlayers;
        private final Map<UUID, String> everRegisteredNames;

        private Harness(
            Nccasino plugin, BlackjackInventory inventory, FakeScheduler scheduler,
            FakeVaultCurrencyProvider currencyProvider, PendingPayoutStore pendingPayoutStore, Preferences preferences,
            UUID dealerId, String internalName, Map<UUID, Player> onlinePlayers, Map<UUID, String> everRegisteredNames,
            MockedStatic<Bukkit> bukkitStatic, MockedStatic<JavaPlugin> javaPluginStatic
        ) {
            this.plugin = plugin;
            this.inventory = inventory;
            this.scheduler = scheduler;
            this.currencyProvider = currencyProvider;
            this.pendingPayoutStore = pendingPayoutStore;
            this.preferences = preferences;
            this.dealerId = dealerId;
            this.internalName = internalName;
            this.onlinePlayers = onlinePlayers;
            this.everRegisteredNames = everRegisteredNames;
            this.bukkitStatic = bukkitStatic;
            this.javaPluginStatic = javaPluginStatic;
        }

        /** Registers {@code player} as online and resolvable via Bukkit.getPlayer/getOnlinePlayers -- does not open the table (see {@link #openTable}). */
        Player registerOnlinePlayer(UUID playerId, String name) {
            Player player = fakePlayer(playerId, name);
            onlinePlayers.put(playerId, player);
            everRegisteredNames.put(playerId, name);
            return player;
        }

        /** {@link #registerOnlinePlayer} plus immediately opening the table for them -- the common case for a test that just needs a seated-and-viewing player. */
        Player seatOnlinePlayer(UUID playerId, String name) {
            Player player = registerOnlinePlayer(playerId, name);
            openTable(player);
            return player;
        }

        /** Marks a previously-online player as disconnected -- Bukkit.getPlayer now returns null for them, matching a real logout. */
        void markOffline(UUID playerId) {
            onlinePlayers.remove(playerId);
        }

        /**
         * Marks {@code player} offline the way production code actually
         * checks it -- {@code Bukkit.getPlayer(id)} still resolves them
         * (still tracked in {@code playerSeats}, still renderable), but
         * their own {@code isOnline()} now reports {@code false}, exactly
         * like the brief real window between a client dropping and the
         * server fully forgetting them. Prefer this over {@link #markOffline}
         * when the scenario needs the seat to still exist and be rendered
         * (e.g. a teardown mid-round) rather than simulating a player who
         * has already been fully cleaned up.
         */
        void setOnline(Player player, boolean online) {
            when(player.isOnline()).thenReturn(online);
        }

        Player onlinePlayer(UUID playerId) {
            return onlinePlayers.get(playerId);
        }

        /**
         * Genuinely opens the table for {@code player} the same way
         * {@code DealerInteractListener} does in production
         * ({@code player.openInventory(blackjack.getOrCreateView(player))}),
         * then fires the {@code InventoryOpenEvent}-triggered
         * {@code onViewOpened} callback that real event dispatch would have
         * -- there is no real Bukkit event bus in this harness, so this
         * substitutes for it. Advances the scheduler 2 ticks to flush the
         * one-time {@code initializeGameMenu()}/chair-guidance scheduling
         * {@code onViewOpened} itself schedules on first open. Without this,
         * the shared inventory's seat slots are never painted with a
         * clickable stairs item, and every chair click silently no-ops.
         */
        void openTable(Player player) {
            inventory.getOrCreateView(player);
            inventory.onViewOpened(player);
            scheduler.advance(2);
        }

        /** Drives a click at {@code slot} through the exact same InventoryClickEvent-shaped entry point production code uses. */
        void click(Player player, int slot) {
            InventoryClickEvent event = mock(InventoryClickEvent.class);
            when(event.getClickedInventory()).thenReturn(inventory.getInventory());
            inventory.handleClick(slot, player, event);
        }

        /**
         * Steps the scheduler forward {@code stepTicks} at a time (never one
         * giant {@code advance}, which would just as easily race past the
         * player-turn timer's own auto-Stand timeout and finish the whole
         * round) until a genuinely actionable decision exists, or gives up
         * after {@code maxSteps}. Callers should stack a deterministic,
         * non-ace/non-ten-value deck first (see the flat-rank helpers in
         * each test) so no insurance phase or natural blackjack interrupts
         * the trip to an actionable turn.
         */
        void advanceToActionableTurn(long stepTicks, int maxSteps) {
            for (int i = 0; i < maxSteps; i++) {
                if (inventory.currentPlayerIdForTest() != null && inventory.turnTimerSecondsRemainingForTest() > 0) {
                    return;
                }
                scheduler.advance(stepTicks);
            }
        }

        @Override
        public void close() {
            // SessionRegistry and DealerInventory.inventories are both
            // static/global, process-wide registries -- a test that seats a
            // player (or never calls delete()) leaves this harness's
            // BlackjackInventory reachable from them indefinitely, past the
            // point where this harness's own static Bukkit/JavaPlugin mocks
            // are closed below. A later, unrelated test that happens to
            // touch that stale registration (e.g. SessionRegistry.terminateAll)
            // would then run production code against a dead mock plugin and
            // hit real, unmocked Bukkit statics. Unregister everything this
            // harness ever added before tearing down its own mocks.
            for (UUID playerId : inventory.seatedPlayerIdsForTest()) {
                org.nc.nccasino.session.SessionRegistry.unregister(playerId, inventory);
            }
            org.nc.nccasino.entities.DealerInventory.inventories.remove(dealerId);
            bukkitStatic.close();
            javaPluginStatic.close();
        }
    }

    static Harness newHarness() {
        return newHarness(new HashMap<>());
    }

    /** @param dealerConfig pre-seeded {@code dealers.<name>.<key>} entries (dotted key -> value), applied before construction. */
    static Harness newHarness(Map<String, Object> dealerConfig) {
        return newHarness(dealerConfig, CurrencyMode.VAULT);
    }

    /**
     * @param dealerConfig pre-seeded {@code dealers.<name>.<key>} entries (dotted key -> value), applied before construction.
     * @param currencyMode the {@code BlackjackInventory}-internal currency mode ({@code plugin.getCurrencyMode(internalName)}) --
     *     independent of the actual funds-movement provider below, which always behaves like an exact-decimal Vault
     *     regardless of this value (see {@code FakeVaultCurrencyProvider}). This is enough to exercise every
     *     currency-mode-*decision* branch (insurance offer rounding, eligibility, display formatting) without needing
     *     a genuine physical-item inventory simulation -- a debit of any whole-unit amount this mode's rounding
     *     produces still succeeds exactly, the same way it would need to for a real physical currency.
     */
    static Harness newHarness(Map<String, Object> dealerConfig, CurrencyMode currencyMode) {
        UUID dealerId = UUID.randomUUID();
        String internalName = "test-table";

        MockedStatic<Bukkit> bukkitStatic = Mockito.mockStatic(Bukkit.class);
        MockedStatic<JavaPlugin> javaPluginStatic = Mockito.mockStatic(JavaPlugin.class);

        // Every mock/stub referenced below is built into a plain local
        // variable FIRST, then wired into the static Bukkit stub as a
        // separate statement -- building it inline as the argument to
        // .thenReturn(...) nests one Mockito stubbing call inside another
        // still-in-progress one (Bukkit::getScheduler's own), which trips
        // Mockito's ongoing-stubbing tracker (UnfinishedStubbingException)
        // even though each stub is individually well-formed.
        FakeScheduler scheduler = new FakeScheduler();
        BukkitScheduler schedulerMock = scheduler.asBukkitScheduler();
        bukkitStatic.when(Bukkit::getScheduler).thenReturn(schedulerMock);

        PluginManager pluginManager = mock(PluginManager.class);
        bukkitStatic.when(Bukkit::getPluginManager).thenReturn(pluginManager);

        bukkitStatic.when(() -> Bukkit.createInventory(any(), anyInt(), org.mockito.ArgumentMatchers.nullable(String.class)))
            .thenAnswer(inv -> fakeInventory(inv.getArgument(1)));
        // Real ItemStack#getItemMeta()/setItemMeta() delegate to
        // Bukkit.getItemFactory() (see ItemStack's own bytecode -- there is
        // no server-independent fallback), so every single rendered item in
        // BlackjackInventory needs this to round-trip correctly, not just
        // return non-null once.
        ItemFactory itemFactory = fakeItemFactory();
        bukkitStatic.when(Bukkit::getItemFactory).thenReturn(itemFactory);
        // Newer Bukkit API surfaces (Attribute, Sound's registry-backed
        // constants, etc.) are interfaces whose enum-like constants are
        // bootstrapped through Bukkit.getRegistry(Class) the first time the
        // owning class loads -- confirmed against the real spigot-api
        // bytecode (Registry.ATTRIBUTE = requireNonNull(Bukkit.getRegistry
        // (Attribute.class), ...)). Unstubbed, that first reference throws
        // ExceptionInInitializerError the moment any rendering code touches
        // Attribute (e.g. the Hit action's sword icon), regardless of
        // anything else in this harness. A single generic, type-aware fake
        // registry answers any such lookup with a fresh mock of whatever
        // type was requested.
        bukkitStatic.when(() -> Bukkit.getRegistry(any())).thenAnswer(inv -> fakeRegistryFor(inv.getArgument(0)));

        Map<UUID, Player> onlineRegistry = new HashMap<>();
        bukkitStatic.when(() -> Bukkit.getPlayer(any(UUID.class)))
            .thenAnswer(inv -> onlineRegistry.get((UUID) inv.getArgument(0)));
        bukkitStatic.when(Bukkit::getOnlinePlayers).thenAnswer(inv -> new ArrayList<>(onlineRegistry.values()));
        // Never pruned on markOffline (unlike onlineRegistry) -- mirrors real
        // Bukkit.getOfflinePlayer, which keeps resolving a name for anyone
        // who was ever seen, even long after they've disconnected.
        Map<UUID, String> everRegisteredNames = new HashMap<>();
        bukkitStatic.when(() -> Bukkit.getOfflinePlayer(any(UUID.class))).thenAnswer(inv -> {
            UUID id = inv.getArgument(0);
            OfflinePlayer offlinePlayer = mock(OfflinePlayer.class);
            when(offlinePlayer.getUniqueId()).thenReturn(id);
            when(offlinePlayer.getName()).thenReturn(everRegisteredNames.get(id));
            return offlinePlayer;
        });

        org.bukkit.configuration.file.FileConfiguration config = new org.bukkit.configuration.file.YamlConfiguration();
        for (Map.Entry<String, Object> entry : dealerConfig.entrySet()) {
            config.set("dealers." + internalName + "." + entry.getKey(), entry.getValue());
        }

        Nccasino plugin = mock(Nccasino.class);
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getLogger("BlackjackControllerTest"));
        when(plugin.getCurrencyMode(anyString())).thenReturn(currencyMode);
        when(plugin.getCurrencyName(anyString())).thenReturn("Dollar");
        when(plugin.getCurrency(anyString())).thenReturn(org.bukkit.Material.EMERALD);
        when(plugin.formatWagerDisplay(any(CurrencyMode.class), anyString(), org.mockito.ArgumentMatchers.anyDouble()))
            .thenAnswer(inv -> "$" + inv.getArgument(2));
        // Fixed chip denominations 1/5/10/25/100 -- ChipSlots.assign sorts
        // ascending, so these land at slots 47-51 in that order. Stubbed
        // here (construction time) rather than left at the mock's default
        // 0.0, since BlackjackInventory's constructor loads and caches
        // chipValues immediately via loadChipValuesFromConfig -- a test
        // stubbing this afterward on an already-built Harness would have no
        // effect.
        when(plugin.getChipValue(anyString(), anyInt())).thenAnswer(inv -> {
            int index = inv.getArgument(1);
            return switch (index) {
                case 1 -> 1.0;
                case 2 -> 5.0;
                case 3 -> 10.0;
                case 4 -> 25.0;
                default -> 100.0;
            };
        });
        when(plugin.getChipDisplayName(any(CurrencyMode.class), anyString(), org.mockito.ArgumentMatchers.anyDouble()))
            .thenAnswer(inv -> "$" + inv.getArgument(2));

        LocalizationService localization = mock(LocalizationService.class);
        when(localization.getServerDefault()).thenReturn("en_US");
        when(localization.text(any(Player.class), anyString(), any(Object[].class))).thenAnswer(inv -> inv.getArgument(1));
        when(localization.text(anyString(), anyString(), any(Map.class))).thenAnswer(inv -> inv.getArgument(1));
        // The DealerInventory/BlackjackView constructors resolve their
        // Bukkit inventory *title* through this 2-arg overload (locale,
        // key) -- unstubbed it returns null, which Bukkit.createInventory
        // then also fails to match against an anyString()-based matcher
        // (Mockito's anyString() never matches null), silently leaving
        // this.inventory null and NPEing on the very first click.
        when(localization.text(anyString(), anyString())).thenAnswer(inv -> inv.getArgument(1));
        when(plugin.getLocalization()).thenReturn(localization);

        Preferences preferences = mock(Preferences.class);
        when(preferences.getSoundSetting()).thenReturn(Preferences.SoundSetting.OFF);
        when(preferences.getMessageSetting()).thenReturn(Preferences.MessageSetting.NONE);
        when(plugin.getPreferences(any(UUID.class))).thenReturn(preferences);

        javaPluginStatic.when(() -> JavaPlugin.getProvidingPlugin(any())).thenAnswer(inv -> plugin);

        File tempDir = createTempDataFolder();
        when(plugin.getDataFolder()).thenReturn(tempDir);
        PendingPayoutStore pendingPayoutStore = new PendingPayoutStore(plugin);
        when(plugin.getPendingPayoutStore()).thenReturn(pendingPayoutStore);

        FakeVaultCurrencyProvider currencyProvider = new FakeVaultCurrencyProvider();
        CurrencyManager currencyManager = mock(CurrencyManager.class);
        when(currencyManager.getProvider(anyString())).thenReturn(currencyProvider);
        when(plugin.getCurrencyManager()).thenReturn(currencyManager);

        BlackjackInventory inventory = new BlackjackInventory(dealerId, plugin, internalName);
        org.nc.nccasino.entities.DealerInventory.inventories.put(dealerId, inventory);

        return new Harness(
            plugin, inventory, scheduler, currencyProvider, pendingPayoutStore, preferences, dealerId, internalName,
            onlineRegistry, everRegisteredNames, bukkitStatic, javaPluginStatic
        );
    }

    private static File createTempDataFolder() {
        try {
            File dir = java.nio.file.Files.createTempDirectory("nccasino-blackjack-test").toFile();
            dir.deleteOnExit();
            return dir;
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    // --- Fakes -------------------------------------------------------------

    /**
     * A generic {@link org.bukkit.Registry} that answers any lookup with a
     * fresh Mockito mock of whatever {@code type} it was created for,
     * cached by key so the same {@link org.bukkit.NamespacedKey} always
     * resolves to the same instance. See the "Newer Bukkit API surfaces"
     * comment where this is wired to {@code Bukkit.getRegistry(Class)}.
     */
    static org.bukkit.Registry<?> fakeRegistryFor(Class<?> type) {
        org.bukkit.Registry<?> registry = mock(org.bukkit.Registry.class);
        Map<Object, Object> cache = new HashMap<>();
        when(registry.get(any())).thenAnswer(inv -> cache.computeIfAbsent(inv.getArgument(0), k -> mock(type)));
        when(registry.getOrThrow(any())).thenAnswer(inv -> cache.computeIfAbsent(inv.getArgument(0), k -> mock(type)));
        return registry;
    }

    static Player fakePlayer(UUID playerId, String name) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.getName()).thenReturn(name);
        when(player.isOnline()).thenReturn(true);
        PlayerInventory playerInventory = mock(PlayerInventory.class);
        when(player.getInventory()).thenReturn(playerInventory);
        World world = mock(World.class);
        Location location = mock(Location.class);
        when(location.getWorld()).thenReturn(world);
        when(player.getWorld()).thenReturn(world);
        when(player.getLocation()).thenReturn(location);
        Firework firework = mock(Firework.class);
        FireworkMeta fireworkMeta = mock(FireworkMeta.class);
        when(firework.getFireworkMeta()).thenReturn(fireworkMeta);
        when(world.spawnEntity(any(Location.class), any(org.bukkit.entity.EntityType.class))).thenAnswer(inv -> {
            org.bukkit.entity.EntityType type = inv.getArgument(1);
            return type == org.bukkit.entity.EntityType.FIREWORK_ROCKET ? firework : mock(Entity.class);
        });
        return player;
    }

    static Inventory fakeInventory(int size) {
        ItemStack[] contents = new ItemStack[size];
        Inventory inv = mock(Inventory.class);
        when(inv.getSize()).thenReturn(size);
        when(inv.getItem(anyInt())).thenAnswer(i -> {
            int slot = i.getArgument(0);
            return slot >= 0 && slot < size ? contents[slot] : null;
        });
        doAnswer(i -> {
            int slot = i.getArgument(0);
            ItemStack item = i.getArgument(1);
            if (slot >= 0 && slot < size) {
                contents[slot] = item;
            }
            return null;
        }).when(inv).setItem(anyInt(), any());
        doAnswer(i -> {
            java.util.Arrays.fill(contents, null);
            return null;
        }).when(inv).clear();
        when(inv.getContents()).thenAnswer(i -> contents.clone());
        return inv;
    }

    /**
     * A real {@link ItemStack}'s getItemMeta/setItemMeta delegate entirely
     * to {@code Bukkit.getItemFactory()} (confirmed against the actual
     * spigot-api bytecode -- there is no meta-less fallback path), so every
     * rendered item in BlackjackInventory needs this factory to produce a
     * working, round-tripping meta, not just a non-null one. getItemMeta()
     * clones whatever setItemMeta() last stored, so clone() must preserve
     * state (specifically SkullMeta's owner, which isPlayerHeadSlot/
     * handleLeaveChair depend on to identify a clicked seat as the clicking
     * player's own head).
     */
    static ItemFactory fakeItemFactory() {
        ItemFactory factory = mock(ItemFactory.class);
        when(factory.getItemMeta(any(Material.class))).thenAnswer(inv -> {
            Material type = inv.getArgument(0);
            return isSkullType(type) ? newFakeSkullMeta(null, null) : newFakeItemMeta(null);
        });
        when(factory.isApplicable(any(), any(Material.class))).thenReturn(true);
        when(factory.asMetaFor(any(ItemMeta.class), any(Material.class))).thenAnswer(inv -> inv.getArgument(0));
        return factory;
    }

    private static boolean isSkullType(Material type) {
        return type == Material.PLAYER_HEAD || type.name().endsWith("_HEAD") || type.name().endsWith("_SKULL");
    }

    private static SkullMeta newFakeSkullMeta(OfflinePlayer initialOwner, String initialDisplayName) {
        return newFakeSkullMeta(initialOwner, initialDisplayName, null, false, null);
    }

    private static SkullMeta newFakeSkullMeta(OfflinePlayer initialOwner, String initialDisplayName, Boolean initialGlint, boolean initialEnchanted, List<String> initialLore) {
        SkullMeta meta = mock(SkullMeta.class);
        Object[] owner = {initialOwner};
        Object[] displayName = {initialDisplayName};
        Object[] glint = {initialGlint};
        Object[] enchanted = {initialEnchanted};
        Object[] lore = {initialLore};
        doAnswer(inv -> {
            owner[0] = inv.getArgument(0);
            return null;
        }).when(meta).setOwningPlayer(any());
        when(meta.getOwningPlayer()).thenAnswer(inv -> owner[0]);
        when(meta.hasOwner()).thenAnswer(inv -> owner[0] != null);
        wireCommonMeta(meta, displayName, glint, enchanted, lore);
        when(meta.clone()).thenAnswer(inv ->
            newFakeSkullMeta((OfflinePlayer) owner[0], (String) displayName[0], (Boolean) glint[0], (boolean) enchanted[0], copyLore(lore[0])));
        return meta;
    }

    private static ItemMeta newFakeItemMeta(String initialDisplayName) {
        return newFakeItemMeta(initialDisplayName, null, false, null);
    }

    private static ItemMeta newFakeItemMeta(String initialDisplayName, Boolean initialGlint, boolean initialEnchanted, List<String> initialLore) {
        ItemMeta meta = mock(ItemMeta.class);
        Object[] displayName = {initialDisplayName};
        Object[] glint = {initialGlint};
        Object[] enchanted = {initialEnchanted};
        Object[] lore = {initialLore};
        wireCommonMeta(meta, displayName, glint, enchanted, lore);
        when(meta.clone()).thenAnswer(inv -> newFakeItemMeta((String) displayName[0], (Boolean) glint[0], (boolean) enchanted[0], copyLore(lore[0])));
        return meta;
    }

    @SuppressWarnings("unchecked")
    private static List<String> copyLore(Object lore) {
        return lore == null ? null : new ArrayList<>((List<String>) lore);
    }

    /**
     * Wires display name, enchant-glint-override, "has any enchant", and
     * lore state that survives {@code clone()} -- a real
     * ItemStack#getItemMeta() clones whatever setItemMeta() last stored, so
     * any test that wants to observe glow or lore must read it back through
     * this, not through Mockito's invocation history on the pre-clone meta.
     * Two independent glow mechanisms exist in production: {@code applyGlow}'s
     * setEnchantmentGlintOverride (chair/wager/action guidance's own
     * flashing) and {@code createEnchantedItem}'s addEnchant (canonical
     * "selected" glints, e.g. a chosen chip/All In) -- both are tracked here,
     * alongside lore (used by the "Currently Selected" subtitle).
     */
    @SuppressWarnings("unchecked")
    private static void wireCommonMeta(ItemMeta meta, Object[] displayName, Object[] glint, Object[] enchanted, Object[] lore) {
        doAnswer(inv -> {
            displayName[0] = inv.getArgument(0);
            return null;
        }).when(meta).setDisplayName(any());
        when(meta.getDisplayName()).thenAnswer(inv -> displayName[0]);
        doAnswer(inv -> {
            glint[0] = inv.getArgument(0);
            return null;
        }).when(meta).setEnchantmentGlintOverride(any());
        when(meta.getEnchantmentGlintOverride()).thenAnswer(inv -> glint[0]);
        when(meta.hasEnchantmentGlintOverride()).thenAnswer(inv -> glint[0] != null);
        doAnswer(inv -> {
            enchanted[0] = true;
            return null;
        }).when(meta).addEnchant(any(), anyInt(), org.mockito.ArgumentMatchers.anyBoolean());
        when(meta.hasEnchants()).thenAnswer(inv -> enchanted[0]);
        when(meta.hasEnchant(any())).thenAnswer(inv -> enchanted[0]);
        doAnswer(inv -> {
            List<String> value = inv.getArgument(0);
            lore[0] = copyLore(value);
            return null;
        }).when(meta).setLore(any());
        when(meta.getLore()).thenAnswer(inv -> copyLore(lore[0]));
        // addItemFlags(ItemFlag...) is void -- Mockito's default no-op is already correct, nothing to stub.
    }

    /** Same override pattern BlackjackWagerTransactionTest's FakeVaultCurrencyProvider uses: only the decimal-aware entry points are overridden, so the real provider-dispatch in BlackjackInventory is genuinely exercised. */
    static final class FakeVaultCurrencyProvider extends VaultCurrencyProvider {
        private BigDecimal balance = BigDecimal.ZERO;
        private boolean nextDepositSucceeds = true;
        private boolean nextWithdrawSucceeds = true;
        final List<BigDecimal> depositAttempts = new ArrayList<>();
        final List<BigDecimal> withdrawAttempts = new ArrayList<>();

        FakeVaultCurrencyProvider() {
            super(null);
        }

        void setBalance(double amount) {
            this.balance = BigDecimal.valueOf(amount);
        }

        void setNextDepositSucceeds(boolean succeeds) {
            this.nextDepositSucceeds = succeeds;
        }

        void setNextWithdrawSucceeds(boolean succeeds) {
            this.nextWithdrawSucceeds = succeeds;
        }

        /**
         * The real {@link VaultCurrencyProvider#getBalance} routes through
         * {@code plugin.getVaultHook().getEconomy()}, which is null in this
         * harness (constructed with {@code super(null)}) -- without this
         * override it would always report 0, silently blocking every All In
         * test (getPlayerTotalBalance reads exactly this method) regardless
         * of the fake balance actually set via {@link #setBalance}.
         */
        @Override
        public int getBalance(Player player, String internalName) {
            return balance.compareTo(BigDecimal.ZERO) <= 0 ? 0 : balance.intValue();
        }

        @Override
        public boolean deposit(Player player, String internalName, BigDecimal amount) {
            depositAttempts.add(amount);
            if (!nextDepositSucceeds) {
                return false;
            }
            balance = balance.add(amount);
            return true;
        }

        @Override
        public boolean hasAtLeastDecimal(Player player, String internalName, BigDecimal amount) {
            return balance.compareTo(amount) >= 0;
        }

        @Override
        public boolean withdrawDecimal(Player player, String internalName, BigDecimal amount) {
            withdrawAttempts.add(amount);
            if (!nextWithdrawSucceeds) {
                return false;
            }
            balance = balance.subtract(amount);
            return true;
        }
    }

    /**
     * Deterministic stand-in for {@link BukkitScheduler}: every
     * runTask/runTaskLater/scheduleSyncRepeatingTask call is captured into a
     * tick-ordered queue instead of running on any real thread or timer.
     * Nothing fires until a test calls {@link #advance(long)}, and tasks due
     * at the same tick run in the order they were scheduled (FIFO), matching
     * single-threaded Bukkit main-thread semantics closely enough for these
     * tests. cancelTask marks a task id inert; a repeating task re-queues
     * itself for {@code current + period} after each run unless it was
     * cancelled during that same run (mirrors BukkitScheduler letting a task
     * cancel itself mid-run).
     */
    static final class FakeScheduler {
        private long currentTick = 0;
        private int nextTaskId = 1;
        private long sequenceCounter = 0;
        private final PriorityQueue<ScheduledTask> pending = new PriorityQueue<>(
            Comparator.<ScheduledTask>comparingLong(t -> t.fireAtTick).thenComparingLong(t -> t.sequence)
        );
        private final java.util.Set<Integer> cancelled = new java.util.HashSet<>();
        private BukkitScheduler mock;

        private static final class ScheduledTask {
            final int id;
            final long fireAtTick;
            final long periodTicks; // <= 0 means one-shot
            final Runnable runnable;
            final long sequence;

            ScheduledTask(int id, long fireAtTick, long periodTicks, Runnable runnable, long sequence) {
                this.id = id;
                this.fireAtTick = fireAtTick;
                this.periodTicks = periodTicks;
                this.runnable = runnable;
                this.sequence = sequence;
            }
        }

        BukkitScheduler asBukkitScheduler() {
            if (mock != null) {
                return mock;
            }
            mock = mock(BukkitScheduler.class);
            doAnswer(inv -> {
                schedule(inv.getArgument(1), 0L, -1L);
                return null;
            }).when(mock).runTask(any(), any(Runnable.class));
            doAnswer(inv -> {
                schedule(inv.getArgument(1), inv.getArgument(2), -1L);
                return null;
            }).when(mock).runTaskLater(any(), any(Runnable.class), anyLong());
            doAnswer(inv ->
                schedule(inv.getArgument(1), inv.getArgument(2), inv.getArgument(3))
            ).when(mock).scheduleSyncRepeatingTask(any(), any(Runnable.class), anyLong(), anyLong());
            doAnswer(inv -> {
                cancelled.add((Integer) inv.getArgument(0));
                return null;
            }).when(mock).cancelTask(anyInt());
            return mock;
        }

        private synchronized int schedule(Runnable runnable, long delay, long period) {
            int id = nextTaskId++;
            long fireAt = currentTick + Math.max(delay, 0);
            pending.add(new ScheduledTask(id, fireAt, period, runnable, sequenceCounter++));
            return id;
        }

        /** Runs every task due at or before {@code currentTick + ticks}, advancing the clock exactly that far. */
        void advance(long ticks) {
            long target = currentTick + ticks;
            while (true) {
                ScheduledTask next;
                synchronized (this) {
                    next = pending.peek();
                    if (next == null || next.fireAtTick > target) {
                        break;
                    }
                    pending.poll();
                    currentTick = next.fireAtTick;
                }
                if (!cancelled.contains(next.id)) {
                    next.runnable.run();
                    if (next.periodTicks > 0 && !cancelled.contains(next.id)) {
                        synchronized (this) {
                            pending.add(new ScheduledTask(next.id, currentTick + next.periodTicks, next.periodTicks, next.runnable, sequenceCounter++));
                        }
                    }
                }
            }
            currentTick = target;
        }

        long currentTick() {
            return currentTick;
        }

        int pendingCount() {
            return pending.size();
        }
    }
}
