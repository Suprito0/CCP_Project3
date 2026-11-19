/*
 * Starter code supplied for Adelaide University COMP3011 Assignment 2.
 * Students are free to modify this file for assessment purposes.
 * 
 * Authors:
 *   1. Simon Ratcliffe, in collaboration with GPT-5.6 Terra
 *   2. Suprito Iqbal, a1950138
 *
 * Copyright 2026 Simon Ratcliffe
 */
package comp3011;

import java.io.File;
import java.util.List;
import java.util.function.BiConsumer;

import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

/**
 * The controller part of the video player model-view-controller architecture.
 * 
 * <p>
 * Wires up events from the {@link VideoPlayerView} to the
 * {@link VideoPlayerModel}.
 * This includes button actions, keyboard shortcuts and display resizing.
 * </p>
 */
public class VideoPlayerController {
    // The model handles playback; the view handles display and buttons.
    private final VideoPlayerModel model;
    private final boolean subtitlesRequested;
    private boolean subtitlesEnabled;
    private List<Subtitle> subtitles = List.of();
    private final VideoPlayerView view;
    private final BiConsumer<Integer, Integer> videoSizeChangedHandler;

    // Creates the view and model, then connects buttons and callbacks.
    public VideoPlayerController(
            boolean audioEnabled,
            List<String> effectNames,
            boolean subtitlesRequested,
            BiConsumer<Integer, Integer> videoSizeChangedHandler) {
        this.view = new VideoPlayerView();
        this.subtitlesRequested = subtitlesRequested;
        this.subtitlesEnabled = subtitlesRequested;
        this.videoSizeChangedHandler = videoSizeChangedHandler;
        model = new VideoPlayerModel(
                audioEnabled,
                effectNames,
                this::onVideoSizeChanged,
                this::onFrameReady,
                this::onStatusChanged,
                this::onPlaybackStateChanged,
                this::onAudioOutputStateChanged);

        // Connect each button to the corresponding controller method.
        view.getStartOverButton().setOnAction(_ -> startOver());
        view.getBackFiveButton().setOnAction(_ -> backFiveSeconds());
        view.getPausePlayButton().setOnAction(_ -> togglePause());
        view.getForwardFiveButton().setOnAction(_ -> forwardFiveSeconds());
        view.getStopButton().setOnAction(_ -> stopAndExit());
        view.getAudioButton().setOnAction(_ -> toggleAudioOutput());
        view.getSubtitleButton().setOnAction(_ -> toggleSubtitles());
        view.showSubtitlesEnabled(subtitlesEnabled);
        view.showAudioEnabled(model.isAudioOutputEnabled());
    }

    // Connects keys to actions such as pause, seek, stop and mute.
    public void installKeyboardShortcuts(Scene scene) {
        scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                stopAndExit();
                event.consume();
            } else if (event.getCode() == KeyCode.SPACE) {
                togglePause();
                event.consume();
            } else if (event.getCode() == KeyCode.LEFT) {
                backFiveSeconds();
                event.consume();
            } else if (event.getCode() == KeyCode.RIGHT) {
                forwardFiveSeconds();
                event.consume();
            } else if (event.getCode() == KeyCode.HOME) {
                startOver();
                event.consume();
            } else if (event.getCode() == KeyCode.S && subtitlesRequested) {
                toggleSubtitles();
                event.consume();
            } else if (event.getCode() == KeyCode.A) {
                toggleAudioOutput();
                event.consume();
            }
        });
    }

    // Gives VideoPlayerApp access to the visual layout.
    public VideoPlayerView getView() {
        return view;
    }

    // Enables the controls and tells the model to open the video.
    public void play(File file) {
        view.setPlaybackControlsDisabled(false);
        view.setSubtitlesAvailable(subtitlesRequested);
        model.play(file);
        // Temporary sample while checking the button, keyboard shortcut and timing.
        // The next step will replace this with actual speech recognition.
        if (subtitlesRequested) {
            setSubtitles(List.of(
                    new Subtitle(0, 3, "Subtitle test: first line"),
                    new Subtitle(3, 6, "Subtitle test: second line")));
        }
    }

    // Asks the model to release its resources.
    public void shutdown() {
        model.shutdown();
    }

    private void toggleSubtitles() {
        if (!subtitlesRequested) {
            return;
        }
        subtitlesEnabled = !subtitlesEnabled;
        view.showSubtitlesEnabled(subtitlesEnabled);
        updateSubtitle();
    }

    // Sets a new list of subtitles when they have been retrieved.
    public void setSubtitles(List<Subtitle> newSubtitles) {
        subtitles = List.copyOf(newSubtitles);
        updateSubtitle();
    }

    private void updateSubtitle() {
        if (!subtitlesEnabled) {
            view.setSubtitleText("");
            return;
        }
        view.setSubtitleText(Subtitle.findText(subtitles, model.getCurrentTimestampUs() / 1_000_000.0));
    }

    // Requests a seek back to the beginning.
    private void startOver() {
        model.startOver();
    }

    // Requests a five-second rewind.
    private void backFiveSeconds() {
        model.backFiveSeconds();
    }

    // Switches between paused and playing states.
    private void togglePause() {
        model.togglePause();
    }

    // Requests a five-second skip forward.
    private void forwardFiveSeconds() {
        model.forwardFiveSeconds();
    }

    // Switches audio playback on or off.
    private void toggleAudioOutput() {
        model.toggleAudioOutput();
    }

    // Stops playback and closes the JavaFX application.
    private void stopAndExit() {
        model.stopPlayback();
        javafx.application.Platform.exit();
    }

    // Passes the decoded video size back to the window.
    private void onVideoSizeChanged(int width, int height) {
        videoSizeChangedHandler.accept(width, height);
    }

    // Displays an image supplied by the model.
    private void onFrameReady(Image image) {
        view.setImage(image);
        updateSubtitle();
    }

    // Updates the status text shown in the controls.
    private void onStatusChanged(String status) {
        view.setStatus(status);
    }

    // Changes the play/pause icon to match the current state.
    private void onPlaybackStateChanged(boolean playing, boolean paused) {
        if (playing && !paused) {
            view.showPauseAction();
        } else {
            view.showPlayAction();
        }
    }

    // Updates the audio icon to match the mute setting.
    private void onAudioOutputStateChanged(boolean audioOutputEnabled) {
        view.showAudioEnabled(audioOutputEnabled);
    }
}