package com.erp.aierpbackend.listener;

import com.erp.aierpbackend.config.RabbitMQConfig;
import com.erp.aierpbackend.dto.dynamics.JobLedgerEntryDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import com.erp.aierpbackend.service.NlpService;
import com.erp.aierpbackend.dto.NlpRequest;
import com.erp.aierpbackend.service.BusinessCentralService; // Added import
import com.erp.aierpbackend.service.OcrService; // Added import
import java.util.HashMap;
import java.util.Map;


@Component
@Slf4j
public class JobLedgerIngestionListener {

    private final NlpService nlpService;
    private final BusinessCentralService businessCentralService; // Added
    private final OcrService ocrService; // Added

    @Autowired
    public JobLedgerIngestionListener(NlpService nlpService,
                                      BusinessCentralService businessCentralService, // Added
                                      OcrService ocrService) { // Added
        this.nlpService = nlpService;
        this.businessCentralService = businessCentralService; // Added
        this.ocrService = ocrService; // Added
    }


    @RabbitListener(queues = RabbitMQConfig.BC_JOB_LEDGER_QUEUE_NAME)
    public void handleIncomingMessage(@Payload Object payload) {
        // Handle different payload types
        if (payload instanceof JobLedgerEntryDTO entry) {
            // Existing logic for handling full DTO
            log.info("Received Job Ledger Entry DTO from queue '{}': Entry No={}, Job No={}, Type={}, No={}, Desc={}",
                    RabbitMQConfig.BC_JOB_LEDGER_QUEUE_NAME,
                    entry.getEntryNo(),
                    entry.getJobNo(),
                    entry.getType(),
                    entry.getNo(),
                    entry.getDescription());
            processJobLedgerEntry(entry);
        } else if (payload instanceof String jobNo) {
            // Logic for handling manual trigger with just Job No
            log.info("Received manual trigger (Job No String) from queue '{}': Job No={}",
                    RabbitMQConfig.BC_JOB_LEDGER_QUEUE_NAME,
                    jobNo);
            processManualTrigger(jobNo);
        } else {
            log.warn("Received message with unexpected payload type: {}", payload != null ? payload.getClass().getName() : "null");
            // Decide how to handle unknown types (e.g., log, move to DLQ)
        }
    }

    private void processJobLedgerEntry(JobLedgerEntryDTO entry) {
         try {
            log.debug("Processing Job Ledger Entry No: {}", entry.getEntryNo());
            // --- Placeholder for actual processing logic based on DTO ---
            // This might involve fetching related documents, calling OCR/NLP etc.
            // Example: triggerVerificationProcess(entry.getJobNo(), entry);
            log.info("Successfully processed Job Ledger Entry No: {}", entry.getEntryNo());
        } catch (Exception e) {
            log.error("Error processing Job Ledger Entry No: {}. Error: {}", entry.getEntryNo(), e.getMessage(), e);
            // Handle error (e.g., DLQ)
        }
    }

    private void processManualTrigger(String jobNo) {
         try {
            log.debug("Processing manual trigger for Job No: {}", jobNo);
            // --- Processing logic based on Job No ---

            // 1. Fetch necessary job details/documents from Business Central using jobNo
            // !!! CRITICAL TODO: Implement this fetching logic in BusinessCentralService !!!
            // This needs to retrieve job metadata AND the associated document(s)
            log.warn("Fetching document for Job No {} is not implemented yet.", jobNo);
            // Example placeholder for fetched data (replace with actual fetched data)
            String fetchedDocumentText = ""; // Replace with actual text after OCR
            Map<String, Object> fetchedMetadata = new HashMap<>(); // Changed type to Map<String, Object>
            fetchedMetadata.put("businessCentralJobId", jobNo);
            // fetchedMetadata.put("jobTitle", "Fetched Title");
            // fetchedMetadata.put("customerName", "Fetched Customer");

            // 2. Call OcrService if documents are involved (Skipped as document fetching is not implemented)
            // byte[] documentBytes = ... // Get document bytes from BC
            // String extractedText = ocrService.extractTextFromPdf(documentBytes); // Or similar method

            // 3. Call NlpService with the required data
            // Use the constructor NlpRequest(String cleanedText, Map<String, Object> metadata)
            NlpRequest nlpRequest = new NlpRequest(fetchedDocumentText, fetchedMetadata);

            log.info("Triggering NLP service for manually triggered Job No: {}", jobNo);

            // Call the NLP service to persist and attempt verification
            nlpService.verifyDocumentAndPersist(nlpRequest)
                .doOnError(error -> log.error("Error during NLP processing triggered manually for Job No: {}", jobNo, error))
                .doOnSuccess(response -> log.info("NLP processing completed for manually triggered Job No: {}. Status: {}", jobNo, response.getStatus()))
                .subscribe(); // Subscribe to execute the Mono

            // Note: The above call is asynchronous. The log message below might appear before processing finishes.
            log.info("Manual trigger processing initiated for Job No: {}", jobNo);

        } catch (Exception e) {
            log.error("Error initiating manual trigger processing for Job No: {}. Error: {}", jobNo, e.getMessage(), e);
            // Handle error (e.g., DLQ)
        }
    }

    // Optional: Refactor common processing logic into a separate method
    private void triggerVerificationProcess(String jobNo /*, potentially other data */) {
         // Inject and use OcrService, NlpService etc. here
         log.info("Triggering verification process for Job No: {}", jobNo);
         // ... implementation ...
    }

    /* Original single-type handler commented out for reference
    @RabbitListener(queues = RabbitMQConfig.BC_JOB_LEDGER_QUEUE_NAME)
    public void handleJobLedgerEntry(@Payload JobLedgerEntryDTO entry) {
        // The @Payload annotation automatically deserializes the JSON message
        // body into a JobLedgerEntryDTO object thanks to the MessageConverter bean.

        log.info("Received Job Ledger Entry from queue '{}': Entry No={}, Job No={}, Type={}, No={}, Desc={}",
                RabbitMQConfig.BC_JOB_LEDGER_QUEUE_NAME,
                entry.getEntryNo(),
                entry.getJobNo(),
                entry.getType(),
                entry.getNo(),
                entry.getDescription());

        try {
            // --- Placeholder for actual processing logic ---
            // Examples:
            // 1. Validate the data
            // 2. Transform the DTO into an internal domain entity
            // 3. Save the entity to a database (e.g., using a JpaRepository)
            // 4. Trigger other services or processes based on the data
            // 5. Update status in another system

            log.debug("Processing Job Ledger Entry No: {}", entry.getEntryNo());
            // Simulate processing time
            // Thread.sleep(100); // Be cautious with Thread.sleep in listeners

            log.info("Successfully processed Job Ledger Entry No: {}", entry.getEntryNo());

        } catch (Exception e) {
            log.error("Error processing Job Ledger Entry No: {}. Error: {}", entry.getEntryNo(), e.getMessage(), e);
            // --- Error Handling ---
            // Depending on the error, you might:
            // - Log the error (already done)
            // - Retry the message (requires specific configuration, potentially with backoff)
            // - Move the message to a Dead Letter Queue (DLQ) if configured
            // - Notify administrators
            // For now, we just log. If not handled, the message might be requeued depending on config.
            // Throwing an exception here might cause the message to be requeued indefinitely if not configured properly.
            // Consider using specific exception types and potentially a DLQ strategy.
            // throw new AmqpRejectAndDontRequeueException("Failed to process message", e); // Example to prevent requeue
        }
    }
    */
}
