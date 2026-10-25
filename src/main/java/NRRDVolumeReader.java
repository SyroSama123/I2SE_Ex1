
import loci.formats.FormatException;
import loci.formats.FormatTools;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Arrays;

public class NRRDVolumeReader implements AutoCloseable {

    private final loci.formats.in.NRRDReader reader;
    private byte[] cachedSlice;
    private int cachedZ = -1;
    private boolean closed = false;

    public NRRDVolumeReader(String filePath)
            throws FormatException, IOException {

        reader = new loci.formats.in.NRRDReader();
        reader.setId(filePath);
    }

    public int getWidth() {
        return reader.getSizeX();
    }

    public int getHeight() {
        return reader.getSizeY();
    }

    public int getDepth() {
        return reader.getSizeZ();
    }

    public int getBitsPerPixel() {
        return FormatTools.getBytesPerPixel(reader.getPixelType()) * 8;
    }

    public boolean isLittleEndian() {
        return reader.isLittleEndian();
    }

    public String getPixelType() {
        return FormatTools.getPixelTypeString(reader.getPixelType());
    }

    /**
     * Returns the pixel at (x, y, z).
     *
     * Integer pixels return Integer, Long, or BigInteger.
     * Floating-point pixels return Float or Double.
     *
     * Assumes a scalar volume with one sample per voxel.
     */
    public Number getPixel(int x, int y, int z)
            throws FormatException, IOException {

        ensureOpen();

        if (x < 0 || x >= getWidth()
                || y < 0 || y >= getHeight()
                || z < 0 || z >= getDepth()) {
            throw new IndexOutOfBoundsException(
                    "Pixel coordinates out of bounds: ("
                            + x + ", " + y + ", " + z + ")");
        }

        // Load a slice only when needed.
        if (cachedSlice == null || cachedZ != z) {
            cachedSlice = reader.openBytes(z);
            cachedZ = z;
        }

        int bytesPerPixel =
                FormatTools.getBytesPerPixel(reader.getPixelType());

        int offset = Math.multiplyExact(
                Math.addExact(Math.multiplyExact(y, getWidth()), x),
                bytesPerPixel);

        if (offset < 0
                || offset > cachedSlice.length - bytesPerPixel) {
            throw new IndexOutOfBoundsException(
                    "Pixel data offset out of bounds: " + offset);
        }

        return decodePixel(cachedSlice, offset);
    }

    private Number decodePixel(byte[] data, int offset) {
        int type = reader.getPixelType();
        boolean littleEndian = reader.isLittleEndian();
        int bytes = FormatTools.getBytesPerPixel(type);

        // Floating-point pixels need bit conversion, not integer decoding.
        if (type == FormatTools.FLOAT) {
            int bits = (int) decodeUnsignedBits(
                    data, offset, bytes, littleEndian);
            return Float.intBitsToFloat(bits);
        }

        if (type == FormatTools.DOUBLE) {
            long bits = decodeUnsignedBits(
                    data, offset, bytes, littleEndian);
            return Double.longBitsToDouble(bits);
        }

        // Unsigned 64-bit integers require BigInteger.
        if ("uint64".equalsIgnoreCase(
                FormatTools.getPixelTypeString(type))) {

            byte[] ordered = new byte[8];

            for (int i = 0; i < 8; i++) {
                int sourceIndex = littleEndian
                        ? offset + 7 - i
                        : offset + i;

                ordered[i] = data[sourceIndex];
            }

            return new BigInteger(1, ordered);
        }

        long bits = decodeUnsignedBits(
                data, offset, bytes, littleEndian);

        if ("int64".equalsIgnoreCase(
                FormatTools.getPixelTypeString(type))) {
            return bits;
        }

        switch (type) {
            case FormatTools.INT8:
                return (int) (byte) bits;

            case FormatTools.UINT8:
                return (int) bits;

            case FormatTools.INT16:
                return (int) (short) bits;

            case FormatTools.UINT16:
                return (int) bits;

            case FormatTools.INT32:
                return (int) bits;

            case FormatTools.UINT32:
                return Integer.toUnsignedLong((int) bits);



            default:
                throw new IllegalArgumentException(
                        "Unsupported pixel type: "
                                + FormatTools.getPixelTypeString(type));
        }
    }

    /**
     * Reads 1, 2, 4, or 8 bytes into their raw bit pattern.
     * The returned long preserves all 64 bits, including bit 63.
     */
    private static long decodeUnsignedBits(
            byte[] data, int offset, int bytes,
            boolean littleEndian) {

        if (bytes != 1 && bytes != 2
                && bytes != 4 && bytes != 8) {
            throw new IllegalArgumentException(
                    "Unsupported pixel size: " + bytes + " bytes");
        }

        long value = 0;

        for (int i = 0; i < bytes; i++) {
            int index = littleEndian
                    ? offset + i
                    : offset + bytes - 1 - i;

            value |= (data[index] & 0xFFL) << (8 * i);
        }

        return value;
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException(
                    "NRRDVolumeReader is already closed");
        }
    }

    @Override
    public void close() throws IOException {
        if (!closed) {
            closed = true;
            cachedSlice = null;
            cachedZ = -1;
            reader.close();
        }
    }
}
