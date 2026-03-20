package com.schematicimporter;

import com.schematicimporter.schematic.SpongeSchematicParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SpongeSchematicParser's varint decode logic.
 *
 * <p>The Sponge .schem format uses Protocol Buffer varints for block palette indices.
 * A single-byte read per block silently corrupts any schematic with 128+ unique block
 * states — this test class verifies the MSB-continuation algorithm is correct.</p>
 */
class VarintDecoderTest {

    /**
     * Convenience wrapper: encode a sequence of int values as Protocol Buffer varints
     * and decode them back, asserting round-trip correctness.
     */
    private int[] roundTrip(int... values) {
        // Encode all values as varints into a byte array
        byte[] encoded = encodeVarints(values);
        return SpongeSchematicParser.decodeVarints(encoded, values.length);
    }

    private static byte[] encodeVarints(int... values) {
        // Calculate needed buffer size (max 5 bytes per int32 varint)
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        for (int value : values) {
            do {
                int bits = value & 0x7F;
                value >>>= 7;
                if (value != 0) {
                    bits |= 0x80;
                }
                baos.write(bits);
            } while (value != 0);
        }
        return baos.toByteArray();
    }

    @Test
    void singleByteValue_zero() {
        // Value 0 encodes as [0x00] — single byte, no continuation bit
        int[] result = SpongeSchematicParser.decodeVarints(new byte[]{0x00}, 1);
        assertEquals(1, result.length);
        assertEquals(0, result[0], "Value 0 should decode to index 0");
    }

    @Test
    void singleByteValue_127() {
        // Value 127 (0x7F) encodes as [0x7F] — single byte, MSB clear
        int[] result = SpongeSchematicParser.decodeVarints(new byte[]{0x7F}, 1);
        assertEquals(1, result.length);
        assertEquals(127, result[0], "Value 127 should decode to index 127");
    }

    @Test
    void twoByteSequence_128() {
        // Value 128 encodes as [0x80, 0x01] — MSB set on first byte, second byte = 1
        int[] result = SpongeSchematicParser.decodeVarints(new byte[]{(byte) 0x80, 0x01}, 1);
        assertEquals(1, result.length);
        assertEquals(128, result[0], "Two-byte [0x80, 0x01] must decode to 128");
    }

    @Test
    void twoByteSequence_255() {
        // Value 255 encodes as [0xFF, 0x01] — MSB set on first byte, bits 0-6 = 0x7F, second byte = 1
        int[] result = SpongeSchematicParser.decodeVarints(new byte[]{(byte) 0xFF, 0x01}, 1);
        assertEquals(1, result.length);
        assertEquals(255, result[0], "Two-byte [0xFF, 0x01] must decode to 255");
    }

    @Test
    void threeByteSequence_16384() {
        // Value 16384 = 0x4000 encodes as [0x80, 0x80, 0x01]
        int[] result = SpongeSchematicParser.decodeVarints(new byte[]{(byte) 0x80, (byte) 0x80, 0x01}, 1);
        assertEquals(1, result.length);
        assertEquals(16384, result[0], "Three-byte [0x80, 0x80, 0x01] must decode to 16384");
    }

    @Test
    void largePalette_200EntryRoundTrip() {
        // Create 200 values (0..199) encoded as varints and verify round-trip
        // Values 0-127 are single-byte; values 128-199 are two-byte (exercising the multi-byte path)
        int[] original = new int[200];
        for (int i = 0; i < 200; i++) {
            original[i] = i;
        }
        int[] decoded = roundTrip(original);
        assertArrayEquals(original, decoded,
                "All 200 palette indices 0-199 must round-trip through varint encode/decode correctly");
    }
}
