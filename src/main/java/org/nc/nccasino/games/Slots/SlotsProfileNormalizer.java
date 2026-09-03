package org.nc.nccasino.games.Slots;

/**
 * Fits a globally-saved {@link SlotsProfile} onto whatever machine it is
 * being loaded at.
 *
 * <p>A profile carries no dealer identity, so nothing in it can be trusted
 * at load time: the geometry may not be supported here, the line count may
 * be illegal for the normalized height, and the saved per-line wager may not
 * exist on this dealer's chip ladder -- or may exist but be unsafe under
 * this dealer's payout rules.
 *
 * <p>The one asymmetric rule is the wager: when the saved amount is not
 * available, the load picks the closest allowed wager that does <em>not
 * exceed</em> it. Loading a profile must never silently increase the
 * player's economic exposure beyond what they saved, so the fallback is
 * always downward; only when no allowed wager is small enough does it settle
 * for the smallest allowed one, and every adjustment is reported.
 */
public final class SlotsProfileNormalizer {

    private SlotsProfileNormalizer() {
    }

    /**
     * The outcome of fitting a profile to a machine.
     *
     * @param height the normalized visible height
     * @param reels the normalized reel count
     * @param paylines the line count, valid for {@code height}
     * @param denominationIndex the chosen index into the dealer's chip values
     * @param adjusted whether any saved value had to be changed
     */
    public record Fitted(int height, int reels, int paylines, int denominationIndex, boolean adjusted) {
    }

    /**
     * @param profile the saved profile being loaded
     * @param chipValues this dealer's chip ladder
     * @param currentIndex the index to keep if nothing at all is allowed
     * @param itemMode whether this dealer pays in items rather than Vault money
     * @param paytable the paytable the normalized geometry will use
     */
    public static Fitted fit(
        SlotsProfile profile,
        double[] chipValues,
        int currentIndex,
        boolean itemMode,
        SlotsPaytable paytable) {

        if (profile == null) {
            throw new IllegalArgumentException("fit requires a profile");
        }
        if (chipValues == null || chipValues.length == 0) {
            throw new IllegalArgumentException("fit requires a non-empty chip ladder");
        }

        int height = SlotsGeometry.normalizeRowCount(profile.height());
        int reels = SlotsGeometry.normalizeColumnCount(profile.reels());
        int paylines = SlotsPaylineCatalog.normalizeLineCount(height, profile.paylines());

        boolean adjusted = height != profile.height()
            || reels != profile.reels()
            || paylines != profile.paylines();

        int index = chooseDenominationIndex(
            profile.wagerPerLine(), chipValues, currentIndex, height, paylines, itemMode, paytable);
        if (!sameWager(chipValues[index], profile.wagerPerLine())) {
            adjusted = true;
        }

        return new Fitted(height, reels, paylines, index, adjusted);
    }

    /**
     * The closest safe chip index at or below {@code savedWager}. Falls back
     * to the smallest safe chip above it only when nothing at or below it is
     * safe, and to {@code currentIndex} when nothing on the ladder is safe at
     * all -- at which point the caller's existing wager is at least a wager
     * the machine already accepted.
     */
    private static int chooseDenominationIndex(
        double savedWager,
        double[] chipValues,
        int currentIndex,
        int visibleRows,
        int activeLines,
        boolean itemMode,
        SlotsPaytable paytable) {

        int bestAtOrBelow = -1;
        int smallestAllowed = -1;
        for (int i = 0; i < chipValues.length; i++) {
            double value = chipValues[i];
            if (!SlotsDenominationPolicy.isAllowed(value, visibleRows, activeLines, itemMode, paytable)) {
                continue;
            }
            if (smallestAllowed < 0 || value < chipValues[smallestAllowed]) {
                smallestAllowed = i;
            }
            if (value <= savedWager && (bestAtOrBelow < 0 || value > chipValues[bestAtOrBelow])) {
                bestAtOrBelow = i;
            }
        }
        if (bestAtOrBelow >= 0) {
            return bestAtOrBelow;
        }
        if (smallestAllowed >= 0) {
            return smallestAllowed;
        }
        return Math.max(0, Math.min(currentIndex, chipValues.length - 1));
    }

    private static boolean sameWager(double chosen, double saved) {
        return Math.abs(chosen - saved) <= 1e-9;
    }
}
