package com.example.SpeechToText.controller;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.example.SpeechToText.service.SubtitleLine;
import com.example.SpeechToText.service.WhisperSubtitleService;

// Test the HTTP endpoints with a fake service instead of calling Whisper.
class SubtitleControllerTest {
    private WhisperSubtitleService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(WhisperSubtitleService.class);
        mvc = MockMvcBuilders.standaloneSetup(new SubtitleController(service)).build();
    }

    @Test
    void returnsSubtitleTimesAsJson() throws Exception {
        when(service.transcribe(any())).thenReturn(
                List.of(new SubtitleLine(1, 2.5, "Example subtitle")));
        MockMultipartFile audio = new MockMultipartFile(
                "audio", "audio.mp3", "audio/mpeg", new byte[] {1, 2, 3});

        mvc.perform(multipart("/api/v1/subtitles").file(audio))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].start").value(1))
                .andExpect(jsonPath("$[0].end").value(2.5))
                .andExpect(jsonPath("$[0].text").value("Example subtitle"));
    }

    @Test
    void returnsCumulativeSecondsNotMinutes() throws Exception {
        when(service.getUsageSeconds()).thenReturn(137.23);
        mvc.perform(get("/api/v1/global/usage"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usage").value(137.23));
    }
}