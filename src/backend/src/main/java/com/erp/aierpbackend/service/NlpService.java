package com.erp.aierpbackend.service;

import com.erp.aierpbackend.dto.NlpRequest;
import com.erp.aierpbackend.dto.NlpResponse;
import com.erp.aierpbackend.dto.gemini.GeminiCandidate; // Import Gemini DTOs
import com.erp.aierpbackend.dto.gemini.GeminiContent;
import com.erp.aierpbackend.dto.gemini.GeminiPart;
import com.erp.aierpbackend.dto.gemini.GeminiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Optional; // For safer access
import java.util.stream.Collectors; // For joining parts
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class NlpService {

    private static final Logger log = LoggerFactory.getLogger(NlpService.class);

    @Autowired
    private GeminiClientService geminiClientService; // Inject the Gemini client

    public Mono<NlpResponse> verifyDocument(NlpRequest request) {
        log.debug("Processing NLP request for document: {}", request.getMetadata().getOrDefault("documentId", "N/A"));

        String prompt = buildVerificationPrompt(request);

        // Call the Gemini API
        return geminiClientService.callGeminiApi(prompt)
                .flatMap(apiResponse -> {
                    log.debug("Received raw response from Gemini API: {}", apiResponse); // Log the DTO
                    NlpResponse nlpResponse = parseApiResponse(apiResponse, request); // Use updated parser

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

    // Updated helper method to parse GeminiResponse
    private NlpResponse parseApiResponse(GeminiResponse apiResponse, NlpRequest request) {
        NlpResponse response = new NlpResponse();
        Map<String, Object> processingMeta = new HashMap<>();
        // Gemini response doesn't typically include model in the main body, maybe add if needed
        processingMeta.put("modelUsed", "gemini-pro (assumed)");
        processingMeta.put("processingTimeMs", System.currentTimeMillis()); // Placeholder

        // Extract text from the first candidate's content parts
        String aiContent = Optional.ofNullable(apiResponse.getCandidates())
                .filter(candidates -> !candidates.isEmpty())
                .map(candidates -> candidates.get(0)) // Get first candidate
                .map(GeminiCandidate::getContent)
                .map(GeminiContent::getParts)
                .filter(parts -> !parts.isEmpty())
                .map(parts -> parts.stream().map(GeminiPart::getText).collect(Collectors.joining("\n"))) // Join parts
                .orElse("No content found in Gemini response."); // Default if structure is missing

        // Simple logic based on response content (similar to before)
        if (aiContent.toLowerCase().contains("flagged") || aiContent.toLowerCase().contains("discrepancy") || aiContent.contains("error") || aiContent.contains("Fallback:")) {
             response.setStatus("FLAGGED");
             response.setDiscrepancies(new ArrayList<>());
             response.getDiscrepancies().add(
                 new NlpResponse.Discrepancy("general", "N/A", "N/A", "AI flagged potential issues.")
             );
             processingMeta.put("confidence", 0.85);
        } else if (aiContent.contains("No content found")) {
             response.setStatus("ERROR");
             processingMeta.put("confidence", 0.1);
        }
        else {
             response.setStatus("VERIFIED");
             response.setDiscrepancies(new ArrayList<>());
             processingMeta.put("confidence", 0.92);
        }
        response.setExplanation(aiContent);
        response.setProcessingMetadata(processingMeta);
        return response;
    }
}
