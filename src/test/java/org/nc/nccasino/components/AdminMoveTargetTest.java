package org.nc.nccasino.components;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every registered admin menu hears a block click. Before, the first one whose
 * owner had any move pending teleported its own dealer: admin B's open menu
 * moved dealer Y to the block admin A picked for dealer X.
 */
class AdminMoveTargetTest {

    private final UUID admin = UUID.randomUUID();
    private final Object dealerX = new Object();
    private final Object dealerY = new Object();

    @Test
    void onlyTheMovingAdminsMenuForThatDealerActs() {
        assertTrue(AdminMenu.isMoveForThisMenu(admin, admin, dealerX, dealerX, true));
    }

    @Test
    void anotherAdminsMenuIgnoresTheClick() {
        assertFalse(AdminMenu.isMoveForThisMenu(admin, UUID.randomUUID(), dealerX, dealerX, true));
    }

    @Test
    void aMenuForAnotherDealerIgnoresTheClick() {
        assertFalse(AdminMenu.isMoveForThisMenu(admin, admin, dealerX, dealerY, true));
    }

    @Test
    void aStaleMenuIgnoresTheClick() {
        assertFalse(AdminMenu.isMoveForThisMenu(admin, admin, dealerX, dealerX, false));
        assertFalse(AdminMenu.isMoveForThisMenu(admin, admin, null, dealerX, true));
    }
}
