package com.example.SpeechToText.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// Converts the word timestamps from Whisper into short subtitle lines.
public class WhisperSubtitleFormatter {

    private static final int MAX_CHARACTERS = 42;

    private WhisperSubtitleFormatter() {
    }

    public static List<SubtitleLine> format(Map<String, Object> response) {
        List<SubtitleLine> result = new ArrayList<>();
        Object wordsValue = response.get("words");
        if (!(wordsValue instanceof List<?> words)) {
            return result;
        }

        StringBuilder current = new StringBuilder();
        double start = -1;
        double end = 0;

        for (Object item : words) {
            if (!(item instanceof Map<?, ?> word)) {
                continue;
            }

            double wordStart = number(word.get("start"), 0);
            double wordEnd = number(word.get("end"), wordStart);
            String wordText = String.valueOf(word.get("word") == null ? "" : word.get("word")).trim();

            if (wordText.isEmpty() || !allowed(response.get("segments"), wordStart)) {
                continue;
            }

            // Break a line at a pause, at the limit, or after about seven seconds.
            if (!current.isEmpty() && (wordStart - end > 0.7
                    || wordEnd - start >= 7
                    || current.length() + wordText.length() + 1 > MAX_CHARACTERS)) {
                add(result, start, end, current.toString());
                current.setLength(0);
            }

            if (current.isEmpty()) {
                start = wordStart;
            } else {
                current.append(' ');
            }
            current.append(wordText);
            end = wordEnd;

            // A sentence ending is usually a good time to change subtitle.
            if (wordText.matches(".*[.!?]$")) {
                add(result, start, end, current.toString());
                current.setLength(0);
            }
        }

        if (!current.isEmpty()) {
            add(result, start, end, current.toString());
        }
        return result;
    }

    private static void add(List<SubtitleLine> lines, double start, double end, String text) {
        if (start >= 0 && !text.isBlank()) {
            // Keep captions visible for 1-7 seconds (assignment guideline).
            double displayEnd = Math.min(start + 7, Math.max(start + 1, end));
            lines.add(new SubtitleLine(start, displayEnd, text));
        }
    }

    // Ignore a word only when its segment meets BOTH assignment thresholds.
    private static boolean allowed(Object segmentsValue, double time) {
        if (!(segmentsValue instanceof List<?> segments)) {
            return true;
        }
        for (Object item : segments) {
            if (!(item instanceof Map<?, ?> segment)) {
                continue;
            }
            double start = number(segment.get("start"), -1);
            double end = number(segment.get("end"), -1);
            if (time >= start - 0.05 && time < end + 0.05) {
                double noSpeech = number(segment.get("no_speech_prob"), 0);
                double avgLog = number(segment.get("avg_logprob"), 0);
                return !(noSpeech > 0.6 && avgLog < -1.0);
            }
        }
        return true;
    }

    private static double number(Object value, double fallback) {
        return value instanceof Number n ? n.doubleValue() : fallback;
    }
}