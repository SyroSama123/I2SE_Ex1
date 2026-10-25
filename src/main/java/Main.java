
public class Main {

    public static void main(String[] args) {
        String nrrdPath = "Datasets/MRHead_1.nrrd";
        String csvPath = "Datasets/planes.csv";
        String outputDirectory = "Datasets/projections";
        String videoPath = "Datasets/projections.mp4";

        try (NRRDVolumeReader volume =
                     new NRRDVolumeReader(nrrdPath)) {

            System.out.println("NRRD loaded successfully.");
            System.out.println("Width: " + volume.getWidth());
            System.out.println("Height: " + volume.getHeight());
            System.out.println("Depth: " + volume.getDepth());

            PlaneProjector.projectCsv(
                    volume,
                    csvPath,
                    outputDirectory
            );

            System.out.println("PNG projections completed.");

        } catch (Exception e) {
            System.err.println(
                    "Error generating projections: " + e.getMessage()
            );
            e.printStackTrace();
            return;
        }

        try {
            Mp4VideoCreator.createVideo(
                    outputDirectory,
                    videoPath,
                    2
            );

            System.out.println("Video creation completed.");

        } catch (Exception e) {
            System.err.println(
                    "Error creating MP4: " + e.getMessage()
            );
            e.printStackTrace();
        }
    }
}
