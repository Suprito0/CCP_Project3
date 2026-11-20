package com.example.SpeechToText.service;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.multipart.MultipartFile;

// Uses the RestClient from Assignment 1 to request Whisper timestamps.
@Service
public class WhisperSubtitleService {

    private final RestClient restClient;
    private final String apiKey;
    private final AtomicLong totalMilliseconds = new AtomicLong();

    // Store subtitles by the contents of each audio chunk, not its filename.
    // ConcurrentHashMap also avoids sending the same chunk twice at once.
    private final Map<String, List<SubtitleLine>> cache = new ConcurrentHashMap<>();

    public WhisperSubtitleService(
            RestClient openAiRestClient,
            @Value("${openai.api.key:}") String apiKey) {
        this.restClient = openAiRestClient;
        this.apiKey = apiKey;
    }

    public List<SubtitleLine> transcribe(MultipartFile file) {
        AudioFileSupport.validate(file);

        if (apiKey == null || apiKey.isBlank()) {
            throw new SpeechToTextException(HttpStatus.SERVICE_UNAVAILABLE,
                    "OPENAI_API_KEY is not configured");
        }

        try {
            byte[] bytes = file.getBytes();
            String key = audioHash(bytes);

            // For a new chunk, call Whisper and save the response.
            // For an existing chunk, return the saved subtitles immediately.
            // computeIfAbsent only stores results when the request succeeds.
            return cache.computeIfAbsent(key, unused -> requestWhisper(file, bytes));
        } catch (IOException exception) {
            throw new SpeechToTextException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Could not read the audio file", exception);
        }
    }

    // SHA-256 produces the same cache key for identical audio bytes,
    // even when the uploaded file has a different name.
    private String audioHash(byte[] bytes) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(bytes);
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    // This runs only when an audio chunk is not already in the cache.
    private List<SubtitleLine> requestWhisper(MultipartFile file, byte[] bytes) {
        String filename = AudioFileSupport.safeFilename(file,
                AudioFileSupport.normaliseMimeType(file));
        ByteArrayResource resource = new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return filename;
            }
        };

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(
                AudioFileSupport.normaliseMimeType(file)));

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("model", "whisper-1");
        body.add("response_format", "verbose_json");
        body.add("timestamp_granularities[]", "word");
        body.add("timestamp_granularities[]", "segment");
        body.add("file", new HttpEntity<>(resource, headers));

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> json = restClient.post()
                    .uri("/audio/transcriptions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .body(Map.class);

            if (json == null) {
                throw new SpeechToTextException(HttpStatus.BAD_GATEWAY,
                        "Transcription returned an empty response");
            }

            List<SubtitleLine> subtitles = WhisperSubtitleFormatter.format(json);

            // Only count a successful cloud request, never a cache hit.
            Object duration = json.get("duration");
            if (duration instanceof Number seconds) {
                totalMilliseconds.addAndGet(Math.round(seconds.doubleValue() * 1000));
            }
            return subtitles;
        } catch (RestClientException exception) {
            throw new SpeechToTextException(HttpStatus.BAD_GATEWAY,
                    "OpenAI transcription request failed", exception);
        }
    }

    // Total successful transcription seconds for the TITAN usage endpoint.
    public double getUsageSeconds() {
        return totalMilliseconds.get() / 1000.0;
    }
}
