package com.adelaide.sttapp.dto;

/**
 * Matches the YAML spec's GlobalStatsResponse schema exactly (additionalProperties: false):
 * inputTokens, outputTokens - cumulative OpenAI STT token usage since server start.
 */
public class GlobalStatsResponse {

    private final long inputTokens;
    private final long outputTokens;

    public GlobalStatsResponse(long inputTokens, long outputTokens) {
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
    }

    public long getInputTokens() {
        return inputTokens;
    }

    public long getOutputTokens() {
        return outputTokens;
    }
}
