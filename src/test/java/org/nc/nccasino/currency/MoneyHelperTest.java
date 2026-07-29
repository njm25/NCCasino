package org.nc.nccasino.currency;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MoneyHelperTest {

    @Test
    void wagerUnitsAcceptOnlyPositiveWholeValues() {
        assertEquals(25, MoneyHelper.toWagerUnits(25));
        assertEquals(25, MoneyHelper.toWagerUnits(25.0000001));
        assertEquals(0, MoneyHelper.toWagerUnits(25.5));
        assertEquals(0, MoneyHelper.toWagerUnits(0));
        assertEquals(0, MoneyHelper.toWagerUnits(-10));
    }

    @Test
    void vaultConversionNeverCreatesNegativeDeposit() {
        assertEquals(12.5, MoneyHelper.toVaultDouble(new BigDecimal("12.5")));
        assertEquals(0, MoneyHelper.toVaultDouble(new BigDecimal("-0.01")));
        assertEquals(0, MoneyHelper.toVaultDouble(null));
    }

    @Test
    void floorToLongDoesNotOverflowOrGoNegative() {
        assertEquals(12, MoneyHelper.floorToLong(new BigDecimal("12.99")));
        assertEquals(0, MoneyHelper.floorToLong(new BigDecimal("-1")));
        assertEquals(Long.MAX_VALUE,
            MoneyHelper.floorToLong(new BigDecimal("999999999999999999999999")));
    }
}
