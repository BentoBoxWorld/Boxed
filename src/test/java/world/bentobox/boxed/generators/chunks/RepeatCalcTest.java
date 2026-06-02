package world.bentobox.boxed.generators.chunks;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import world.bentobox.boxed.WhiteBox;

/**
 * Tests {@link AbstractBoxedChunkGenerator#repeatCalc(int)} - the function that
 * maps an arbitrary chunk coordinate back into the repeating seed region
 * {@code [-size, size)}. This is what makes the small captured seed area tile
 * infinitely across the game world.
 */
class RepeatCalcTest {

    private void setSize(int size) {
        WhiteBox.setInternalState(AbstractBoxedChunkGenerator.class, "size", size);
    }

    @Test
    void testIdentityWithinRange() {
        setSize(5);
        // Coordinates already inside [-size, size) are returned unchanged
        for (int c = -5; c < 5; c++) {
            assertEquals(c, AbstractBoxedChunkGenerator.repeatCalc(c), "coord " + c);
        }
    }

    @Test
    void testWrapsAboveRange() {
        setSize(5);
        // size maps back to -size, and it keeps wrapping with period 2*size
        assertEquals(-5, AbstractBoxedChunkGenerator.repeatCalc(5));
        assertEquals(-4, AbstractBoxedChunkGenerator.repeatCalc(6));
        assertEquals(0, AbstractBoxedChunkGenerator.repeatCalc(10));
        assertEquals(4, AbstractBoxedChunkGenerator.repeatCalc(14));
        assertEquals(-1, AbstractBoxedChunkGenerator.repeatCalc(19));
    }

    @Test
    void testWrapsBelowRange() {
        setSize(5);
        assertEquals(-5, AbstractBoxedChunkGenerator.repeatCalc(-5));
        assertEquals(0, AbstractBoxedChunkGenerator.repeatCalc(-10));
        assertEquals(-1, AbstractBoxedChunkGenerator.repeatCalc(-11));
    }

    @Test
    void testResultAlwaysWithinRange() {
        setSize(8);
        for (int c = -100; c <= 100; c++) {
            int r = AbstractBoxedChunkGenerator.repeatCalc(c);
            assertEquals(true, r >= -8 && r < 8, "coord " + c + " mapped out of range to " + r);
        }
    }

    @Test
    void testDifferentSize() {
        setSize(1);
        // With size 1 the region is just {-1, 0}
        assertEquals(0, AbstractBoxedChunkGenerator.repeatCalc(0));
        assertEquals(-1, AbstractBoxedChunkGenerator.repeatCalc(-1));
        assertEquals(-1, AbstractBoxedChunkGenerator.repeatCalc(1));
        assertEquals(0, AbstractBoxedChunkGenerator.repeatCalc(2));
    }
}
