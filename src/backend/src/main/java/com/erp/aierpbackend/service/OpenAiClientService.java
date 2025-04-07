package com.erp.aierpbackend.service;

import com.erp.aierpbackend.dto.openai.OpenAiChatRequest;
import com.erp.aierpbackend.dto.openai.OpenAiChatResponse;
import com.erp.aierpbackend.dto.openai.OpenAiMessage;
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

import java.util.ArrayList;
import java.util.stream.Collectors;

@Service
public class OpenAiClientService {

    private static final Logger log = LoggerFactory.getLogger(OpenAiClientService.class);

    private final WebClient webClient;

    @Value("${openai.api.key}")
    private String apiKey;

    @Value("${openai.api.endpoint}")
    private String apiEndpoint;

    public OpenAiClientService(WebClient.Builder webClientBuilder) {
        // Builder is typically configured centrally, but we create a basic one here
        this.webClient = webClientBuilder.build();
    }

    @PostConstruct
    private void init() {
        log.info("OpenAI Client Service initialized. Endpoint: {}", apiEndpoint);
        if ("YOUR_API_KEY".equals(apiKey)) {
            log.warn("OpenAI API Key is set to the default placeholder. Please update application.properties.");
        }
    }

    @CircuitBreaker(name = "openAiCircuitBreaker", fallbackMethod = "fallbackForOpenAi")
    @Retry(name = "openAiRetry")
    public Mono<OpenAiChatResponse> callOpenAiApi(String prompt) {
        log.debug("Calling OpenAI API with prompt snippet: {}", prompt.substring(0, Math.min(prompt.length(), 50)) + "...");
    
        OpenAiMessage userMsg = new OpenAiMessage("user", prompt);
        ArrayList<OpenAiMessage> messages = new ArrayList<>();
        messages.add(userMsg);
    
        OpenAiChatRequest chatRequest = OpenAiChatRequest.builder()
                .model("gpt-3.5-turbo")
                .messages(messages.stream().map(m -> m).collect(Collectors.toList()))
                .temperature(0.7)
                .max_tokens(150)
                .build();
    
        return webClient.post()
                .uri(apiEndpoint)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(chatRequest)
                .retrieve()
                .bodyToMono(OpenAiChatResponse.class)
                .doOnError(error -> log.error("Error calling OpenAI API: {}", error.getMessage()));
    }
    
    private Mono<OpenAiChatResponse> fallbackForOpenAi(String prompt, Throwable t) {
        log.warn("Fallback triggered due to error: {}", t.getMessage());
        OpenAiChatResponse fallback = new OpenAiChatResponse();
        fallback.setId("fallback");
        fallback.setObject("error");
        fallback.setModel("gpt-3.5-turbo");
        fallback.setCreated(System.currentTimeMillis());
        fallback.setChoices(new ArrayList<>());
        return Mono.just(fallback);
    }
}
