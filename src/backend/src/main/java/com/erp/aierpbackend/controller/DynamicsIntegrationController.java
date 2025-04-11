package com.erp.aierpbackend.controller;

import com.erp.aierpbackend.config.RabbitMQConfig;
import com.erp.aierpbackend.service.BusinessCentralService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/dynamics") // Changed base path for clarity
@RequiredArgsConstructor // Lombok constructor injection
@Slf4j
public class DynamicsIntegrationController {

    private final BusinessCentralService businessCentralService;
    private final RabbitTemplate rabbitTemplate;

    // Endpoint to fetch and queue Job Ledger Entries (original functionality)
    @PostMapping("/ingest/job-ledger/{jobNo}")
    public Mono<ResponseEntity<String>> ingestJobLedgerEntries(@PathVariable String jobNo) {
        log.info("Received request to ingest Job Ledger Entries for Job No: {}", jobNo);

        return businessCentralService.fetchJobLedgerEntries(jobNo)
            .flatMap(entry -> {
                // Send each entry as a separate message to RabbitMQ
                try {
                    log.debug("Sending entry {} for job {} to RabbitMQ queue {}", entry.getEntryNo(), jobNo, RabbitMQConfig.BC_JOB_LEDGER_QUEUE_NAME);
                    rabbitTemplate.convertAndSend(
                        RabbitMQConfig.EXCHANGE_NAME,
                        RabbitMQConfig.BC_JOB_LEDGER_ROUTING_KEY,
                        entry // Jackson2JsonMessageConverter will serialize this DTO
                    );
                    return Mono.just(entry); // Pass the entry along if needed, or just signal success
                } catch (Exception e) {
                    log.error("Failed to send Job Ledger Entry {} for job {} to RabbitMQ", entry.getEntryNo(), jobNo, e);
                    // Decide on error handling: continue processing others or fail the whole request?
                    // Here, we log and continue, but potentially return an error signal
                    return Mono.error(new RuntimeException("Failed to publish message for entry " + entry.getEntryNo(), e));
                }
            })
            .collectList() // Collect all results (or signals) after processing the Flux
            .map(results -> {
                // If we reach here without error, it means all messages were attempted to be sent
                log.info("Successfully queued all fetched Job Ledger Entries for Job No: {}", jobNo);
                // Return 202 Accepted as the processing is asynchronous
                return ResponseEntity.accepted().body("Ingestion request accepted for Job No: " + jobNo + ". " + results.size() + " entries queued.");
            })
            .onErrorResume(e -> {
                // Handle errors during fetching or sending
                log.error("Error during ingestion process for Job No: {}", jobNo, e);
                // Return an appropriate error response
                return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                 .body("Failed to ingest data for Job No: " + jobNo + ". Error: " + e.getMessage()));
            });
    }

    /**
     * Manually triggers the second checking process for a specific job by sending its Job_No to the queue.
     * This is primarily for testing purposes.
     *
     * @param jobNo The Business Central Job_No to trigger processing for.
     * @return ResponseEntity indicating acceptance or failure.
     */
    @GetMapping("/trigger-job-check/{jobNo}")
    public ResponseEntity<String> triggerJobCheck(@PathVariable String jobNo) {
        log.info("Received manual request to trigger second check for Job No: {}", jobNo);
        try {
            // We need to determine what message format the JobLedgerIngestionListener expects.
            // Assuming it expects the Job_No as a simple String message for now.
            // If it expects a JobLedgerEntryDTO, we might need to fetch one first or send a simplified message.
            // Let's send the Job_No string directly.
            log.debug("Sending Job No {} to RabbitMQ queue {} with routing key {}",
                      jobNo, RabbitMQConfig.BC_JOB_LEDGER_QUEUE_NAME, RabbitMQConfig.BC_JOB_LEDGER_ROUTING_KEY);

            rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE_NAME,
                RabbitMQConfig.BC_JOB_LEDGER_ROUTING_KEY,
                jobNo // Sending the Job_No as the message payload
            );

            log.info("Successfully queued manual trigger for Job No: {}", jobNo);
            return ResponseEntity.accepted().body("Manual trigger accepted for Job No: " + jobNo + ". Processing initiated.");

        } catch (Exception e) {
            log.error("Failed to send manual trigger message for Job No {} to RabbitMQ", jobNo, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                 .body("Failed to trigger processing for Job No: " + jobNo + ". Error: " + e.getMessage());
        }
    }
}
