public class Main {

    public static void main(String[] args) {
        String nrrdPath = "Datasets/MRHead_1.nrrd";
        String csvPath = "Datasets/planes.csv";
        String outputDirectory = "Datasets/projections";

        try (NRRDVolumeReader volume =
                     new NRRDVolumeReader(nrrdPath)) {

            System.out.println("NRRD loaded successfully.");
            System.out.println("Width: " + volume.getWidth());
            System.out.println("Height: " + volume.getHeight());
            System.out.println("Depth: " + volume.getDepth());
            System.out.println("Bits per pixel: "
                    + volume.getBitsPerPixel());
            System.out.println("Pixel type: "
                    + volume.getPixelType());
            System.out.println("Little endian: "
                    + volume.isLittleEndian());

            PlaneProjector.projectCsv(
                    volume,
                    csvPath,
                    outputDirectory
            );

            System.out.println(
                    "All plane projections completed successfully.");

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
