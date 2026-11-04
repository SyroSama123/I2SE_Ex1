# Experiment Documentation

## 1. Objective

The purpose of this experiment was to evaluate the functionality, robustness, and runtime performance of the implemented 3D volume plane-projection application.

The experiment was designed to test the complete execution pipeline, starting with loading a 3D medical image, processing a collection of plane definitions, generating 2D projection images, and combining those images into an MP4 video.

An additional objective was to examine how the application handles invalid input data without preventing the processing of other valid plane definitions.

## 2. Test Environment and Input Data

The experiment was performed using the provided demo files.

The following files were used:

| File | Description |
|---|---|
| `I2SE_Ex1_Demo.jar` | Executable Java application |
| `MRHead_1.nrrd` | 3D head MRI volume |
| `planes_400_test.csv` | CSV file containing 400 plane definitions |

The CSV file contained a mixture of valid and intentionally invalid plane definitions. This was intended to test both the normal projection workflow and the application's input validation and error-handling mechanisms.

### Execution command

The experiment was executed using the following command:

```bash
java -jar I2SE_Ex1_Demo.jar MRHead_1.nrrd planes_400_test.csv /home/boazcg/IdeaProjects/I2SE_Ex1/Experiment/experiment_output.mp4
```

The application generated the projection frames in its output frames directory and saved the final video to:

```text
/home/boazcg/IdeaProjects/I2SE_Ex1/Experiment/experiment_output.mp4
```

## 3. Experimental Procedure

The experiment followed the application's normal execution pipeline:

1. **Volume loading:** The application loaded the supplied NRRD file containing the head MRI volume.
2. **CSV processing:** The application read the 400 plane definitions from the CSV file.
3. **Input validation:** Each plane definition was processed and checked against the geometric and coordinate requirements of the projection algorithm.
4. **Projection generation:** Valid plane definitions were used to generate 2D grayscale projection images. Invalid definitions were handled through the application's error-handling mechanism.
5. **Video generation:** The generated PNG images were combined into an MP4 video using FFmpeg, with a frame rate of 50 FPS.

## 4. Results

### 4.1 Execution Results

The application completed the video-generation stage successfully.

The following results were obtained:

| Metric | Result |
|---|---:|
| Number of CSV entries | 400 |
| Number of video frames | 400 |
| Image generation rate | Approximately 105 images/second |
| Video frame rate | 50 FPS |
| Video duration | 8 seconds |
| Video encoding time | Approximately 3.79 seconds |
| Output video size | Approximately 4.72 MiB |
| FFmpeg exit status | Successful |

The resulting video was saved as `experiment_output.mp4` at the specified output location.

### 4.2 Projection Generation

The experiment achieved an image generation rate of approximately **105 images per second**.

The video-generation log confirms that FFmpeg processed 400 frames.

The input images had varying dimensions, indicating that the generated projections were not all restricted to a single original image size.

During video encoding, FFmpeg applied the configured video filter:

- Resized each frame to fit within a 512 × 512 resolution while preserving its aspect ratio.
- Added padding where necessary to produce a consistent output resolution.
- Set the sample aspect ratio to 1:1.
- Converted the frames to the `yuv420p` pixel format for video compatibility.

Consequently, the resulting MP4 video has a consistent 512 × 512 frame resolution, regardless of the original dimensions of individual projections.

### 4.3 Invalid Input Handling

The CSV file deliberately included invalid plane definitions to test the robustness of the application.

The intended behavior is that an invalid plane should not prevent the processing of subsequent plane definitions. Instead, the application should report the error and generate an error frame for the corresponding entry.

The experiment produced a video containing 400 frames, demonstrating that the video-generation stage successfully processed the complete set of generated frame files.

**Note:** The supplied execution log contains the FFmpeg output but does not include the projection algorithm's final success/failure summary. Therefore, the exact number of valid and invalid plane definitions processed cannot be established from the available results alone.

## 5. Performance Evaluation

The experiment achieved an image generation rate of approximately **105 images per second**.

The video contained 400 frames at 50 FPS, resulting in a total playback duration of:

400/50 = 8 seconds

FFmpeg completed the encoding process in approximately 3.79 seconds.

The image generation rate of approximately 105 images per second indicates that the application can generate projection images at a rate more than twice the video's playback frame rate of 50 FPS.

It is important to distinguish the image generation rate from the video-encoding time: the former describes projection generation performance, while the latter describes the time FFmpeg took to combine the generated frames into a video.

## 6. Conclusion

The experiment demonstrated that the application can successfully execute the video-generation stage using the provided demo dataset.

A collection of 400 projection frames was successfully encoded into an 8-second MP4 video at 50 FPS. The image generation rate reached approximately 105 images per second, and FFmpeg completed the encoding process in approximately 3.79 seconds.

The use of a CSV file containing both valid and intentionally invalid plane definitions also provided a practical test case for evaluating input validation and error handling.

Overall, the experiment provides evidence that the application can process the supplied demo dataset and generate the expected video output while achieving a projection generation rate of approximately 105 images per second. A more detailed evaluation of projection correctness and invalid-input handling would require examining the generated frames and the projection algorithm's success/failure summary.
