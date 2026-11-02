import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test suite for Mp4VideoCreator, verifying frame sequence validation,
 * parameter checking, and handling of FFmpeg process execution paths.
 */
public class Mp4VideoCreatorTest {

    /**
     * Helper method to create a dummy PNG frame file.
     */
    private void createDummyPng(Path path) throws IOException {
        BufferedImage img = new BufferedImage(10, 10, BufferedImage.TYPE_BYTE_GRAY);
        ImageIO.write(img, "png", path.toFile());
    }

    /**
     * Tests validation of FPS and input directory existence.
     */
    @Test
    @DisplayName("Test input parameter validation for FPS and directory existence")
    public void testInputValidation(@TempDir Path tempDir) {
        Path missingDir = tempDir.resolve("nonexistent");
        Path outFile = tempDir.resolve("output.mp4");

        assertThrows(IllegalArgumentException.class, () ->
                Mp4VideoCreator.createVideo(tempDir.toString(), outFile.toString(), 0));

        assertThrows(IllegalArgumentException.class, () ->
                Mp4VideoCreator.createVideo(tempDir.toString(), outFile.toString(), -10));

        assertThrows(IOException.class, () ->
                Mp4VideoCreator.createVideo(missingDir.toString(), outFile.toString(), 30));
    }

    /**
     * Tests frame sequence checking: empty directories, gaps, and missing initial frames.
     */
    @Test
    @DisplayName("Test frame sequence completeness and gap detection")
    public void testFrameSequenceValidation(@TempDir Path tempDir) throws IOException {
        Path framesDir = tempDir.resolve("frames");
        Files.createDirectories(framesDir);
        Path outFile = tempDir.resolve("output.mp4");

        // Empty directory
        assertThrows(IOException.class, () ->
                Mp4VideoCreator.createVideo(framesDir.toString(), outFile.toString(), 25));

        // Sequence starting at frame 1 instead of 0
        createDummyPng(framesDir.resolve("projection_1.png"));
        assertThrows(IOException.class, () ->
                Mp4VideoCreator.createVideo(framesDir.toString(), outFile.toString(), 25));

        // Fix start frame, but introduce gap (missing frame 1)
        createDummyPng(framesDir.resolve("projection_0.png"));
        createDummyPng(framesDir.resolve("projection_2.png"));
        Files.deleteIfExists(framesDir.resolve("projection_1.png"));

        assertThrows(IOException.class, () ->
                Mp4VideoCreator.createVideo(framesDir.toString(), outFile.toString(), 25));
    }
}
