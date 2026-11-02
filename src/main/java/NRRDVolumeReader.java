
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.zip.GZIPInputStream;

/**
 * Reads a three-dimensional scalar NRRD volume.
 *
 * <p>This implementation parses the NRRD header directly, avoiding
 * Bio-Formats' interpretation of a small leading dimension as channels.</p>
 *
 * <p>Supported encodings: raw and gzip/gz, for inline .nrrd files.</p>
 *
 * <p>Voxel coordinates are zero-based, with X varying fastest in the
 * file's raw data.</p>
 */
public class NRRDVolumeReader implements AutoCloseable {

    private final int width;
    private final int height;
    private final int depth;
    private final String pixelTypeString;
    private final int bytesPerPixel;
    private final boolean littleEndian;
    private final String encoding;
    private final Path filePath;
    private final long dataOffset;

    private byte[] volumeData;
    private boolean closed;

    /**
     * Opens an inline NRRD file and parses its header.
     *
     * @param filePath path to the .nrrd file
     * @throws IOException if the file cannot be read or its header is invalid
     */
    public NRRDVolumeReader(String filePath) throws IOException {
        this.filePath = Path.of(filePath);

        byte[] fileBytes = Files.readAllBytes(this.filePath);
        int headerEnd = findHeaderEnd(fileBytes);

        String header = new String(
                fileBytes, 0, headerEnd, StandardCharsets.US_ASCII);

        if (!header.startsWith("NRRD")) {
            throw new IOException("Not a valid NRRD file: " + filePath);
        }

        String type = requiredField(header, "type")
                .toLowerCase(Locale.ROOT);
        String[] sizes = requiredField(header, "sizes").trim().split("\\s+");

        int dimension = Integer.parseInt(requiredField(header, "dimension"));
        if (dimension != 3 || sizes.length != 3) {
            throw new IOException(
                    "Expected a 3D scalar NRRD, but dimension is " + dimension);
        }

        width = positiveInt(sizes[0], "X size");
        height = positiveInt(sizes[1], "Y size");
        depth = positiveInt(sizes[2], "Z size");

        pixelTypeString = normalizeType(type);
        bytesPerPixel = bytesPerPixel(pixelTypeString);

        String endianField = optionalField(header, "endian");
        littleEndian = endianField == null
                || endianField.trim().equalsIgnoreCase("little");

        if (bytesPerPixel > 1 && endianField != null
                && !endianField.trim().equalsIgnoreCase("little")
                && !endianField.trim().equalsIgnoreCase("big")) {
            throw new IOException("Unsupported NRRD endian value: " + endianField);
        }

        String enc = optionalField(header, "encoding");
        encoding = enc == null ? "raw" : enc.trim().toLowerCase(Locale.ROOT);

        if (!encoding.equals("raw") && !encoding.equals("gzip")
                && !encoding.equals("gz")) {
            throw new IOException("Unsupported NRRD encoding: " + encoding);
        }

        dataOffset = headerEnd;

        if (optionalField(header, "data file") != null
                || optionalField(header, "datafile") != null) {
            throw new IOException(
                    "Detached NRRD files (.nhdr) are not supported.");
        }

        if (optionalField(header, "kinds") != null) {
            String[] kinds = requiredField(header, "kinds")
                    .trim().split("\\s+");
            if (kinds.length != 3) {
                throw new IOException("Invalid NRRD kinds field.");
            }
            for (String kind : kinds) {
                if (!kind.equalsIgnoreCase("domain")
                        && !kind.equalsIgnoreCase("space")) {
                    throw new IOException(
                            "Expected three spatial axes; found kind: " + kind);
                }
            }
        }

        // Validate dimensions and expected payload size without overflowing.
        expectedDataSize();
    }

    /** Returns the X dimension. */
    public int getWidth() {
        return width;
    }

    /** Returns the Y dimension. */
    public int getHeight() {
        return height;
    }

    /** Returns the Z dimension. */
    public int getDepth() {
        return depth;
    }

    /** Returns the pixel type's bit depth. */
    public int getBitsPerPixel() {
        return bytesPerPixel * 8;
    }

    /** Returns true when multibyte values use little-endian byte order. */
    public boolean isLittleEndian() {
        return littleEndian;
    }

    /** Returns the normalized NRRD pixel type. */
    public String getPixelType() {
        return pixelTypeString;
    }

    /**
     * Loads the entire volume into memory. Repeated calls are harmless.
     *
     * @throws IOException if the data cannot be read or is truncated
     */
    public void preloadVolume() throws IOException {
        ensureOpen();

        if (volumeData != null) {
            return;
        }

        byte[] fileBytes = Files.readAllBytes(filePath);
        if (dataOffset > fileBytes.length) {
            throw new IOException("NRRD header extends beyond end of file.");
        }

        byte[] payload = Arrays.copyOfRange(
                fileBytes, Math.toIntExact(dataOffset), fileBytes.length);

        if (encoding.equals("gzip") || encoding.equals("gz")) {
            payload = decompressGzip(payload);
        }

        long expected = expectedDataSize();
        if (payload.length < expected) {
            throw new IOException(
                    "NRRD data is truncated: expected " + expected
                            + " bytes, got " + payload.length);
        }

        if (payload.length > expected) {
            payload = Arrays.copyOf(payload, Math.toIntExact(expected));
        }

        volumeData = payload;
    }

    /**
     * Returns a voxel as a Number without losing unsigned integer precision
     * for 64-bit values.
     */
    public Number getPixel(int x, int y, int z) throws IOException {
        checkCoordinates(x, y, z);
        preloadVolume();

        int offset = Math.toIntExact(
                (((long) z * height + y) * width + x) * bytesPerPixel);

        ByteBuffer buffer = ByteBuffer.wrap(volumeData, offset, bytesPerPixel)
                .order(littleEndian ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN);

        switch (pixelTypeString) {
            case "int8":
                return buffer.get();
            case "uint8":
                return Byte.toUnsignedInt(buffer.get());
            case "int16":
                return buffer.getShort();
            case "uint16":
                return Short.toUnsignedInt(buffer.getShort());
            case "int32":
                return buffer.getInt();
            case "uint32":
                return Integer.toUnsignedLong(buffer.getInt());
            case "int64":
                return buffer.getLong();
            case "uint64":
                return unsignedLongToBigInteger(buffer.getLong());
            case "float":
                return buffer.getFloat();
            case "double":
                return buffer.getDouble();
            default:
                throw new IOException("Unsupported pixel type: " + pixelTypeString);
        }
    }

    /**
     * Returns a voxel as double for calculations and projections.
     * Large 64-bit integers may lose precision when converted to double.
     */
    public double getPixelValueUnchecked(int x, int y, int z)
            throws IOException {
        return getPixel(x, y, z).doubleValue();
    }

    private void checkCoordinates(int x, int y, int z) {
        ensureOpen();

        if (x < 0 || x >= width || y < 0 || y >= height
                || z < 0 || z >= depth) {
            throw new IndexOutOfBoundsException(
                    "Voxel coordinates out of range: ("
                            + x + ", " + y + ", " + z + ")");
        }
    }

    private long expectedDataSize() throws IOException {
        try {
            return Math.multiplyExact(
                    Math.multiplyExact(
                            Math.multiplyExact((long) width, height), depth),
                    bytesPerPixel);
        } catch (ArithmeticException ex) {
            throw new IOException("NRRD volume is too large.", ex);
        }
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException(
                    "The NRRD volume reader is already closed.");
        }
    }

    @Override
    public void close() {
        closed = true;
        volumeData = null;
    }

    private static int findHeaderEnd(byte[] bytes) throws IOException {
        for (int i = 0; i < bytes.length - 1; i++) {
            if (bytes[i] == '\n' && bytes[i + 1] == '\n') {
                return i + 2;
            }
            if (i + 3 < bytes.length
                    && bytes[i] == '\r' && bytes[i + 1] == '\n'
                    && bytes[i + 2] == '\r' && bytes[i + 3] == '\n') {
                return i + 4;
            }
        }
        throw new IOException("NRRD header terminator was not found.");
    }

    private static String requiredField(String header, String name)
            throws IOException {
        String value = optionalField(header, name);
        if (value == null || value.isBlank()) {
            throw new IOException("Missing NRRD header field: " + name);
        }
        return value;
    }

    private static String optionalField(String header, String name) {
        for (String line : header.split("\\r?\\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")
                    || trimmed.startsWith("NRRD")) {
                continue;
            }

            int colon = trimmed.indexOf(':');
            if (colon > 0
                    && trimmed.substring(0, colon).trim()
                    .equalsIgnoreCase(name)) {
                return trimmed.substring(colon + 1).trim();
            }
        }
        return null;
    }

    private static int positiveInt(String value, String name)
            throws IOException {
        try {
            int result = Integer.parseInt(value);
            if (result <= 0) {
                throw new IOException(name + " must be positive.");
            }
            return result;
        } catch (NumberFormatException ex) {
            throw new IOException("Invalid " + name + ": " + value, ex);
        }
    }

    private static String normalizeType(String type) throws IOException {
        switch (type) {
            case "signed char":
            case "int8":
            case "int8_t":
            case "char":
                return "int8";
            case "unsigned char":
            case "uchar":
            case "uint8":
            case "uint8_t":
                return "uint8";
            case "short":
            case "short int":
            case "signed short":
            case "int16":
            case "int16_t":
                return "int16";
            case "unsigned short":
            case "unsigned short int":
            case "ushort":
            case "uint16":
            case "uint16_t":
                return "uint16";
            case "int":
            case "signed int":
            case "int32":
            case "int32_t":
                return "int32";
            case "unsigned int":
            case "uint":
            case "uint32":
            case "uint32_t":
                return "uint32";
            case "long long":
            case "long long int":
            case "int64":
            case "int64_t":
                return "int64";
            case "unsigned long long":
            case "unsigned long long int":
            case "uint64":
            case "uint64_t":
                return "uint64";
            case "float":
            case "double":
                return type;
            default:
                throw new IOException("Unsupported NRRD type: " + type);
        }
    }

    private static int bytesPerPixel(String type) {
        switch (type) {
            case "int8":
            case "uint8":
                return 1;
            case "int16":
            case "uint16":
                return 2;
            case "int32":
            case "uint32":
            case "float":
                return 4;
            case "int64":
            case "uint64":
            case "double":
                return 8;
            default:
                throw new IllegalArgumentException("Unsupported pixel type: " + type);
        }
    }

    private static byte[] decompressGzip(byte[] compressed) throws IOException {
        try (GZIPInputStream input = new GZIPInputStream(
                new java.io.ByteArrayInputStream(compressed));
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            input.transferTo(output);
            return output.toByteArray();
        }
    }

    private static BigInteger unsignedLongToBigInteger(long value) {
        BigInteger result = BigInteger.valueOf(value & Long.MAX_VALUE);
        if (value < 0) {
            result = result.setBit(63);
        }
        return result;
    }
}
