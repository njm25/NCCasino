package org.nc.nccasino;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * /ncc delete force-loads a dealer's chunk from its saved block-centre
 * coordinates. On negative coordinates the old (int) x >> 4 named the
 * neighbouring chunk, so a live dealer there was taken for an orphan and its
 * records removed while the mob lived on.
 */
class ChunkOfTest {

    @Test
    void negativeBlockCentresAreInTheChunkBelowZero() {
        assertEquals(-1, Nccasino.chunkOf(-0.5));
        assertEquals(-2, Nccasino.chunkOf(-16.5));
        assertEquals(-1, Nccasino.chunkOf(-15.5));
    }

    @Test
    void positiveCoordinatesAreUnchanged() {
        assertEquals(0, Nccasino.chunkOf(0.5));
        assertEquals(0, Nccasino.chunkOf(15.5));
        assertEquals(1, Nccasino.chunkOf(16.5));
    }
}
