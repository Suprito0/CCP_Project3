<!-->Used AI to format it better<!-->
# COMP3011 Assignment 2 - Multithreaded Video Player

This project is based on the single-threaded video player provided for COMP3011 Assignment 2. The original code was developed by Simon Ratcliffe with the assistance of GPT-5.6 Terra.

The project has been modified to support command-line frame effects and multithreaded video processing. AI assistance was also used during development and testing. Changes to the original code are identified in the source files.

## Requirements

- Java JDK 25
- Apache Maven 3.9 or newer
- VS Code or another Java IDE
- A graphical desktop environment that supports JavaFX
- A supported video file, such as MP4
- An audio output device if audio playback is required

Open the project folder containing `pom.xml` in VS Code.

## Building and Testing

The project uses Maven to manage dependencies, compile the code and run tests.

Run the following commands in the project directory:

```bash
mvn validate
mvn compile
mvn package
mvn verify
```

These commands validate the project, compile the Java files, package the application and run the automated tests.

## Running the Application

Use the following commands to run the video player:

```bash
mvn compile javafx:run '-Djavafx.args=--help'
mvn compile javafx:run '-Djavafx.args=sample.mp4'
mvn compile javafx:run '-Djavafx.args=--audio -jfdb sample.mp4'
```

Replace `sample.mp4` with the name or path of the video you want to play.

The player supports different visual effects that can be selected through command-line arguments. Multiple effects can be used together, repeated and combined using short or long options.

For example, `-jfd` applies jitter, flicker and dust in that order, while `-jj` applies jitter twice.

The full list of available options can be viewed using `--help`.

### Playback Controls

- **Space:** Play or pause the video
- **Left Arrow:** Rewind 5 seconds
- **Right Arrow:** Forward 5 seconds
- **Home:** Restart the video
- **A:** Enable or disable audio
- **Escape:** Stop playback and close the player

The application also supports window resizing and maximising.

## Multithreading Implementation

The original video player performed decoding, frame processing and GUI updates on the same thread. This caused playback to slow down when multiple effects were applied.

The modified player uses multiple threads to separate these tasks and improve performance.

- **JavaFX thread:** Handles the GUI, displays frames and responds to user input.
- **Decoder thread:** Reads and decodes frames using FFmpeg.
- **Processing threads:** Apply visual effects to different frames concurrently using an `ExecutorService`.
- **Audio thread:** Handles audio output separately from video processing.
- **Lifecycle thread:** Handles the closing and restarting of playback sessions.

Each processing worker has its own set of frame processors, and frames are cloned before being passed to workers to prevent different threads from modifying the same frame.

`FrameOrderBuffer` is used to store completed frames and make sure they are displayed in the correct order, even when processing finishes out of order.

`FrameSlots` limits the number of frames being processed or waiting for display to prevent excessive memory usage.

When seeking or restarting playback, the previous session is cancelled and cleaned up before a new session starts. This helps prevent conflicts between different playback sessions.

More information about the concurrency design and synchronization is available in [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Testing

JUnit tests have been added to check the functionality and correctness of the modified player.

The tests cover:

- Command-line argument parsing and effect ordering
- Consistency of frame effects across repeated runs
- Correct frame ordering when using multiple threads
- Safe management of frame-processing slots
- Audio completion and cancellation
- Comparison of single-threaded and multithreaded frame processing

The performance benchmark compares serial and parallel processing using generated frames. However, this only measures frame processing and does not represent the performance of the entire video player.

To evaluate the actual performance improvement, the original single-threaded player should be compared with the modified player using the same video, effects and hardware.

The comparison should include at least three runs and record:

- Frame headroom and the number of late frames
- Video playback smoothness
- GUI responsiveness
- Audio interruptions or synchronization problems
- CPU usage

The results can be compared using:

```bash
python3 scripts/compare_headroom.py baseline.log multithreaded.log
```

This script calculates the average recorded frame headroom and how often frames were late. It does not measure audio quality or the actual displayed frame rate.

The baseline player should support the same command-line effects to make the comparison fair.

## Known Limitations

Although multithreading allows the player to use multiple CPU cores, using too many effects can still cause playback to fall behind.

Enabling audio during video-only playback requires restarting decoding from the current position, which may cause a short interruption.

Closing FFmpeg and audio resources can also take some time. A separate lifecycle thread handles this to prevent the GUI from freezing. A new session will not start if the previous one fails to close within the configured timeout.

The complete video player has not been tested in the AI assistant's environment using Java 25 and real video/audio playback. These features still need to be verified on the target computer.

## Conclusion

The main goal of this project is to improve the performance and responsiveness of the original video player by introducing multithreading while maintaining its existing functionality.

The changes allow multiple frames to be processed concurrently, keep frame ordering consistent and reduce the amount of work performed on the JavaFX thread.

The actual performance improvements will need to be confirmed through testing and comparison with the original single-threaded implementation.