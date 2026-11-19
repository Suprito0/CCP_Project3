package comp3011;

import java.util.List;

// Stores a subtitle and the part of the video where it should appear.
public record Subtitle(double start, double end, String text) {

    // Use the video timestamp instead of a separate timer so seeking also works.
    public static String findText(List<Subtitle> subtitles, double seconds) {
        for (Subtitle subtitle : subtitles) {
            if (seconds >= subtitle.start && seconds < subtitle.end) {
                return subtitle.text;
            }
        }
        return "";
    }
}