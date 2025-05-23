package com.erp.aierpbackend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Service for interacting with Business Central Web Services.
 * This service uses the Business Central Web Services API instead of OData
 * to avoid issues with ETag requirements.
 */
@Service
@Slf4j
public class BusinessCentralWebService {

    private final WebClient webClient;
    private final String basicAuthHeader;

    /**
     * Constructor with configuration from application properties.
     *
     * @param webClientBuilder The WebClient builder
     * @param baseUrl The base URL for Business Central Web Services
     * @param username The username for Business Central
     * @param password The password for Business Central
     */
    public BusinessCentralWebService(
            WebClient.Builder webClientBuilder,
            @Value("${dynamics.bc.odata.base-url}") String baseUrl,
            @Value("${dynamics.bc.odata.username}") String username,
            @Value("${dynamics.bc.odata.key}") String password) {

        this.webClient = webClientBuilder
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();

        // Create Basic Auth header
        String auth = username + ":" + password;
        this.basicAuthHeader = "Basic " + java.util.Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));

        log.info("BusinessCentralWebService initialized with base URL: {}", baseUrl);
    }

    /**
     * Updates verification fields for a job using the Business Central Web Services API.
     * For successful verification, updates both date and comment. For failed verification, updates only comment.
     *
     * @param jobNo The job number
     * @param comment The verification comment
     * @param isSuccess Whether the verification was successful (updates date) or failed (only updates comment)
     * @return A Mono indicating completion or error
     */
    public Mono<Void> updateAllVerificationFields(String jobNo, String comment, boolean isSuccess) {
        if (isSuccess) {
            log.info("Updating second check date field and verification comment via Web Services for successful verification, Job No: {}", jobNo);
        } else {
            log.info("Updating only verification comment via Web Services for failed verification, Job No: {}", jobNo);
        }

        // Get current date and time (only used for successful verifications)
        LocalDate currentDate = LocalDate.now();
        String formattedDate = currentDate.format(DateTimeFormatter.ISO_LOCAL_DATE); // Format as YYYY-MM-DD

        // Create request body based on success/failure
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("jobNo", jobNo);
        requestBody.put("comment", comment);
        if (isSuccess) {
            requestBody.put("checkDate", formattedDate);
        }

        // Call the Web Service endpoint
        // Using the standard OData endpoint but with a different HTTP method
        String webServiceEndpoint = "/Job_Card(No='" + jobNo + "')/Microsoft.NAV.updateVerificationFields";

        return this.webClient.post()
                .uri(webServiceEndpoint)
                .header(HttpHeaders.AUTHORIZATION, this.basicAuthHeader)
                .bodyValue(requestBody)
                .retrieve()
                .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(), response ->
                    response.bodyToMono(String.class)
                        .flatMap(body -> {
                            String errorMessage = String.format("Error calling Business Central Web Service for Job No: %s. Status: %s, Body: %s",
                                    jobNo, response.statusCode(), body);
                            log.error(errorMessage);
                            return Mono.error(new RuntimeException(errorMessage));
                        })
                )
                .toBodilessEntity()
                .doOnSuccess(response -> log.info("Successfully updated second check date field via Web Service for Job No: {}", jobNo))
                .then()
                .onErrorResume(error -> {
                    log.error("Error updating second check date field via Web Service for Job No: {}", jobNo, error);
                    return Mono.error(new RuntimeException("Failed to update second check date field via Web Service for " + jobNo, error));
                });
    }

    /**
     * Backward compatibility method that assumes successful verification (updates both date and comment).
     *
     * @param jobNo The job number
     * @param comment The verification comment
     * @return A Mono indicating completion or error
     */
    public Mono<Void> updateAllVerificationFields(String jobNo, String comment) {
        return updateAllVerificationFields(jobNo, comment, true); // Default to success (updates both fields)
    }

    /**
     * Updates a specific field for a job using the Business Central Web Services API.
     *
     * @param jobNo The job number
     * @param fieldName The field name to update
     * @param fieldValue The new value for the field
     * @return A Mono indicating completion or error
     */
    public Mono<Void> updateJobCardField(String jobNo, String fieldName, String fieldValue) {
        log.info("Updating field {} via Web Services for Job No: {}", fieldName, jobNo);

        // Create request body
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("jobNo", jobNo);
        requestBody.put("fieldName", fieldName);
        requestBody.put("fieldValue", fieldValue);

        // Call the Web Service endpoint
        // Using the standard OData endpoint but with a different HTTP method
        String webServiceEndpoint = "/Job_Card(No='" + jobNo + "')/Microsoft.NAV.updateField";

        return this.webClient.post()
                .uri(webServiceEndpoint)
                .header(HttpHeaders.AUTHORIZATION, this.basicAuthHeader)
                .bodyValue(requestBody)
                .retrieve()
                .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(), response ->
                    response.bodyToMono(String.class)
                        .flatMap(body -> {
                            String errorMessage = String.format("Error calling Business Central Web Service for Job No: %s. Status: %s, Body: %s",
                                    jobNo, response.statusCode(), body);
                            log.error(errorMessage);
                            return Mono.error(new RuntimeException(errorMessage));
                        })
                )
                .toBodilessEntity()
                .doOnSuccess(response -> log.info("Successfully updated field {} via Web Service for Job No: {}", fieldName, jobNo))
                .then()
                .onErrorResume(error -> {
                    log.error("Error updating field {} via Web Service for Job No: {}", fieldName, jobNo, error);
                    return Mono.error(new RuntimeException("Failed to update field via Web Service for " + jobNo, error));
                });
    }
}
