package com.example.SpeechToText.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.SpeechToText.service.SpeechToTextException;
import com.example.SpeechToText.service.WhisperSubtitleService;

// Assignment 3 endpoints alongside Assignment 1's existing /transcribe endpoint.
@RestController
@RequestMapping("/api/v1")
public class SubtitleController {

    private final WhisperSubtitleService whisper;

    public SubtitleController(WhisperSubtitleService whisper) {
        this.whisper = whisper;
    }

    @PostMapping("/subtitles")
    public ResponseEntity<?> subtitles(@RequestParam("audio") MultipartFile audio) {
        try {
            return ResponseEntity.ok(whisper.transcribe(audio));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
        } catch (SpeechToTextException exception) {
            return ResponseEntity.status(exception.status())
                    .body(Map.of("error", exception.getMessage()));
        } catch (Exception exception) {
            // Do not return provider internals or credentials to the Java player.
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Subtitle generation failed"));
        }
    }

    @GetMapping("/global/usage")
    public Map<String, Double> usage() {
        return Map.of("usage", whisper.getUsageSeconds());
    }
}