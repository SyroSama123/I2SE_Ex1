
import loci.formats.FormatException;
import loci.formats.FormatTools;
import loci.formats.in.NRRDReader;

import java.io.IOException;
import java.math.BigInteger;

public class NRRDVolumeReader implements AutoCloseable {
    private final NRRDReader reader;
    private final int width;
    private final int height;
    private final int depth;
    private final int pixelType;
    private final int bytesPerPixel;
    private final boolean littleEndian;
    private final String pixelTypeString;

    private byte[] cachedSlice;
    private int cachedZ = -1;
    private byte[][] allSlices;
    private boolean closed = false;

    public NRRDVolumeReader(String filePath)
            throws FormatException, IOException {
        reader = new NRRDReader();
        reader.setId(filePath);

        width = reader.getSizeX();
        height = reader.getSizeY();
        depth = reader.getSizeZ();
        pixelType = reader.getPixelType();
        bytesPerPixel = FormatTools.getBytesPerPixel(pixelType);
        littleEndian = reader.isLittleEndian();
        pixelTypeString = FormatTools.getPixelTypeString(pixelType);
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getDepth() {
        return depth;
    }

    public int getBitsPerPixel() {
        return bytesPerPixel * 8;
    }

    public boolean isLittleEndian() {
        return littleEndian;
    }

    public String getPixelType() {
        return pixelTypeString;
    }

    /**
     * Load each Z slice once into memory.
     * Call this before processing projections.
     */
    public void preloadVolume() throws FormatException, IOException {
        ensureOpen();

        if (allSlices != null) {
            return;
        }

        System.out.println("Preloading " + depth + " NRRD slices...");
        long start = System.nanoTime();

        byte[][] slices = new byte[depth][];

        for (int z = 0; z < depth; z++) {
            slices[z] = reader.openBytes(z);
        }

        allSlices = slices;
        cachedSlice = null;
        cachedZ = -1;

        double seconds =
                (System.nanoTime() - start) / 1_000_000_000.0;

        System.out.printf("Volume preloaded in %.2f seconds.%n", seconds);
    }

    public Number getPixel(int x, int y, int z)
            throws FormatException, IOException {
        checkCoordinates(x, y, z);
        return decodePixel(getSlice(z), pixelOffset(x, y));
    }

    /**
     * Fast pixel access for projection loops.
     * Coordinates must be valid.
     */
    public double getPixelValueUnchecked(int x, int y, int z)
            throws FormatException, IOException {
        ensureOpen();

        byte[] slice = getSlice(z);
        int offset = pixelOffset(x, y);
        long bits = readBits(slice, offset);

        if (pixelType == FormatTools.FLOAT) {
            return Float.intBitsToFloat((int) bits);
        }

        if (pixelType == FormatTools.DOUBLE) {
            return Double.longBitsToDouble(bits);
        }

        if ("uint64".equalsIgnoreCase(pixelTypeString)) {
            if (bits >= 0) {
                return (double) bits;
            }

            return (double) (bits & Long.MAX_VALUE) + 0x1.0p63;
        }

        if ("int64".equalsIgnoreCase(pixelTypeString)) {
            return (double) bits;
        }

        switch (pixelType) {
            case FormatTools.INT8:
                return (byte) bits;
            case FormatTools.UINT8:
                return bits & 0xFFL;
            case FormatTools.INT16:
                return (short) bits;
            case FormatTools.UINT16:
                return bits & 0xFFFFL;
            case FormatTools.INT32:
                return (int) bits;
            case FormatTools.UINT32:
                return Integer.toUnsignedLong((int) bits);
            default:
                throw new IllegalArgumentException(
                        "Unsupported pixel type: " + pixelTypeString);
        }
    }

    private byte[] getSlice(int z) throws FormatException, IOException {
        ensureOpen();

        if (z < 0 || z >= depth) {
            throw new IndexOutOfBoundsException("Invalid Z coordinate: " + z);
        }

        // Fast path: all slices are already in memory.
        if (allSlices != null) {
            return allSlices[z];
        }

        // Fallback for callers that have not preloaded the volume.
        if (cachedSlice == null || cachedZ != z) {
            cachedSlice = reader.openBytes(z);
            cachedZ = z;
        }

        return cachedSlice;
    }

    private int pixelOffset(int x, int y) {
        return (y * width + x) * bytesPerPixel;
    }

    private void checkCoordinates(int x, int y, int z) {
        ensureOpen();

        if (x < 0 || x >= width ||
                y < 0 || y >= height ||
                z < 0 || z >= depth) {
            throw new IndexOutOfBoundsException(
                    "Voxel coordinates out of range: (" +
                            x + ", " + y + ", " + z + ")");
        }
    }

    private long readBits(byte[] data, int offset) {
        long value = 0;

        if (littleEndian) {
            for (int i = 0; i < bytesPerPixel; i++) {
                value |= (data[offset + i] & 0xFFL) << (8 * i);
            }
        } else {
            for (int i = 0; i < bytesPerPixel; i++) {
                value = (value << 8) | (data[offset + i] & 0xFFL);
            }
        }

        return value;
    }

    private Number decodePixel(byte[] data, int offset) {
        long bits = readBits(data, offset);

        if (pixelType == FormatTools.FLOAT) {
            return Float.intBitsToFloat((int) bits);
        }

        if (pixelType == FormatTools.DOUBLE) {
            return Double.longBitsToDouble(bits);
        }

        if ("uint64".equalsIgnoreCase(pixelTypeString)) {
            BigInteger value = BigInteger.valueOf(bits);

            if (bits < 0) {
                value = value.add(BigInteger.ONE.shiftLeft(64));
            }

            return value;
        }

        if ("int64".equalsIgnoreCase(pixelTypeString)) {
            return bits;
        }

        switch (pixelType) {
            case FormatTools.INT8:
                return (byte) bits;
            case FormatTools.UINT8:
                return bits & 0xFFL;
            case FormatTools.INT16:
                return (short) bits;
            case FormatTools.UINT16:
                return bits & 0xFFFFL;
            case FormatTools.INT32:
                return (int) bits;
            case FormatTools.UINT32:
                return Integer.toUnsignedLong((int) bits);
            default:
                throw new IllegalArgumentException(
                        "Unsupported pixel type: " + pixelTypeString);
        }
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException(
                    "The NRRD volume reader is already closed.");
        }
    }

    @Override
    public void close() throws IOException {
        if (!closed) {
            closed = true;
            allSlices = null;
            cachedSlice = null;
            cachedZ = -1;
            reader.close();
        }
    }
}
