package org.nc.nccasino.games.Roulette;

import org.junit.jupiter.api.Test;
import org.nc.nccasino.budget.Commitment;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A player's Roulette bets live on the wheel and are shown on a betting table
 * that is rebuilt each time it is opened.
 *
 * <p>Opened in the tick betting closed, a table used to start open for the
 * whole spin: the spin's settlement copy shares its bet stack, so a bet placed
 * then was never paid and an Undo All was refunded while the copy still paid.
 * Reopened mid-round, the new table used to reserve the whole stack again on a
 * LIMITED dealer, crediting it twice and leaving the first reservation for good.
 */
class RouletteTableHandoverTest {

    @Test
    void aTableStartsClosedWhenTheWheelHasClosedBetting() {
        RouletteInventory wheel = mock(RouletteInventory.class);
        when(wheel.areBetsClosed()).thenReturn(true);

        assertTrue(BettingTable.initialBetsClosed(wheel));
    }

    @Test
    void aTableStartsOpenWhileTheWheelTakesBets() {
        RouletteInventory wheel = mock(RouletteInventory.class);
        when(wheel.areBetsClosed()).thenReturn(false);

        assertFalse(BettingTable.initialBetsClosed(wheel));
    }

    @Test
    void aReopenedTableTakesOverTheReservationOfTheOneItReplaces() throws Exception {
        Commitment open = Commitment.released();
        BettingTable first = tableWithout(open);
        BettingTable reopened = tableWithout(null);

        reopened.adoptBudgetCommitment(first);

        assertSame(open, commitmentOf(reopened), "the new table settles the original reservation");
        assertNull(commitmentOf(first), "the old table can no longer settle or refund it");
    }

    @Test
    void aTableThatAlreadyHoldsAReservationKeepsItsOwn() throws Exception {
        Commitment mine = Commitment.released();
        Commitment stale = Commitment.released();
        BettingTable first = tableWithout(stale);
        BettingTable reopened = tableWithout(mine);

        reopened.adoptBudgetCommitment(first);

        assertSame(mine, commitmentOf(reopened));
        assertNull(commitmentOf(first));
    }

    /** A table built without its constructor (which needs a live inventory), holding {@code commitment}. */
    private static BettingTable tableWithout(Commitment commitment) throws Exception {
        BettingTable table = mock(BettingTable.class, CALLS_REAL_METHODS);
        commitmentField().set(table, commitment);
        return table;
    }

    private static Commitment commitmentOf(BettingTable table) throws Exception {
        return (Commitment) commitmentField().get(table);
    }

    private static Field commitmentField() throws Exception {
        Field field = BettingTable.class.getDeclaredField("budgetCommitment");
        field.setAccessible(true);
        return field;
    }
}
