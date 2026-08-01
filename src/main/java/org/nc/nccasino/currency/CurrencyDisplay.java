package org.nc.nccasino.currency;

/**
 * Pure formatting helpers for compact currency controls.
 */
public final class CurrencyDisplay {

    private CurrencyDisplay() {
    }

    public static String chipName(CurrencyMode mode, String currencyName, double value) {
        if (mode == CurrencyMode.VAULT) {
            return "$" + MoneyHelper.roundDisplay(MoneyHelper.bd(value)).toPlainString();
        }
        int whole = (int) value;
        String name = currencyName != null && !currencyName.isBlank() ? currencyName : "Emerald";
        return whole + " \u00d7 " + name;
    }
}
