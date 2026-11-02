
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests metadata, voxel decoding, preloading, and lifecycle behavior
 * of {@link NRRDVolumeReader}.
 */
public class NRRDVolumeReaderTest {

    /**
     * Verifies that Bio-Formats metadata matches the dimensions declared
     * by a synthetic NRRD fixture.
     *
     * @param tempDir JUnit-managed temporary directory
     * @throws Exception if fixture creation or reader initialization fails
     */
    @Test
    @DisplayName("Loads the dimensions declared by a NRRD fixture")
    public void testBasicMetadata(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("metadata.nrrd");

        TestVolumeHelper.createNrrdFile(
                file, 10, 5, 3, "ushort", true, new double[150]
        );

        try (NRRDVolumeReader reader =
                     new NRRDVolumeReader(file.toString())) {
            assertAll(
                    "NRRD dimensions and pixel metadata",
                    () -> assertEquals(10, reader.getWidth(),
                            "Unexpected X dimension"),
                    () -> assertEquals(5, reader.getHeight(),
                            "Unexpected Y dimension"),
                    () -> assertEquals(3, reader.getDepth(),
                            "Unexpected Z dimension"),
                    () -> assertEquals(16, reader.getBitsPerPixel()),
                    () -> assertTrue(reader.isLittleEndian())
            );
        }
    }

    /**
     * Verifies unsigned 8-bit decoding at the minimum, middle, and maximum
     * values representable by the type.
     *
     * @param tempDir JUnit-managed temporary directory
     * @throws Exception if fixture creation or reading fails
     */
    @Test
    @DisplayName("Decodes unsigned 8-bit pixels")
    public void testUnsignedByteDecoding(@TempDir Path tempDir)
            throws Exception {
        Path file = tempDir.resolve("uint8.nrrd");

        TestVolumeHelper.createNrrdFile(
                file, 3, 2, 2, "uchar", true,
                new double[]{
                        0, 127, 255,
                        10, 20, 30,
                        40, 50, 60,
                        70, 80, 90
                }
        );

        try (NRRDVolumeReader reader =
                     new NRRDVolumeReader(file.toString())) {
            assertEquals(3, reader.getWidth());
            assertEquals(2, reader.getHeight());
            assertEquals(2, reader.getDepth());

            assertEquals(0.0,
                    reader.getPixelValueUnchecked(0, 0, 0), 1e-6);
            assertEquals(127.0,
                    reader.getPixelValueUnchecked(1, 0, 0), 1e-6);
            assertEquals(255.0,
                    reader.getPixelValueUnchecked(2, 0, 0), 1e-6);

            assertEquals(0L, reader.getPixel(0, 0, 0).longValue());
            assertEquals(255L, reader.getPixel(2, 0, 0).longValue());
        }
    }

    /**
     * Verifies floating-point decoding using a single-precision NRRD fixture.
     *
     * @param tempDir JUnit-managed temporary directory
     * @throws Exception if fixture creation or reading fails
     */
    @Test
    @DisplayName("Decodes single-precision floating-point pixels")
    public void testFloatDecoding(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("float.nrrd");

        TestVolumeHelper.createNrrdFile(
                file, 3, 2, 2, "float", true,
                new double[]{
                        -1.5, 0.0, 3.14159,
                        4.25, 5.5, 6.75,
                        7.0, 8.0, 9.0,
                        10.0, 11.0, 12.0
                }
        );

        try (NRRDVolumeReader reader =
                     new NRRDVolumeReader(file.toString())) {
            assertEquals(-1.5,
                    reader.getPixelValueUnchecked(0, 0, 0), 1e-5);
            assertEquals(0.0,
                    reader.getPixelValueUnchecked(1, 0, 0), 1e-5);
            assertEquals((double) (float) 3.14159,
                    reader.getPixelValueUnchecked(2, 0, 0), 1e-5);
            assertEquals(7.0,
                    reader.getPixelValueUnchecked(0, 0, 1), 1e-5);
        }
    }

    /**
     * Verifies double-precision decoding independently of float decoding.
     *
     * @param tempDir JUnit-managed temporary directory
     * @throws Exception if fixture creation or reading fails
     */
    @Test
    @DisplayName("Decodes double-precision floating-point pixels")
    public void testDoubleDecoding(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("double.nrrd");

        TestVolumeHelper.createNrrdFile(
                file, 3, 2, 2, "double", false,
                new double[]{
                        -1.0 / 3.0, 0.0, Math.PI,
                        4.0, 5.0, 6.0,
                        7.0, 8.0, 9.0,
                        10.0, 11.0, 12.0
                }
        );

        try (NRRDVolumeReader reader =
                     new NRRDVolumeReader(file.toString())) {
            assertFalse(reader.isLittleEndian());
            assertEquals(-1.0 / 3.0,
                    reader.getPixelValueUnchecked(0, 0, 0), 1e-12);
            assertEquals(Math.PI,
                    reader.getPixelValueUnchecked(2, 0, 0), 1e-12);
        }
    }

    /**
     * Verifies that repeated preloading preserves values across all slices.
     *
     * @param tempDir JUnit-managed temporary directory
     * @throws Exception if fixture creation, loading, or reading fails
     */
    @Test
    @DisplayName("Preloading preserves voxel values across slices")
    public void testPreloading(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("preload.nrrd");

        double[] values = {
                10, 20, 30, 40,
                50, 60, 70, 80
        };

        TestVolumeHelper.createNrrdFile(
                file, 2, 2, 2, "uchar", true, values
        );

        try (NRRDVolumeReader reader =
                     new NRRDVolumeReader(file.toString())) {
            assertEquals(2, reader.getWidth());
            assertEquals(2, reader.getHeight());
            assertEquals(2, reader.getDepth());

            assertEquals(10.0,
                    reader.getPixelValueUnchecked(0, 0, 0), 1e-6);
            assertEquals(50.0,
                    reader.getPixelValueUnchecked(0, 0, 1), 1e-6);

            reader.preloadVolume();
            reader.preloadVolume();

            assertEquals(10.0,
                    reader.getPixelValueUnchecked(0, 0, 0), 1e-6);
            assertEquals(40.0,
                    reader.getPixelValueUnchecked(1, 1, 0), 1e-6);
            assertEquals(50.0,
                    reader.getPixelValueUnchecked(0, 0, 1), 1e-6);
            assertEquals(80.0,
                    reader.getPixelValueUnchecked(1, 1, 1), 1e-6);
        }
    }

    /**
     * Verifies coordinate validation and behavior after the reader is closed.
     *
     * @param tempDir JUnit-managed temporary directory
     * @throws Exception if fixture creation or reader initialization fails
     */
    @Test
    @DisplayName("Rejects invalid coordinates and reads after close")
    public void testBoundsAndClosedState(@TempDir Path tempDir)
            throws Exception {
        Path file = tempDir.resolve("bounds.nrrd");

        TestVolumeHelper.createNrrdFile(
                file, 2, 2, 2, "uchar", true, new double[8]
        );

        NRRDVolumeReader reader =
                new NRRDVolumeReader(file.toString());

        try {
            assertThrows(IndexOutOfBoundsException.class,
                    () -> reader.getPixel(-1, 0, 0));
            assertThrows(IndexOutOfBoundsException.class,
                    () -> reader.getPixel(2, 0, 0));
            assertThrows(IndexOutOfBoundsException.class,
                    () -> reader.getPixel(0, -1, 0));
            assertThrows(IndexOutOfBoundsException.class,
                    () -> reader.getPixel(0, 0, 2));
        } finally {
            reader.close();
        }

        assertDoesNotThrow(reader::close);

        assertThrows(IllegalStateException.class,
                () -> reader.getPixel(0, 0, 0));
        assertThrows(IllegalStateException.class,
                reader::preloadVolume);
    }
}
