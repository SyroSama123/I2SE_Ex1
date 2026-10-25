
import loci.formats.FormatException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

public class Main {
    private static final int FRAMES_PER_SECOND = 50;

    public static void main(String[] args) {
        if (args.length != 3) {
            System.err.println(
                    "Usage: java -jar I2SE_Ex1.jar " +
                            "<input.nrrd> <planes.csv> <output.mp4>");
            System.exit(1);
            return;
        }

        Path nrrdPath = Paths.get(args[0]).toAbsolutePath();
        Path csvPath = Paths.get(args[1]).toAbsolutePath();
        Path videoPath = Paths.get(args[2]).toAbsolutePath();

        Path videoParent = videoPath.getParent();
        Path framesDirectory = (videoParent == null
                ? Paths.get("projection_frames").toAbsolutePath()
                : videoParent.resolve("projection_frames"));

        System.out.println("Loading NRRD: " + nrrdPath);
        System.out.println("Reading planes: " + csvPath);
        System.out.println("Output video: " + videoPath);

        try {
            if (!Files.isRegularFile(nrrdPath)) {
                throw new IOException("NRRD file not found: " + nrrdPath);
            }

            if (!Files.isRegularFile(csvPath)) {
                throw new IOException("CSV file not found: " + csvPath);
            }

            if (videoParent != null) {
                Files.createDirectories(videoParent);
            }

            prepareFramesDirectory(framesDirectory);

            try (NRRDVolumeReader volume =
                         new NRRDVolumeReader(nrrdPath.toString())) {

                System.out.println("NRRD loaded successfully.");
                System.out.println("Width: " + volume.getWidth());
                System.out.println("Height: " + volume.getHeight());
                System.out.println("Depth: " + volume.getDepth());
                System.out.println("Pixel type: " + volume.getPixelType());

                // Load every slice once to improve projection performance.
                volume.preloadVolume();

                PlaneProjector.projectCsv(
                        volume,
                        csvPath.toString(),
                        framesDirectory.toString());
            }

            Mp4VideoCreator.createVideo(
                    framesDirectory.toString(),
                    videoPath.toString(),
                    FRAMES_PER_SECOND);

            System.out.println(
                    "Video created successfully: " + videoPath);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Operation interrupted.");
            e.printStackTrace();
            System.exit(1);

        } catch (FormatException | IOException e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);

        } catch (Exception e) {
            System.err.println("Unexpected error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void prepareFramesDirectory(Path directory)
            throws IOException {
        Files.createDirectories(directory);

        try (Stream<Path> files = Files.list(directory)) {
            for (Path file : files.toList()) {
                String name = file.getFileName().toString();

                if (Files.isRegularFile(file)
                        && name.matches("projection_\\d+\\.png")) {
                    Files.deleteIfExists(file);
                }
            }
        }
    }
}
