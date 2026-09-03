package org.nc.nccasino.games.Slots;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The five variance levels: their weight tables, their effect on the derived
 * paytable, and the two invariants the whole feature depends on --
 * {@code BALANCED} must reproduce the machine's original fixed shape exactly,
 * and every level must land on the same configured RTP regardless of shape.
 */
class SlotsVarianceTest {

    private static final double RTP_TOLERANCE = 1e-9;

    // ---- weight tables -----------------------------------------------

    @ParameterizedTest
    @EnumSource(SlotsVariance.class)
    void everyLevelsWeightsSumToTheTotal(SlotsVariance variance) {
        int sum = 0;
        for (SlotsSymbol symbol : SlotsSymbol.values()) {
            sum += variance.weight(symbol);
        }
        assertEquals(SlotsSymbol.TOTAL_WEIGHT, sum, variance + " weight sum");
    }

    @ParameterizedTest
    @EnumSource(SlotsVariance.class)
    void everyLevelHasAPositiveLengthBase(SlotsVariance variance) {
        assertTrue(variance.lengthBase() > 1.0,
            variance + " must make longer runs pay more, not less or the same");
    }

    @Test
    void balancedMatchesTheOriginalFixedShapeExactly() {
        // The regression that matters most: every dealer that never touches
        // variance must see byte-identical behavior to before this feature.
        assertEquals(6.0, SlotsVariance.BALANCED.lengthBase(), 0.0);
        for (SlotsSymbol symbol : SlotsSymbol.values()) {
            assertEquals(symbol.weight(), SlotsVariance.BALANCED.weight(symbol),
                "BALANCED must reuse SlotsSymbol's own weight for " + symbol);
        }
    }

    // ---- hit frequency ordering ---------------------------------------

    @Test
    void hitFrequencyDecreasesMonotonicallyFromSteadyToHighRoller() {
        double steady = SlotsPaytable.lineHitProbability(SlotsVariance.STEADY);
        double low = SlotsPaytable.lineHitProbability(SlotsVariance.LOW);
        double balanced = SlotsPaytable.lineHitProbability(SlotsVariance.BALANCED);
        double high = SlotsPaytable.lineHitProbability(SlotsVariance.HIGH);
        double highRoller = SlotsPaytable.lineHitProbability(SlotsVariance.HIGH_ROLLER);

        assertTrue(steady > low, "STEADY must hit more often than LOW");
        assertTrue(low > balanced, "LOW must hit more often than BALANCED");
        assertTrue(balanced > high, "BALANCED must hit more often than HIGH");
        assertTrue(high > highRoller, "HIGH must hit more often than HIGH_ROLLER");
    }

    @Test
    void balancedLineHitProbabilityMatchesTheParameterlessOverload() {
        assertEquals(
            SlotsPaytable.lineHitProbability(),
            SlotsPaytable.lineHitProbability(SlotsVariance.BALANCED),
            RTP_TOLERANCE);
    }

    // ---- top multiplier / max payout ordering --------------------------

    @ParameterizedTest
    @ValueSource(ints = {3, 5, 7})
    void topMultiplierIncreasesMonotonicallyFromSteadyToHighRoller(int columns) {
        Map<SlotsVariance, Double> topMultiplier = new EnumMap<>(SlotsVariance.class);
        for (SlotsVariance variance : SlotsVariance.values()) {
            topMultiplier.put(variance,
                SlotsPaytable.forConfig(columns, SlotsPaytable.DEFAULT_HOUSE_EDGE, variance).maxLineMultiplier());
        }

        assertTrue(topMultiplier.get(SlotsVariance.STEADY) < topMultiplier.get(SlotsVariance.LOW),
            "STEADY's top multiplier must be the smallest");
        assertTrue(topMultiplier.get(SlotsVariance.LOW) < topMultiplier.get(SlotsVariance.BALANCED));
        assertTrue(topMultiplier.get(SlotsVariance.BALANCED) < topMultiplier.get(SlotsVariance.HIGH));
        assertTrue(topMultiplier.get(SlotsVariance.HIGH) < topMultiplier.get(SlotsVariance.HIGH_ROLLER),
            "HIGH_ROLLER's top multiplier must be the largest");
    }

    // ---- RTP preservation: the property the whole feature depends on ---

    @ParameterizedTest
    @EnumSource(SlotsVariance.class)
    void everyVarianceReproducesExactlyTheConfiguredReturnToPlayer(SlotsVariance variance) {
        for (int columns : new int[] {3, 5, 7}) {
            for (double edge : new double[] {
                SlotsPaytable.MIN_HOUSE_EDGE, SlotsPaytable.DEFAULT_HOUSE_EDGE, SlotsPaytable.MAX_HOUSE_EDGE}) {

                SlotsPaytable paytable = SlotsPaytable.forConfig(columns, edge, variance);
                assertEquals(1.0 - edge, paytable.theoreticalRtp(), RTP_TOLERANCE,
                    variance + " at " + columns + " columns, edge " + edge);

                // Verify by full enumeration too, independent of the
                // paytable's own bookkeeping -- this is what an integrity
                // check must do rather than trusting the field it is
                // supposed to be validating.
                double enumeratedRtp = 0.0;
                for (SlotsSymbol symbol : SlotsSymbol.values()) {
                    if (!symbol.pays()) {
                        continue;
                    }
                    for (int run = symbol.minimumRun(); run <= columns; run++) {
                        enumeratedRtp += SlotsPaytable.runProbability(symbol, run, columns, variance)
                            * paytable.multiplier(symbol, run);
                    }
                }
                assertEquals(1.0 - edge, enumeratedRtp, RTP_TOLERANCE,
                    "enumerated RTP for " + variance + " at " + columns + " columns, edge " + edge);
            }
        }
    }

    @ParameterizedTest
    @EnumSource(SlotsVariance.class)
    void everyVarianceProducesOnlyFiniteNonNegativeMultipliers(SlotsVariance variance) {
        for (int columns : new int[] {3, 5, 7}) {
            SlotsPaytable paytable = SlotsPaytable.forConfig(columns, SlotsPaytable.DEFAULT_HOUSE_EDGE, variance);
            for (SlotsSymbol symbol : SlotsSymbol.values()) {
                for (int run = 1; run <= columns; run++) {
                    double multiplier = paytable.multiplier(symbol, run);
                    assertTrue(Double.isFinite(multiplier) && multiplier >= 0.0,
                        variance + " " + symbol + " run " + run + " -> " + multiplier);
                }
            }
        }
    }

    // ---- parsing / fallback --------------------------------------------

    @Test
    void parseAcceptsEveryDeclaredLevelCaseInsensitively() {
        for (SlotsVariance variance : SlotsVariance.values()) {
            assertEquals(variance, SlotsVariance.parse(variance.name(), null));
            assertEquals(variance, SlotsVariance.parse(variance.name().toLowerCase(), null));
        }
    }

    @Test
    void parseFallsBackSafelyOnInvalidInput() {
        assertEquals(SlotsVariance.BALANCED, SlotsVariance.parse("not-a-level", SlotsVariance.BALANCED));
        assertEquals(SlotsVariance.BALANCED, SlotsVariance.parse(null, SlotsVariance.BALANCED));
        assertEquals(SlotsVariance.BALANCED, SlotsVariance.parse("", SlotsVariance.BALANCED));
    }

    // ---- the strip-based generator actually samples with the requested weights ------

    @Test
    void theReelStripReallyReflectsTheRequestedVarianceNotSlotsSymbolsFixedWeights() {
        // HIGH_ROLLER weights SEEDS far higher than BALANCED. If both reels'
        // strips agreed everywhere, generation would not really be
        // consulting the passed-in variance -- some stop must land on a
        // different symbol between the two.
        SlotsReelStrip highRoller = SlotsReelStrip.forReel(SlotsVariance.HIGH_ROLLER, 0);
        SlotsReelStrip balanced = SlotsReelStrip.forReel(SlotsVariance.BALANCED, 0);
        boolean sawDivergence = false;
        for (int stop = 0; stop < SlotsReelStrip.SIZE; stop++) {
            if (highRoller.symbolAt(stop) != balanced.symbolAt(stop)) {
                sawDivergence = true;
                break;
            }
        }
        assertTrue(sawDivergence, "different variances must produce different strip compositions");
        assertEquals(SlotsVariance.HIGH_ROLLER.weight(SlotsSymbol.SEEDS), highRoller.countOf(SlotsSymbol.SEEDS));
        assertEquals(SlotsVariance.BALANCED.weight(SlotsSymbol.SEEDS), balanced.countOf(SlotsSymbol.SEEDS));
    }

    @Test
    void theDefaultGenerateFromStripsOverloadUsesBalancedWhenVarianceIsNull() {
        SlotsRandomSource fixed = bound -> 0;
        SlotsOutcome viaNull = SlotsSpinGenerator.generateFromStrips(5, 3, fixed, null).outcome();
        SlotsOutcome viaExplicitBalanced = SlotsSpinGenerator.generateFromStrips(5, 3, fixed, SlotsVariance.BALANCED).outcome();
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 5; col++) {
                assertEquals(viaNull.symbolAt(row, col), viaExplicitBalanced.symbolAt(row, col));
            }
        }
    }
}
