package com.adelaide.sttapp.service;

import com.adelaide.sttapp.config.OpenAiProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * Talks to https://api.openai.com/v1/audio/transcriptions using the
 * gpt-4o-mini-transcribe model, entirely without blocking the calling
 * (servlet) thread.
 */
@Service
public class OpenAiSttService {

    private static final Logger log = LoggerFactory.getLogger(OpenAiSttService.class);

    private final WebClient webClient;
    private final OpenAiProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OpenAiSttService(WebClient openAiWebClient, OpenAiProperties properties) {
        this.webClient = openAiWebClient;
        this.properties = properties;
    }

    /**
     * Sends the uploaded audio to OpenAI and returns the transcribed text.
     * The returned CompletableFuture completes on a Netty I/O thread, never
     * on the original Tomcat request thread.
     */
    public CompletableFuture<String> transcribe(MultipartFile audio) {
        if (!properties.isConfigured()) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("OPENAI_API_KEY is not set in the environment"));
        }

        final byte[] bytes;
        try {
            bytes = audio.getBytes();
        } catch (IOException e) {
            return CompletableFuture.failedFuture(e);
        }

        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        ByteArrayResource fileResource = new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                String original = audio.getOriginalFilename();
                return (original != null && !original.isBlank()) ? original : "recording.webm";
            }
        };
        parts.add("file", fileResource);
        parts.add("model", properties.getModel());

        return webClient.post()
                .uri(properties.getTranscriptionUrl())
                .headers(h -> h.setBearerAuth(properties.getApiKey()))
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(parts))
                .retrieve()
                .onStatus(status -> status.isError(), response ->
                        response.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .flatMap(body -> {
                                    // Never log the Authorization header / API key here.
                                    log.warn("OpenAI STT call failed with status {}", response.statusCode());
                                    return Mono.error(new UpstreamSttException(
                                            response.statusCode().value(), body));
                                }))
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(20))
                .map(this::extractText)
                .toFuture();
    }

    private String extractText(String json) {
        try {
            JsonNode node = objectMapper.readTree(json);
            JsonNode textNode = node.get("text");
            return textNode != null ? textNode.asText() : "";
        } catch (IOException e) {
            log.error("Could not parse OpenAI STT response body");
            throw new IllegalStateException("Malformed response from STT provider", e);
        }
    }

    /** Thrown when OpenAI itself returns a non-2xx response. */
    public static class UpstreamSttException extends RuntimeException {
        private final int statusCode;

        public UpstreamSttException(int statusCode, String body) {
            super("Upstream STT provider returned HTTP " + statusCode);
            this.statusCode = statusCode;
        }

        public int getStatusCode() {
            return statusCode;
        }
    }
}
