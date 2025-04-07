package com.erp.aierpbackend.service;

import com.erp.aierpbackend.dto.NlpRequest;
import com.erp.aierpbackend.dto.NlpResponse;
import org.springframework.beans.factory.annotation.Autowired; // Added import
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono; // Added import

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class NlpService {

    private static final Logger log = LoggerFactory.getLogger(NlpService.class);

    @Autowired
    private OpenAiClientService openAiClientService; // Inject the client

    // Method now returns Mono for async handling
    public Mono<NlpResponse> verifyDocument(NlpRequest request) {
        log.debug("Processing NLP request for document: {}", request.getMetadata().getOrDefault("documentId", "N/A"));

        // 1. Prepare the prompt for the AI model
        String prompt = buildVerificationPrompt(request); // Helper method to build prompt

        // 2. Call the OpenAI API (asynchronously)
        return openAiClientService.callOpenAiApi(prompt)
                .flatMap(apiResponseString -> {
                    // 3. Parse the AI's response (currently a dummy string)
                    //    In a real scenario, parse the JSON response into an actual DTO
                    log.debug("Received raw response from OpenAI API: {}", apiResponseString);
                    NlpResponse nlpResponse = parseApiResponse(apiResponseString, request); // Helper method

                    log.debug("Finished NLP processing for document: {}, Status: {}",
                            request.getMetadata().getOrDefault("documentId", "N/A"), nlpResponse.getStatus());
                    return Mono.just(nlpResponse);
                });
    }

    // Helper method to build the prompt (example)
    private String buildVerificationPrompt(NlpRequest request) {
        // Customize this prompt based on requirements
        return "Verify the following document text and check for discrepancies against standard job details. " +
               "Document Text: \n---\n" + request.getCleanedText() + "\n---\n" +
               "Respond with verification status (VERIFIED/FLAGGED), list discrepancies if any, and provide a brief explanation.";
    }

    // Helper method to parse the OpenAiChatResponse and map it to NlpResponse
    private NlpResponse parseApiResponse(com.erp.aierpbackend.dto.openai.OpenAiChatResponse apiResponse, NlpRequest request) {
        NlpResponse response = new NlpResponse();
        Map<String, Object> processingMeta = new HashMap<>();
        processingMeta.put("modelUsed", apiResponse.getModel());
        processingMeta.put("processingTimeMs", System.currentTimeMillis()); // You may want to measure actual processing time
        
        // Assume the first choice is our answer
        if (apiResponse.getChoices() != null && !apiResponse.getChoices().isEmpty()) {
            String aiContent = apiResponse.getChoices().get(0).getMessage().getContent();
            // Simple logic: if AI response contains certain keywords, mark as FLAGGED
            if (aiContent.toLowerCase().contains("flagged") || aiContent.toLowerCase().contains("discrepancy") || aiContent.toLowerCase().contains("error")) {
                response.setStatus("FLAGGED");
                response.setDiscrepancies(new ArrayList<>());
                response.getDiscrepancies().add(
                    new NlpResponse.Discrepancy("general", "N/A", "N/A", "AI flagged potential issues: " + aiContent)
                );
            } else {
                response.setStatus("VERIFIED");
                response.setDiscrepancies(new ArrayList<>());
            }
            response.setExplanation(aiContent);
        } else {
            // If no choices returned, consider it an error
            response.setStatus("ERROR");
            response.setExplanation("No response from AI service.");
        }
        response.setProcessingMetadata(processingMeta);
        return response;
    }
}
