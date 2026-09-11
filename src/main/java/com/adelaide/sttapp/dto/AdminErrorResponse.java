package com.adelaide.sttapp.dto;

/**
 * Matches the YAML spec's ErrorResponse schema exactly (additionalProperties: false):
 * timestamp, status, error, message, path.
 *
 * This is a DIFFERENT shape from the plain ErrorResponse used by
 * TranscriptionController (which isn't covered by this YAML spec at all -
 * only /api/v1/admin/uptime, /api/v1/admin/shutdown and /api/v1/global/stats
 * are). Named distinctly to avoid confusing the two.
 */
public class AdminErrorResponse {

    private final String timestamp;
    private final int status;
    private final String error;
    private final String message;
    private final String path;

    public AdminErrorResponse(String timestamp, int status, String error, String message, String path) {
        this.timestamp = timestamp;
        this.status = status;
        this.error = error;
        this.message = message;
        this.path = path;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public int getStatus() {
        return status;
    }

    public String getError() {
        return error;
    }

    public String getMessage() {
        return message;
    }

    public String getPath() {
        return path;
    }
}
