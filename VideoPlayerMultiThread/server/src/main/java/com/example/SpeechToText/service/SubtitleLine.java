package com.example.SpeechToText.service;

// One subtitle line, using seconds from the start of the audio chunk.
public record SubtitleLine(double start, double end, String text) {
}