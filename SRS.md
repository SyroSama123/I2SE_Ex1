# Software Requirements Specification (SRS)

## Project: I2SE\_Ex1 — 3D Volume Plane Projection

### 1\. Introduction

#### 1.1 Purpose

This document specifies the software requirements for the "I2SE\_Ex1" system, a Java-based application designed to project 2D planes defined in 3D space onto a three-dimensional raster volume. The primary goal is to demonstrate robust Object-Oriented Design (OOD) while maintaining high algorithmic runtime efficiency.

#### 1.2 Scope

The software operates as a Command Line Interface (CLI) application. It reads 3D volumetric data from NRRD files using the Bio-Formats library and parses plane coordinates from a CSV file. It calculates 2D projections using nearest-voxel interpolation, saves the projections as PNG images, and utilizes FFmpeg to stitch these frames into an MP4 video sequence.

### 2\. Glossary / Definitions

* **Voxel:** A volumetric pixel; a single discrete data point on a regular 3D grid.  
* **Nearest-Neighbor Interpolation:** A volume sampling method that selects the value of the closest discrete voxel without applying color blending.  
* **EPS (**$\epsilon$**):** The defined mathematical tolerance ($0.001$) used to handle floating-point inaccuracies during geometric validation.  
* **Isosceles Trapezoid:** A convex quadrilateral ensuring two directional vectors on the same plane share the exact same length.

### 3\. Operating Environment & Constraints

* **Tech Stack:** Java 21, Apache Maven, JUnit 5\.  
* **External Dependencies:** Bio-Formats (for reading NRRD files).  
* **External Tools:** FFmpeg (must be installed on the host OS for video generation).  
* **Memory Limitations:** Processing large volumes (e.g., $50{0}^{3}$ bytes) requires significant heap memory; the JVM may require custom memory allocation flags (e.g., `-Xmx2G`).  
* **Data Validness:** Provided NRRD files must be uncorrupted and strictly adhere to the supported dimensional limits.

### 4\. System Architecture

The system is divided into four primary components following the Single Responsibility Principle (SRP):

1. **`Main`**: Coordinates the application workflow, argument parsing, and command-line execution.  
2. **`NRRDVolumeReader`**: Reads NRRD volume data and provides voxel access.  
3. **`PlaneProjector`**: Performs plane validation and geometric projection calculations.  
4. **`Mp4VideoCreator`**: Interacts with FFmpeg to stitch generated PNG images into an MP4 video sequence.

### 5\. Functional Requirements (FR)

* **FR-1: Data Ingestion**

  * The NRRD volume dimensions ($sx,sy,sz$) must satisfy: $0 \le \text{dimension} \le 1024$.  
* **FR-2: Plane Mathematical Validation**

  * The system shall validate the integrity of planes defined in the CSV using $\epsilon =0.001$.  
  * **3 Points (**$a,b,c$**):** Must form an isosceles triangle: $\vert dist(a,b) - dist(a,c) \vert < \epsilon$.
  * **4 Points (**${a}_{1},{a}_{2},b,c$**):** Must form an isosceles trapezoid on the same plane: $\vert dist(a_1,b) - dist(a_2,c) \vert < \epsilon$ and $dist({a}_{1},{a}_{2})\leq dist(b,c)$.  
* **FR-3: Geometric Projection**

  * The projection algorithm shall sample the volume by stepping across the normalized directional vectors of the defined plane.  
  * Voxel sampling must exclusively use the nearest-neighbor approach.  
  * Sampled coordinates falling strictly outside the 3D volume boundaries shall default to a pixel value of $0$ (black).  
* **FR-4: Output Generation**

  * The system shall generate a 2D projection image in PNG format for each valid plane.  
  * The system shall execute an FFmpeg command to compile the generated sequence of PNG files into a single MP4 video file.  
* **FR-5: Error Frame Generation**

  * Invalid CSV rows (failing FR-2 validation) **shall not terminate the process**.  
  * The system shall catch the validation exception and generate a distinct "Error Frame" (e.g., a clearly marked black PNG) for that specific sequence index, maintaining video continuity.

### 6\. Non-Functional Requirements (NFR)

* **NFR-1: Algorithmic Performance**

  * The projection processing loop must be highly optimized. For a baseline volume of $50{0}^{3}$ bytes, runtime performance must strictly exceed **30 Frames Per Second (FPS)**. The target benchmark for this Java implementation is **100+ FPS**.  
* **NFR-2: Robustness**

  * The application must provide graceful degradation. Missing files, unsupported NRRD encodings, or missing system binaries (FFmpeg) must result in clear, descriptive console logs rather than raw system crashes.