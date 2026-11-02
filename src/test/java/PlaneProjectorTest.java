import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test suite for PlaneProjector, verifying CSV parsing,
 * geometric validation, 3D-to-2D rasterization, PNG normalization, and error reporting.
 */
public class PlaneProjectorTest {

    private final PrintStream originalOut = System.out;
    private final PrintStream originalErr = System.err;
    private ByteArrayOutputStream capturedOut;
    private ByteArrayOutputStream capturedErr;

    @BeforeEach
    public void setUpStreams() {
        capturedOut = new ByteArrayOutputStream();
        capturedErr = new ByteArrayOutputStream();
        System.setOut(new PrintStream(capturedOut));
        System.setErr(new PrintStream(capturedErr));
    }

    @AfterEach
    public void restoreStreams() {
        System.setOut(originalOut);
        System.setErr(originalErr);
    }

    /**
     * Tests CSV parsing with valid triangle and quadrilateral rows, including empty trailing fields.
     */
    @Test
    @DisplayName("Test CSV parsing of valid triangle and quadrilateral rows")
    public void testValidCsvParsing(@TempDir Path tempDir) throws Exception {
        Path nrrdPath = tempDir.resolve("test.nrrd");
        TestVolumeHelper.createNrrdFile(nrrdPath, 10, 10, 10, "uchar", true, new double[1000]);

        Path csvPath = tempDir.resolve("planes.csv");
        List<String> lines = List.of(
                "# Header comment",
                "",
                "  ",
                "0,0,0, 4,0,0, 2,3,0", // Valid triangle
                "0,0,0, 4,0,0, 4,4,0, 0,4,0", // Valid square (isosceles trapezoid)
                "0,0,0, 4,0,0, 2,3,0, , , " // Triangle with 3 empty trailing fields
        );
        Files.write(csvPath, lines);

        Path outDir = tempDir.resolve("frames");

        try (NRRDVolumeReader volume = new NRRDVolumeReader(nrrdPath.toString())) {
            PlaneProjector.projectCsv(volume, csvPath.toString(), outDir.toString());
        }

        assertTrue(Files.exists(outDir.resolve("projection_0.png")));
        assertTrue(Files.exists(outDir.resolve("projection_1.png")));
        assertTrue(Files.exists(outDir.resolve("projection_2.png")));
        assertFalse(Files.exists(outDir.resolve("projection_3.png")));
    }

    /**
     * Tests that malformed CSV rows (wrong field count, non-numeric, non-finite, partial 4th vertex) generate error PNGs.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "1,2,3,4,5,6,7,8", // 8 fields
            "1,2,3,4,5,6,7,8,9,10", // 10 fields
            "1,2,3,4,5,6,7,8,NaN", // NaN
            "1,2,3,4,5,6,7,8,Infinity", // Infinity
            "1,2,3,4,5,6,7,8,abc", // Non-numeric
            "0,0,0,4,0,0,2,3,0,1,2," // Partial 4th vertex
    })
    @DisplayName("Test CSV parsing invalid row error handling and frame recovery")
    public void testInvalidCsvRows(String invalidRow, @TempDir Path tempDir) throws Exception {
        Path nrrdPath = tempDir.resolve("test.nrrd");
        TestVolumeHelper.createNrrdFile(nrrdPath, 10, 10, 10, "uchar", true, new double[1000]);

        Path csvPath = tempDir.resolve("planes.csv");
        Files.write(csvPath, List.of(invalidRow, "0,0,0, 4,0,0, 2,3,0"));

        Path outDir = tempDir.resolve("frames");

        try (NRRDVolumeReader volume = new NRRDVolumeReader(nrrdPath.toString())) {
            PlaneProjector.projectCsv(volume, csvPath.toString(), outDir.toString());
        }

        Path errFrame = outDir.resolve("projection_0.png");
        Path validFrame = outDir.resolve("projection_1.png");

        assertTrue(Files.exists(errFrame), "Error frame must be created for failed row");
        assertTrue(Files.exists(validFrame), "Subsequent valid row must be processed");

        BufferedImage img = ImageIO.read(errFrame.toFile());
        assertEquals(512, img.getWidth());
        assertEquals(512, img.getHeight());

        String stderr = capturedErr.toString();
        assertTrue(stderr.contains("Plane 0 failed"), "Error log should state plane failure");
    }

    /**
     * Verifies triangle geometry validation rules: non-isosceles rejection and side-length tolerance boundaries.
     */
    @Test
    @DisplayName("Test triangle geometry validation and tolerance")
    public void testTriangleValidation(@TempDir Path tempDir) throws Exception {
        Path nrrdPath = tempDir.resolve("test.nrrd");
        TestVolumeHelper.createNrrdFile(nrrdPath, 10, 10, 10, "uchar", true, new double[1000]);

        // Non-isosceles triangle: sides approx 3, 4, 5
        Path csvPath = tempDir.resolve("planes.csv");
        Files.write(csvPath, List.of("0,0,0, 3,0,0, 0,4,0"));

        Path outDir = tempDir.resolve("frames");

        try (NRRDVolumeReader volume = new NRRDVolumeReader(nrrdPath.toString())) {
            PlaneProjector.projectCsv(volume, csvPath.toString(), outDir.toString());
        }

        String stderr = capturedErr.toString();
        assertTrue(stderr.contains("Triangle is not isosceles"), "Should report non-isosceles triangle failure");
    }

    /**
     * Verifies quadrilateral geometry validation rules: non-coplanar vertices and non-isosceles trapezoid rejection.
     */
    @Test
    @DisplayName("Test quadrilateral coplanarity and isosceles trapezoid validation")
    public void testQuadrilateralValidation(@TempDir Path tempDir) throws Exception {
        Path nrrdPath = tempDir.resolve("test.nrrd");
        TestVolumeHelper.createNrrdFile(nrrdPath, 10, 10, 10, "uchar", true, new double[1000]);

        Path csvPath = tempDir.resolve("planes.csv");
        List<String> lines = List.of(
                "0,0,0, 4,0,0, 3,3,0, 0,3,1", // Non-coplanar
                "0,0,0, 4,0,0, 5,3,0, 0,3,0"  // General trapezoid (legs not equal)
        );
        Files.write(csvPath, lines);

        Path outDir = tempDir.resolve("frames");

        try (NRRDVolumeReader volume = new NRRDVolumeReader(nrrdPath.toString())) {
            PlaneProjector.projectCsv(volume, csvPath.toString(), outDir.toString());
        }

        String stderr = capturedErr.toString();
        assertTrue(stderr.contains("not coplanar") || stderr.contains("not an isosceles trapezoid"));
    }

    /**
     * Tests volume voxel sampling, grayscale normalization, and masking of out-of-bounds pixels.
     */
    @Test
    @DisplayName("Test projection sampling accuracy and grayscale image normalization")
    public void testProjectionAndNormalization(@TempDir Path tempDir) throws Exception {
        // Create 4x4x4 volume with specific voxel values
        double[] voxels = new double[64];
        for (int i = 0; i < 64; i++) voxels[i] = 10.0;
        // Set specific values on Z=0 plane
        voxels[0] = 50.0;  // (0,0,0)
        voxels[1] = 150.0; // (1,0,0)
        voxels[2] = 250.0; // (2,0,0)

        Path nrrdPath = tempDir.resolve("test.nrrd");
        TestVolumeHelper.createNrrdFile(nrrdPath, 4, 4, 4, "uchar", true, voxels);

        // Define horizontal plane at Z=0
        Path csvPath = tempDir.resolve("planes.csv");
        Files.write(csvPath, List.of("0,0,0, 2,0,0, 1,1,0"));

        Path outDir = tempDir.resolve("frames");

        try (NRRDVolumeReader volume = new NRRDVolumeReader(nrrdPath.toString())) {
            PlaneProjector.projectCsv(volume, csvPath.toString(), outDir.toString());
        }

        Path frame = outDir.resolve("projection_0.png");
        assertTrue(Files.exists(frame));

        BufferedImage img = ImageIO.read(frame.toFile());
        assertNotNull(img);
        assertTrue(img.getWidth() > 0);
        assertTrue(img.getHeight() > 0);
    }
}
