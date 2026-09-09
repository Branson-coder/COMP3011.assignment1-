package com.adelaide.sttapp;

import com.adelaide.sttapp.controller.TranscriptionController;
import com.adelaide.sttapp.service.AppStatsService;
import com.adelaide.sttapp.service.OpenAiSttService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

/**
 * Unit test for the controller's validation branch. Uses a mocked
 * OpenAiSttService so this test never makes a real network call and never
 * needs a real OPENAI_API_KEY to run.
 */
class TranscriptionControllerTest {

    @Test
    void emptyUploadIsRejectedWithoutCallingStt() throws ExecutionException, InterruptedException {
        OpenAiSttService sttService = mock(OpenAiSttService.class);
        AppStatsService stats = new AppStatsService();
        TranscriptionController controller = new TranscriptionController(sttService, stats);

        MockMultipartFile emptyFile = new MockMultipartFile("audio", "recording.webm", "audio/webm", new byte[0]);

        ResponseEntity<?> response = controller.transcribe(emptyFile).get();

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(1, stats.getTotalRequests());
        assertEquals(1, stats.getFailedTranscriptions());
    }
}
