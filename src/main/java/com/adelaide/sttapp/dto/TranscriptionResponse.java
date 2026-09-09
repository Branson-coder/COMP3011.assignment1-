package com.adelaide.sttapp.dto;

/**
 * JSON shape returned to the browser after a successful transcription.
 */
public class TranscriptionResponse {

    private final String text;

    public TranscriptionResponse(String text) {
        this.text = text;
    }

    public String getText() {
        return text;
    }
}
