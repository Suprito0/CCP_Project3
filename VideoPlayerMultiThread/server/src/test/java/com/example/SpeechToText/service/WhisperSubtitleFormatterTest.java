package com.example.SpeechToText.service;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

// Check subtitle timing and noise filtering without an OpenAI API key.
class WhisperSubtitleFormatterTest {
    private Map<String, Object> word(double start, double end, String text) {
        return Map.of("start", start, "end", end, "word", text);
    }

    private Map<String, Object> segment(double noSpeech, double logProbability) {
        return Map.of("start", 0.0, "end", 10.0,
                "no_speech_prob", noSpeech, "avg_logprob", logProbability);
    }

    @Test
    void keepsNormalSpeech() {
        Map<String, Object> response = Map.of(
                "words", List.of(word(1, 1.4, "Hello"), word(1.5, 2, "world.")),
                "segments", List.of(segment(0.1, -0.2)));

        List<SubtitleLine> result = WhisperSubtitleFormatter.format(response);
        assertEquals(1, result.size());
        assertEquals("Hello world.", result.get(0).text());
        assertEquals(1, result.get(0).start());
        assertEquals(2, result.get(0).end());
    }

    @Test
    void removesNoiseOnlyWhenBothThresholdsAreMet() {
        Map<String, Object> noise = Map.of(
                "words", List.of(word(1, 2, "Noise")),
                "segments", List.of(segment(0.9, -1.5)));
        assertTrue(WhisperSubtitleFormatter.format(noise).isEmpty());

        Map<String, Object> speech = Map.of(
                "words", List.of(word(1, 2, "Speech")),
                "segments", List.of(segment(0.9, -0.5)));
        assertFalse(WhisperSubtitleFormatter.format(speech).isEmpty());
    }

    @Test
    void breaksLongSubtitlesIntoShortLines() {
        Map<String, Object> response = Map.of("words", List.of(
                word(0, 1, "This"), word(1, 2, "is"),
                word(2, 3, "a"), word(3, 4, "sample"),
                word(4, 5, "with"), word(5, 6, "a"),
                word(6, 8, "very"), word(8, 9, "long"),
                word(9, 10, "sentence.")));

        List<SubtitleLine> result = WhisperSubtitleFormatter.format(response);
        assertTrue(result.size() > 1);
        for (SubtitleLine line : result) {
            assertTrue(line.end() - line.start() <= 7);
            assertTrue(line.end() - line.start() >= 1);
            assertTrue(line.text().length() <= 42);
        }
    }
}