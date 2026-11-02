import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test suite for Main CLI application wrapper, testing argument handling,
 * frame directory preparation, stale frame cleanup, and overall pipeline execution.
 */
public class MainTest {

    private final PrintStream originalOut = System.out;
    private final PrintStream originalErr = System.err;
    private ByteArrayOutputStream capturedOut;
    private ByteArrayOutputStream capturedErr;

    @BeforeEach
    public void setUp() {
        capturedOut = new ByteArrayOutputStream();
        capturedErr = new ByteArrayOutputStream();
        System.setOut(new PrintStream(capturedOut));
        System.setErr(new PrintStream(capturedErr));
    }

    @AfterEach
    public void tearDown() {
        System.setOut(originalOut);
        System.setErr(originalErr);
    }

    /**
     * Tests CLI argument count validation.
     */
    @Test
    @DisplayName("Test Main execution with invalid command line argument counts")
    public void testArgumentValidation() {
        assertEquals(1, Main.runApp(new String[]{}));
        assertEquals(1, Main.runApp(new String[]{"one"}));
        assertEquals(1, Main.runApp(new String[]{"one", "two"}));
        assertEquals(1, Main.runApp(new String[]{"one", "two", "three", "four"}));
    }

    /**
     * Tests error handling when provided input NRRD or CSV files do not exist.
     */
    @Test
    @DisplayName("Test Main execution with missing input files")
    public void testMissingInputFiles(@TempDir Path tempDir) {
        Path missingNrrd = tempDir.resolve("missing.nrrd");
        Path missingCsv = tempDir.resolve("missing.csv");
        Path videoPath = tempDir.resolve("out.mp4");

        assertEquals(1, Main.runApp(new String[]{
                missingNrrd.toString(), missingCsv.toString(), videoPath.toString()
        }));
    }

    /**
     * Tests stale frame deletion in projection_frames directory during preparation.
     */
    @Test
    @DisplayName("Test stale frame cleanup in output directory")
    public void testStaleFrameCleanup(@TempDir Path tempDir) throws Exception {
        Path nrrdPath = tempDir.resolve("test.nrrd");
        TestVolumeHelper.createNrrdFile(nrrdPath, 2, 2, 2, "uchar", true, new double[8]);

        Path csvPath = tempDir.resolve("planes.csv");
        Files.write(csvPath, java.util.List.of("0,0,0, 1,0,0, 0,1,0"));

        Path framesDir = tempDir.resolve("projection_frames");
        Files.createDirectories(framesDir);

        Path staleFrame = framesDir.resolve("projection_99.png");
        Path nonFrameFile = framesDir.resolve("keep_me.txt");
        Files.writeString(staleFrame, "stale");
        Files.writeString(nonFrameFile, "keep");

        Path videoPath = tempDir.resolve("out.mp4");

        // runApp will attempt FFmpeg at the end; even if FFmpeg is missing, frame prep happens early
        Main.runApp(new String[]{
                nrrdPath.toString(), csvPath.toString(), videoPath.toString()
        });

        assertFalse(Files.exists(staleFrame), "Stale projection_99.png frame should be deleted");
        assertTrue(Files.exists(nonFrameFile), "Unrelated file keep_me.txt should be preserved");
    }
}
