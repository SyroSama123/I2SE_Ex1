
public class Main {
    public static void main(String[] args) throws Exception {

        String path = "Datasets/MRHead_1.nrrd";

        try (NRRDVolumeReader volume =
                     new NRRDVolumeReader(path)) {

            System.out.println("Width: " + volume.getWidth());
            System.out.println("Height: " + volume.getHeight());
            System.out.println("Depth: " + volume.getDepth());
            System.out.println("Bits per pixel: "
                    + volume.getBitsPerPixel());
            System.out.println("Pixel type: " + volume.getPixelType());
            System.out.println("Little endian: "
                    + volume.isLittleEndian());

            Number value = volume.getPixel(15, 62, 3);

            System.out.println("Pixel value: " + value);
            System.out.println("Java type: "
                    + value.getClass().getSimpleName());
        }
    }
}
