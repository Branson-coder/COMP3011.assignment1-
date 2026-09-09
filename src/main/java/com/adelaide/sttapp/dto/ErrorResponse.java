package com.adelaide.sttapp.dto;

/**
 * JSON shape returned to clients on any handled error condition
 * (bad upload, upstream STT failure, timeout, etc).
 */
public class ErrorResponse {

    private final String error;
    private final String message;

    public ErrorResponse(String error, String message) {
        this.error = error;
        this.message = message;
    }

    public String getError() {
        return error;
    }

    public String getMessage() {
        return message;
    }
}
