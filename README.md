# I2SE_Ex1 — 3D Volume Plane Projection

# Table Of Contents
1. [Overview](#overview)
2. [Features](#features)
3. [Requirements](#requirements)
4. [Input Format](#input-format)
5. [Geometric Constraints](#geometric-constraints)
6. [Projection Algorithm](#projection-algorithm)
7. [Output](#output)
8. [Project Structure](#project-structure)
9. [Running the Application](#running-the-application)
10. [Error Handling](#error-handling)
11. [Performance Considerations](#performance-considerations)
12. [Troubleshooting](#troubleshooting)

## Overview

I2SE_Ex1 is a Java application developed for the Introduction to Software Engineering (OOP/OOD) assignment.

The application projects geometric planes, defined by vertices in three-dimensional space, onto a 3D scalar volume stored in an NRRD file. Each plane is rasterized into a two-dimensional grayscale PNG image representing the corresponding voxel values.

The generated projection images are then combined into an MP4 video using FFmpeg.

The project emphasizes robust software design, algorithmic correctness, runtime efficiency, input validation, and error handling.

## Features

- **3D NRRD volume reading:** Supports signed and unsigned integer values (8, 16, 32, and 64 bits), as well as 32-bit and 64-bit floating-point values.
- **Multiple NRRD encodings:** Supports raw and gzip-compressed data.
- **Endian support:** Handles both little-endian and big-endian voxel data.
- **Plane projection:** Projects isosceles triangles and isosceles trapezoids onto a 3D volume.
- **Automatic orientation:** Places the triangle's base or the trapezoid's longer parallel side at the bottom of the projection.
- **Barycentric rasterization:** Maps pixels in the 2D projection back to their corresponding 3D coordinates.
- **Grayscale output:** Normalizes voxel values into 8-bit grayscale PNG images.
- **Video generation:** Combines projection images into an MP4 video using FFmpeg.
- **Error handling:** Generates an error image for invalid planes and continues processing subsequent planes.
- **Performance optimization:** Preloads the entire NRRD volume into memory to avoid repeated disk access.
- **Processing statistics:** Reports the number of successful and failed projections and the processing throughput.

## Requirements

- Java 21 or later.
- Maven.
- FFmpeg installed and accessible through the system's `PATH`.
- Sufficient memory to load the entire NRRD volume into memory.

The NRRD reader is implemented directly in Java and does not require Bio-Formats.

## Input Format

The application accepts two input files:

1. A 3D NRRD volume (`.nrrd`).
2. A CSV file containing plane definitions.

### NRRD Volume

The NRRD file must represent a three-dimensional scalar volume.

Supported pixel types:

| Type | Bit depth |
|---|---:|
| int8 / uint8 | 8 |
| int16 / uint16 | 16 |
| int32 / uint32 | 32 |
| int64 / uint64 | 64 |
| float | 32 |
| double | 64 |

Supported encodings:

- `raw`
- `gzip`
- `gz`

Both little-endian and big-endian byte orders are supported.

The reader supports inline NRRD files (`.nrrd`). Detached NRRD files (`.nhdr`) are not supported.

### CSV Plane Definitions

The CSV file contains one plane per line. Each line defines either a triangle or a quadrilateral using three-dimensional vertex coordinates.

**Triangle — 9 fields:**

```text
x1,y1,z1,x2,y2,z2,x3,y3,z3
```

**Quadrilateral — 12 fields:**

```text
x1,y1,z1,x2,y2,z2,x3,y3,z3,x4,y4,z4
```

Each vertex consists of three coordinates:

- `x`: X-coordinate.
- `y`: Y-coordinate.
- `z`: Z-coordinate.

Coordinates are zero-based and may be integers or decimal values.

Example:

```csv
100,100,50,200,100,50,150,200,50
100,100,60,200,100,60,175,200,60,125,200,60
```

The CSV parser also supports:

- Empty lines, which are ignored.
- Comment lines beginning with `#`, which are ignored.
- Optional empty fourth-vertex fields in a 12-field row, allowing a triangle to be represented using 12 fields.


### Coordinate Constraints

Each coordinate must satisfy:

```text
0 <= coordinate <= 1023
```

Coordinates are additionally checked against the actual dimensions of the loaded volume during rasterization.

Coordinates outside the volume do not cause an entire projection to fail. Pixels mapped outside the volume are simply excluded from the projection.

## Geometric Constraints

### Isosceles Triangles

A triangle must:

- Have three distinct vertices.
- Have non-collinear vertices.
- Have at least two approximately equal side lengths.

The permitted difference between equal side lengths is:

```text
0.001
```

The algorithm identifies the two equal sides and determines the remaining side as the base.

### Isosceles Trapezoids

A quadrilateral must:

- Have four distinct vertices.
- Have non-collinear first three vertices.
- Have all four vertices approximately coplanar.
- Have at least one pair of parallel opposite sides.
- Have approximately equal non-parallel sides (the legs).

The vertices must be supplied in perimeter order:

```text
A -> B -> C -> D
```

The permitted geometric tolerance for coplanarity, parallelism, and equal side lengths is `0.001`.

## Projection Algorithm

The projection algorithm is described in detail in the "Algorithm_Definition.md" file.

## Output

### Projection PNG Images

For every non-empty, non-comment CSV row, the application generates one PNG image.

Successful projections contain the normalized grayscale voxel values.

Failed projections produce a dedicated error image containing:

- The plane number.
- The error message.
- An indication that the plane was skipped.

Both successful and failed planes receive sequential frame numbers.

The output directory is named:

```text
projection_frames/
```

It is created next to the requested MP4 output file.

Example:

```text
project/
├── input.nrrd
├── planes.csv
├── output.mp4
└── projection_frames/
    ├── projection_0.png
    ├── projection_1.png
    ├── projection_2.png
    └── ...
```

Before processing begins, existing files matching `projection_<number>.png` in the output directory are deleted to prevent stale frames from being included in the video.

### MP4 Video

After all planes have been processed, the application combines the generated PNG frames into an MP4 video.

Default video settings:

| Property | Value |
|---|---|
| Frames per second | 50 |
| Resolution | 512 × 512 |
| Video codec | MPEG-4 (`mpeg4`) |
| Quality | 3 (`-q:v 3`) |
| Pixel format | `yuv420p` |
| Fast start | Enabled |
| Maximum FFmpeg execution time | 10 minutes |

Images are scaled while preserving their aspect ratio and centered on a 512 × 512 canvas.

The video duration is calculated as:

```text
duration = number of frames / frames per second
```

Error images are included as frames, preserving the original plane sequence.

## Project Structure

The main application components are:

| File | Responsibility |
|---|---|
| `Main.java` | Application entry point, argument validation, and processing workflow. |
| `NRRDVolumeReader.java` | Parses NRRD headers, loads volume data, and provides voxel access. |
| `PlaneProjector.java` | Validates geometric shapes, constructs projection coordinates, rasterizes planes, and generates PNG images. |
| `Mp4VideoCreator.java` | Validates generated frames and invokes FFmpeg to create the MP4 video. |
| `pom.xml` | Maven project configuration, Java version, and dependencies. |

## Building the Project

Navigate to the project root directory and execute:

```bash
mvn clean package
```

Maven compiles the source code, resolves dependencies, executes the configured build lifecycle, and packages the application.

The resulting JAR file is located in the Maven `target/` directory.

If necessary, ensure that the JAR manifest specifies `Main` as the application entry point.

## Running the Application

The application requires exactly three command-line arguments.

```bash
java -jar I2SE_Ex1.jar input.nrrd planes.csv output.mp4
```

Argument descriptions:

| Argument | Description |
|---|---|
| `input.nrrd` | Path to the input 3D NRRD volume. |
| `planes.csv` | Path to the CSV file containing plane definitions. |
| `output.mp4` | Path where the resulting MP4 video will be saved. |

Absolute and relative paths are supported.

The parent directory of the requested MP4 output file is created automatically if it does not exist.

### Example

```bash
java -jar I2SE_Ex1.jar \
    Datasets/head.nrrd \
    Datasets/planes.csv \
    Results/projection.mp4
```

The application will:

1. Validate the input files.
2. Prepare the projection frames directory.
3. Load and preload the NRRD volume.
4. Process each plane in the CSV file.
5. Generate one PNG frame per processed plane.
6. Report successful and failed projections.
7. Invoke FFmpeg to generate the MP4 video.
8. Verify that the output video exists and is not empty.

The application returns exit code `0` on success and `1` on failure.

## Error Handling

The application handles errors at both the application and individual-plane levels.

### Application-Level Errors

The application terminates with an error status if it encounters problems such as:

- Incorrect command-line arguments.
- Missing input files.
- Invalid or unsupported NRRD headers.
- Unsupported NRRD pixel types or encodings.
- Truncated NRRD data.
- Failure to create output directories.
- Failure to write output images.
- Missing FFmpeg executable.
- FFmpeg execution failure or timeout.
- Failure to produce a valid output video.

### Plane-Level Errors

Individual plane failures do not stop the entire projection process.

Potential plane-level errors include:

- Invalid CSV formatting.
- Non-numeric coordinates.
- Coordinates outside the supported range.
- Coincident vertices.
- Collinear vertices.
- Non-isosceles triangles.
- Non-coplanar quadrilaterals.
- Invalid trapezoid geometry.
- Degenerate projected triangles.

When a plane fails, the application:

1. Reports the error to the console.
2. Generates an error PNG frame.
3. Continues processing the next plane.

This ensures that one invalid plane does not prevent the remaining valid planes from being processed.

## Performance Considerations

Several implementation choices improve runtime efficiency:

- **Volume preloading:** The entire NRRD volume is loaded into memory before projection begins, avoiding repeated file reads.
- **Efficient voxel access:** Voxel values are retrieved directly from the preloaded byte array.
- **Triangle rasterization:** Only pixels within each projected triangle's bounding box are considered.
- **Shared rasterization procedure:** Both triangles and quadrilaterals use the same rasterization implementation.
- **Sequential frame generation:** Images are written directly to disk instead of retaining every generated image in memory.
- **External video encoding:** FFmpeg handles video encoding through a separate process.

The main memory requirement comes from loading the complete volume into memory, in addition to the temporary projection matrices and masks.

## Troubleshooting

### The application reports incorrect command-line arguments

Ensure that exactly three arguments are supplied:

```bash
java -jar I2SE_Ex1.jar input.nrrd planes.csv output.mp4
```

### The application cannot load the NRRD file

Check that:

- The file exists and is readable.
- The file is an inline `.nrrd` file rather than a detached `.nhdr` file.
- The volume has exactly three dimensions.
- The pixel type is supported.
- The encoding is `raw`, `gzip`, or `gz`.
- The data payload is complete.

### The application rejects a plane

Check that:

- Each CSV row contains 9 or 12 fields.
- Coordinates are valid numeric values.
- All coordinates are between 0 and 1023 inclusive.
- Triangle vertices define an isosceles triangle.
- Quadrilateral vertices define an isosceles trapezoid.
- Quadrilateral vertices are listed in perimeter order.
- No required vertices coincide.

### Some projected pixels appear black

Pixels outside the volume are excluded from the projection and displayed as black.

Additionally, each projection is normalized independently. If all valid voxel values in a projection are identical, the image will be entirely black.

### The projection PNG images are not generated

Check that:

- The input NRRD file can be loaded.
- The CSV file contains valid plane definitions.
- The output directory is writable.
- The application has sufficient available memory.

If a plane fails validation, an error image is generated instead of a grayscale projection.

### The MP4 video is not generated

Ensure that:

- FFmpeg is installed.
- FFmpeg is accessible through the system's `PATH`.
- At least one projection frame was generated.
- The generated frames are consecutively numbered, starting from `projection_0.png`.
- The output directory is writable.
