package world.bentobox.boxed.generators.chunks;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Tests {@link AbstractBoxedChunkGenerator#repeatCalc(int, int)} - the function that
 * maps an arbitrary chunk coordinate back into the repeating seed region
 * {@code [-size, size)}. This is what makes the small captured seed area tile
 * infinitely across the game world.
 */
class RepeatCalcTest {

    private int size;

    private void setSize(int size) {
        this.size = size;
    }

    private int repeatCalc(int c) {
        return AbstractBoxedChunkGenerator.repeatCalc(c, size);
    }

    @Test
    void testIdentityWithinRange() {
        setSize(5);
        // Coordinates already inside [-size, size) are returned unchanged
        for (int c = -5; c < 5; c++) {
            assertEquals(c, repeatCalc(c), "coord " + c);
        }
    }

    @Test
    void testWrapsAboveRange() {
        setSize(5);
        // size maps back to -size, and it keeps wrapping with period 2*size
        assertEquals(-5, repeatCalc(5));
        assertEquals(-4, repeatCalc(6));
        assertEquals(0, repeatCalc(10));
        assertEquals(4, repeatCalc(14));
        assertEquals(-1, repeatCalc(19));
    }

    @Test
    void testWrapsBelowRange() {
        setSize(5);
        assertEquals(-5, repeatCalc(-5));
        assertEquals(0, repeatCalc(-10));
        assertEquals(-1, repeatCalc(-11));
    }

    @Test
    void testResultAlwaysWithinRange() {
        setSize(8);
        for (int c = -100; c <= 100; c++) {
            int r = repeatCalc(c);
            assertEquals(true, r >= -8 && r < 8, "coord " + c + " mapped out of range to " + r);
        }
    }

    @Test
    void testDifferentSize() {
        setSize(1);
        // With size 1 the region only contains the two chunks -1 and 0
        assertEquals(0, repeatCalc(0));
        assertEquals(-1, repeatCalc(-1));
        assertEquals(-1, repeatCalc(1));
        assertEquals(0, repeatCalc(2));
    }
}
