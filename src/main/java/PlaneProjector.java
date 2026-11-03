import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class PlaneProjector {

    private static final double ISOSCELES_EPSILON = 0.001;
    private static final double GEOMETRY_EPSILON = 1e-12;
    private static final double MIN_COORDINATE = 0.0;
    private static final double MAX_COORDINATE = 1023.0;

    private static final int ERROR_IMAGE_WIDTH = 512;
    private static final int ERROR_IMAGE_HEIGHT = 512;

    private record Point3(double x, double y, double z) {

        Point3 subtract(Point3 p) {
            return new Point3(x - p.x, y - p.y, z - p.z);
        }

        Point3 add(Point3 p) {
            return new Point3(x + p.x, y + p.y, z + p.z);
        }

        Point3 scale(double s) {
            return new Point3(x * s, y * s, z * s);
        }

        double dot(Point3 p) {
            return x * p.x + y * p.y + z * p.z;
        }

        Point3 cross(Point3 p) {
            return new Point3(
                    y * p.z - z * p.y,
                    z * p.x - x * p.z,
                    x * p.y - y * p.x
            );
        }

        double length() {
            return Math.sqrt(x * x + y * y + z * z);
        }

        Point3 normalize() {
            double len = length();

            if (!Double.isFinite(len) || len < GEOMETRY_EPSILON) {
                throw new IllegalArgumentException(
                        "Plane contains coincident vertices.");
            }

            return scale(1.0 / len);
        }
    }

    private record Point2(double x, double y) {}

    private record Projection(double[][] values, boolean[][] mask) {}

    /*
     * Validate an individual coordinate.
     * Both 0 and 1024 are valid values.
     */
    private static void validateCoordinate(double coordinate)
            throws IOException {

        if (!Double.isFinite(coordinate)
                || coordinate < MIN_COORDINATE
                || coordinate > MAX_COORDINATE) {

            throw new IOException(
                    "Coordinate must be between 0 and 1023 inclusive: "
                            + coordinate);
        }
    }

    /*
     * Accepted CSV formats:
     *
     * Triangle:
     * x1,y1,z1,x2,y2,z2,x3,y3,z3
     *
     * Quadrilateral:
     * x1,y1,z1,x2,y2,z2,x3,y3,z3,x4,y4,z4
     *
     * An empty fourth vertex is also permitted:
     * x1,y1,z1,x2,y2,z2,x3,y3,z3,,,
     *
     * Empty lines and lines starting with # are ignored.
     */
    private static List<Point3> readVertices(String line)
            throws IOException {

        String[] fields = line.split(",", -1);

        if (fields.length != 9 && fields.length != 12) {
            throw new IOException(
                    "Each CSV row must contain 9 fields for a triangle "
                            + "or 12 fields for a quadrilateral: " + line);
        }

        List<Point3> points = new ArrayList<>(4);

        try {
            for (int i = 0; i < 9; i += 3) {
                double x = Double.parseDouble(fields[i].trim());
                double y = Double.parseDouble(fields[i + 1].trim());
                double z = Double.parseDouble(fields[i + 2].trim());

                validateCoordinate(x);
                validateCoordinate(y);
                validateCoordinate(z);

                points.add(new Point3(x, y, z));
            }

            if (fields.length == 12) {
                String x4 = fields[9].trim();
                String y4 = fields[10].trim();
                String z4 = fields[11].trim();

                boolean allEmpty = x4.isEmpty()
                        && y4.isEmpty()
                        && z4.isEmpty();

                boolean allPresent = !x4.isEmpty()
                        && !y4.isEmpty()
                        && !z4.isEmpty();

                if (allPresent) {
                    double x = Double.parseDouble(x4);
                    double y = Double.parseDouble(y4);
                    double z = Double.parseDouble(z4);

                    validateCoordinate(x);
                    validateCoordinate(y);
                    validateCoordinate(z);

                    points.add(new Point3(x, y, z));

                } else if (!allEmpty) {
                    throw new IOException(
                            "The fourth vertex must have all 3 coordinates "
                                    + "or all 3 fields must be empty: " + line);
                }
            }

        } catch (NumberFormatException e) {
            throw new IOException("Invalid coordinate: " + line, e);
        }

        return points;
    }

    private static double distance(Point3 a, Point3 b) {
        return a.subtract(b).length();
    }

    private static boolean approximatelyEqual(double a, double b) {
        return Math.abs(a - b) <= ISOSCELES_EPSILON;
    }

    private static void validateVertices(List<Point3> vertices) {
        if (vertices.size() != 3 && vertices.size() != 4) {
            throw new IllegalArgumentException(
                    "A plane must have exactly 3 or 4 vertices.");
        }

        Point3 a = vertices.get(0);
        Point3 b = vertices.get(1);
        Point3 c = vertices.get(2);

        double ab = distance(a, b);
        double bc = distance(b, c);
        double ca = distance(c, a);

        if (ab < GEOMETRY_EPSILON
                || bc < GEOMETRY_EPSILON
                || ca < GEOMETRY_EPSILON) {
            throw new IllegalArgumentException(
                    "Plane contains coincident vertices.");
        }

        Point3 cross = b.subtract(a).cross(c.subtract(a));

        if (cross.length() < GEOMETRY_EPSILON) {
            throw new IllegalArgumentException(
                    "The first three vertices are collinear.");
        }

        if (vertices.size() == 3) {
            // A triangle is isosceles if any two sides are equal.
            if (!approximatelyEqual(ab, bc)
                    && !approximatelyEqual(bc, ca)
                    && !approximatelyEqual(ca, ab)) {
                throw new IllegalArgumentException(
                        String.format(
                                "Triangle is not isosceles. "
                                        + "Side lengths: AB=%.6f, "
                                        + "BC=%.6f, CA=%.6f. "
                                        + "Allowed difference: %.3f.",
                                ab, bc, ca, ISOSCELES_EPSILON));
            }

            return;
        }

        Point3 d = vertices.get(3);

        double cd = distance(c, d);
        double da = distance(d, a);

        if (cd < GEOMETRY_EPSILON || da < GEOMETRY_EPSILON) {
            throw new IllegalArgumentException(
                    "Quadrilateral contains coincident vertices.");
        }

        // Verify that all four vertices lie in the same plane.
        Point3 normal = cross.normalize();
        double distanceFromPlane = Math.abs(d.subtract(a).dot(normal));

        if (distanceFromPlane > ISOSCELES_EPSILON) {
            throw new IllegalArgumentException(
                    "Quadrilateral vertices are not coplanar.");
        }

        /*
         * Vertices must be ordered around the perimeter: A, B, C, D.
         * An isosceles trapezoid has one pair of parallel opposite sides
         * and equal-length legs.
         */
        boolean basesAB_CD = areParallel(
                b.subtract(a), d.subtract(c));

        boolean basesBC_DA = areParallel(
                c.subtract(b), a.subtract(d));

        boolean isIsoscelesTrapezoid =
                (basesAB_CD && approximatelyEqual(bc, da))
                        || (basesBC_DA && approximatelyEqual(ab, cd));

        if (!isIsoscelesTrapezoid) {
            throw new IllegalArgumentException(
                    String.format(
                            "Quadrilateral is not an isosceles trapezoid. "
                                    + "Side lengths: AB=%.6f, BC=%.6f, "
                                    + "CD=%.6f, DA=%.6f. "
                                    + "Allowed difference: %.3f.",
                            ab, bc, cd, da, ISOSCELES_EPSILON));
        }
    }

    private static boolean areParallel(Point3 a, Point3 b) {
        double lengthA = a.length();
        double lengthB = b.length();

        if (lengthA < GEOMETRY_EPSILON
                || lengthB < GEOMETRY_EPSILON) {
            return false;
        }

        // Relative cross-product magnitude measures angular deviation.
        return a.cross(b).length() / (lengthA * lengthB)
                <= ISOSCELES_EPSILON;
    }

    private static Projection projectVertices(
            NRRDVolumeReader volume,
            List<Point3> vertices) throws Exception {

        validateVertices(vertices);

        Point3 a = vertices.get(0);
        Point3 b = vertices.get(1);
        Point3 c = vertices.get(2);

        boolean quadrilateral = vertices.size() == 4;
        Point3 d = quadrilateral ? vertices.get(3) : null;

        // Build a local 2D coordinate system in the plane.
        Point3 u = b.subtract(a).normalize();
        Point3 normal = b.subtract(a)
                .cross(c.subtract(a))
                .normalize();
        Point3 v = normal.cross(u).normalize();

        List<Point2> projected = new ArrayList<>();

        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;

        for (Point3 p : vertices) {
            Point3 relative = p.subtract(a);

            double x = relative.dot(u);
            double y = relative.dot(v);

            projected.add(new Point2(x, y));

            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
        }

        double pixelSize = 1.0;

        int width = Math.max(
                1, (int) Math.ceil((maxX - minX) / pixelSize) + 1);
        int height = Math.max(
                1, (int) Math.ceil((maxY - minY) / pixelSize) + 1);

        double[][] output = new double[height][width];
        boolean[][] mask = new boolean[height][width];

        Point2[] p2 = new Point2[projected.size()];

        for (int i = 0; i < projected.size(); i++) {
            Point2 p = projected.get(i);

            p2[i] = new Point2(
                    (p.x() - minX) / pixelSize,
                    (maxY - p.y()) / pixelSize
            );
        }

        int volumeWidth = volume.getWidth();
        int volumeHeight = volume.getHeight();
        int volumeDepth = volume.getDepth();

        rasterizeTriangle(
                output, mask,
                a, b, c,
                p2[0], p2[1], p2[2],
                volume, volumeWidth, volumeHeight, volumeDepth);

        if (quadrilateral) {
            rasterizeTriangle(
                    output, mask,
                    a, c, d,
                    p2[0], p2[2], p2[3],
                    volume, volumeWidth, volumeHeight, volumeDepth);
        }

        return new Projection(output, mask);
    }

    private static void rasterizeTriangle(
            double[][] output,
            boolean[][] mask,
            Point3 a, Point3 b, Point3 c,
            Point2 pa, Point2 pb, Point2 pc,
            NRRDVolumeReader volume,
            int volumeWidth, int volumeHeight, int volumeDepth)
            throws Exception {

        int height = output.length;
        int width = output[0].length;

        double area = edge(pa, pb, pc.x(), pc.y());

        if (Math.abs(area) < GEOMETRY_EPSILON) {
            throw new IllegalArgumentException(
                    "Projection contains a degenerate triangle.");
        }

        int minX = Math.max(0, (int) Math.floor(
                Math.min(pa.x(), Math.min(pb.x(), pc.x()))));
        int maxX = Math.min(width - 1, (int) Math.ceil(
                Math.max(pa.x(), Math.max(pb.x(), pc.x()))));
        int minY = Math.max(0, (int) Math.floor(
                Math.min(pa.y(), Math.min(pb.y(), pc.y()))));
        int maxY = Math.min(height - 1, (int) Math.ceil(
                Math.max(pa.y(), Math.max(pb.y(), pc.y()))));

        final double epsilon = 1e-9;

        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                double wA = edge(pb, pc, x, y) / area;
                double wB = edge(pc, pa, x, y) / area;
                double wC = edge(pa, pb, x, y) / area;

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

                    mask[y][x] = true;
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
            Projection projection, String outputPath) throws IOException {

        double[][] matrix = projection.values();
        boolean[][] mask = projection.mask();

        int height = matrix.length;
        int width = matrix[0].length;

        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (mask[y][x] && Double.isFinite(matrix[y][x])) {
                    min = Math.min(min, matrix[y][x]);
                    max = Math.max(max, matrix[y][x]);
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
                int gray = 0;

                if (mask[y][x] && normalize
                        && Double.isFinite(matrix[y][x])) {
                    gray = (int) Math.round(
                            255.0 * (matrix[y][x] - min) / (max - min));

                    gray = Math.max(0, Math.min(255, gray));
                }

                // Pixels outside the projected polygon remain black.
                pixels[offset + x] = (byte) gray;
            }
        }

        if (!ImageIO.write(image, "png", new File(outputPath))) {
            throw new IOException("Could not write PNG: " + outputPath);
        }
    }

    /*
     * Write a black frame with readable white error text.
     * This frame occupies the invalid plane's position in the video.
     */
    private static void saveErrorPng(
            String outputPath, int planeNumber, String errorMessage)
            throws IOException {

        BufferedImage image = new BufferedImage(
                ERROR_IMAGE_WIDTH,
                ERROR_IMAGE_HEIGHT,
                BufferedImage.TYPE_BYTE_GRAY);

        Graphics2D graphics = image.createGraphics();

        try {
            graphics.setColor(Color.BLACK);
            graphics.fillRect(
                    0, 0, ERROR_IMAGE_WIDTH, ERROR_IMAGE_HEIGHT);

            graphics.setColor(Color.WHITE);
            graphics.setFont(new Font(Font.MONOSPACED, Font.BOLD, 18));
            graphics.setRenderingHint(
                    RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int margin = 24;
            int maxChars = 43;
            int lineHeight = 26;
            int y = 40;

            graphics.drawString("ERROR: Plane " + planeNumber, margin, y);
            y += lineHeight * 2;

            graphics.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));

            String message = errorMessage == null
                    ? "Unknown error."
                    : errorMessage.replace('\n', ' ').replace('\r', ' ');

            for (int start = 0;
                 start < message.length();
                 start += maxChars) {

                if (y > ERROR_IMAGE_HEIGHT - margin) {
                    break;
                }

                int end = Math.min(start + maxChars, message.length());

                graphics.drawString(
                        message.substring(start, end), margin, y);

                y += lineHeight;
            }

            graphics.drawString(
                    "This plane was skipped.",
                    margin, ERROR_IMAGE_HEIGHT - margin);

        } finally {
            graphics.dispose();
        }

        if (!ImageIO.write(image, "png", new File(outputPath))) {
            throw new IOException(
                    "Could not write error frame: " + outputPath);
        }
    }

    public static void projectCsv(
            NRRDVolumeReader volume,
            String csvPath,
            String outputDirectory) throws Exception {

        Path outputDir = Path.of(outputDirectory);
        Files.createDirectories(outputDir);

        List<String> lines = Files.readAllLines(Path.of(csvPath));

        int planeNumber = 0;
        int successfulPlanes = 0;
        int failedPlanes = 0;

        long startTime = System.nanoTime();

        for (String originalLine : lines) {
            String line = originalLine.trim();

            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }

            Path outputPath = outputDir.resolve(
                    "projection_" + planeNumber + ".png");

            try {
                List<Point3> vertices = readVertices(line);

                Projection projection = projectVertices(volume, vertices);

                savePng(projection, outputPath.toString());

                successfulPlanes++;

                System.out.println(
                        "Generated projection for plane " + planeNumber);

            } catch (Exception e) {
                failedPlanes++;

                String errorMessage = e.getMessage();

                if (errorMessage == null || errorMessage.isBlank()) {
                    errorMessage = e.getClass().getSimpleName();
                }

                System.err.println(
                        "Plane " + planeNumber + " failed: " + errorMessage);

                // Generate this plane's error frame and continue.
                saveErrorPng(
                        outputPath.toString(),
                        planeNumber,
                        errorMessage);
            }

            // Every nonempty, non-comment CSV row gets one frame.
            planeNumber++;

            if (planeNumber % 25 == 0) {
                double elapsed =
                        (System.nanoTime() - startTime) / 1_000_000_000.0;

                System.out.printf(
                        "Processed %d planes | %.1f planes/sec%n",
                        planeNumber,
                        planeNumber / Math.max(elapsed, 0.000001));
            }
        }

        double elapsed =
                (System.nanoTime() - startTime) / 1_000_000_000.0;

        System.out.printf(
                "Finished: %d planes, %d successful, %d failed "
                        + "in %.2f seconds (%.1f planes/sec)%n",
                planeNumber,
                successfulPlanes,
                failedPlanes,
                elapsed,
                planeNumber / Math.max(elapsed, 0.000001));
    }
}