package org.nc.nccasino.games.Slots;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the Auto Spin defaults and the immutability that keeps a running
 * batch from being retuned by an edit made after it started.
 */
class SlotsAutoSpinSettingsTest {

    @Test
    void defaultsAreSpinLimitFifteenAndEveryStopConditionOff() {
        SlotsAutoSpinSettings defaults = SlotsAutoSpinSettings.defaults();
        assertEquals(15L, defaults.spinLimit());
        assertTrue(defaults.hasSpinLimit());
        assertFalse(defaults.stopOnAnyWin());
        assertFalse(defaults.hasBigWinMultiplier());
        assertFalse(defaults.hasProfitTarget());
        assertFalse(defaults.hasLossLimit());
        assertEquals(0.0, defaults.bigWinMultiplier());
        assertEquals(0.0, defaults.profitTarget());
        assertEquals(0.0, defaults.lossLimit());
    }

    @Test
    void resetAutoSettingsRestoresExactlyThoseDefaults() {
        // Reset is literally "go back to defaults()", so a fully customised
        // configuration must compare equal to a brand-new one afterward.
        SlotsAutoSpinSettings customised = SlotsAutoSpinSettings.defaults()
            .withSpinLimit(400L)
            .withStopOnAnyWin(true)
            .withBigWinMultiplier(12.5)
            .withProfitTarget(900.0)
            .withLossLimit(250.0);
        assertNotSame(SlotsAutoSpinSettings.defaults(), customised);
        assertEquals(SlotsAutoSpinSettings.defaults(), SlotsAutoSpinSettings.defaults());
        assertFalse(customised.equals(SlotsAutoSpinSettings.defaults()));
    }

    @Test
    void thereIsNoConsecutiveLossSettingAndNoSpeedSetting() {
        // Gameplay speed is owned by the Clock's right-click cycle, so no
        // Auto Spin default can possibly disturb it, and the redesign has no
        // consecutive-loss stop rule at all. This is pinned structurally: the
        // only stop conditions that exist are the four checked above.
        SlotsAutoSpinSettings defaults = SlotsAutoSpinSettings.defaults();
        assertEquals(SlotsAutoSpinSettings.DEFAULT_SPIN_LIMIT, defaults.spinLimit());
        for (java.lang.reflect.Method method : SlotsAutoSpinSettings.class.getDeclaredMethods()) {
            String name = method.getName().toLowerCase(java.util.Locale.ROOT);
            assertFalse(name.contains("speed"), "spin speed must not be an Auto Spin setting: " + name);
            assertFalse(name.contains("consecutive"), "there is no consecutive-loss rule: " + name);
        }
    }

    @Test
    void everyWitherLeavesTheOriginalUntouched() {
        SlotsAutoSpinSettings original = SlotsAutoSpinSettings.defaults();
        original.withSpinLimit(3L);
        original.withStopOnAnyWin(true);
        original.withBigWinMultiplier(5.0);
        original.withProfitTarget(10.0);
        original.withLossLimit(20.0);
        assertEquals(SlotsAutoSpinSettings.defaults(), original);
    }

    @Test
    void aNonPositiveSpinLimitBecomesUnlimited() {
        assertEquals(SlotsAutoSpinSettings.UNLIMITED_SPINS,
            SlotsAutoSpinSettings.defaults().withSpinLimit(0L).spinLimit());
        assertEquals(SlotsAutoSpinSettings.UNLIMITED_SPINS,
            SlotsAutoSpinSettings.defaults().withSpinLimit(-5L).spinLimit());
        assertFalse(SlotsAutoSpinSettings.defaults().withSpinLimit(0L).hasSpinLimit());
    }

    @Test
    void unlimitedIsNotTheSameThingAsAStopConditionBeingOff() {
        SlotsAutoSpinSettings unlimited =
            SlotsAutoSpinSettings.defaults().withSpinLimit(SlotsAutoSpinSettings.UNLIMITED_SPINS);
        assertFalse(unlimited.hasSpinLimit());
        assertEquals(SlotsAutoSpinSettings.UNLIMITED_SPINS, unlimited.spinLimit());
        // A huge but genuine limit is still a limit.
        assertTrue(SlotsAutoSpinSettings.defaults().withSpinLimit(Long.MAX_VALUE).hasSpinLimit());
    }

    @Test
    void anyNonPositiveOrNonFiniteThresholdIsStoredCanonicallyAsOff() {
        for (double raw : new double[] {0.0, -1.5, Double.NaN, Double.POSITIVE_INFINITY,
            Double.NEGATIVE_INFINITY}) {

            SlotsAutoSpinSettings settings = SlotsAutoSpinSettings.defaults()
                .withBigWinMultiplier(raw)
                .withProfitTarget(raw)
                .withLossLimit(raw);
            assertEquals(0.0, settings.bigWinMultiplier(), "big win " + raw);
            assertEquals(0.0, settings.profitTarget(), "profit " + raw);
            assertEquals(0.0, settings.lossLimit(), "loss " + raw);
            assertFalse(settings.hasBigWinMultiplier());
            assertFalse(settings.hasProfitTarget());
            assertFalse(settings.hasLossLimit());
        }
    }

    @Test
    void togglingStopOnAnyWinFlipsItBothWays() {
        SlotsAutoSpinSettings on = SlotsAutoSpinSettings.defaults().toggleStopOnAnyWin();
        assertTrue(on.stopOnAnyWin());
        assertFalse(on.toggleStopOnAnyWin().stopOnAnyWin());
    }

    @Test
    void equalityAndHashingCoverEverySettingSoASnapshotComparisonIsReliable() {
        SlotsAutoSpinSettings a = SlotsAutoSpinSettings.of(20L, true, 5.0, 100.0, 50.0);
        SlotsAutoSpinSettings b = SlotsAutoSpinSettings.of(20L, true, 5.0, 100.0, 50.0);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertFalse(a.equals(a.withLossLimit(51.0)));
        assertFalse(a.equals(a.withSpinLimit(21L)));
        assertFalse(a.equals(a.withStopOnAnyWin(false)));
        assertFalse(a.equals(a.withBigWinMultiplier(6.0)));
        assertFalse(a.equals(a.withProfitTarget(101.0)));
    }
}
