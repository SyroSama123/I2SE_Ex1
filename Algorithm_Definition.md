# 3D Volume Plane Projection Algorithm

## 1. Overview

The `PlaneProjector` class is responsible for projecting geometric planes defined by vertices in a three-dimensional NRRD volume onto two-dimensional grayscale images.

The algorithm supports two types of planes:

- **Isosceles triangles** — defined by three vertices.
- **Isosceles trapezoids** — defined by four vertices.

Each valid plane is converted into a 2D image representing the voxel intensity values sampled from the corresponding plane in the 3D volume.

The algorithm performs the following main operations:

1. Read plane vertices from a CSV file.
2. Validate the vertices and the geometric shape.
3. Construct a local 2D coordinate system for the plane.
4. Project the vertices into that coordinate system.
5. Rasterize the plane into a 2D pixel grid.
6. Interpolate 3D coordinates for each covered pixel.
7. Sample voxel values from the NRRD volume.
8. Normalize the sampled values and save the result as a PNG image.

If a plane fails validation or processing, an error image is generated instead of terminating the entire projection process.

---

## 2. Input Format

The algorithm receives a CSV file in which each non-empty, non-comment line describes one plane.

### 2.1 Triangle

A triangle is represented by three 3D vertices, resulting in nine comma-separated values:

```text
x1,y1,z1,x2,y2,z2,x3,y3,z3,,,
```

The triangle must be isosceles, meaning that at least two of its side lengths must be approximately equal.

### 2.2 Trapezoid

A trapezoid is represented by four 3D vertices, resulting in twelve comma-separated values:

```text
x1,y1,z1,x2,y2,z2,x3,y3,z3,x4,y4,z4
```

The vertices must be ordered around the perimeter of the trapezoid. The shape must be an isosceles trapezoid, meaning that it has a pair of parallel bases and equal non-parallel sides.

### 2.3 Coordinate Constraints

Every coordinate must:

- Be a valid finite number.
- Be greater than or equal to `0`.
- Be less than or equal to `1023`.

The algorithm accepts decimal coordinates, although voxel sampling ultimately uses integer coordinates.

Empty lines and lines beginning with `#` are ignored.

---

## 3. Geometric Validation

Before projection, the algorithm verifies that the supplied vertices define a valid supported shape.

### 3.1 General Validation

The algorithm first checks that the plane contains exactly three or four vertices.

It then calculates the lengths of the first three edges:

- `AB`
- `BC`
- `CA`

If any of these edges is shorter than `GEOMETRY_EPSILON` (`1e-12`), the plane is rejected because it contains coincident vertices.

The algorithm also calculates the cross product:

```text
(B - A) × (C - A)
```

If the magnitude of this vector is smaller than `GEOMETRY_EPSILON`, the first three vertices are considered collinear, and the plane is rejected.

This cross product is also used later to calculate the plane's normal vector.

### 3.2 Isosceles Triangle Validation

For a triangle, the algorithm checks whether at least one pair of side lengths is approximately equal.

Two lengths are considered approximately equal when:

```text
|length1 - length2| <= 0.001
```

If no pair satisfies this condition, the triangle is rejected.

The tolerance allows small floating-point differences when comparing side lengths.

### 3.3 Isosceles Trapezoid Validation

For a quadrilateral, the algorithm performs additional checks.

**Step 1: Check for coincident vertices**

The fourth vertex is checked against the third and first vertices to ensure that the remaining perimeter edges are not degenerate.

**Step 2: Check coplanarity**

The algorithm calculates the normal vector of the plane using the first three vertices.

It then calculates the absolute distance of the fourth vertex from the plane:

```text
distance = |(D - A) · normal|
```

If this distance exceeds `0.001`, the vertices are not considered coplanar, and the quadrilateral is rejected.

**Step 3: Identify parallel sides**

The algorithm checks the two possible pairs of opposite sides:

- `AB` and `CD`
- `BC` and `DA`

Two sides are considered parallel when the magnitude of their cross product, divided by the product of their lengths, is no greater than `0.001`.

This is equivalent to checking that the angle between the two side directions is sufficiently close to either zero or 180 degrees.

**Step 4: Check the equal legs**

For the pair `AB` and `CD` to be the bases, the legs `BC` and `DA` must have approximately equal lengths.

For the pair `BC` and `DA` to be the bases, the legs `AB` and `CD` must have approximately equal lengths.

If neither configuration satisfies both conditions, the quadrilateral is rejected.

---

## 4. Determining the Projection Orientation

After validation, the algorithm determines which side of the shape should be placed at the bottom of the resulting image.

This ensures consistent orientation regardless of the original ordering of the vertices.

### 4.1 Triangle Orientation

For an isosceles triangle, the algorithm identifies the two approximately equal sides.

The remaining side is selected as the base, while the vertex opposite that base is selected as the apex.

For example:

| Equal sides | Base | Apex |
|---|---|---|
| AB and AC | BC | A |
| AB and BC | AC | B |
| BC and CA | AB | C |

The selected base will appear at the bottom of the resulting image, with the apex above it.

### 4.2 Trapezoid Orientation

For a trapezoid, the algorithm identifies which pair of opposite sides is parallel.

It then compares the lengths of the two parallel sides and selects the longer one as the base.

The midpoint of the opposite, shorter base is used to determine which direction should be considered upward.

Consequently, the longer parallel side appears at the bottom of the resulting image.

---

## 5. Constructing a Local 2D Coordinate System

The original vertices are defined in 3D coordinates. To generate a 2D image, the algorithm constructs a local coordinate system that lies within the plane.

This is an orthonormal coordinate system consisting of two perpendicular unit vectors, `u` and `v`.

### 5.1 Calculating the Horizontal Axis

The horizontal axis follows the selected base:

```text
u = normalize(baseEnd - baseStart)
```

Therefore, the selected base is aligned with the horizontal direction of the output image.

### 5.2 Calculating the Vertical Axis

The algorithm first calculates the normal vector of the plane:

```text
normal = normalize((B - A) × (C - A))
```

It then calculates the second axis:

```text
v = normalize(normal × u)
```

Since `normal` is perpendicular to the plane and `u` lies within the plane, `v` also lies within the plane and is perpendicular to `u`.

### 5.3 Ensuring the Correct Orientation

The algorithm calculates the midpoint of the selected base and compares it with the opposite vertex or opposite base midpoint.

If the opposite part of the shape lies in the negative `v` direction, the vertical axis is reversed:

```text
v = -v
```

This guarantees that the apex of a triangle or the opposite base of a trapezoid is oriented above the selected base.

### 5.4 Projecting the Vertices

Each 3D vertex is transformed into local 2D coordinates by subtracting the base's starting point and calculating its dot products with the two local axes.

For a 3D point `P`, its 2D coordinates are:

```text
x = (P - baseStart) · u
y = (P - baseStart) · v
```

This operation preserves distances and angles within the plane because `u` and `v` are perpendicular unit vectors.

The resulting coordinates describe the shape independently of its original orientation in the 3D volume.

---

## 6. Calculating the Output Image Dimensions

After projecting all vertices into 2D, the algorithm calculates the minimum and maximum coordinates along both axes:

- `minX`
- `maxX`
- `minY`
- `maxY`

The output image is sized to contain the entire projected shape.

The pixel size is fixed at `1.0`, meaning that one image pixel represents one unit of distance in the local 2D coordinate system.

The dimensions are calculated as:

```text
width  = ceil((maxX - minX) / pixelSize) + 1
height = ceil((maxY - minY) / pixelSize) + 1
```

Both dimensions are guaranteed to be at least one pixel.

The algorithm then allocates two matrices:

- `output[height][width]` stores the sampled voxel intensity values.
- `mask[height][width]` indicates which pixels belong to the projected shape and contain valid sampled data.

The mask is necessary because pixels outside the shape should not be treated as actual voxel samples.

### 6.1 Converting to Image Coordinates

The projected vertices are translated into image coordinates by subtracting the minimum coordinates.

The vertical coordinate is also inverted because image coordinates increase downward, whereas the local geometric vertical axis increases upward.

The conversion is:

```text
imageX = (projectedX - minX) / pixelSize
imageY = (maxY - projectedY) / pixelSize
```

This places the shape within the image boundaries while preserving its orientation.

---

## 7. Rasterization

Rasterization determines which pixels belong to the projected shape and calculates the corresponding voxel intensity for each of them.

The algorithm uses **barycentric coordinates** to determine whether a pixel lies inside a triangle and to interpolate its corresponding 3D position.

### 7.1 Triangulating the Shapes

The rasterization implementation operates on triangles.

A triangle is rasterized directly using its three vertices.

A quadrilateral is divided into two triangles along the diagonal connecting vertices `A` and `C`:

```text
Triangle 1: A, B, C
Triangle 2: A, C, D
```

Both triangles are rasterized into the same output and mask matrices.

This allows the same rasterization procedure to handle both supported shapes.

### 7.2 Calculating the Rasterization Bounds

For each triangle, the algorithm calculates its 2D bounding box.

The minimum coordinates are rounded down using `floor`, while the maximum coordinates are rounded up using `ceil`.

The resulting bounds are clamped to the output image dimensions.

Only pixels within this bounding box are examined, avoiding unnecessary calculations outside the triangle's vicinity.

### 7.3 Barycentric Coordinates

For each candidate pixel `(x, y)`, the algorithm calculates three barycentric weights:

```text
wA = edge(B, C, x, y) / area
wB = edge(C, A, x, y) / area
wC = edge(A, B, x, y) / area
```

The `edge` function calculates a signed 2D edge expression:

```text
edge(P1, P2, x, y) =
    (x - P1.x) * (P2.y - P1.y)
  - (y - P1.y) * (P2.x - P1.x)
```

The weights describe the pixel's position relative to the triangle's vertices.

For a point inside the triangle, all three weights are # 3D Volume Plane Projection Algorithm

## 1. Overview

The `PlaneProjector` class is responsible for projecting geometric planes defined by vertices in a three-dimensional NRRD volume onto two-dimensional grayscale images.

The algorithm supports two types of planes:

- **Isosceles triangles** — defined by three vertices.
- **Isosceles trapezoids** — defined by four vertices.

Each valid plane is converted into a 2D image representing the voxel intensity values sampled from the corresponding plane in the 3D volume.

The algorithm performs the following main operations:

1. Read plane vertices from a CSV file.
2. Validate the vertices and the geometric shape.
3. Construct a local 2D coordinate system for the plane.
4. Project the vertices into that coordinate system.
5. Rasterize the plane into a 2D pixel grid.
6. Interpolate 3D coordinates for each covered pixel.
7. Sample voxel values from the NRRD volume.
8. Normalize the sampled values and save the result as a PNG image.

If a plane fails validation or processing, an error image is generated instead of terminating the entire projection process.

---

## 2. Input Format

The algorithm receives a CSV file in which each non-empty, non-comment line describes one plane.

### 2.1 Triangle

A triangle is represented by three 3D vertices, resulting in nine comma-separated values:

```text
x1,y1,z1,x2,y2,z2,x3,y3,z3
```

The triangle must be isosceles, meaning that at least two of its side lengths must be approximately equal.

### 2.2 Trapezoid

A trapezoid is represented by four 3D vertices, resulting in twelve comma-separated values:

```text
x1,y1,z1,x2,y2,z2,x3,y3,z3,x4,y4,z4
```

The vertices must be ordered around the perimeter of the trapezoid. The shape must be an isosceles trapezoid, meaning that it has a pair of parallel bases and equal non-parallel sides.

### 2.3 Coordinate Constraints

Every coordinate must:

- Be a valid finite number.
- Be greater than or equal to `0`.
- Be less than or equal to `1023`.

The algorithm accepts decimal coordinates, although voxel sampling ultimately uses integer coordinates.

Empty lines and lines beginning with `#` are ignored.

---

## 3. Geometric Validation

Before projection, the algorithm verifies that the supplied vertices define a valid supported shape.

### 3.1 General Validation

The algorithm first checks that the plane contains exactly three or four vertices.

It then calculates the lengths of the first three edges:

- `AB`
- `BC`
- `CA`

If any of these edges is shorter than `GEOMETRY_EPSILON` (`1e-12`), the plane is rejected because it contains coincident vertices.

The algorithm also calculates the cross product:

```text
(B - A) × (C - A)
```

If the magnitude of this vector is smaller than `GEOMETRY_EPSILON`, the first three vertices are considered collinear, and the plane is rejected.

This cross product is also used later to calculate the plane's normal vector.

### 3.2 Isosceles Triangle Validation

For a triangle, the algorithm checks whether at least one pair of side lengths is approximately equal.

Two lengths are considered approximately equal when:

```text
|length1 - length2| <= 0.001
```

If no pair satisfies this condition, the triangle is rejected.

The tolerance allows small floating-point differences when comparing side lengths.

### 3.3 Isosceles Trapezoid Validation

For a quadrilateral, the algorithm performs additional checks.

**Step 1: Check for coincident vertices**

The fourth vertex is checked against the third and first vertices to ensure that the remaining perimeter edges are not degenerate.

**Step 2: Check coplanarity**

The algorithm calculates the normal vector of the plane using the first three vertices.

It then calculates the absolute distance of the fourth vertex from the plane:

```text
distance = |(D - A) · normal|
```

If this distance exceeds `0.001`, the vertices are not considered coplanar, and the quadrilateral is rejected.

**Step 3: Identify parallel sides**

The algorithm checks the two possible pairs of opposite sides:

- `AB` and `CD`
- `BC` and `DA`

Two sides are considered parallel when the magnitude of their cross product, divided by the product of their lengths, is no greater than `0.001`.

This is equivalent to checking that the angle between the two side directions is sufficiently close to either zero or 180 degrees.

**Step 4: Check the equal legs**

For the pair `AB` and `CD` to be the bases, the legs `BC` and `DA` must have approximately equal lengths.

For the pair `BC` and `DA` to be the bases, the legs `AB` and `CD` must have approximately equal lengths.

If neither configuration satisfies both conditions, the quadrilateral is rejected.

---

## 4. Determining the Projection Orientation

After validation, the algorithm determines which side of the shape should be placed at the bottom of the resulting image.

This ensures consistent orientation regardless of the original ordering of the vertices.

### 4.1 Triangle Orientation

For an isosceles triangle, the algorithm identifies the two approximately equal sides.

The remaining side is selected as the base, while the vertex opposite that base is selected as the apex.

For example:

| Equal sides | Base | Apex |
|---|---|---|
| AB and AC | BC | A |
| AB and BC | AC | B |
| BC and CA | AB | C |

The selected base will appear at the bottom of the resulting image, with the apex above it.

### 4.2 Trapezoid Orientation

For a trapezoid, the algorithm identifies which pair of opposite sides is parallel.

It then compares the lengths of the two parallel sides and selects the longer one as the base.

The midpoint of the opposite, shorter base is used to determine which direction should be considered upward.

Consequently, the longer parallel side appears at the bottom of the resulting image.

---

## 5. Constructing a Local 2D Coordinate System

The original vertices are defined in 3D coordinates. To generate a 2D image, the algorithm constructs a local coordinate system that lies within the plane.

This is an orthonormal coordinate system consisting of two perpendicular unit vectors, `u` and `v`.

### 5.1 Calculating the Horizontal Axis

The horizontal axis follows the selected base:

```text
u = normalize(baseEnd - baseStart)
```

Therefore, the selected base is aligned with the horizontal direction of the output image.

### 5.2 Calculating the Vertical Axis

The algorithm first calculates the normal vector of the plane:

```text
normal = normalize((B - A) × (C - A))
```

It then calculates the second axis:

```text
v = normalize(normal × u)
```

Since `normal` is perpendicular to the plane and `u` lies within the plane, `v` also lies within the plane and is perpendicular to `u`.

### 5.3 Ensuring the Correct Orientation

The algorithm calculates the midpoint of the selected base and compares it with the opposite vertex or opposite base midpoint.

If the opposite part of the shape lies in the negative `v` direction, the vertical axis is reversed:

```text
v = -v
```

This guarantees that the apex of a triangle or the opposite base of a trapezoid is oriented above the selected base.

### 5.4 Projecting the Vertices

Each 3D vertex is transformed into local 2D coordinates by subtracting the base's starting point and calculating its dot products with the two local axes.

For a 3D point `P`, its 2D coordinates are:

```text
x = (P - baseStart) · u
y = (P - baseStart) · v
```

This operation preserves distances and angles within the plane because `u` and `v` are perpendicular unit vectors.

The resulting coordinates describe the shape independently of its original orientation in the 3D volume.

---

## 6. Calculating the Output Image Dimensions

After projecting all vertices into 2D, the algorithm calculates the minimum and maximum coordinates along both axes:

- `minX`
- `maxX`
- `minY`
- `maxY`

The output image is sized to contain the entire projected shape.

The pixel size is fixed at `1.0`, meaning that one image pixel represents one unit of distance in the local 2D coordinate system.

The dimensions are calculated as:

```text
width  = ceil((maxX - minX) / pixelSize) + 1
height = ceil((maxY - minY) / pixelSize) + 1
```

Both dimensions are guaranteed to be at least one pixel.

The algorithm then allocates two matrices:

- `output[height][width]` stores the sampled voxel intensity values.
- `mask[height][width]` indicates which pixels belong to the projected shape and contain valid sampled data.

The mask is necessary because pixels outside the shape should not be treated as actual voxel samples.

### 6.1 Converting to Image Coordinates

The projected vertices are translated into image coordinates by subtracting the minimum coordinates.

The vertical coordinate is also inverted because image coordinates increase downward, whereas the local geometric vertical axis increases upward.

The conversion is:

```text
imageX = (projectedX - minX) / pixelSize
imageY = (maxY - projectedY) / pixelSize
```

This places the shape within the image boundaries while preserving its orientation.

---

## 7. Rasterization

Rasterization determines which pixels belong to the projected shape and calculates the corresponding voxel intensity for each of them.

The algorithm uses **barycentric coordinates** to determine whether a pixel lies inside a triangle and to interpolate its corresponding 3D position.

### 7.1 Triangulating the Shapes

The rasterization implementation operates on triangles.

A triangle is rasterized directly using its three vertices.

A quadrilateral is divided into two triangles along the diagonal connecting vertices `A` and `C`:

```text
Triangle 1: A, B, C
Triangle 2: A, C, D
```

Both triangles are rasterized into the same output and mask matrices.

This allows the same rasterization procedure to handle both supported shapes.

### 7.2 Calculating the Rasterization Bounds

For each triangle, the algorithm calculates its 2D bounding box.

The minimum coordinates are rounded down using `floor`, while the maximum coordinates are rounded up using `ceil`.

The resulting bounds are clamped to the output image dimensions.

Only pixels within this bounding box are examined, avoiding unnecessary calculations outside the triangle's vicinity.

### 7.3 Barycentric Coordinates

For each candidate pixel `(x, y)`, the algorithm calculates three barycentric weights:

```text
wA = edge(B, C, x, y) / area
wB = edge(C, A, x, y) / area
wC = edge(A, B, x, y) / area
```

The `edge` function calculates a signed 2D edge expression:

```text
edge(P1, P2, x, y) =
    (x - P1.x) * (P2.y - P1.y)
  - (y - P1.y) * (P2.x - P1.x)
```

The weights describe the pixel's position relative to the triangle's vertices.

For a point inside the triangle, all three weights are non-negative, apart from small numerical tolerance.

The algorithm uses an epsilon of `1e-9`:

```text
if (wA < -epsilon || wB < -epsilon || wC < -epsilon)
    skip pixel
```

Pixels outside the triangle are ignored.

### 7.4 Interpolating the 3D Position

Once a pixel is determined to be inside the triangle, its corresponding 3D position is calculated using the same barycentric weights.

For the triangle's 3D vertices `A`, `B`, and `C`:

```text
vx = wA * A.x + wB * B.x + wC * C.x
vy = wA * A.y + wB * B.y + wC * C.y
vz = wA * A.z + wB * B.z + wC * C.z
```

This produces a point on the original 3D triangle corresponding to the current 2D pixel.

Because barycentric interpolation is used, the mapping is linear across the triangle.

For a trapezoid, the same process is performed independently for both component triangles.

---

## 8. Sampling the 3D Volume

The interpolated 3D coordinates are generally fractional values.

However, the NRRD volume is a discrete voxel grid, so the algorithm converts them into integer voxel coordinates by rounding each coordinate to the nearest integer:

```text
voxelX = round(vx)
voxelY = round(vy)
voxelZ = round(vz)
```

Before accessing the volume, the algorithm verifies that the coordinates are within its actual dimensions:

```text
0 <= voxelX < volumeWidth
0 <= voxelY < volumeHeight
0 <= voxelZ < volumeDepth
```

If the coordinates are valid, the corresponding voxel value is retrieved using:

```text
volume.getPixelValueUnchecked(voxelX, voxelY, voxelZ)
```

The sampled value is stored in the output matrix, and the corresponding mask entry is set to `true`.

If the voxel coordinates are outside the volume, the pixel remains unchanged and its mask entry remains `false`.

**Important:** The current implementation uses nearest-neighbor voxel sampling. It does not perform trilinear interpolation between neighboring voxels.

---

## 9. Generating the PNG Image

Once rasterization is complete, the algorithm converts the output matrix into a grayscale PNG image.

The image uses an 8-bit grayscale format:

```text
BufferedImage.TYPE_BYTE_GRAY
```

### 9.1 Finding the Intensity Range

The algorithm scans the output matrix and finds the minimum and maximum values among pixels that:

- Are marked as valid by the mask.
- Contain finite values.

Pixels outside the projected shape are excluded from this calculation.

### 9.2 Intensity Normalization

If the minimum and maximum values are finite and different, the algorithm normalizes the sampled values into the range `[0, 255]`.

The normalization formula is:

```text
gray = 255 * (value - min) / (max - min)
```

The result is rounded to the nearest integer and clamped to the valid grayscale range.

This means that:

- The minimum sampled intensity becomes black (`0`).
- The maximum sampled intensity becomes white (`255`).
- Intermediate values are mapped proportionally between them.

If the image has no valid finite samples, or all valid samples have the same intensity, normalization is disabled. In that case, all pixels remain black.

### 9.3 Pixels Outside the Shape

Pixels whose mask entries are `false` are assigned grayscale value `0`.

Consequently, the area outside the projected triangle or trapezoid appears black.

The resulting image is written as a PNG file.

---

## 10. Error Handling

The algorithm is designed to process multiple planes without allowing one invalid plane to terminate the entire operation.

Each plane is processed independently inside a `try-catch` block.

If an exception occurs during parsing, validation, projection, sampling, or PNG generation:

1. The plane is counted as failed.
2. The error message is extracted.
3. An error is printed to the standard error stream.
4. A dedicated error PNG is generated in place of the normal projection.

### 10.1 Error Image Format

Error images have a fixed size of `512 × 512` pixels.

They contain:

- A black background.
- A white heading identifying the failed plane number.
- The error message, wrapped into lines.
- A message indicating that the plane was skipped.

This allows the output sequence to retain one image per processed plane, including failed planes.

The error image itself is also saved using the normal projection filename:

```text
projection_<planeNumber>.png
```

---

## 11. Processing the CSV File

The public entry point is:

```text
projectCsv(volume, csvPath, outputDirectory)
```

The method performs the following operations:

1. Creates the output directory if it does not already exist.
2. Reads all lines from the CSV file.
3. Initializes counters for processed, successful, and failed planes.
4. Starts a timer using `System.nanoTime()`.
5. Iterates through the CSV lines.
6. Skips empty lines and comment lines.
7. Assigns each processed plane a sequential number.
8. Parses, validates, projects, and saves the plane.
9. Generates an error image if processing fails.
10. Prints progress information every 25 processed planes.
11. Prints a final processing summary.

### 11.1 Output Naming

Each processed plane receives a sequential index beginning at zero:

```text
projection_0.png
projection_1.png
projection_2.png
...
```

Empty lines and comment lines do not increment the plane counter.

Failed planes still receive an index and produce an error image.

### 11.2 Performance Reporting

The algorithm reports progress every 25 processed planes.

The reported throughput is calculated as:

```text
planesPerSecond = processedPlanes / elapsedSeconds
```

At the end, the algorithm reports:

- Total number of processed planes.
- Number of successful planes.
- Number of failed planes.
- Total elapsed time.
- Average processing throughput.

---

## 12. Algorithm Summary

The complete algorithm can be summarized as follows:

```text
INPUT:
    3D NRRD volume
    CSV file containing triangle and trapezoid definitions
    Output directory

CREATE output directory

FOR EACH non-empty, non-comment CSV line:

    PARSE vertices

    IF parsing fails:
        GENERATE error image
        CONTINUE

    VALIDATE geometry:
        Check coordinate ranges
        Check number of vertices
        Check coincident vertices
        Check collinearity
        Check isosceles triangle or trapezoid properties
        Check trapezoid coplanarity

    IF validation fails:
        GENERATE error image
        CONTINUE

    DETERMINE shape orientation:
        Triangle: identify base and apex
        Trapezoid: identify longer parallel base

    CONSTRUCT local 2D coordinate system:
        Calculate horizontal axis u
        Calculate vertical axis v
        Orient v toward the apex or opposite base

    PROJECT vertices into 2D

    CALCULATE output image dimensions

    INITIALIZE output matrix and mask

    IF triangle:
        RASTERIZE the triangle
    ELSE:
        SPLIT trapezoid into two triangles
        RASTERIZE both triangles

    FOR EACH pixel inside a rasterized triangle:
        CALCULATE barycentric coordinates
        INTERPOLATE corresponding 3D position
        ROUND position to nearest voxel
        CHECK volume bounds
        SAMPLE voxel intensity
        STORE intensity and update mask

    NORMALIZE valid intensities to [0, 255]

    SAVE grayscale PNG

PRINT final processing statistics
```

---

## 13. Important Implementation Characteristics

| Characteristic | Current implementation |
|---|---|
| Supported shapes | Isosceles triangles and isosceles trapezoids |
| Input coordinates | Three-dimensional floating-point coordinates |
| Coordinate range | `[0, 1023]` inclusive |
| Triangle validation tolerance | `0.001` |
| Trapezoid coplanarity tolerance | `0.001` |
| Parallelism tolerance | `0.001` |
| Geometric degeneracy tolerance | `1e-12` |
| Rasterization method | Triangle rasterization with barycentric coordinates |
| Quadrilateral triangulation | Diagonal AC |
| Pixel spacing | `1.0` |
| Voxel sampling | Nearest-neighbor |
| Output format | 8-bit grayscale PNG |
| Intensity normalization | Per-image min-max normalization |
| Error handling | One error image per failed plane |
| Progress reporting | Every 25 processed planes |

### 13.1 Computational Complexity

Let:

- `N` be the number of planes in the CSV file.
- `A_i` be the number of candidate pixels examined within the rasterization bounding box of plane `i`.

The dominant computational work is rasterization. Each candidate pixel requires a constant number of geometric calculations and, when inside the triangle and within the volume bounds, a voxel lookup.

Therefore, the overall rasterization cost is approximately:

```text
O(sum(A_i)), for i = 1 ... N
```

The algorithm also performs a linear scan of each output image when calculating its intensity range and another scan when generating the PNG. These operations are proportional to the number of output pixels.

The algorithm does not iterate over the entire 3D volume. It samples only voxels corresponding to pixels covered by the projected planes.

---

## 14. Limitations and Assumptions

The current implementation makes several assumptions worth noting:

1. **Only specific shapes are supported.** Triangles must be isosceles, and quadrilaterals must be isosceles trapezoids.
2. **Quadrilateral vertices must follow perimeter order.** The algorithm assumes the order `A, B, C, D`.
3. **The first three vertices define the plane.** The fourth vertex is checked for coplanarity but does not determine the plane normal.
4. **Voxel sampling uses rounding.** Fractional coordinates are rounded to the nearest voxel rather than interpolated between voxels.
5. **Normalization is performed independently for each plane.** Consequently, identical voxel intensities may map to different grayscale values in different output images.
6. **The image dimensions depend on the projected shape.** They are not fixed to a common resolution.
7. **The output is a planar sampling, not a maximum-intensity projection.** Each output pixel represents one sampled location on the specified geometric plane.
8. **The mask distinguishes valid shape pixels from the background.** However, valid pixels with an intensity of zero are visually indistinguishable from background pixels in the saved grayscale image.
9. **A failure during PNG writing is also caught by the per-plane error handler.** The implementation then attempts to write an error image at the same output path.

These characteristics describe the behavior of the current implementation and should be considered when interpreting the generated projection images.non-negative, apart from small numerical tolerance.

The algorithm uses an epsilon of `1e-9`:

```text
if (wA < -epsilon || wB < -epsilon || wC < -epsilon)
    skip pixel
```

Pixels outside the triangle are ignored.

### 7.4 Interpolating the 3D Position

Once a pixel is determined to be inside the triangle, its corresponding 3D position is calculated using the same barycentric weights.

For the triangle's 3D vertices `A`, `B`, and `C`:

```text
vx = wA * A.x + wB * B.x + wC * C.x
vy = wA * A.y + wB * B.y + wC * C.y
vz = wA * A.z + wB * B.z + wC * C.z
```

This produces a point on the original 3D triangle corresponding to the current 2D pixel.

Because barycentric interpolation is used, the mapping is linear across the triangle.

For a trapezoid, the same process is performed independently for both component triangles.

---

## 8. Sampling the 3D Volume

The interpolated 3D coordinates are generally fractional values.

However, the NRRD volume is a discrete voxel grid, so the algorithm converts them into integer voxel coordinates by rounding each coordinate to the nearest integer:

```text
voxelX = round(vx)
voxelY = round(vy)
voxelZ = round(vz)
```

Before accessing the volume, the algorithm verifies that the coordinates are within its actual dimensions:

```text
0 <= voxelX < volumeWidth
0 <= voxelY < volumeHeight
0 <= voxelZ < volumeDepth
```

If the coordinates are valid, the corresponding voxel value is retrieved using:

```text
volume.getPixelValueUnchecked(voxelX, voxelY, voxelZ)
```

The sampled value is stored in the output matrix, and the corresponding mask entry is set to `true`.

If the voxel coordinates are outside the volume, the pixel remains unchanged and its mask entry remains `false`.

**Important:** The current implementation uses nearest-neighbor voxel sampling. It does not perform trilinear interpolation between neighboring voxels.

---

## 9. Generating the PNG Image

Once rasterization is complete, the algorithm converts the output matrix into a grayscale PNG image.

The image uses an 8-bit grayscale format:

```text
BufferedImage.TYPE_BYTE_GRAY
```

### 9.1 Finding the Intensity Range

The algorithm scans the output matrix and finds the minimum and maximum values among pixels that:

- Are marked as valid by the mask.
- Contain finite values.

Pixels outside the projected shape are excluded from this calculation.

### 9.2 Intensity Normalization

If the minimum and maximum values are finite and different, the algorithm normalizes the sampled values into the range `[0, 255]`.

The normalization formula is:

```text
gray = 255 * (value - min) / (max - min)
```

The result is rounded to the nearest integer and clamped to the valid grayscale range.

This means that:

- The minimum sampled intensity becomes black (`0`).
- The maximum sampled intensity becomes white (`255`).
- Intermediate values are mapped proportionally between them.

If the image has no valid finite samples, or all valid samples have the same intensity, normalization is disabled. In that case, all pixels remain black.

### 9.3 Pixels Outside the Shape

Pixels whose mask entries are `false` are assigned grayscale value `0`.

Consequently, the area outside the projected triangle or trapezoid appears black.

The resulting image is written as a PNG file.

---

## 10. Error Handling

The algorithm is designed to process multiple planes without allowing one invalid plane to terminate the entire operation.

Each plane is processed independently inside a `try-catch` block.

If an exception occurs during parsing, validation, projection, sampling, or PNG generation:

1. The plane is counted as failed.
2. The error message is extracted.
3. An error is printed to the standard error stream.
4. A dedicated error PNG is generated in place of the normal projection.

### 10.1 Error Image Format

Error images have a fixed size of `512 × 512` pixels.

They contain:

- A black background.
- A white heading identifying the failed plane number.
- The error message, wrapped into lines.
- A message indicating that the plane was skipped.

This allows the output sequence to retain one image per processed plane, including failed planes.

The error image itself is also saved using the normal projection filename:

```text
projection_<planeNumber>.png
```

---

## 11. Processing the CSV File

The public entry point is:

```text
projectCsv(volume, csvPath, outputDirectory)
```

The method performs the following operations:

1. Creates the output directory if it does not already exist.
2. Reads all lines from the CSV file.
3. Initializes counters for processed, successful, and failed planes.
4. Starts a timer using `System.nanoTime()`.
5. Iterates through the CSV lines.
6. Skips empty lines and comment lines.
7. Assigns each processed plane a sequential number.
8. Parses, validates, projects, and saves the plane.
9. Generates an error image if processing fails.
10. Prints progress information every 25 processed planes.
11. Prints a final processing summary.

### 11.1 Output Naming

Each processed plane receives a sequential index beginning at zero:

```text
projection_0.png
projection_1.png
projection_2.png
...
```

Empty lines and comment lines do not increment the plane counter.

Failed planes still receive an index and produce an error image.

### 11.2 Performance Reporting

The algorithm reports progress every 25 processed planes.

The reported throughput is calculated as:

```text
planesPerSecond = processedPlanes / elapsedSeconds
```

At the end, the algorithm reports:

- Total number of processed planes.
- Number of successful planes.
- Number of failed planes.
- Total elapsed time.
- Average processing throughput.

---

## 12. Algorithm Summary

The complete algorithm can be summarized as follows:

```text
INPUT:
    3D NRRD volume
    CSV file containing triangle and trapezoid definitions
    Output directory

CREATE output directory

FOR EACH non-empty, non-comment CSV line:

    PARSE vertices

    IF parsing fails:
        GENERATE error image
        CONTINUE

    VALIDATE geometry:
        Check coordinate ranges
        Check number of vertices
        Check coincident vertices
        Check collinearity
        Check isosceles triangle or trapezoid properties
        Check trapezoid coplanarity

    IF validation fails:
        GENERATE error image
        CONTINUE

    DETERMINE shape orientation:
        Triangle: identify base and apex
        Trapezoid: identify longer parallel base

    CONSTRUCT local 2D coordinate system:
        Calculate horizontal axis u
        Calculate vertical axis v
        Orient v toward the apex or opposite base

    PROJECT vertices into 2D

    CALCULATE output image dimensions

    INITIALIZE output matrix and mask

    IF triangle:
        RASTERIZE the triangle
    ELSE:
        SPLIT trapezoid into two triangles
        RASTERIZE both triangles

    FOR EACH pixel inside a rasterized triangle:
        CALCULATE barycentric coordinates
        INTERPOLATE corresponding 3D position
        ROUND position to nearest voxel
        CHECK volume bounds
        SAMPLE voxel intensity
        STORE intensity and update mask

    NORMALIZE valid intensities to [0, 255]

    SAVE grayscale PNG

PRINT final processing statistics
```

---

## 13. Important Implementation Characteristics

| Characteristic | Current implementation |
|---|---|
| Supported shapes | Isosceles triangles and isosceles trapezoids |
| Input coordinates | Three-dimensional floating-point coordinates |
| Coordinate range | `[0, 1023]` inclusive |
| Triangle validation tolerance | `0.001` |
| Trapezoid coplanarity tolerance | `0.001` |
| Parallelism tolerance | `0.001` |
| Geometric degeneracy tolerance | `1e-12` |
| Rasterization method | Triangle rasterization with barycentric coordinates |
| Quadrilateral triangulation | Diagonal AC |
| Pixel spacing | `1.0` |
| Voxel sampling | Nearest-neighbor |
| Output format | 8-bit grayscale PNG |
| Intensity normalization | Per-image min-max normalization |
| Error handling | One error image per failed plane |
| Progress reporting | Every 25 processed planes |

### 13.1 Computational Complexity

Let:

- `N` be the number of planes in the CSV file.
- `A_i` be the number of candidate pixels examined within the rasterization bounding box of plane `i`.

The dominant computational work is rasterization. Each candidate pixel requires a constant number of geometric calculations and, when inside the triangle and within the volume bounds, a voxel lookup.

Therefore, the overall rasterization cost is approximately:

```text
O(sum(A_i)), for i = 1 ... N
```

The algorithm also performs a linear scan of each output image when calculating its intensity range and another scan when generating the PNG. These operations are proportional to the number of output pixels.

The algorithm does not iterate over the entire 3D volume. It samples only voxels corresponding to pixels covered by the projected planes.

---

## 14. Limitations and Assumptions

The current implementation makes several assumptions worth noting:

1. **Only specific shapes are supported.** Triangles must be isosceles, and quadrilaterals must be isosceles trapezoids.
2. **Quadrilateral vertices must follow perimeter order.** The algorithm assumes the order `A, B, C, D`.
3. **The first three vertices define the plane.** The fourth vertex is checked for coplanarity but does not determine the plane normal.
4. **Voxel sampling uses rounding.** Fractional coordinates are rounded to the nearest voxel rather than interpolated between voxels.
5. **Normalization is performed independently for each plane.** Consequently, identical voxel intensities may map to different grayscale values in different output images.
6. **The image dimensions depend on the projected shape.** They are not fixed to a common resolution.
7. **The output is a planar sampling, not a maximum-intensity projection.** Each output pixel represents one sampled location on the specified geometric plane.
8. **The mask distinguishes valid shape pixels from the background.** However, valid pixels with an intensity of zero are visually indistinguishable from background pixels in the saved grayscale image.
9. **A failure during PNG writing is also caught by the per-plane error handler.** The implementation then attempts to write an error image at the same output path.

These characteristics describe the behavior of the current implementation and should be considered when interpreting the generated projection images.