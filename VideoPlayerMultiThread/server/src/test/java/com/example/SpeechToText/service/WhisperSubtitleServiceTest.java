package com.example.SpeechToText.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import static org.springframework.test.web.client.ExpectedCount.once;
import org.springframework.test.web.client.MockRestServiceServer;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import org.springframework.web.client.RestClient;

class WhisperSubtitleServiceTest {

    private MockRestServiceServer server;
    private WhisperSubtitleService service;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://api.openai.test/v1");
        server = MockRestServiceServer.bindTo(builder).build();
        service = new WhisperSubtitleService(builder.build(), "test-key");
    }

    @Test
    void reusesCachedAudioEvenIfFilenameChanges() {
        expectOneWhisperRequest(20.0);

        // These two uploads contain the same bytes but have different names.
        service.transcribe(audio("first.mp3", "same audio"));
        service.transcribe(audio("renamed.mp3", "same audio"));

        assertEquals(20.0, service.getUsageSeconds(), 0.001);
        server.verify(); // Only one request should have reached Whisper.
    }

    @Test
    void newAudioIsTranscribedAndCountedSeparately() {
        expectOneWhisperRequest(12.5);
        expectOneWhisperRequest(12.5);

        service.transcribe(audio("one.mp3", "first audio"));
        service.transcribe(audio("two.mp3", "second audio"));

        assertEquals(25.0, service.getUsageSeconds(), 0.001);
        server.verify();
    }

    private void expectOneWhisperRequest(double seconds) {
        server.expect(once(), requestTo("https://api.openai.test/v1/audio/transcriptions"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {"duration": %s, "words": [
                          {"start": 0.0, "end": 1.0, "word": "Hello"}
                        ]}
                        """.formatted(seconds), MediaType.APPLICATION_JSON));
    }

    private MockMultipartFile audio(String name, String contents) {
        return new MockMultipartFile("audio", name, "audio/mpeg", contents.getBytes());
    }
}