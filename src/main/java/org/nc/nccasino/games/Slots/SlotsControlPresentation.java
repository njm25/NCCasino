package org.nc.nccasino.games.Slots;

import org.bukkit.Material;

/**
 * The production source of truth for every named, fixed-material Slots
 * control: its material, and whether it is one of the approved explicit-glint
 * roles. {@link SlotsMachine}'s rendering consumes this directly rather than
 * inlining a {@code Material.XXX} literal per call site, and
 * {@code SlotsGlintPolicyTest} inspects these exact same values -- so if a
 * future change reverted a control back to an inherently-glinting material
 * (as {@code ENCHANTED_BOOK}/{@code KNOWLEDGE_BOOK} once were), the test
 * would be inspecting production's actual current choice and would catch it,
 * rather than a hardcoded literal the test happens to still agree with.
 *
 * <p>Glint in this UI is reserved for exactly three roles: a ready real Spin
 * (here, {@link Role#SPIN_READY}), and a matched paid or matched hypothetical
 * Demo winning symbol -- the latter two are deliberately absent from this
 * catalog, because their material is whichever {@link SlotsSymbol} matched,
 * not a fixed control material; see {@code SlotsMachine.highlightLine}. Every
 * role listed here must render without any inherent glint, and none of them
 * may ever be passed to {@code setGlowingItem}.
 */
public final class SlotsControlPresentation {

    private SlotsControlPresentation() {
    }

    public enum Role {
        /** The only fixed-material control approved to explicitly glint (via {@code setGlowingItem}). */
        SPIN_READY(Material.LEVER, true),
        /** The lever while a paid or Demo Spin presentation is running -- an ordinary left-click fast-forwards it. */
        SPIN_ACTIVE(Material.LEVER, false),
        SPIN_LOCKED(Material.REDSTONE_BLOCK, false),
        PAYOUT_BLOCKED(Material.REDSTONE_BLOCK, false),
        GUIDE_BOOK(Material.BOOK, false),
        /** Slot 48 in Game View: opens the Paytable. */
        PAYTABLE_OPEN(Material.BOOK, false),
        /**
         * NCCasino's established Return/Back material, swapped in place over
         * whichever single bottom-row slot the open modal view owns -- 48 for
         * Paytable, 50 for Auto Spin Settings, 53 for Profiles; see
         * {@link SlotsUiView#backToGameSlot()}.
         */
        BACK_TO_GAME(Material.MAGENTA_GLAZED_TERRACOTTA, false),
        NEUTRAL_CELL(Material.WHITE_STAINED_GLASS_PANE, false),
        HEIGHT_CONTROL(Material.PINK_STAINED_GLASS_PANE, false),
        REELS_CONTROL(Material.BROWN_STAINED_GLASS_PANE, false),
        PAYLINES_CONTROL(Material.GREEN_STAINED_GLASS_PANE, false),
        WAGER_CONTROL(Material.BLACK_STAINED_GLASS_PANE, false),
        EXIT_CONTROL(Material.SPRUCE_DOOR, false),
        /** Slot 50, every Auto Spin state -- the material stays CLOCK; only name/lore distinguishes them. */
        AUTO_SPIN_CONTROL(Material.CLOCK, false),
        /** Slot 53 in Game View: saves, and opens, this player's globally-portable Slots profiles. */
        PROFILES_CONTROL(Material.ENDER_CHEST, false),
        /** One saved profile in Profiles View -- a named, loadable configuration. */
        PROFILE_ENTRY(Material.NAME_TAG, false),
        /** The deliberately un-control-like material of the Paytable's slot 36-44 information rail. */
        INFO_RAIL(Material.LIGHT_GRAY_STAINED_GLASS_PANE, false),
        /** The Auto Spin Settings menu's blank backdrop -- never a reel cell, never clickable. */
        AUTO_SETTINGS_BACKDROP(Material.LIGHT_GRAY_STAINED_GLASS_PANE, false),
        /** Auto Spin Settings slot 4: the menu's own title/instructions card. */
        AUTO_SETTINGS_OVERVIEW(Material.CLOCK, false),
        /** Auto Spin Settings slot 11: how many spins one Auto Spin batch may commit. */
        AUTO_SETTINGS_SPIN_LIMIT(Material.REPEATER, false),
        /** Auto Spin Settings slot 22: commit the settings, return to Game, and start the batch. */
        AUTO_SETTINGS_START(Material.LEVER, false),
        /** Auto Spin Settings slot 33: restore every Auto Spin default (never the gameplay speed). */
        AUTO_SETTINGS_RESET(Material.BARRIER, false),
        /** Auto Spin Settings slot 13 while Stop on Any Win is on. */
        AUTO_SETTINGS_ANY_WIN_ON(Material.LIME_DYE, false),
        /** Auto Spin Settings slot 15 while the Big-Win Multiplier is on. */
        AUTO_SETTINGS_BIG_WIN_ON(Material.GOLD_INGOT, false),
        /** Auto Spin Settings slot 29 while the Profit Target is on. */
        AUTO_SETTINGS_PROFIT_ON(Material.EMERALD, false),
        /** Auto Spin Settings slot 31 while the Loss Limit is on. */
        AUTO_SETTINGS_LOSS_ON(Material.REDSTONE, false),
        /** Any Auto Spin Settings entry that is currently switched off. */
        AUTO_SETTINGS_OFF(Material.GRAY_DYE, false);

        private final Material material;
        private final boolean approvedToGlint;

        Role(Material material, boolean approvedToGlint) {
            this.material = material;
            this.approvedToGlint = approvedToGlint;
        }

        public Material material() {
            return material;
        }

        /** Whether this role is one of the approved explicit-glint roles. */
        public boolean approvedToGlint() {
            return approvedToGlint;
        }
    }
}
