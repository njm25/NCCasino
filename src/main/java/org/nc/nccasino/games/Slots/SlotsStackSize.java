package org.nc.nccasino.games.Slots;

/**
 * Pure ItemStack-amount math for the four setting controls (Section 4 of the
 * control redesign): each control's current numeric value is echoed as its
 * visible stack size, in addition to its authoritative localized lore.
 *
 * <p>Height (1/3/5), Reels (3/5/7), and Paylines (1-9) are all always exactly
 * representable as a Minecraft stack amount, so those three simply clamp into
 * the legal [1, 64] stack range as a safety net. Per-Line Wager is the one
 * genuinely lossy case: a stack amount can never truthfully represent 1 (a
 * stack of 1 looks identical to "no value shown"), anything above 64, a
 * fractional amount, or a non-finite one -- {@link #forWager} falls back to a
 * stack size of 1 in every one of those cases specifically so no misleading
 * number is ever shown, and the localized lore remains the only authoritative
 * display of the actual wager.
 */
public final class SlotsStackSize {

    private static final int MIN_STACK = 1;
    private static final int MAX_STACK = 64;
    private static final double EPSILON = 1e-9;

    private SlotsStackSize() {
    }

    public static int forHeight(int visibleRows) {
        return clamp(visibleRows);
    }

    public static int forReels(int columns) {
        return clamp(columns);
    }

    public static int forPaylines(int activeLines) {
        return clamp(activeLines);
    }

    /**
     * The Ender Chest's stack amount: the exact saved-profile count for 2
     * through {@link #MAX_STACK}, and 1 for 0 or 1 profiles (a stack of 1
     * cannot distinguish "one profile" from "none", and both cases are
     * already stated authoritatively in the lore).
     *
     * @param profileCount how many profiles this player currently has saved
     */
    public static int forProfiles(int profileCount) {
        if (profileCount < 2) {
            return MIN_STACK;
        }
        return Math.min(MAX_STACK, profileCount);
    }

    /**
     * The Spin Limit entry's stack amount: the exact limit when it is a
     * representable 2-64, and 1 otherwise (unlimited, a limit of 1, or a
     * limit too large for a stack). As everywhere else here, the localized
     * lore stays the authoritative display.
     *
     * @param spinLimit the configured limit, or a negative value for unlimited
     */
    public static int forSpinLimit(long spinLimit) {
        if (spinLimit < 2 || spinLimit > MAX_STACK) {
            return MIN_STACK;
        }
        return (int) spinLimit;
    }

    /**
     * @param wager the current per-line wager, in whole currency units
     * @return {@code wager} rounded to the nearest int when that is an exact
     *     value in [2, 64]; {@code 1} otherwise (a wager of 1, above 64,
     *     fractional, or non-finite)
     */
    public static int forWager(double wager) {
        if (!Double.isFinite(wager)) {
            return 1;
        }
        long rounded = Math.round(wager);
        if (Math.abs(wager - rounded) > EPSILON) {
            return 1;
        }
        if (rounded < 2 || rounded > 64) {
            return 1;
        }
        return (int) rounded;
    }

    private static int clamp(int value) {
        return Math.max(MIN_STACK, Math.min(MAX_STACK, value));
    }
}
