<!-->Used AI to format it better<!-->

# Concurrency Architecture and Testing

## Threads and Responsibilities

The video player uses multiple threads to separate different tasks and improve performance.

- **JavaFX thread:** Handles the GUI, keyboard and mouse controls, playback state and displaying frames. Video decoding and frame effects are not performed on this thread.
- **Decoder thread (video-decoder):** Uses FFmpeg to read and decode video frames. It also handles opening, seeking and closing the video. Each frame is cloned before being sent to a processing worker to prevent conflicts between threads.
- **Processing threads (frame-processor):** Uses an ExecutorService to process multiple frames concurrently. Each worker has its own set of frame processors to prevent shared data from being modified by different threads. Effects are still applied in the order specified by the user.
- **Audio thread (audio-output):** Handles audio playback separately from video processing. When playback finishes normally, it allows the remaining audio to finish playing before closing. When seeking or stopping, the remaining audio is discarded instead.
- **Lifecycle thread (playback-lifecycle):** Handles closing the previous playback session before starting another one. This prevents different sessions from accessing the same resources and avoids blocking the JavaFX thread during cleanup.

## Shared Resources and Synchronization

Since multiple threads are running at the same time, some resources need to be protected to prevent race conditions.

- **FFmpegFrameGrabber:** Only the decoder thread can access it. This prevents different threads from trying to read or modify the video decoder at the same time.
- **Video frames:** Each decoded frame is cloned before being passed to a processing worker. This prevents workers from modifying data that FFmpeg may still be using.
- **Frame processors:** Each worker has its own processor instances because some effects store temporary data that changes during processing.
- **FrameSlots:** Uses a Semaphore to limit the number of frames being processed or waiting for display. Each permit can only be released once to prevent incorrect permit counts.
- **FrameOrderBuffer:** Uses ConcurrentHashMap to store completed frames according to their sequence numbers. This allows workers to finish in any order while ensuring frames are displayed in the correct order.
- **Audio queue:** Uses ArrayBlockingQueue to pass decoded audio from the decoder thread to the audio thread. The queue has a fixed capacity to prevent excessive memory usage.
- **Session variables:** Volatile variables are used for important states such as cancellation, completion, errors and timing so changes made by one thread are visible to other threads.
- **Session tracking:** The session and requestVersion variables help prevent outdated playback requests from interfering with the current session.
- **Session cleanup:** A single-threaded executor manages cleanup so that a new playback session does not start before the previous session has closed.

FrameSlots also uses AtomicBoolean.compareAndSet() to ensure each permit is released only once. This is important because frame display and cancellation can happen at the same time.

Workers also release their resources in finally blocks, including when processing is cancelled.

## Seeking and Cancellation

Seeking and stopping need to be handled carefully because multiple threads may still be processing frames from the previous playback position.

When the user seeks or stops the video:

1. The JavaFX thread stops the playback timer and cancels the current session without waiting for the background threads.
2. The lifecycle thread closes the old audio output and waits for the decoder, processing workers and audio thread to finish.
3. Once the previous session has closed, the new playback session can start from the requested position.
4. The requestVersion variable is checked to make sure an older request cannot restart playback after a newer request has been made.

If the previous session cannot close properly, the player reports an error rather than starting another session that could conflict with it.

This approach helps prevent race conditions and keeps the GUI responsive during seeking and stopping.

## Audio Handling

Audio is handled separately from video processing so it does not slow down frame rendering.

When the --audio option is not used, the player does not initialise the audio device. Video frames are read using grabImage(), so audio hardware is not required.

If audio is enabled but the audio device cannot be opened, the video should continue playing without sound.

When audio is enabled during video-only playback, the player starts a new decoding session from the current position so that audio packets can be read.

When audio is disabled, any pending audio data is cleared.

If playback reaches the end normally, the audio device is allowed to finish playing its remaining buffered samples before closing. However, when the user seeks or stops playback, the buffered audio is discarded to avoid playing sound from the previous position.

## Testing

JUnit tests are used to check the main components of the multithreaded implementation.

These tests cover command-line parsing, frame ordering, deterministic effects, safe management of frame-processing slots and audio completion.

However, automated tests alone are not enough to verify the complete video player. The following features should also be tested manually:

- Playing a video without effects and with multiple effects.
- Playing videos with audio enabled and disabled.
- Enabling audio after starting video-only playback.
- Pausing and resuming playback.
- Seeking forward and backward while playing or paused.
- Repeatedly seeking, restarting and pausing to check for race conditions.
- Playing until the end to check that the final audio samples are not cut off.
- Closing and reopening the player to check for leftover background threads.
- Resizing and maximising the window.
- Testing all keyboard controls and tooltips.
- Playing video when no audio device is available.
- Using the -n option to display frame numbers and checking that frames remain in the correct order.

Thread cleanup can also be checked by making sure no unexpected video-decoder, frame-processor or audio-output threads remain after playback stops.

## Performance Comparison

The main goal of the multithreaded implementation is to improve playback performance when multiple effects are applied.

To measure this improvement, the modified player should be compared with the original single-threaded player.

Both versions should be tested using the same video, effects, hardware and display settings to make the comparison fair.

Each test should be repeated at least three times to get more reliable results.

The following should be recorded:

- Video resolution and frame rate.
- Effects used and the order they are applied.
- Average frame headroom.
- Number of frames that were processed late.
- Audio interruptions or synchronization problems.
- Responsiveness when pausing, seeking and resizing.
- CPU utilization during playback.

The frame-headroom logs can be compared using:

`python3 scripts/compare_headroom.py baseline.log multithreaded.log`

This script calculates the average recorded frame headroom and the frequency of late frames.

The FrameProcessingBenchmarkTest also compares serial and parallel frame processing using generated frames. However, this only measures the processing effects and does not include video decoding, audio or GUI performance.

Therefore, actual playback testing is still needed to determine whether the multithreaded player performs better overall.

## Performance Results

The following results should be completed after testing both versions.

Video resolution and frame rate:
- Single-threaded: Not measured
- Multithreaded: Not measured

Effects used:
- Single-threaded: Not recorded
- Multithreaded: Not recorded

Average frame headroom:
- Single-threaded: Not measured
- Multithreaded: Not measured

Number of late frames:
- Single-threaded: Not measured
- Multithreaded: Not measured

Audio interruptions:
- Single-threaded: Not tested
- Multithreaded: Not tested

GUI responsiveness:
- Single-threaded: Not tested
- Multithreaded: Not tested

Maximum CPU utilization:
- Single-threaded: Not measured
- Multithreaded: Not measured

## Limitations

Although multithreading allows the player to use multiple CPU cores, it does not guarantee better performance in every situation.

Cloning frames and transferring them between threads requires additional memory and processing time. For videos with only a few simple effects, this overhead may reduce the benefit of parallel processing.

Some JavaFX and JavaCV operations may also behave differently depending on the operating system, so actual video playback needs to be tested.

Enabling audio during playback can cause a short interruption because decoding needs to restart from the current position.

FFmpeg may also take some time to close its resources, especially when a thread is interrupted. The lifecycle thread prevents this from blocking the GUI, but playback may fail to restart if the previous session cannot close within the configured timeout.

The time required to drain or close an audio device can also vary depending on the hardware and drivers.

## Conclusion

The multithreaded design separates decoding, frame processing, audio and GUI operations into different threads.

This allows multiple video frames to be processed concurrently while keeping the JavaFX thread available for user interactions.

Synchronization techniques such as Semaphores, ConcurrentHashMap, AtomicBoolean, volatile variables and bounded queues are used to manage shared resources and reduce the possibility of race conditions.

Frame ordering and session management are also handled separately to prevent incorrectly ordered frames and conflicts between playback sessions.

The implementation is intended to improve performance and responsiveness while maintaining the original player's functionality. However, the actual improvement must be confirmed by comparing both versions using real video playback and recorded results.