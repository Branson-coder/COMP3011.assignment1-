package com.adelaide.sttapp.controller;

import com.adelaide.sttapp.dto.ErrorResponse;
import com.adelaide.sttapp.dto.TranscriptionResponse;
import com.adelaide.sttapp.service.AppStatsService;
import com.adelaide.sttapp.service.OpenAiSttService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.concurrent.CompletableFuture;

@RestController
public class TranscriptionController {

    private static final Logger log = LoggerFactory.getLogger(TranscriptionController.class);

    private final OpenAiSttService sttService;
    private final AppStatsService stats;

    public TranscriptionController(OpenAiSttService sttService, AppStatsService stats) {
        this.sttService = sttService;
        this.stats = stats;
    }

    @PostMapping(value = "/api/v1/transcribe", consumes = "multipart/form-data")
    public CompletableFuture<ResponseEntity<Object>> transcribe(@RequestParam("audio") MultipartFile audio) {
        stats.recordRequestStarted();

        if (audio.isEmpty()) {
            stats.recordRequestFinished(false);
            return CompletableFuture.completedFuture(
                    ResponseEntity.badRequest()
                            .body((Object) new ErrorResponse("bad_request", "No audio data was received")));
        }

        return sttService.transcribe(audio)
                .thenApply(text -> {
                    stats.recordRequestFinished(true);
                    return ResponseEntity.ok((Object) new TranscriptionResponse(text));
                })
                .exceptionally(ex -> {
                    stats.recordRequestFinished(false);
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    log.error("Transcription request failed: {}", cause.getClass().getSimpleName());

                    if (cause instanceof OpenAiSttService.UpstreamSttException upstream) {
                        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                                .body((Object) new ErrorResponse("upstream_error",
                                        "STT provider returned HTTP " + upstream.getStatusCode()));
                    }
                    if (cause instanceof IllegalStateException && cause.getMessage() != null
                            && cause.getMessage().contains("OPENAI_API_KEY")) {
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body((Object) new ErrorResponse("server_misconfigured",
                                        "Server is missing its STT provider credentials"));
                    }
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body((Object) new ErrorResponse("internal_error", "Transcription failed"));
                });
    }
}