package org.nc.nccasino.games.Slots;

/**
 * One saved Slots configuration, owned by a player rather than by a dealer.
 *
 * <p>A profile is a pure snapshot of what the player had selected when they
 * saved it: geometry, per-line wager, gameplay spin speed, and the complete
 * Auto Spin settings. It carries no dealer identity at all, which is what
 * makes profiles globally portable -- loading one at a different machine
 * runs it through {@link SlotsProfileNormalizer} rather than assuming the
 * saved values are legal there.
 *
 * <p>{@code wagerPerLine} is stored as the raw chip value rather than a chip
 * index: a dealer's chip ladder is its own configuration, so an index saved
 * at one dealer would silently mean a different amount at another.
 */
public record SlotsProfile(
    String name,
    int height,
    int reels,
    int paylines,
    double wagerPerLine,
    SlotsSpinSpeed spinSpeed,
    SlotsAutoSpinSettings autoSettings) {

    public SlotsProfile {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("a profile must have a name");
        }
        spinSpeed = spinSpeed == null ? SlotsSpinSpeed.NORMAL : spinSpeed;
        autoSettings = autoSettings == null ? SlotsAutoSpinSettings.defaults() : autoSettings;
    }

    /** Whether {@code candidate} names this profile, ignoring case -- the uniqueness rule. */
    public boolean hasSameName(String candidate) {
        return candidate != null && name.equalsIgnoreCase(candidate.trim());
    }

    /** A copy under a different (already validated) display name, keeping every stored value. */
    public SlotsProfile renamed(String newName) {
        return new SlotsProfile(newName, height, reels, paylines, wagerPerLine, spinSpeed, autoSettings);
    }
}
