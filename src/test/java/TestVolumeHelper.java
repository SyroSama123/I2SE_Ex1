
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Creates synthetic NRRD files for testing volume readers.
 *
 * <p>Fixtures use NRRD0004, three dimensions, raw encoding, and
 * X-fastest voxel ordering. Supported types are uchar, char, ushort,
 * short, uint, int, float, and double.</p>
 */
public final class TestVolumeHelper {

    /**
     * Prevents instantiation of this utility class.
     */
    private TestVolumeHelper() {
    }

    /**
     * Writes a three-dimensional NRRD file containing the supplied voxel values.
     *
     * <p>Values are supplied in X-fastest order: X changes first, followed
     * by Y and then Z. Integer values must be integral and within the
     * representable range of the selected NRRD type.</p>
     *
     * @param targetPath destination file path
     * @param width number of voxels along X
     * @param height number of voxels along Y
     * @param depth number of voxels along Z
     * @param type NRRD scalar type
     * @param littleEndian true for little-endian binary data
     * @param voxelValues one numeric value per voxel
     * @throws IOException if writing the fixture fails
     * @throws IllegalArgumentException if dimensions, type, or values are invalid
     */
    public static void createNrrdFile(
            Path targetPath,
            int width,
            int height,
            int depth,
            String type,
            boolean littleEndian,
            double[] voxelValues
    ) throws IOException {
        Objects.requireNonNull(targetPath, "targetPath must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(voxelValues, "voxelValues must not be null");

        if (width <= 0 || height <= 0 || depth <= 0) {
            throw new IllegalArgumentException(
                    "All dimensions must be positive."
            );
        }

        final int voxelCount;
        try {
            voxelCount = Math.multiplyExact(
                    Math.multiplyExact(width, height), depth
            );
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException(
                    "The requested volume is too large.", ex
            );
        }

        if (voxelValues.length != voxelCount) {
            throw new IllegalArgumentException(
                    "Expected " + voxelCount + " voxel values, but received "
                            + voxelValues.length + "."
            );
        }

        int bytesPerVoxel = bytesPerVoxel(type);
        ByteOrder byteOrder = littleEndian
                ? ByteOrder.LITTLE_ENDIAN
                : ByteOrder.BIG_ENDIAN;

        String header = "NRRD0004\n"
                + "type: " + type + "\n"
                + "dimension: 3\n"
                + "sizes: " + width + " " + height + " " + depth + "\n"
                + "encoding: raw\n"
                + (bytesPerVoxel > 1
                ? "endian: " + (littleEndian ? "little" : "big") + "\n"
                : "")
                + "\n";

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.write(header.getBytes(StandardCharsets.US_ASCII));

        ByteBuffer buffer = ByteBuffer
                .allocate(bytesPerVoxel)
                .order(byteOrder);

        for (int i = 0; i < voxelValues.length; i++) {
            double value = voxelValues[i];
            validateValue(type, value, i);

            buffer.clear();

            switch (type) {
                case "uchar", "uint8" ->
                        buffer.put((byte) ((long) value & 0xFFL));
                case "char", "int8" ->
                        buffer.put((byte) value);
                case "ushort", "uint16" ->
                        buffer.putShort((short) ((long) value & 0xFFFFL));
                case "short", "int16" ->
                        buffer.putShort((short) value);
                case "uint", "uint32" ->
                        buffer.putInt((int) ((long) value & 0xFFFFFFFFL));
                case "int", "int32" ->
                        buffer.putInt((int) value);
                case "float" ->
                        buffer.putFloat((float) value);
                case "double" ->
                        buffer.putDouble(value);
                default ->
                        throw new IllegalArgumentException(
                                "Unsupported test NRRD type: " + type
                        );
            }

            output.write(buffer.array());
        }

        Path parent = targetPath.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.write(targetPath, output.toByteArray());
    }

    /**
     * Returns the storage size of one voxel for a supported NRRD type.
     *
     * @param type NRRD scalar type
     * @return bytes per voxel
     * @throws IllegalArgumentException if the type is unsupported
     */
    private static int bytesPerVoxel(String type) {
        return switch (type) {
            case "uchar", "uint8", "char", "int8" -> 1;
            case "ushort", "uint16", "short", "int16" -> 2;
            case "uint", "uint32", "int", "int32", "float" -> 4;
            case "double" -> 8;
            default -> throw new IllegalArgumentException(
                    "Unsupported test NRRD type: " + type
            );
        };
    }

    /**
     * Checks whether a value is representable by the requested scalar type.
     *
     * @param type NRRD scalar type
     * @param value value to validate
     * @param index voxel index used in diagnostic messages
     * @throws IllegalArgumentException if the value is not representable
     */
    private static void validateValue(String type, double value, int index) {
        if (!Double.isFinite(value)) {
            if (type.equals("float") || type.equals("double")) {
                return;
            }
            throw new IllegalArgumentException(
                    "Non-finite integer voxel at index " + index
            );
        }

        if (type.equals("float") || type.equals("double")) {
            if (type.equals("float")
                    && Math.abs(value) > Float.MAX_VALUE) {
                throw new IllegalArgumentException(
                        "Value exceeds float range at index " + index
                );
            }
            return;
        }

        if (value != Math.rint(value)) {
            throw new IllegalArgumentException(
                    "Fractional integer value at index " + index
            );
        }

        boolean valid = switch (type) {
            case "uchar", "uint8" ->
                    value >= 0 && value <= 255;
            case "char", "int8" ->
                    value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE;
            case "ushort", "uint16" ->
                    value >= 0 && value <= 65535;
            case "short", "int16" ->
                    value >= Short.MIN_VALUE && value <= Short.MAX_VALUE;
            case "uint", "uint32" ->
                    value >= 0 && value <= 4294967295.0;
            case "int", "int32" ->
                    value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE;
            default -> false;
        };

        if (!valid) {
            throw new IllegalArgumentException(
                    "Value " + value + " is outside the range for "
                            + type + " at index " + index
            );
        }
    }
}
