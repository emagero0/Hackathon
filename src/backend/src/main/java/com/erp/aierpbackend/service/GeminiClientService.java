package com.erp.aierpbackend.service;

import com.erp.aierpbackend.dto.gemini.*; // Import all Gemini DTOs
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Collections;

@Service
public class GeminiClientService { // Renamed class

    private static final Logger log = LoggerFactory.getLogger(GeminiClientService.class);

    private final WebClient webClient;

    @Value("${gemini.api.key}") // Updated property name
    private String apiKey;

    @Value("${gemini.api.endpoint}") // Updated property name
    private String apiEndpoint;

    public GeminiClientService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.baseUrl(apiEndpoint) // Base URL can be set here
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                // API key will be added per request
                .build();
    }

    @PostConstruct
    private void init() {
        log.info("Gemini Client Service initialized. Endpoint base: {}", apiEndpoint); // Log base endpoint
        if ("YOUR_API_KEY".equals(apiKey)) {
            log.warn("Gemini API Key is set to the default placeholder. Please update application.properties.");
        }
    }

    @CircuitBreaker(name = "geminiCircuitBreaker", fallbackMethod = "fallbackForGemini") // Updated annotation name
    @Retry(name = "geminiRetry") // Updated annotation name
    public Mono<GeminiResponse> callGeminiApi(String prompt) { // Renamed method, updated return type
        log.debug("Calling Gemini API with prompt snippet: {}", prompt.substring(0, Math.min(prompt.length(), 50)) + "...");

        // Build Gemini Request
        GeminiPart part = new GeminiPart(prompt);
        GeminiContent content = new GeminiContent(Collections.singletonList(part), "user"); // Assuming 'user' role
        GeminiGenerationConfig config = GeminiGenerationConfig.builder()
                .temperature(0.7)
                .maxOutputTokens(150)
                .build();

        GeminiRequest geminiRequest = GeminiRequest.builder()
                .contents(Collections.singletonList(content))
                .generationConfig(config)
                .build();

        return webClient.post()
                // Add the API key as a query parameter to the endpoint URI
                .uri(uriBuilder -> uriBuilder
                        // Assuming the base URL in properties already contains the path like /v1beta/models/gemini-pro:generateContent
                        // If not, add .path(apiEndpoint) or similar here.
                        .queryParam("key", apiKey)
                        .build())
                .bodyValue(geminiRequest)
                .retrieve()
                .bodyToMono(GeminiResponse.class) // Use GeminiResponse DTO
                .doOnError(error -> log.error("Error calling Gemini API: {}", error.getMessage()));
    }

    // Updated fallback method
    private Mono<GeminiResponse> fallbackForGemini(String prompt, Throwable t) {
        log.warn("Fallback triggered for Gemini API due to error: {}", t.getMessage());
        GeminiResponse fallback = new GeminiResponse();
        // Create a minimal fallback response structure
        GeminiPart fallbackPart = new GeminiPart("Fallback: Service unavailable or error occurred.");
        GeminiContent fallbackContent = new GeminiContent(Collections.singletonList(fallbackPart), "model");
        GeminiCandidate fallbackCandidate = new GeminiCandidate();
        fallbackCandidate.setContent(fallbackContent);
        fallbackCandidate.setFinishReason("ERROR");
        fallback.setCandidates(Collections.singletonList(fallbackCandidate));
        return Mono.just(fallback);
    }
}
