package org.nc.nccasino.games.Slots;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fitting a globally-saved profile onto whatever machine it is loaded at.
 *
 * <p>The rule that actually matters here is asymmetric on purpose: when the
 * saved per-line wager is not available on this dealer's chip ladder, the
 * load picks the closest allowed wager that does <em>not exceed</em> it.
 * Loading a profile must never silently increase the player's exposure
 * beyond what they saved.
 */
class SlotsProfileNormalizerTest {

    private static final SlotsPaytable PAYTABLE =
        SlotsPaytable.forConfig(5, SlotsPaytable.DEFAULT_HOUSE_EDGE, SlotsVariance.BALANCED);

    private static SlotsProfile profile(int height, int reels, int paylines, double wager) {
        return new SlotsProfile("saved", height, reels, paylines, wager,
            SlotsSpinSpeed.FAST, SlotsAutoSpinSettings.defaults());
    }

    private static SlotsProfileNormalizer.Fitted fit(SlotsProfile profile, double[] chips) {
        return SlotsProfileNormalizer.fit(profile, chips, 0, false, PAYTABLE);
    }

    // ---- geometry --------------------------------------------------------

    @Test
    void aProfileSavedAtASupportedGeometryLoadsUnchanged() {
        double[] chips = {1, 5, 10, 25, 100};
        SlotsProfileNormalizer.Fitted fitted = fit(profile(3, 5, 5, 10.0), chips);
        assertEquals(3, fitted.height());
        assertEquals(5, fitted.reels());
        assertEquals(5, fitted.paylines());
        assertEquals(2, fitted.denominationIndex());
        assertFalse(fitted.adjusted(), "nothing had to change, so nothing may be reported as adjusted");
    }

    @Test
    void anUnsupportedHeightOrReelCountIsClampedIntoTheSupportedSet() {
        double[] chips = {1, 5, 10, 25, 100};
        SlotsProfileNormalizer.Fitted tooBig = fit(profile(9, 11, 5, 10.0), chips);
        assertEquals(5, tooBig.height());
        assertEquals(7, tooBig.reels());
        assertTrue(tooBig.adjusted());

        SlotsProfileNormalizer.Fitted tooSmall = fit(profile(0, 0, 5, 10.0), chips);
        assertEquals(1, tooSmall.height());
        assertEquals(3, tooSmall.reels());
        assertTrue(tooSmall.adjusted());
    }

    @Test
    void paylinesAreKeptValidForTheNormalizedHeight() {
        double[] chips = {1, 5, 10, 25, 100};
        // Height 1 offers exactly one payline, whatever the profile stored.
        SlotsProfileNormalizer.Fitted heightOne = fit(profile(1, 5, 9, 10.0), chips);
        assertEquals(1, heightOne.height());
        assertEquals(1, heightOne.paylines());
        assertTrue(heightOne.adjusted());

        // And an unsupported height that normalizes DOWN to 1 clamps the
        // line count with it, not against the height that was saved.
        SlotsProfileNormalizer.Fitted normalizedToOne = fit(profile(-4, 5, 7, 10.0), chips);
        assertEquals(1, normalizedToOne.height());
        assertEquals(1, normalizedToOne.paylines());
    }

    @Test
    void anOutOfRangeLineCountIsClampedInBothDirections() {
        double[] chips = {1, 5, 10, 25, 100};
        assertEquals(9, fit(profile(5, 5, 99, 10.0), chips).paylines());
        assertEquals(1, fit(profile(5, 5, 0, 10.0), chips).paylines());
        assertEquals(1, fit(profile(5, 5, -3, 10.0), chips).paylines());
    }

    // ---- wager clamping --------------------------------------------------

    @Test
    void anExactlyAvailableWagerIsKept() {
        double[] chips = {1, 5, 10, 25, 100};
        assertEquals(4, fit(profile(3, 5, 5, 100.0), chips).denominationIndex());
        assertFalse(fit(profile(3, 5, 5, 100.0), chips).adjusted());
    }

    @Test
    void anUnavailableWagerFallsBackDownwardNeverUpward() {
        double[] chips = {1, 5, 10, 25, 100};
        // 50 does not exist on this ladder: 25 is the closest at or below it.
        SlotsProfileNormalizer.Fitted fitted = fit(profile(3, 5, 5, 50.0), chips);
        assertEquals(3, fitted.denominationIndex());
        assertEquals(25.0, chips[fitted.denominationIndex()], 1e-9);
        assertTrue(fitted.adjusted(), "the player must be told the wager was adjusted");
    }

    @Test
    void loadingNeverIncreasesExposureForAnyWagerOnAnyLadder() {
        double[] chips = {2, 7, 40, 500};
        for (double saved : new double[] {2, 3, 6.5, 7, 39.9, 40, 499, 500, 5000}) {
            SlotsProfileNormalizer.Fitted fitted = fit(profile(3, 5, 5, saved), chips);
            assertTrue(chips[fitted.denominationIndex()] <= saved,
                "saved " + saved + " must never load as the larger "
                    + chips[fitted.denominationIndex()]);
        }
    }

    @Test
    void aWagerBelowEveryChipSettlesForTheSmallestAllowedOne() {
        // Nothing at or below the saved amount exists, so the only options
        // are all larger -- take the smallest, and report the adjustment.
        double[] chips = {10, 50, 200};
        SlotsProfileNormalizer.Fitted fitted = fit(profile(3, 5, 5, 1.0), chips);
        assertEquals(0, fitted.denominationIndex());
        assertEquals(10.0, chips[fitted.denominationIndex()], 1e-9);
        assertTrue(fitted.adjusted());
    }

    @Test
    void anUnsortedLadderStillPicksTheClosestSafeWagerAtOrBelowTheSavedOne() {
        double[] chips = {100, 5, 25, 1, 50};
        SlotsProfileNormalizer.Fitted fitted = fit(profile(3, 5, 5, 30.0), chips);
        assertEquals(25.0, chips[fitted.denominationIndex()], 1e-9);
    }

    @Test
    void anUnsafeChipIsSkippedEvenWhenItIsTheExactSavedAmount() {
        // A chip whose total bet or maximum possible payout the machine
        // cannot represent safely is not an allowed wager, so the fit must
        // step past it rather than load an exposure the machine would then
        // refuse to spin at all.
        double[] chips = {5, Long.MAX_VALUE / 4.0};
        SlotsProfileNormalizer.Fitted fitted = SlotsProfileNormalizer.fit(
            profile(3, 5, 5, Long.MAX_VALUE / 4.0), chips, 0, true, PAYTABLE);
        assertEquals(0, fitted.denominationIndex());
        assertEquals(5.0, chips[fitted.denominationIndex()], 1e-9);
        assertTrue(fitted.adjusted());
    }

    @Test
    void aLadderWithNothingSafeAtAllFallsBackToTheWagerTheMachineAlreadyAccepted() {
        double[] chips = {0.0, 0.0, 0.0};
        SlotsProfileNormalizer.Fitted fitted =
            SlotsProfileNormalizer.fit(profile(3, 5, 5, 10.0), chips, 1, false, PAYTABLE);
        assertEquals(1, fitted.denominationIndex());
        assertTrue(fitted.adjusted());
    }

    @Test
    void speedAndAutoSettingsAreNotTheNormalizersBusinessAndAreLoadedVerbatim() {
        // The fit only ever decides geometry and wager; the profile's own
        // spin speed and Auto Spin settings load exactly as saved.
        SlotsProfile saved = new SlotsProfile("saved", 3, 5, 5, 10.0,
            SlotsSpinSpeed.SLOW, SlotsAutoSpinSettings.of(99L, true, 3.0, 500.0, 250.0));
        assertEquals(SlotsSpinSpeed.SLOW, saved.spinSpeed());
        assertEquals(99L, saved.autoSettings().spinLimit());
        assertTrue(saved.autoSettings().stopOnAnyWin());
        assertEquals(3.0, saved.autoSettings().bigWinMultiplier(), 1e-9);
        assertEquals(500.0, saved.autoSettings().profitTarget(), 1e-9);
        assertEquals(250.0, saved.autoSettings().lossLimit(), 1e-9);
    }

    @Test
    void fitRefusesAMissingProfileOrAnEmptyChipLadder() {
        assertThrows(IllegalArgumentException.class,
            () -> SlotsProfileNormalizer.fit(null, new double[] {1}, 0, false, PAYTABLE));
        assertThrows(IllegalArgumentException.class,
            () -> SlotsProfileNormalizer.fit(profile(3, 5, 5, 1.0), new double[0], 0, false, PAYTABLE));
        assertThrows(IllegalArgumentException.class,
            () -> SlotsProfileNormalizer.fit(profile(3, 5, 5, 1.0), null, 0, false, PAYTABLE));
    }

    @Test
    void aProfileAlwaysNeedsAName() {
        assertThrows(IllegalArgumentException.class,
            () -> new SlotsProfile(null, 3, 5, 5, 1.0, SlotsSpinSpeed.NORMAL, SlotsAutoSpinSettings.defaults()));
        assertThrows(IllegalArgumentException.class,
            () -> new SlotsProfile("", 3, 5, 5, 1.0, SlotsSpinSpeed.NORMAL, SlotsAutoSpinSettings.defaults()));
    }

    @Test
    void aProfileWithNoStoredSpeedOrAutoSettingsFallsBackToTheDefaults() {
        SlotsProfile truncated = new SlotsProfile("saved", 3, 5, 5, 1.0, null, null);
        assertEquals(SlotsSpinSpeed.NORMAL, truncated.spinSpeed());
        assertEquals(SlotsAutoSpinSettings.defaults(), truncated.autoSettings());
    }

    @Test
    void renamingKeepsEveryStoredValue() {
        SlotsProfile original = new SlotsProfile("pending", 5, 7, 9, 25.0,
            SlotsSpinSpeed.SLOW, SlotsAutoSpinSettings.of(4L, true, 2.0, 0.0, 0.0));
        SlotsProfile renamed = original.renamed("High Roller");
        assertEquals("High Roller", renamed.name());
        assertEquals(original.height(), renamed.height());
        assertEquals(original.reels(), renamed.reels());
        assertEquals(original.paylines(), renamed.paylines());
        assertEquals(original.wagerPerLine(), renamed.wagerPerLine(), 1e-9);
        assertEquals(original.spinSpeed(), renamed.spinSpeed());
        assertEquals(original.autoSettings(), renamed.autoSettings());
    }
}
