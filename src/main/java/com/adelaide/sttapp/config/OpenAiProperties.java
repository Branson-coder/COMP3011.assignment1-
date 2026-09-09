package com.adelaide.sttapp.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Holds configuration read from the environment / application properties.
 *
 * SECURITY NOTE (per assignment non-functional spec #1):
 * The OpenAI API key is read once from the OPENAI_API_KEY environment
 * variable at startup and kept only in memory as a process variable.
 * - It is never written to a log statement.
 * - It is never returned in any HTTP response body.
 * - It is never hard-coded, committed, or persisted to disk.
 * toString() is deliberately NOT overridden to print the key, and callers
 * must not System.out.println(...) or log.debug(...) this value.
 */
@Component
public class OpenAiProperties {

    private final String apiKey;
    private final String transcriptionUrl;
    private final String model;

    public OpenAiProperties(
            @Value("${OPENAI_API_KEY:}") String apiKey,
            @Value("${stt.openai.transcription-url:https://api.openai.com/v1/audio/transcriptions}") String transcriptionUrl,
            @Value("${stt.openai.model:gpt-4o-mini-transcribe}") String model) {
        this.apiKey = apiKey;
        this.transcriptionUrl = transcriptionUrl;
        this.model = model;
    }

    public String getApiKey() {
        return apiKey;
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    public String getTranscriptionUrl() {
        return transcriptionUrl;
    }

    public String getModel() {
        return model;
    }
}
