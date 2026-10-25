
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class PlaneProjector {

    private record Point3(double x, double y, double z) {
        double distance(Point3 p) {
            double dx = x - p.x;
            double dy = y - p.y;
            double dz = z - p.z;
            return Math.sqrt(dx * dx + dy * dy + dz * dz);
        }

        Point3 interpolate(Point3 p, double t) {
            return new Point3(
                    x + (p.x - x) * t,
                    y + (p.y - y) * t,
                    z + (p.z - z) * t
            );
        }
    }

    private record Point2(double x, double y) {}

    private static List<Point3> readVertices(String line)
            throws IOException {
        String[] fields = line.split(",", -1);

        if (fields.length != 12) {
            throw new IOException(
                    "Each CSV row must contain 12 fields: " + line);
        }

        List<Point3> points = new ArrayList<>(4);

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

    private static double[][] projectVertices(
            NRRDVolumeReader volume,
            List<Point3> vertices) throws Exception {

        Point3 a = vertices.get(0);
        Point3 b = vertices.get(1);
        Point3 c = vertices.get(2);
        boolean quadrilateral = vertices.size() == 4;
        Point3 d = quadrilateral ? vertices.get(3) : a;

        double rowLength = quadrilateral
                ? Math.max(a.distance(d), b.distance(c))
                : Math.max(a.distance(b), a.distance(c));

        double columnLength = quadrilateral
                ? Math.max(a.distance(b), d.distance(c))
                : b.distance(c);

        int height = Math.max(1, (int) Math.ceil(rowLength) + 1);
        int width = Math.max(1, (int) Math.ceil(columnLength) + 1);

        double[][] output = new double[height][width];

        int volumeWidth = volume.getWidth();
        int volumeHeight = volume.getHeight();
        int volumeDepth = volume.getDepth();

        // A triangle is sampled directly using barycentric coordinates.
        // A quadrilateral is split into triangles ABC and ACD.
        Point2 pa = new Point2(0, 0);
        Point2 pb = new Point2(0, height - 1);
        Point2 pc = new Point2(width - 1, height - 1);
        Point2 pd = new Point2(width - 1, 0);

        if (!quadrilateral) {
            rasterizeTriangle(
                    output, a, b, c,
                    pa, pb, pc,
                    volume, volumeWidth, volumeHeight, volumeDepth
            );
        } else {
            rasterizeTriangle(
                    output, a, b, c,
                    pa, pb, pc,
                    volume, volumeWidth, volumeHeight, volumeDepth
            );

            rasterizeTriangle(
                    output, a, c, d,
                    pa, pc, pd,
                    volume, volumeWidth, volumeHeight, volumeDepth
            );
        }

        return output;
    }

    private static void rasterizeTriangle(
            double[][] output,
            Point3 a, Point3 b, Point3 c,
            Point2 pa, Point2 pb, Point2 pc,
            NRRDVolumeReader volume,
            int volumeWidth, int volumeHeight, int volumeDepth)
            throws Exception {

        int height = output.length;
        int width = output[0].length;

        double area = edge(pa, pb, pc.x(), pc.y());
        if (Math.abs(area) < 1e-12) {
            return;
        }

        double minX = Math.max(0,
                Math.floor(Math.min(pa.x(), Math.min(pb.x(), pc.x()))));
        double maxX = Math.min(width - 1,
                Math.ceil(Math.max(pa.x(), Math.max(pb.x(), pc.x()))));
        double minY = Math.max(0,
                Math.floor(Math.min(pa.y(), Math.min(pb.y(), pc.y()))));
        double maxY = Math.min(height - 1,
                Math.ceil(Math.max(pa.y(), Math.max(pb.y(), pc.y()))));

        for (int y = (int) minY; y <= (int) maxY; y++) {
            for (int x = (int) minX; x <= (int) maxX; x++) {
                double px = x;
                double py = y;

                double wA = edge(pb, pc, px, py) / area;
                double wB = edge(pc, pa, px, py) / area;
                double wC = edge(pa, pb, px, py) / area;

                // Ignore every pixel outside this triangle.
                double epsilon = 1e-9;
                if (wA < -epsilon || wB < -epsilon || wC < -epsilon) {
                    continue;
                }

                double vx = wA * a.x() + wB * b.x() + wC * c.x();
                double vy = wA * a.y() + wB * b.y() + wC * c.y();
                double vz = wA * a.z() + wB * b.z() + wC * c.z();

                int voxelX = (int) Math.round(vx);
                int voxelY = (int) Math.round(vy);
                int voxelZ = (int) Math.round(vz);

                if (voxelX >= 0 && voxelX < volumeWidth
                        && voxelY >= 0 && voxelY < volumeHeight
                        && voxelZ >= 0 && voxelZ < volumeDepth) {
                    output[y][x] = volume.getPixelValueUnchecked(
                            voxelX, voxelY, voxelZ);
                }
            }
        }
    }

    private static double edge(
            Point2 a, Point2 b, double x, double y) {
        return (x - a.x()) * (b.y() - a.y())
                - (y - a.y()) * (b.x() - a.x());
    }

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

        byte[] pixels = ((DataBufferByte)
                image.getRaster().getDataBuffer()).getData();

        boolean normalize = Double.isFinite(min)
                && Double.isFinite(max)
                && max > min;

        for (int y = 0; y < height; y++) {
            int offset = y * width;

            for (int x = 0; x < width; x++) {
                double value = matrix[y][x];
                int gray = 0;

                if (Double.isFinite(value) && normalize) {
                    gray = (int) Math.round(
                            255.0 * (value - min) / (max - min));
                    gray = Math.max(0, Math.min(255, gray));
                }

                pixels[offset + x] = (byte) gray;
            }
        }

        ImageIO.write(image, "png", new File(outputPath));
    }

    public static void projectCsv(
            NRRDVolumeReader volume,
            String csvPath,
            String outputDirectory) throws Exception {

        Path outputDir = Path.of(outputDirectory);
        Files.createDirectories(outputDir);

        List<String> lines = Files.readAllLines(Path.of(csvPath));
        int planeNumber = 0;
        long startTime = System.nanoTime();

        for (String line : lines) {
            line = line.trim();

            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }

            List<Point3> vertices = readVertices(line);
            double[][] projection = projectVertices(volume, vertices);

            Path outputPath = outputDir.resolve(
                    "projection_" + planeNumber + ".png");

            savePng(projection, outputPath.toString());
            planeNumber++;

            if (planeNumber % 25 == 0) {
                double elapsedSeconds =
                        (System.nanoTime() - startTime) / 1_000_000_000.0;

                System.out.printf(
                        "Generated %d projections | %.1f projections/sec%n",
                        planeNumber,
                        planeNumber / elapsedSeconds
                );
            }
        }

        double elapsedSeconds =
                (System.nanoTime() - startTime) / 1_000_000_000.0;

        System.out.printf(
                "Finished: %d projections in %.2f seconds (%.1f projections/sec)%n",
                planeNumber,
                elapsedSeconds,
                planeNumber / Math.max(elapsedSeconds, 0.000001)
        );
    }
}
