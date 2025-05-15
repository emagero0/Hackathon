package com.erp.aierpbackend.service;

import com.erp.aierpbackend.dto.gemini.GeminiVerificationResult; // Reusing DTO, consider renaming later
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.io.IOException; // Keep for method signature, though WebClient uses reactive exceptions
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class LLMProxyService {

    private final WebClient webClient;
    private final ObjectMapper objectMapper; // For synchronous parsing if needed, or for request prep

    @Value("${llm.python.service.baseurl}") // e.g., http://gemini-python-service:8000
    private String llmServiceBaseUrl;

    // Key for identifier extraction, consistent with previous GeminiService
    private static final String SALES_QUOTE_NUMBER_KEY_FROM_LLM = "salesQuoteNo"; // Or make it generic

    public LLMProxyService(WebClient.Builder webClientBuilder, ObjectMapper objectMapper,
                           @Value("${llm.python.service.baseurl}") String llmServiceBaseUrl) {
        this.webClient = webClientBuilder.baseUrl(llmServiceBaseUrl).build();
        this.objectMapper = objectMapper;
        this.llmServiceBaseUrl = llmServiceBaseUrl; // Ensure it's set if used directly
        log.info("LLMProxyService initialized. Python service base URL: {}", this.llmServiceBaseUrl);
    }

    // Inner classes for request payloads to Python service
    private static class DocumentImagePayload {
        public String image_base64;
        public String mime_type;

        public DocumentImagePayload(String image_base64, String mime_type) {
            this.image_base64 = image_base64;
            this.mime_type = mime_type;
        }
    }

    private static class IdentifierExtractionRequestPayload {
        public String job_no;
        public String document_type;
        public List<DocumentImagePayload> document_images;

        public IdentifierExtractionRequestPayload(String job_no, String document_type, List<DocumentImagePayload> document_images) {
            this.job_no = job_no;
            this.document_type = document_type;
            this.document_images = document_images;
        }
    }

    private static class VerificationRequestPayload {
        public String job_no;
        public String document_type;
        public List<DocumentImagePayload> document_images;
        public Map<String, Object> erp_data;

        public VerificationRequestPayload(String job_no, String document_type, List<DocumentImagePayload> document_images, Map<String, Object> erp_data) {
            this.job_no = job_no;
            this.document_type = document_type;
            this.document_images = document_images;
            this.erp_data = erp_data;
        }
    }
    
    // Response DTO for identifier extraction from Python service
    private static class IdentifierExtractionResponsePayload {
        public Map<String, String> extracted_identifiers;
        public String error_message; // Optional
    }


    public Map<String, String> extractDocumentIdentifiers(
            String jobNo,
            String documentType,
            List<byte[]> documentImageBytesList // Changed from documentImages to avoid confusion
    ) throws IOException { // Keep IOException for now for compatibility with JobDocumentVerificationService
        log.info("LLMProxyService: Starting identifier extraction for Job No: {}, Document Type: {}", jobNo, documentType);

        if (documentImageBytesList == null || documentImageBytesList.isEmpty()) {
            log.warn("No document images provided for identifier extraction. Job No: {}, Document Type: {}", jobNo, documentType);
            return Collections.emptyMap();
        }

        List<DocumentImagePayload> imagePayloads = documentImageBytesList.stream()
                .map(bytes -> new DocumentImagePayload(Base64.getEncoder().encodeToString(bytes), "image/png")) // Assuming PNG
                .collect(Collectors.toList());

        IdentifierExtractionRequestPayload payload = new IdentifierExtractionRequestPayload(jobNo, documentType, imagePayloads);

        try {
            IdentifierExtractionResponsePayload response = webClient.post()
                    .uri("/extract_identifiers")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(IdentifierExtractionResponsePayload.class)
                    .block(); // Blocking for simplicity, consider reactive flow later

            if (response != null) {
                if (response.error_message != null && !response.error_message.isEmpty()) {
                    log.error("Error from LLM service (extract_identifiers) for job {}: {}", jobNo, response.error_message);
                    throw new IOException("LLM service error: " + response.error_message);
                }
                return response.extracted_identifiers != null ? response.extracted_identifiers : Collections.emptyMap();
            } else {
                log.error("No response from LLM service (extract_identifiers) for job {}", jobNo);
                throw new IOException("No response from LLM service for identifier extraction.");
            }
        } catch (Exception e) {
            log.error("Error calling LLM service for identifier extraction (job {}): {}", jobNo, e.getMessage(), e);
            throw new IOException("Failed to call LLM service for identifier extraction: " + e.getMessage(), e);
        }
    }

    public GeminiVerificationResult verifyDocument(
            String jobNo,
            String documentType,
            List<byte[]> documentImageBytesList,
            Map<String, Object> erpData
    ) throws IOException { // Keep IOException for now
        log.info("LLMProxyService: Starting verification for Job No: {}, Document Type: {}", jobNo, documentType);

        if (documentImageBytesList == null || documentImageBytesList.isEmpty()) {
            log.warn("No document images provided for verification. Job No: {}, Document Type: {}", jobNo, documentType);
            return new GeminiVerificationResult(); // Or throw
        }

        List<DocumentImagePayload> imagePayloads = documentImageBytesList.stream()
                .map(bytes -> new DocumentImagePayload(Base64.getEncoder().encodeToString(bytes), "image/png")) // Assuming PNG
                .collect(Collectors.toList());

        VerificationRequestPayload payload = new VerificationRequestPayload(jobNo, documentType, imagePayloads, erpData);

        try {
            // The Python service is expected to return a JSON compatible with GeminiVerificationResult
            GeminiVerificationResult response = webClient.post()
                    .uri("/verify_document")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(GeminiVerificationResult.class) // Deserialize directly to this DTO
                    .block(); // Blocking for simplicity

            if (response != null) {
                // Check for an error message field if your Python service adds one to GeminiVerificationResult structure
                // For example, if GeminiVerificationResult had an 'errorMessage' field:
                // if (response.getErrorMessage() != null && !response.getErrorMessage().isEmpty()) {
                //    log.error("Error from LLM service (verify_document) for job {}: {}", jobNo, response.getErrorMessage());
                //    throw new IOException("LLM service error: " + response.getErrorMessage());
                // }
                return response;
            } else {
                log.error("No response from LLM service (verify_document) for job {}", jobNo);
                throw new IOException("No response from LLM service for document verification.");
            }
        } catch (Exception e) {
            log.error("Error calling LLM service for document verification (job {}): {}", jobNo, e.getMessage(), e);
            throw new IOException("Failed to call LLM service for document verification: " + e.getMessage(), e);
        }
    }
}
