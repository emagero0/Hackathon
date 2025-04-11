package com.erp.aierpbackend.service;

import com.erp.aierpbackend.dto.NlpRequest;
import com.erp.aierpbackend.dto.NlpResponse;
import com.erp.aierpbackend.dto.gemini.GeminiCandidate; // Import Gemini DTOs
import com.erp.aierpbackend.dto.gemini.GeminiContent;
import com.erp.aierpbackend.dto.gemini.GeminiPart;
import com.erp.aierpbackend.dto.gemini.GeminiResponse;
import com.erp.aierpbackend.entity.Discrepancy;
import com.erp.aierpbackend.entity.Job;
import com.erp.aierpbackend.entity.VerificationResult;
import com.erp.aierpbackend.repository.DiscrepancyRepository;
import com.erp.aierpbackend.repository.JobRepository;
import com.erp.aierpbackend.repository.VerificationResultRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value; // Import Value
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional; // Import Transactional
import reactor.core.publisher.Mono;

import java.time.LocalDateTime; // Import LocalDateTime
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

    private final GeminiClientService geminiClientService;
    private final JobRepository jobRepository;
    private final VerificationResultRepository verificationResultRepository;
    private final DiscrepancyRepository discrepancyRepository;
    private final BusinessCentralService businessCentralService;
    private final ActivityLogService activityLogService; // Inject ActivityLogService

    @Value("${dynamics.bc.odata.status-field-name}") // Inject config property
    private String bcStatusFieldName;

    @Autowired
    public NlpService(GeminiClientService geminiClientService,
                      JobRepository jobRepository,
                      VerificationResultRepository verificationResultRepository,
                      DiscrepancyRepository discrepancyRepository,
                      BusinessCentralService businessCentralService,
                      ActivityLogService activityLogService) { // Add ActivityLogService
        this.geminiClientService = geminiClientService;
        this.jobRepository = jobRepository;
        this.verificationResultRepository = verificationResultRepository;
        this.discrepancyRepository = discrepancyRepository;
        this.businessCentralService = businessCentralService;
        this.activityLogService = activityLogService; // Assign ActivityLogService
    }

    @Transactional // Ensure database operations are atomic
    public Mono<NlpResponse> verifyDocumentAndPersist(NlpRequest request) {
        // Safely get and cast metadata
        Object bcIdObj = request.getMetadata().get("businessCentralJobId");
        String businessCentralJobId = (bcIdObj instanceof String) ? (String) bcIdObj : null;

        if (businessCentralJobId == null || businessCentralJobId.isBlank()) {
            log.error("Business Central Job ID is missing or not a String in the request metadata.");
            // Return an error response or throw an exception wrapped in Mono.error
            return Mono.error(new IllegalArgumentException("Business Central Job ID is required."));
        }

        log.debug("Processing NLP request for BC Job ID: {}", businessCentralJobId);
        // Log NLP start event (Job ID might not exist yet if it's new)
        activityLogService.recordActivity(ActivityLogService.EVENT_NLP_STARTED,
                "NLP verification process started for BC Job ID: " + businessCentralJobId,
                null, // Internal Job ID not known yet if new
                "System"); // Assuming system triggers this

        // Find or create the Job entity
        Job job = jobRepository.findByBusinessCentralJobId(businessCentralJobId)
                .orElseGet(() -> {
                    log.info("Creating new Job entity for BC Job ID: {}", businessCentralJobId);
                    Job newJob = new Job();
                    newJob.setBusinessCentralJobId(businessCentralJobId);
                    // Safely get and cast optional metadata
                    Object titleObj = request.getMetadata().get("jobTitle");
                    if (titleObj instanceof String) {
                        newJob.setJobTitle((String) titleObj);
                    }
                    Object customerObj = request.getMetadata().get("customerName");
                     if (customerObj instanceof String) {
                        newJob.setCustomerName((String) customerObj);
                    }
                    return newJob; // Not saved yet
                });

        // Update status to PROCESSING before calling API
        job.setStatus(Job.JobStatus.PROCESSING);
        jobRepository.save(job); // Save initial state or update status

        String prompt = buildVerificationPrompt(request);

        // Call the Gemini API
        return geminiClientService.callGeminiApi(prompt)
                .flatMap(apiResponse -> {
                    log.debug("Received raw response from Gemini API for BC Job ID {}: {}", businessCentralJobId, apiResponse);
                    NlpResponse nlpResponse = parseApiResponse(apiResponse, request); // Parse the response

                    // Create and populate VerificationResult
                    VerificationResult verificationResult = new VerificationResult();
                    verificationResult.setVerificationTimestamp(LocalDateTime.now());
                    verificationResult.setRawAiResponse(nlpResponse.getExplanation()); // Store raw AI text
                    // Extract confidence if available
                    Object confidenceObj = nlpResponse.getProcessingMetadata().get("confidence");
                    if (confidenceObj instanceof Number) {
                        verificationResult.setAiConfidenceScore(((Number) confidenceObj).doubleValue());
                    }

                    // Create and add Discrepancy entities
                    if (nlpResponse.getDiscrepancies() != null) {
                        for (NlpResponse.Discrepancy dtoDiscrepancy : nlpResponse.getDiscrepancies()) {
                            Discrepancy entityDiscrepancy = new Discrepancy();
                            // Map DTO field to entity discrepancyType (or use field if more appropriate)
                            entityDiscrepancy.setDiscrepancyType(dtoDiscrepancy.getField() != null ? dtoDiscrepancy.getField() : "UNKNOWN_FIELD");
                            entityDiscrepancy.setFieldName(dtoDiscrepancy.getField());
                            entityDiscrepancy.setExpectedValue(dtoDiscrepancy.getExpectedValue());
                            entityDiscrepancy.setActualValue(dtoDiscrepancy.getActualValue());
                            entityDiscrepancy.setDescription(dtoDiscrepancy.getDescription());
                            verificationResult.addDiscrepancy(entityDiscrepancy); // Associates and sets back-reference
                        }
                    }

                    // Link VerificationResult to Job
                    job.setVerificationResult(verificationResult); // Associates and sets back-reference

                    // Update Job status based on NLP result
                    switch (nlpResponse.getStatus()) {
                        case "VERIFIED":
                            job.setStatus(Job.JobStatus.VERIFIED);
                            break;
                        case "FLAGGED":
                            job.setStatus(Job.JobStatus.FLAGGED);
                            break;
                        default:
                            job.setStatus(Job.JobStatus.ERROR);
                            break;
                    }
                    job.setLastProcessedAt(LocalDateTime.now());

                    // Save the updated Job (cascades to VerificationResult and Discrepancies)
                    // Save the updated Job (cascades to VerificationResult and Discrepancies)
                    Job savedJob = jobRepository.save(job); // Save first to ensure transaction consistency
                    log.info("Saved verification results locally for BC Job ID: {}, Status: {}", businessCentralJobId, savedJob.getStatus());
                    activityLogService.recordActivity(ActivityLogService.EVENT_JOB_PROCESSED,
                            "Job processed locally. Status: " + savedJob.getStatus(),
                            savedJob.getId(),
                            "System");

                    // Map internal status to BC status value (example)
                    String bcStatusValue;
                    switch (savedJob.getStatus()) {
                        case VERIFIED: bcStatusValue = "AI Verified"; break;
                        case FLAGGED: bcStatusValue = "AI Flagged"; break;
                        case ERROR: bcStatusValue = "AI Error"; break;
                        default: bcStatusValue = "AI Pending"; break; // Or some other default
                    }

                    // Chain the Business Central update after local save
                    final Job finalSavedJob = savedJob; // Need final variable for lambda
                    return businessCentralService.updateJobStatusInBC(businessCentralJobId, bcStatusFieldName, bcStatusValue)
                        .doOnSuccess(v -> {
                            log.info("Successfully updated status in Business Central for Job ID: {}", businessCentralJobId);
                            activityLogService.recordActivity(ActivityLogService.EVENT_BC_UPDATE_SUCCESS,
                                    "Successfully updated status in Business Central to: " + bcStatusValue,
                                    finalSavedJob.getId(),
                                    "System");
                        })
                        .doOnError(e -> {
                             log.error("Failed to update status in Business Central for Job ID: {}. Error: {}", businessCentralJobId, e.getMessage());
                             activityLogService.recordActivity(ActivityLogService.EVENT_BC_UPDATE_FAILURE,
                                     "Failed to update status in Business Central. Error: " + e.getMessage(),
                                     finalSavedJob.getId(),
                                     "System");
                        })
                        // Even if BC update fails, we proceed, but log the error. Consider different error handling if needed.
                        .then(Mono.just(nlpResponse)); // Return the original DTO response after BC update attempt completes
                })
                .onErrorResume(error -> {
                    // Handle errors during API call or processing
                    log.error("Error processing NLP request for BC Job ID {}: {}", businessCentralJobId, error.getMessage());
                    // Update Job status to ERROR
                    Job errorJob = jobRepository.findByBusinessCentralJobId(businessCentralJobId)
                                        .orElse(job); // Use the job instance if already retrieved
                    errorJob.setStatus(Job.JobStatus.ERROR);
                    errorJob.setLastProcessedAt(LocalDateTime.now());
                    Job finalErrorJob = jobRepository.save(errorJob); // Save the error status
                    // Log the error event
                    activityLogService.recordActivity(ActivityLogService.EVENT_ERROR,
                            "Error during NLP processing: " + error.getMessage(),
                            finalErrorJob.getId(), // Use ID from saved error job
                            "System");
                    // Return an error NlpResponse or re-throw wrapped in Mono.error
                    NlpResponse errorNlpResponse = new NlpResponse();
                    errorNlpResponse.setStatus("ERROR");
                    errorNlpResponse.setExplanation("Failed to process document due to internal error: " + error.getMessage());
                    return Mono.just(errorNlpResponse);
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
        // IMPORTANT: This parsing logic needs significant improvement to extract specific discrepancies
        // It should ideally parse structured data (like JSON) from Gemini or use regex/keywords
        // For now, it provides a basic status and a general discrepancy if flagged.
        if (aiContent.toLowerCase().contains("flagged") || aiContent.toLowerCase().contains("discrepancy") || aiContent.contains("error") || aiContent.contains("Fallback:")) {
             response.setStatus("FLAGGED");
             response.setDiscrepancies(new ArrayList<>());
             // Create a *single* general discrepancy DTO using setters
             NlpResponse.Discrepancy generalDiscrepancy = new NlpResponse.Discrepancy();
             generalDiscrepancy.setField("General Flag"); // Use 'field' as the type indicator for now
             generalDiscrepancy.setDescription("AI analysis flagged potential issues based on content: " + aiContent.substring(0, Math.min(aiContent.length(), 200)) + "...");
             // expectedValue and actualValue remain null
             response.getDiscrepancies().add(generalDiscrepancy);
             processingMeta.put("confidence", 0.85); // Example confidence
        } else if (aiContent.contains("No content found")) {
             response.setStatus("ERROR");
             processingMeta.put("confidence", 0.1);
        } else {
             response.setStatus("VERIFIED");
             response.setDiscrepancies(new ArrayList<>()); // Ensure empty list if verified
             processingMeta.put("confidence", 0.92); // Example confidence
        }
        response.setExplanation(aiContent); // Keep the full AI explanation
        response.setProcessingMetadata(processingMeta);
        return response;
    }
}
