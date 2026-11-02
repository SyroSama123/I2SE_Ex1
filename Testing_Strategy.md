# Testing Strategy — I2SE_Ex1

## 1. Purpose

This document describes the testing strategy for `I2SE_Ex1`, a Java application that reads a 3D NRRD volume, processes planes defined in a CSV file, generates 2D projection PNG images, and creates an MP4 video using FFmpeg.

## 2. Testing Environment

- Language: Java 21
- Build system: Maven
- Testing framework: JUnit 5
- External tools: FFmpeg
- Supported operating systems: Linux and Windows

## 3. Test Inventory

| Test class             | Purpose                                                             |
| ---------------------- | ------------------------------------------------------------------- |
| `MainTest`             | Tests application entry-point behavior and argument validation.     |
| `NRRDVolumeReaderTest` | Tests loading NRRD volumes and accessing voxel values.              |
| `PlaneProjectorTest`   | Tests geometric calculations, plane projection, and invalid inputs. |
| `Mp4VideoCreatorTest`  | Tests PNG ordering and video-generation behavior.                   |
| `TestVolumeHelper`     | Provides helper methods for creating test volumes.                  |

The test suite contains 16 test methods and 21 test invocations, including parameterized tests.

## 4. Testing Approach

### 4.1 NRRD Volume Reader

Verify that the reader:

- Loads valid NRRD files.
- Reports the correct dimensions.
- Returns the expected voxel values.
- Handles unsupported formats and invalid files appropriately.

### 4.2 CSV Input

Verify that the application:

- Parses valid triangle and quadrilateral definitions.
- Handles missing or malformed coordinates.
- Rejects invalid input files with meaningful errors.

### 4.3 Geometry and Projection

Test:

- Distances between 3D points.
- Triangle and quadrilateral projection calculations.
- Correct output dimensions.
- Degenerate or invalid geometric inputs.
- Boundary conditions and invalid coordinates.

### 4.4 PNG and Video Generation

Verify that:

- Projection images are generated successfully.
- PNG files are processed in the correct order.
- FFmpeg is invoked with the appropriate arguments.
- Errors during video generation are reported appropriately.

### 4.5 Command-Line Integration

Test the complete workflow, from loading the input volume and CSV file to generating projection images and the final video.

## 5. Environment Setup

### Linux (Fedora)

Install the required tools:

```
sudo dnf install java-21-openjdk-devel maven ffmpeg
```

Verify the installations:

```
java -version
mvn -version
ffmpeg -version
```

### Windows (PowerShell)

Install Java 21, Maven, and FFmpeg using `winget`:

```
winget install EclipseAdoptium.Temurin.21.JDK
winget install Apache.Maven
winget install Gyan.FFmpeg
```

Verify the installations:

```
java -version
mvn -version
ffmpeg -version
```

If the commands are not recognized, restart the terminal so the updated `PATH` can take effect.

## 6. Running the Tests

Run all tests:

```
mvn clean test
```

Build the application:

```
mvn clean package
```

Run the packaged application using its actual JAR filename and command-line arguments. For example:

```
java -jar target/I2SE_Ex1.jar planes.csv video.mp4
```

Note: Confirm the actual JAR filename and required argument order in the project before running this command.

## 7. Known Testing Gaps

Further tests could improve coverage by verifying:

- Exact pixel values and image normalization.
- Correct handling of pixels outside the projected region.
- Successful MP4 decoding and expected frame count.
- Different voxel data types, endianness, and compression settings.
- Valid quadrilateral projections and geometric tolerance boundaries.
- FFmpeg failures, missing executables, and invalid output paths.
- End-to-end behavior using representative input files.

## 8. Success Criteria

Testing is considered successful when:

1. All automated tests pass.
2. Invalid inputs produce appropriate errors.
3. Valid inputs generate correctly sized projection images.
4. Video generation completes successfully when FFmpeg is available.
5. The complete application workflow is verified using representative NRRD and CSV inputs.