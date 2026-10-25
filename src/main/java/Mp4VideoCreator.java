
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

public class Mp4VideoCreator {

    public static void createVideo(
            String inputDirectory,
            String outputFile,
            int framesPerSecond
    ) throws IOException, InterruptedException {

        Path inputPath = Path.of(inputDirectory).toAbsolutePath();
        Path outputPath = Path.of(outputFile).toAbsolutePath();

        if (framesPerSecond <= 0) {
            throw new IllegalArgumentException(
                    "Frames per second must be positive."
            );
        }

        if (!Files.isDirectory(inputPath)) {
            throw new IOException(
                    "Frame directory not found: " + inputPath
            );
        }

        long frameCount;

        try (Stream<Path> files = Files.list(inputPath)) {
            frameCount = files
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString()
                            .matches("projection_\\d+\\.png"))
                    .count();
        }

        if (frameCount == 0) {
            throw new IOException(
                    "No projection PNG frames found in " + inputPath
            );
        }

        // The image sequence must be numbered consecutively from zero.
        for (long i = 0; i < frameCount; i++) {
            Path frame = inputPath.resolve("projection_" + i + ".png");

            if (!Files.isRegularFile(frame)) {
                throw new IOException(
                        "Missing frame: " + frame
                                + ". Frames must be numbered consecutively."
                );
            }
        }

        Path parent = outputPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        List<String> command = List.of(
                "ffmpeg",
                "-hide_banner",
                "-y",

                "-framerate", String.valueOf(framesPerSecond),
                "-start_number", "0",
                "-i", inputPath.resolve("projection_%d.png").toString(),

                "-frames:v", String.valueOf(frameCount),

                "-vf",
                "scale=512:512:force_original_aspect_ratio=decrease,"
                        + "pad=512:512:(ow-iw)/2:(oh-ih)/2,"
                        + "setsar=1,format=yuv420p",

                "-c:v", "mpeg4",
                "-q:v", "3",
                "-movflags", "+faststart",

                outputPath.toString()
        );

        System.out.println("Creating video from " + frameCount + " frames...");

        Process process = new ProcessBuilder(command)
                .inheritIO()
                .start();

        boolean finished = process.waitFor(
                10, java.util.concurrent.TimeUnit.MINUTES
        );

        if (!finished) {
            process.destroyForcibly();
            throw new IOException("FFmpeg timed out.");
        }

        if (process.exitValue() != 0) {
            throw new IOException(
                    "FFmpeg failed with exit code " + process.exitValue()
            );
        }

        if (!Files.isRegularFile(outputPath)
                || Files.size(outputPath) == 0) {
            throw new IOException(
                    "FFmpeg finished but produced an empty video."
            );
        }

        System.out.println("MP4 created successfully: " + outputPath);
        System.out.println("Frames: " + frameCount);
        System.out.println("Duration: "
                + (double) frameCount / framesPerSecond + " seconds");
    }
}
