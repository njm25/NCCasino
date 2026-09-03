package org.nc.nccasino.games.Slots;

import java.util.SplittableRandom;

/**
 * A demo-only randomness source with its own independent state, so a demo
 * spin can never advance (or be advanced by) the stream backing real, paid
 * spins.
 *
 * <p>{@link SlotsRandomSource#production()} draws from
 * {@code ThreadLocalRandom.current()} -- a single shared, thread-scoped
 * stream. A previous demo implementation drew from that exact same source
 * via a second lambda, which is not an independent stream at all: both run
 * on the Bukkit server thread, so every demo draw advanced the identical
 * generator state a subsequent paid spin would otherwise have consumed.
 * {@link SplittableRandom} is a genuinely separate algorithm/state, self-seeded
 * independently of {@code ThreadLocalRandom} by its no-arg constructor, so
 * consuming any number of demo values here cannot influence what a paid
 * spin draws.
 *
 * <p>One instance is created per {@code SlotsMachine} and reused for every
 * demo draw (stops and demo payout rounding alike) -- never recreated per
 * draw, which would otherwise reseed from a fresh, cheaply-guessable source
 * each time and defeat the point of holding independent state at all.
 */
final class SlotsDemoRandomSource implements SlotsRandomSource {

    private final SplittableRandom random;

    SlotsDemoRandomSource() {
        this(new SplittableRandom());
    }

    /** Test-only: pins a deterministic seed for reproducible assertions. */
    SlotsDemoRandomSource(SplittableRandom random) {
        this.random = random;
    }

    @Override
    public int nextInt(int bound) {
        return random.nextInt(bound);
    }
}
