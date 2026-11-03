# Software Requirements Specification (SRS)

## Project: I2SE_Ex1 — 3D Volume Plane Projection

### 1. Introduction

#### 1.1 Purpose

This document specifies the software requirements for the `I2SE_Ex1` system, a Java-based application designed to project 2D planes defined in 3D space onto a three-dimensional raster volume. The primary goal is to demonstrate robust Object-Oriented Design (OOD) while maintaining high algorithmic runtime efficiency.

#### 1.2 Scope

The software operates as a Command Line Interface (CLI) application. It reads 3D volumetric data from NRRD files and parses plane coordinates from a CSV file. It calculates 2D projections using nearest-neighbor voxel sampling, saves the projections as PNG images, and utilizes FFmpeg to stitch these frames into an MP4 video sequence.

The NRRD reader parses the file header directly and supports inline NRRD files with raw or gzip-compressed data. Detached NRRD files are not supported.

### 2. Glossary / Definitions

- **Voxel:** A volumetric pixel; a single discrete data point on a regular 3D grid.
- **Nearest-Neighbor Interpolation:** A volume sampling method that selects the value of the closest discrete voxel without applying color blending.
- **EPS (ε):** The defined mathematical tolerance (`0.001`) used to handle floating-point inaccuracies during geometric validation.
- **Isosceles Triangle:** A triangle in which at least two side lengths are approximately equal within the specified tolerance.
- **Isosceles Trapezoid:** A four-vertex planar shape for which one pair of opposite sides is approximately parallel and the other pair of opposite sides has approximately equal lengths, according to the implemented validation rules. The implementation does not explicitly guarantee convexity or exclude every self-intersecting quadrilateral.

### 3. Operating Environment & Constraints

- **Tech Stack:** Java 21 and Apache Maven. JUnit 5 may be used for testing if configured in the project.
- **External Dependencies:** The NRRD reader implementation described in this specification parses NRRD headers directly and does not require Bio-Formats for this functionality.
- **External Tools:** FFmpeg must be installed and accessible on the host OS for video generation.
- **Memory Limitations:** The NRRD reader loads the entire decompressed volume into memory when voxel data is first requested. Large volumes therefore require sufficient heap memory. The required memory depends on the dimensions, bytes per voxel, decompression overhead, and other application allocations. JVM options such as `-Xmx2G` may be necessary, depending on the input data and available system memory.
- **Data Validity:** Provided NRRD files must be readable and use supported dimensions, data types, and encodings. The reader requires a three-dimensional scalar volume and supports inline NRRD files using `raw`, `gzip`, or `gz` encoding. The volume dimensions must be positive integers. Detached NRRD files are not supported.

### 4. System Architecture

The system is divided into four primary components following the Single Responsibility Principle (SRP):

1. **`Main`**: Coordinates the application workflow, argument parsing, and command-line execution.
2. **`NRRDVolumeReader`**: Parses the NRRD header, loads the volume data, and provides voxel access with support for the implemented signed, unsigned, and floating-point pixel types.
3. **`PlaneProjector`**: Performs plane validation and geometric projection calculations.
4. **`Mp4VideoCreator`**: Interacts with FFmpeg to stitch generated PNG images into an MP4 video sequence.

### 5. Functional Requirements (FR)

- **FR-1: Data Ingestion**
    - The system shall read a three-dimensional scalar volume from an inline NRRD file.
    - The volume dimensions `(sx, sy, sz)` must each be positive integers. For the assignment's specified input constraints, each dimension must satisfy `1 <= sx, sy, sz < 2^10`, meaning dimensions from 1 through 1023.
    - The NRRD reader shall support the following encodings: `raw`, `gzip`, and `gz`.
    - The reader shall support the pixel types implemented in `NRRDVolumeReader`, including 8-, 16-, 32-, and 64-bit signed or unsigned integers, as well as 32-bit floating-point and 64-bit double-precision values.
    - The reader shall interpret voxel data according to the specified endian order for multibyte values. If the NRRD header omits the endian field, the reader shall use little-endian order.
    - The reader shall reject unsupported encodings, unsupported pixel types, invalid dimensions, detached data files, and truncated voxel payloads with descriptive errors.
    - The system shall validate plane coordinates as finite values in the inclusive range `[0, 1024]`. This coordinate constraint is distinct from the volume-dimension constraint.

- **FR-2: Plane Mathematical Validation**
    - The system shall validate the integrity of planes defined in the CSV using `ε = 0.001`.
    - **CSV Format:** Each row shall contain either 9 coordinate fields for a triangle or 12 coordinate fields for a quadrilateral. For a triangle, the row contains three vertices, each represented by `(x, y, z)`. For a quadrilateral, it contains four vertices. An omitted fourth vertex is permitted only when all three corresponding coordinate fields are empty.
    - **3 Points `(a, b, c)`:** The vertices must be distinct and non-collinear, and at least two of the three side lengths must be approximately equal within the specified tolerance.
    - **4 Points `(a1, a2, b, c)`:** The vertices must be distinct and coplanar within the specified tolerance. One pair of opposite sides must be approximately parallel, and the other pair of opposite sides must have approximately equal lengths within the specified tolerance.

- **FR-3: Geometric Projection**
    - The projection algorithm shall map the vertices of each valid plane into a local two-dimensional coordinate system.
    - The algorithm shall rasterize the plane's two-dimensional region and use barycentric interpolation to calculate corresponding 3D sampling coordinates.
    - Voxel sampling must exclusively use the nearest-neighbor approach, rounding the calculated sampling coordinates to the nearest voxel.
    - Sampled coordinates falling outside the valid 3D voxel index range shall contribute a pixel value of `0` (black).
    - Pixels outside the projected polygon shall remain black.
    - The algorithm shall use the implemented projection dimensions and rasterization logic; it shall not be assumed to use uniform stepping along normalized directional vectors unless the implementation is changed to do so.

- **FR-4: Output Generation**
    - The system shall generate a 2D projection image in PNG format for each processed plane row, including invalid rows for which an error frame can be generated.
    - Projection images shall be saved as sequentially indexed PNG files.
    - For valid projections, the system shall convert sampled voxel values to grayscale pixel intensities using the implemented normalization procedure. When the sampled values have no varying finite range, the output shall follow the implementation's constant-range handling behavior.
    - The system shall execute FFmpeg to compile the generated PNG sequence into an MP4 video file.
    - The video configuration shall use the settings implemented by the video creator. If the current implementation is configured for 50 FPS, 512 × 512 output frames, MPEG-4 encoding, and `yuv420p` pixel format, these values shall be treated as implementation-specific output settings rather than universal requirements of plane projection.

- **FR-5: Error Frame Generation**
    - Invalid CSV rows that fail coordinate or geometric validation shall not terminate processing of subsequent rows, provided that the application can generate the corresponding error frame.
    - The system shall catch the applicable validation or processing exception and attempt to generate a distinct error frame for the corresponding sequence index.
    - Error frames shall be visibly distinguishable from valid projection frames, for example by using a black background and an error message.
    - Errors that prevent the system from generating an error frame, reading the input volume, or completing video generation may prevent successful completion of the overall process. The system shall report such failures clearly.

### 6. Non-Functional Requirements (NFR)

- **NFR-1: Algorithmic Performance**
    - The projection processing loop should be designed to avoid unnecessary computation and memory allocation.
    - A minimum frame rate of 30 FPS and target of 100+ FPS.
    - Performance evaluation should specify the volume dimensions, pixel type, plane geometry, projection resolution, number of planes, hardware, JVM configuration, and whether file I/O and video encoding are included in the measurements.

- **NFR-2: Robustness**
    - The application should provide clear, descriptive error messages for missing files, malformed CSV input, invalid geometry, unsupported NRRD encodings or data types, truncated voxel data, and unavailable FFmpeg executables.
    - Recoverable errors affecting individual plane rows should be handled without unnecessarily terminating the processing of subsequent rows.
    - Failures affecting the input volume or required output generation may terminate the overall process, but should be reported clearly.

- **NFR-3: Maintainability**
    - Each component should have a clearly defined responsibility, with volume reading, plane projection, command-line coordination, and video generation kept separate.

- **NFR-4: Portability**
    - The application should run on systems with a compatible Java 21 runtime and the required dependencies. Video generation additionally requires a compatible FFmpeg installation accessible to the application.

### 7. Assumptions and Limitations

- Input NRRD files are inline files containing a three-dimensional scalar volume.
- Only `raw`, `gzip`, and `gz` encodings are supported by the current NRRD reader.
- The current reader loads the entire volume into memory when voxel data is requested.
- Plane coordinates are constrained to finite values in the inclusive range `[0, 1024]`.
- Volume dimensions are positive integers, with the assignment specifying an upper bound of 1023 for each dimension.
- Projection samples outside the volume are treated as black, and large integer values may lose precision when converted to `double` for projection calculations.
- Geometric validation follows the implemented checks and does not guarantee that every accepted quadrilateral is convex or non-self-intersecting.
- Performance depends on input data, plane geometry, hardware, JVM configuration, and output settings.

### 8. Acceptance Criteria

The system shall be considered to satisfy this specification when:

1. It successfully reads supported inline 3D NRRD files with valid dimensions, pixel types, and encodings.
2. It rejects unsupported or malformed NRRD input with descriptive errors.
3. It parses valid triangle and quadrilateral rows from the CSV file.
4. It rejects invalid coordinates and geometries according to the implemented validation rules.
5. It generates PNG projection frames using nearest-neighbor voxel sampling.
6. It produces visibly distinguishable error frames for recoverable invalid plane rows and continues processing subsequent rows.
7. It generates an MP4 video from the PNG sequence when FFmpeg is available and the video-generation process succeeds.
8. It reports unrecoverable input or output failures clearly.
9. Its performance is measured under documented conditions rather than assumed to meet an unverified FPS threshold.