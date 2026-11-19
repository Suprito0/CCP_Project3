package com.example.SpeechToText.service;

import java.io.IOException;
import java.util.List;
import java.util.Map;
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

// Uses the RestClient configuration from Assignment 1, but requests timestamps.
@Service
public class WhisperSubtitleService {

    private final RestClient restClient;
    private final String apiKey;
    private final AtomicLong totalMilliseconds = new AtomicLong();

    public WhisperSubtitleService(
            RestClient openAiRestClient,
            @Value("${openai.api.key:}") String apiKey) {
        this.restClient = openAiRestClient;
        this.apiKey = apiKey;
    }

    public List<SubtitleLine> transcribe(MultipartFile file) {
        AudioFileSupport.validate(file); // Reuse Assignment 1's upload checks.

        if (apiKey == null || apiKey.isBlank()) {
            throw new SpeechToTextException(HttpStatus.SERVICE_UNAVAILABLE,
                    "OPENAI_API_KEY is not configured");
        }

        try {
            byte[] bytes = file.getBytes();
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
            // Whisper reports audio duration in seconds. Track successful calls only.
            Object duration = json.get("duration");
            if (duration instanceof Number seconds) {
                totalMilliseconds.addAndGet(Math.round(seconds.doubleValue() * 1000));
            }
            return subtitles;
        } catch (IOException exception) {
            throw new SpeechToTextException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Could not read the audio file", exception);
        } catch (RestClientException exception) {
            throw new SpeechToTextException(HttpStatus.BAD_GATEWAY,
                    "OpenAI transcription request failed", exception);
        }
    }

    // Cumulative successful audio minutes; not an invoice or token count.
    public double getUsageMinutes() {
        return Math.round(totalMilliseconds.get() / 60.0) / 1000.0;
    }
}