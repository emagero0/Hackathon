package com.erp.aierpbackend.service;

import com.erp.aierpbackend.dto.ClassifyAndVerifyResultDTO;
import com.erp.aierpbackend.dto.gemini.DocumentClassificationResult;
import com.erp.aierpbackend.dto.gemini.GeminiVerificationResult; // Reusing DTO, consider renaming later
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException; // Keep for method signature, though WebClient uses reactive exceptions
import java.time.Duration;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class LLMProxyService {

    private final WebClient webClient;
    private final ObjectMapper objectMapper; // For synchronous parsing if needed, or for request prep
    private final SystemErrorHandler systemErrorHandler;

    @Value("${llm.python.service.baseurl}") // e.g., http://gemini-python-service:8000
    private String llmServiceBaseUrl;

    // Key for identifier extraction, consistent with previous GeminiService
    private static final String SALES_QUOTE_NUMBER_KEY_FROM_LLM = "salesQuoteNo"; // Or make it generic

    public LLMProxyService(WebClient.Builder webClientBuilder, ObjectMapper objectMapper,
                           SystemErrorHandler systemErrorHandler,
                           @Value("${llm.python.service.baseurl}") String llmServiceBaseUrl) {
        this.webClient = webClientBuilder.baseUrl(llmServiceBaseUrl).build();
        this.objectMapper = objectMapper;
        this.systemErrorHandler = systemErrorHandler;
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

    // Request payload for document classification
    private static class ClassificationRequestPayload {
        public String job_no;
        public List<DocumentImagePayload> document_images;

        public ClassificationRequestPayload(String job_no, List<DocumentImagePayload> document_images) {
            this.job_no = job_no;
            this.document_images = document_images;
        }
    }

    // Request payload for combined classification and verification
    private static class ClassifyAndVerifyRequestPayload {
        public String job_no;
        public List<DocumentImagePayload> document_images;
        public Map<String, Object> erp_data;

        public ClassifyAndVerifyRequestPayload(String job_no, List<DocumentImagePayload> document_images, Map<String, Object> erp_data) {
            this.job_no = job_no;
            this.document_images = document_images;
            this.erp_data = erp_data;
        }
    }


    /**
     * @deprecated Use the new extractDocumentIdentifiers method that uses the classify_and_verify endpoint
     */
    @Deprecated
    private Map<String, String> extractDocumentIdentifiersLegacy(
            String jobNo,
            String documentType,
            List<byte[]> documentImageBytesList
    ) throws IOException {
        log.info("LLMProxyService: Starting legacy identifier extraction for Job No: {}, Document Type: {}", jobNo, documentType);

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
                    .timeout(Duration.ofSeconds(30)) // Add 30-second timeout for identifier extraction
                    .block(Duration.ofSeconds(35)); // Blocking with timeout

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

    /**
     * Classifies a document using the LLM service.
     *
     * @param jobNo The job number
     * @param documentImageBytesList List of document images as byte arrays
     * @return Mono containing the classification result
     */
    public Mono<DocumentClassificationResult> classifyDocument(
            String jobNo,
            List<byte[]> documentImageBytesList
    ) {
        log.info("LLMProxyService: Starting document classification for Job No: '{}'", jobNo);

        if (documentImageBytesList == null || documentImageBytesList.isEmpty()) {
            log.warn("No document images provided for classification. Job No: '{}'", jobNo);
            return Mono.error(new IOException("No document images provided for classification"));
        }

        List<DocumentImagePayload> imagePayloads = documentImageBytesList.stream()
                .map(bytes -> new DocumentImagePayload(Base64.getEncoder().encodeToString(bytes), "image/png"))
                .collect(Collectors.toList());

        ClassificationRequestPayload payload = new ClassificationRequestPayload(jobNo, imagePayloads);

        return systemErrorHandler.withSystemErrorRetry(
                webClient.post()
                        .uri("/classify_document")
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(payload)
                        .retrieve()
                        .bodyToMono(DocumentClassificationResult.class)
                        .timeout(Duration.ofSeconds(30)),
                "LLM document classification"
        ).onErrorResume(e -> {
            if (systemErrorHandler.isSystemError(e)) {
                // System error - create fallback response without exposing details
                log.error("System error during LLM document classification for job '{}': {}", jobNo, e.getMessage());
                DocumentClassificationResult fallback = new DocumentClassificationResult();
                fallback.setDocumentType("UNKNOWN");
                fallback.setConfidence(0.0);
                fallback.setReasoning("System temporarily unavailable");
                return Mono.just(fallback);
            } else {
                // Business logic error - propagate with details
                log.error("Business logic error during LLM document classification for job '{}': {}", jobNo, e.getMessage());
                return Mono.error(new SystemErrorHandler.BusinessLogicErrorException(
                        "Document classification failed: " + e.getMessage(), e));
            }
        });
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
                    .timeout(Duration.ofSeconds(60)) // Add 60-second timeout for verification
                    .block(Duration.ofSeconds(65)); // Blocking with timeout

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

    /**
     * Classifies and verifies a document in a single step using the LLM service.
     * The document remains UNCLASSIFIED in the system, but the LLM identifies the type
     * and performs verification against the appropriate ERP data.
     *
     * This method has been enhanced to support two modes:
     * 1. Initial classification mode: When erpData only contains jobNo, it focuses on extracting document identifiers
     * 2. Verification mode: When erpData contains Business Central data, it performs full verification
     *
     * @param jobNo The job number
     * @param documentImageBytesList List of document images as byte arrays
     * @param erpData Map containing all ERP data for different document types
     * @return ClassifyAndVerifyResultDTO containing both classification and verification results
     * @throws IOException If there is an error communicating with the LLM service
     */
    public ClassifyAndVerifyResultDTO classifyAndVerifyDocument(
            String jobNo,
            List<byte[]> documentImageBytesList,
            Map<String, Object> erpData
    ) throws IOException {
        // Determine if this is the initial classification step or the verification step
        boolean isInitialClassification = erpData.size() == 1 && erpData.containsKey("jobNo");

        if (isInitialClassification) {
            log.info("LLMProxyService: Starting initial classification and identifier extraction for Job No: {}", jobNo);
        } else {
            log.info("LLMProxyService: Starting full verification with Business Central data for Job No: {}", jobNo);
        }

        if (documentImageBytesList == null || documentImageBytesList.isEmpty()) {
            log.warn("No document images provided for classification and verification. Job No: {}", jobNo);
            return ClassifyAndVerifyResultDTO.builder()
                    .documentType("UNKNOWN")
                    .classificationConfidence(0.0)
                    .errorMessage("No document images provided")
                    .build();
        }

        List<DocumentImagePayload> imagePayloads = documentImageBytesList.stream()
                .map(bytes -> new DocumentImagePayload(Base64.getEncoder().encodeToString(bytes), "image/png"))
                .collect(Collectors.toList());

        ClassifyAndVerifyRequestPayload payload = new ClassifyAndVerifyRequestPayload(jobNo, imagePayloads, erpData);

        try {
            // Use a longer timeout for verification mode
            int timeoutSeconds = isInitialClassification ? 60 : 120;

            ClassifyAndVerifyResultDTO response = webClient.post()
                    .uri("/classify_and_verify")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(ClassifyAndVerifyResultDTO.class)
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .block(Duration.ofSeconds(timeoutSeconds + 5)); // Blocking with timeout

            if (response != null) {
                if (response.getErrorMessage() != null && !response.getErrorMessage().isEmpty()) {
                    String errorMessage = response.getErrorMessage();
                    log.error("Error from LLM service (classify_and_verify) for job {}: {}", jobNo, errorMessage);

                    // Check if this is a business logic error (missing identifiers)
                    if (errorMessage.toLowerCase().contains("cannot find") &&
                        (errorMessage.toLowerCase().contains("sales quote number") ||
                         errorMessage.toLowerCase().contains("tax invoice number") ||
                         errorMessage.toLowerCase().contains("job number"))) {
                        // This is a business logic error - missing identifier
                        throw new SystemErrorHandler.BusinessLogicErrorException(errorMessage);
                    } else {
                        // This is a system error
                        throw new SystemErrorHandler.SystemErrorException("LLM service error: " + errorMessage,
                                new IOException(errorMessage));
                    }
                }

                if (isInitialClassification) {
                    log.info("Successfully classified document and extracted identifiers for Job No: '{}', Document Type: '{}'",
                            jobNo, response.getDocumentType());
                } else {
                    log.info("Successfully verified document for Job No: '{}', Document Type: '{}', Verification Confidence: {}",
                            jobNo, response.getDocumentType(), response.getOverallVerificationConfidence());
                }

                return response;
            } else {
                log.error("No response from LLM service (classify_and_verify) for job {}", jobNo);
                throw new IOException("No response from LLM service for document classification and verification.");
            }
        } catch (SystemErrorHandler.BusinessLogicErrorException | SystemErrorHandler.SystemErrorException e) {
            // Re-throw our custom exceptions as-is
            throw e;
        } catch (Exception e) {
            log.error("Error calling LLM service for document classification and verification (job {}): {}",
                    jobNo, e.getMessage(), e);

            // Check if this is a system error that should be retried
            if (systemErrorHandler.isSystemError(e)) {
                throw new SystemErrorHandler.SystemErrorException(
                        "Failed to call LLM service for document classification and verification: " + e.getMessage(), e);
            } else {
                throw new SystemErrorHandler.BusinessLogicErrorException(
                        "Failed to call LLM service for document classification and verification: " + e.getMessage(), e);
            }
        }
    }

    /**
     * Batch processes multiple documents to extract identifiers in a single call.
     * This method is optimized for the initial phase of document processing where we need
     * to extract key identifiers (sales quote number, invoice number) from all documents.
     *
     * @param jobNo The job number
     * @param documentImagesBytesList List of document images as byte arrays
     * @return ClassifyAndVerifyResultDTO containing extracted identifiers
     * @throws IOException If there is an error communicating with the LLM service
     */
    public ClassifyAndVerifyResultDTO batchExtractDocumentIdentifiers(
            String jobNo,
            List<byte[]> documentImagesBytesList
    ) throws IOException {
        log.info("LLMProxyService: Starting batch identifier extraction for Job No: {}", jobNo);

        // Create minimal ERP data with just the job number
        Map<String, Object> minimalErpData = Collections.singletonMap("jobNo", jobNo);

        // Use the existing classifyAndVerifyDocument method with minimal ERP data
        // This will trigger the initial classification mode
        return classifyAndVerifyDocument(jobNo, documentImagesBytesList, minimalErpData);
    }

    /**
     * Extracts document identifiers from a specific document type.
     * This method is used when we need to extract identifiers from a specific document type
     * after the initial batch processing.
     *
     * @param jobNo The job number
     * @param documentType The document type (SalesQuote, ProformaInvoice, JobConsumption)
     * @param documentImagesBytesList List of document images as byte arrays
     * @return Map of field names to extracted values
     * @throws IOException If there is an error communicating with the LLM service
     */
    public Map<String, String> extractDocumentIdentifiers(
            String jobNo,
            String documentType,
            List<byte[]> documentImagesBytesList
    ) throws IOException {
        log.info("LLMProxyService: Extracting identifiers for document type: {} for Job No: {}", documentType, jobNo);

        // Create minimal ERP data with just the job number
        Map<String, Object> minimalErpData = new HashMap<>();
        minimalErpData.put("jobNo", jobNo);
        minimalErpData.put("documentType", documentType);

        // Use the existing classifyAndVerifyDocument method with minimal ERP data
        ClassifyAndVerifyResultDTO result = classifyAndVerifyDocument(jobNo, documentImagesBytesList, minimalErpData);

        // Extract the identifiers from the result
        Map<String, String> extractedIdentifiers = new HashMap<>();
        if (result != null && result.getFieldConfidences() != null) {
            for (ClassifyAndVerifyResultDTO.FieldConfidenceDTO field : result.getFieldConfidences()) {
                if (field.getExtractedValue() != null && !field.getExtractedValue().isBlank()) {
                    extractedIdentifiers.put(field.getFieldName(), field.getExtractedValue());
                    log.info("Extracted {} = {} from {} document for Job No: {}",
                            field.getFieldName(), field.getExtractedValue(), documentType, jobNo);
                }
            }
        }

        return extractedIdentifiers;
    }
}
