package org.nc.nccasino.games.Baccarat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The Banker's third-card rule against the standard Punto Banco tableau,
 * written out independently of the implementation.
 */
class BaccaratDrawRulesTest {

    // Rows: Banker's two-card total 0..7. Columns: Player's third card 0..9.
    // D = Banker draws, S = Banker stands.
    private static final String[] TABLEAU = {
        "DDDDDDDDDD",
        "DDDDDDDDDD",
        "DDDDDDDDDD",
        "DDDDDDDDSD",
        "SSDDDDDDSS",
        "SSSSDDDDSS",
        "SSSSSSDDSS",
        "SSSSSSSSSS",
    };

    @Test
    void bankerFollowsTheTableauAfterThePlayerDraws() {
        for (int banker = 0; banker < TABLEAU.length; banker++) {
            for (int third = 0; third <= 9; third++) {
                boolean draws = TABLEAU[banker].charAt(third) == 'D';
                assertEquals(draws, BaccaratServer.shouldBankerDraw(0, banker, third),
                    "Banker " + banker + ", Player's third card " + third);
            }
        }
    }

    @Test
    void bankerDrawsOnFiveOrLessWhenThePlayerStands() {
        for (int banker = 0; banker <= 7; banker++) {
            assertEquals(banker <= 5, BaccaratServer.shouldBankerDraw(6, banker, null), "Banker " + banker);
        }
    }
}
