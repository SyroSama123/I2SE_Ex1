# I2SE_Ex1 — 3D Volume Plane Projection

## Overview

I2SE_Ex1 is a Java application developed for the Introduction to Software Engineering (OOP/OOD) assignment. The application projects planes defined by vertices onto a three-dimensional volume stored in an NRRD file and generates two-dimensional grayscale PNG images representing the resulting projections.

The project focuses on robust software design, algorithmic correctness, runtime efficiency, and handling input validation and errors.

## Features

- **3D NRRD volume reading:** Reads a three-dimensional volume containing unsigned 8-bit, 16-bit, or 32-bit voxel values.
- **Plane projection:** Projects triangles and trapezoids defined by their vertices onto the volume.
- **Automatic orientation:**
    - Isosceles triangles are oriented with their base at the bottom.
    - Isosceles trapezoids are oriented with their longer parallel base at the bottom.
- **Grayscale output:** Generates PNG images representing the projected voxel values.
- **Video generation:** Supports combining generated projection images into an MP4 video using FFmpeg.
- **Input validation:** Checks the validity of input coordinates and geometric constraints.
- **Error handling:** Handles invalid input and projection failures.

## Requirements

- Java 21 or later
- Maven
- FFmpeg, if MP4 video generation is required

The NRRD-reading dependencies are declared in `pom.xml`.

## Input Format

The input CSV file contains one plane per line. Each line specifies the coordinates of three or four vertices in the 3D volume.

The expected format is:

```text
x1,y1,z1,x2,y2,z2,x3,y3,z3
```

for a triangle, or:

```text
x1,y1,z1,x2,y2,z2,x3,y3,z3,x4,y4,z4
```

for a trapezoid.

Each vertex is represented by three integer coordinates:

- `x`: X-coordinate
- `y`: Y-coordinate
- `z`: Z-coordinate

The coordinates use zero-based indexing.

### Coordinate Constraints

Each coordinate must satisfy:

```text
0 <= coordinate < 1024
```

The coordinates must also be valid for the dimensions of the loaded volume.

### Geometric Constraints

**Triangles**

The three vertices must define an isosceles triangle. The algorithm identifies the base as the side opposite the apex, where the two sides connecting the apex to the base endpoints have equal lengths.

**Trapezoids**

The four vertices must define a valid trapezoid, with at least one pair of parallel opposite sides. The algorithm identifies the parallel sides and uses the longer one as the base at the bottom of the projection.

The vertices should be provided in perimeter order around the shape.

## Projection Orientation

The projection algorithm automatically establishes a consistent orientation for the supported shapes.

### Isosceles Triangles

The algorithm identifies the equal sides and determines the base as the remaining side. The triangle is oriented so that its base appears at the bottom of the resulting image and its apex appears above it.

### Isosceles Trapezoids

The algorithm identifies the parallel sides and compares their lengths. The longer parallel side is placed at the bottom of the resulting image, while the shorter parallel side is placed above it.

If both parallel sides have equal lengths, either side may be selected as the bottom base.

### Coordinate Mapping

The algorithm constructs a local coordinate system for the plane:

- The horizontal axis follows the selected base.
- The vertical axis points from the base toward the opposite vertex or opposite base.
- The vertical image coordinate is inverted during rasterization so that the base appears at the bottom of the image.

This approach preserves the relationship between the original 3D vertices and their projected positions.

## Output

For each valid plane in the CSV file, the application generates a grayscale PNG projection.

The generated images are saved in:

```text
Datasets/projections/
```

The output filenames follow this pattern:

```text
projection_0.png
projection_1.png
projection_2.png
...
```

The image dimensions depend on the geometry of the projected plane and the distance between its defining vertices.

The grayscale values are derived from the corresponding voxel values in the 3D volume.

If video generation is enabled, the generated PNG images are sorted and passed to FFmpeg to produce an MP4 video.

## Project Structure

The project includes the following main components:

- `NRRDVolumeReader.java` — Reads the NRRD volume and provides access to individual voxel values.
- `PlaneProjector.java` — Validates plane geometry, determines its orientation, and generates the 2D projection.
- `Mp4VideoCreator.java` — Collects the generated PNG images and uses FFmpeg to create an MP4 video.
- `pom.xml` — Defines the Maven project configuration, Java version, and dependencies.
- `Datasets/projections/` — Contains the generated projection images.

## Building the Project

Navigate to the project directory and run:

```bash
mvn clean package
```

Maven compiles the source code, resolves the declared dependencies, and packages the application.

## Running the Application

The intended executable interface is:

```bash
java -jar I2SE_Ex1.jar planes.csv video.mp4
```

Where:

- `I2SE_Ex1.jar` is the executable application.
- `planes.csv` is the input CSV file containing the plane definitions.
- `video.mp4` is the desired output video filename.

The exact command-line arguments must match the application's implemented entry point. If the NRRD volume path is also required by the implementation, it must be supplied according to the argument format supported by the application.

## Error Handling

The application validates input and reports errors encountered during processing.

Potential errors include:

- Missing or unreadable input files.
- Invalid CSV formatting.
- Invalid or out-of-range coordinates.
- Coordinates outside the loaded volume.
- Invalid triangle or trapezoid geometry.
- Failure to read the NRRD volume.
- Failure to write projection images.
- Failure to execute FFmpeg or generate the output video.

Invalid input should be reported clearly rather than silently producing an incorrect projection.

## Implementation Notes

- The volume is accessed using zero-based coordinates.
- Voxel values are interpreted according to their stored bit depth and byte order.
- Projection orientation is determined from the geometry of the input vertices rather than relying on a fixed vertex order for the base.
- The rasterization process maps points in the local plane coordinate system to pixels in the output image.
- PNG images are generated before video creation.
- FFmpeg is used for video encoding rather than implementing a video encoder directly.

## Troubleshooting

### The projection is upside down

Verify that the latest version of `PlaneProjector.java` is being used. The projection should automatically place the base of an isosceles triangle at the bottom and the longer parallel base of a trapezoid at the bottom.

### The program rejects a plane

Check that:

- The CSV line contains exactly nine values for a triangle or twelve values for a trapezoid.
- The coordinates are integers.
- The coordinates are within the supported range and the volume dimensions.
- The vertices define a valid isosceles triangle or trapezoid.
- The vertices of a trapezoid are listed in perimeter order.

### The PNG images are not generated

Check that the output directory is writable and that the input volume and CSV file can be read successfully.

### The MP4 video is not generated

Ensure that FFmpeg is installed and available through the system's `PATH`. Verify that the projection images were generated successfully before video creation.

## Assignment Context

This project was developed for the course **Introduction to Software Engineering (OOP/OOD)**.

The assignment focuses on implementing a plane-projection algorithm for a 3D volume while emphasizing software design, robustness, runtime efficiency, input validation, and code quality.