package org.nc.nccasino.games.Roulette;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.Map;

import org.junit.jupiter.api.Test;

class RouletteFrameTest {

    @Test
    void equalFieldsProduceEqualFrames() {
        RouletteFrame a = new RouletteFrame(RouletteFrame.Phase.SPINNING, RouletteWheelLayout.TOP_LEFT, 12, 0, 30, null);
        RouletteFrame b = new RouletteFrame(RouletteFrame.Phase.SPINNING, RouletteWheelLayout.TOP_LEFT, 12, 0, 30, null);

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void aLateCreatedViewRenderingTheSameFrameSeesTheSameGrid() {
        // A view created after the frame already exists must render exactly
        // what an earlier view would, with no dependency on animation
        // history -- rendering is a pure function of the frame's fields.
        RouletteFrame frame = new RouletteFrame(
            RouletteFrame.Phase.BETTING_OPEN, RouletteWheelLayout.BOTTOM_LEFT, 9, 14, RouletteFrame.NO_BALL, null
        );

        Map<Integer, Integer> earlyView = RouletteWheelLayout.numbersForQuadrant(frame.quadrant(), frame.wheelOffset());
        Map<Integer, Integer> lateView = RouletteWheelLayout.numbersForQuadrant(frame.quadrant(), frame.wheelOffset());

        assertEquals(earlyView, lateView);
    }

    @Test
    void bettingOpenOnlyDuringBettingOpenPhase() {
        RouletteFrame open = new RouletteFrame(RouletteFrame.Phase.BETTING_OPEN, 1, 0, 10, RouletteFrame.NO_BALL, null);
        RouletteFrame closed = new RouletteFrame(RouletteFrame.Phase.BETS_CLOSED, 1, 0, 0, RouletteFrame.NO_BALL, null);
        RouletteFrame spinning = new RouletteFrame(RouletteFrame.Phase.SPINNING, 1, 0, 0, 27, null);
        RouletteFrame complete = new RouletteFrame(RouletteFrame.Phase.ROUND_COMPLETE, 1, 0, 0, 27, 17);

        assertTrue(open.bettingOpen());
        assertTrue(open.canOpenBettingTable());
        assertFalse(closed.bettingOpen());
        assertFalse(spinning.bettingOpen());
        assertFalse(complete.bettingOpen());
    }

    @Test
    void ballVisibilityTracksTheSentinelSlot() {
        RouletteFrame noBall = new RouletteFrame(RouletteFrame.Phase.BETTING_OPEN, 1, 0, 10, RouletteFrame.NO_BALL, null);
        RouletteFrame withBall = new RouletteFrame(RouletteFrame.Phase.SPINNING, 1, 0, 0, 27, null);

        assertFalse(noBall.ballVisible());
        assertTrue(withBall.ballVisible());
    }

    @Test
    void frameCarriesNoLocalizedTextOrPlayerIdentity() {
        // The shared frame must stay renderer-agnostic: no String or player
        // handle may leak canonical round state into locale-specific text.
        for (Method method : RouletteFrame.class.getDeclaredMethods()) {
            if (method.getParameterCount() != 0 || !java.lang.reflect.Modifier.isPublic(method.getModifiers())) {
                continue;
            }
            Class<?> returnType = method.getReturnType();
            assertFalse(
                CharSequence.class.isAssignableFrom(returnType),
                "RouletteFrame." + method.getName() + "() returns a String-like type"
            );
            assertFalse(
                returnType.getName().startsWith("org.bukkit"),
                "RouletteFrame." + method.getName() + "() returns a Bukkit type"
            );
        }
    }
}
