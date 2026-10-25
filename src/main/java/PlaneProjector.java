
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class PlaneProjector {

    private record Point3(double x, double y, double z) {
        Point3 add(Point3 p) {
            return new Point3(x + p.x, y + p.y, z + p.z);
        }

        Point3 subtract(Point3 p) {
            return new Point3(x - p.x, y - p.y, z - p.z);
        }

        Point3 multiply(double s) {
            return new Point3(x * s, y * s, z * s);
        }

        double distance(Point3 p) {
            double dx = x - p.x;
            double dy = y - p.y;
            double dz = z - p.z;
            return Math.sqrt(dx * dx + dy * dy + dz * dz);
        }
    }

    private static Point3 interpolate(Point3 a, Point3 b, double t) {
        return a.add(b.subtract(a).multiply(t));
    }

    private static List<Point3> readVertices(String line)
            throws IOException {

        String[] fields = line.split(",", -1);

        if (fields.length != 12) {
            throw new IOException(
                    "Each CSV row must contain 12 fields: " + line);
        }

        List<Point3> points = new ArrayList<>();

        try {
            for (int i = 0; i < 9; i += 3) {
                points.add(new Point3(
                        Double.parseDouble(fields[i].trim()),
                        Double.parseDouble(fields[i + 1].trim()),
                        Double.parseDouble(fields[i + 2].trim())
                ));
            }

            boolean fourthMissing =
                    fields[9].trim().isEmpty()
                            && fields[10].trim().isEmpty()
                            && fields[11].trim().isEmpty();

            if (!fourthMissing) {
                if (fields[9].trim().isEmpty()
                        || fields[10].trim().isEmpty()
                        || fields[11].trim().isEmpty()) {
                    throw new IOException(
                            "Fourth vertex must have all 3 coordinates "
                                    + "or all 3 fields must be empty: " + line);
                }

                points.add(new Point3(
                        Double.parseDouble(fields[9].trim()),
                        Double.parseDouble(fields[10].trim()),
                        Double.parseDouble(fields[11].trim())
                ));
            }
        } catch (NumberFormatException e) {
            throw new IOException("Invalid coordinate: " + line, e);
        }

        return points;
    }

    /**
     * Projects one triangle or trapezoid into a 2D matrix.
     *
     * Triangle: vertices A, B, C, where A is the apex.
     * Trapezoid: vertices A, B, C, D in perimeter order,
     * with AB and DC as the parallel bases.
     *
     * Coordinates are voxel coordinates.
     */
    private static double[][] projectVertices(
            NRRDVolumeReader volume, List<Point3> v) throws Exception {

        Point3 A = v.get(0);
        Point3 B = v.get(1);
        Point3 C = v.get(2);

        boolean trapezoid = v.size() == 4;
        Point3 D = trapezoid ? v.get(3) : A;

        double rowLength = trapezoid
                ? Math.max(A.distance(D), B.distance(C))
                : Math.max(A.distance(B), A.distance(C));

        double columnLength = trapezoid
                ? Math.max(A.distance(B), D.distance(C))
                : B.distance(C);

        int height = Math.max(1, (int) Math.ceil(rowLength) + 1);
        int width = Math.max(1, (int) Math.ceil(columnLength) + 1);

        double[][] output = new double[height][width];

        for (int y = 0; y < height; y++) {
            double t = rowLength == 0
                    ? 0
                    : Math.min(1.0, y / rowLength);

            Point3 start;
            Point3 end;

            if (trapezoid) {
                start = interpolate(A, D, t);
                end = interpolate(B, C, t);
            } else {
                start = interpolate(A, B, t);
                end = interpolate(A, C, t);
            }

            double sectionLength = start.distance(end);
            int samples = Math.max(1, (int) Math.ceil(sectionLength));
            int xStart = (width - samples - 1) / 2;

            for (int i = 0; i <= samples; i++) {
                double s = i / (double) samples;
                Point3 p = interpolate(start, end, s);

                int px = (int) Math.round(p.x());
                int py = (int) Math.round(p.y());
                int pz = (int) Math.round(p.z());

                double value = 0;

                if (px >= 0 && px < volume.getWidth()
                        && py >= 0 && py < volume.getHeight()
                        && pz >= 0 && pz < volume.getDepth()) {
                    value = volume.getPixel(px, py, pz).doubleValue();
                }

                int outX = xStart + i;

                if (outX >= 0 && outX < width) {
                    output[y][outX] = value;
                }
            }
        }

        return output;
    }

    /**
     * Saves a matrix as an 8-bit grayscale PNG.
     */
    private static void savePng(
            double[][] matrix, String outputPath) throws IOException {

        int height = matrix.length;
        int width = matrix[0].length;

        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;

        for (double[] row : matrix) {
            for (double value : row) {
                if (Double.isFinite(value)) {
                    min = Math.min(min, value);
                    max = Math.max(max, value);
                }
            }
        }

        BufferedImage image = new BufferedImage(
                width, height, BufferedImage.TYPE_BYTE_GRAY);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                double value = matrix[y][x];
                int gray = 0;

                if (Double.isFinite(value) && max > min) {
                    gray = (int) Math.round(
                            255.0 * (value - min) / (max - min));
                }

                gray = Math.max(0, Math.min(255, gray));
                int rgb = (gray << 16) | (gray << 8) | gray;
                image.setRGB(x, y, rgb);
            }
        }

        ImageIO.write(image, "png", new File(outputPath));
    }

    /**
     * Reads all planes from the CSV and writes one PNG per row.
     */
    public static void projectCsv(
            NRRDVolumeReader volume,
            String csvPath,
            String outputDirectory) throws Exception {

        Path outputDir = Path.of(outputDirectory);
        Files.createDirectories(outputDir);

        List<String> lines = Files.readAllLines(Path.of(csvPath));
        int planeNumber = 0;

        for (String line : lines) {
            line = line.trim();

            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }

            List<Point3> vertices = readVertices(line);

            double[][] projection = projectVertices(volume, vertices);

            String outputPath = outputDir.resolve(
                    "projection_" + planeNumber + ".png"
            ).toString();

            savePng(projection, outputPath);

            System.out.println("Saved: " + outputPath);
            planeNumber++;
        }
    }
}
