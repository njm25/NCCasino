package org.nc.nccasino.currency;

import org.bukkit.Material;

/**
 * A dealer's currency at one moment. Taken before a currency change, it is
 * what that dealer's tables were played in, and so what they must settle in:
 * a stake of 64 emeralds is refunded as 64 emeralds, never as $64.
 */
public record DealerCurrencySnapshot(CurrencyMode mode, Material material, String name) {

    /** For the admin: "Vault" for Vault money, the item's name otherwise. */
    public String label() {
        if (mode == CurrencyMode.VAULT) {
            return "Vault";
        }
        return name != null && !name.isBlank() ? name : material.name();
    }
}
