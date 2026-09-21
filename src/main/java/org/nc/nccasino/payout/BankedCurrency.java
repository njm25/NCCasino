package org.nc.nccasino.payout;

import org.nc.nccasino.currency.CurrencyMode;

import java.util.Locale;

/**
 * The identity of one kind of banked money, snapshotted at the moment the
 * overflow was recorded.
 *
 * <p>Identity is captured rather than re-resolved from the originating
 * dealer at claim time for the same reason {@link PendingPayout} snapshots
 * it: the dealer's configured currency can change, or the dealer can be
 * deleted outright, between the win and the claim. What the player is owed
 * must not change because an administrator edited a dealer.
 *
 * <p>{@code material} is the canonical grouping key -- two dealers paying
 * the same material with different display names bank into one balance, so
 * a claim delivers a single merged stack rather than several
 * indistinguishable ones.
 */
public record BankedCurrency(CurrencyMode mode, String material, String name) {

    public BankedCurrency {
        if (material != null) {
            material = material.trim().toUpperCase(Locale.ROOT);
        }
    }

    /**
     * The stable storage/index key. Only mode and material participate:
     * {@link #name} is display text and must never split one material's
     * balance into two un-mergeable piles.
     */
    public String storageKey() {
        return mode.name() + "|" + (material == null ? "" : material);
    }

    public String displayName() {
        return name != null && !name.isBlank() ? name : (material == null ? "currency" : material);
    }
}
