package com.adelaide.sttapp.dto;

/**
 * Matches the YAML spec's ShutdownResponse schema exactly (additionalProperties: false): message only.
 */
public class ShutdownResponse {

    private final String message;

    public ShutdownResponse(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }
}
