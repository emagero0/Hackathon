package com.erp.aierpbackend.service;

import com.erp.aierpbackend.dto.dynamics.JobLedgerEntryDTO;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.http.HttpMethod; // Import HttpMethod
import org.springframework.http.MediaType; // Import MediaType
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Map; // Import Map
import java.util.Base64;
import java.util.List;

@Service
@Slf4j
public class BusinessCentralService {

    private final WebClient webClient;
    private final String oDataBaseUrl;
    private final String basicAuthHeader;

    // Helper class to map the OData response structure
    private static class ODataResponseWrapper {
        @JsonProperty("value")
        List<JobLedgerEntryDTO> value;
    }

    public BusinessCentralService(
            WebClient.Builder webClientBuilder,
            @Value("${dynamics.bc.odata.base-url}") String oDataBaseUrl,
            @Value("${dynamics.bc.odata.username}") String username,
            @Value("${dynamics.bc.odata.key}") String key) {
        this.oDataBaseUrl = oDataBaseUrl;
        this.webClient = webClientBuilder.baseUrl(oDataBaseUrl).build();

        // Prepare Basic Authentication header
        String auth = username + ":" + key;
        this.basicAuthHeader = "Basic " + Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
        log.info("BusinessCentralService initialized. Base URL: {}", oDataBaseUrl);
        // Avoid logging the key itself for security
        log.debug("Using Basic Auth Header for user: {}", username);
    }

    public Flux<JobLedgerEntryDTO> fetchJobLedgerEntries(String jobNo) {
        log.info("Fetching Job Ledger Entries for Job No: {}", jobNo);

        // Construct the specific endpoint URL with filter
        String endpointPath = "/Job_Ledger_Entries";
        String filterQuery = "Job_No eq '" + jobNo + "'"; // Ensure proper quoting for OData string literals

        // Construct the URI using WebClient's builder to handle encoding properly
        String filterQueryParam = "$filter";

        log.debug("Requesting URI with base: {}, path: {}, filter: {}", this.oDataBaseUrl, endpointPath, filterQuery);
        return this.webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(endpointPath)
                        .queryParam(filterQueryParam, filterQuery) // Let WebClient handle encoding
                        .build())
                .header(HttpHeaders.AUTHORIZATION, this.basicAuthHeader)
                .retrieve()
                .onStatus(status -> status.equals(HttpStatus.UNAUTHORIZED), response -> {
                    log.error("Unauthorized access to Business Central API. Check credentials.");
                    return Mono.error(new RuntimeException("Unauthorized access to Business Central API. Status: " + response.statusCode()));
                })
                .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(), response -> {
                    log.error("Error fetching data from Business Central API. Status: {}", response.statusCode());
                    return response.bodyToMono(String.class)
                            .flatMap(body -> {
                                log.error("Error body: {}", body);
                                return Mono.error(new RuntimeException("Error from Business Central API: " + response.statusCode() + " - " + body));
                            });
                })
                .bodyToMono(ODataResponseWrapper.class) // Expect the OData wrapper
                .flatMapMany(responseWrapper -> {
                    if (responseWrapper != null && responseWrapper.value != null) {
                        log.info("Successfully fetched {} Job Ledger Entries for Job No: {}", responseWrapper.value.size(), jobNo);
                        return Flux.fromIterable(responseWrapper.value); // Emit each entry as an item in the Flux
                    } else {
                        log.warn("Received null or empty response wrapper for Job No: {}", jobNo);
                        return Flux.empty();
                    }
                })
                .doOnError(error -> log.error("Error during Business Central API call for Job No: {}", jobNo, error))
                .onErrorResume(e -> {
                    log.error("Failed to fetch job ledger entries for job {}: {}", jobNo, e.getMessage());
                    return Flux.error(new RuntimeException("Failed to fetch job ledger entries for job " + jobNo, e)); // Propagate a specific error
                });
    }

    /**
     * Updates a specific field (e.g., AI verification status) on a Job entity in Business Central.
     *
     * @param jobNo The Job_No of the job to update.
     * @param statusFieldName The OData field name to update (e.g., "AI_Verification_Status").
     * @param statusValue The new value for the status field.
     * @return A Mono indicating completion or error.
     */
    public Mono<Void> updateJobStatusInBC(String jobNo, String statusFieldName, String statusValue) {
        log.info("Attempting to update status for Job No: {} in Business Central. Field: {}, Value: {}", jobNo, statusFieldName, statusValue);

        // Construct the specific endpoint URL for the Job entity
        // IMPORTANT: Replace 'Jobs' with the actual OData entity set name for Jobs if different.
        // The key format ('jobNo') assumes Job_No is the primary key and is a string. Adjust if needed.
        String endpointPath = "/Jobs('" + jobNo + "')";

        // Create the request body with the field to update
        Map<String, String> requestBody = Map.of(statusFieldName, statusValue);

        log.debug("Sending PATCH request to URI: {}{}", this.oDataBaseUrl, endpointPath);
        return this.webClient.patch() // Use PATCH for partial updates
                .uri(endpointPath)
                .header(HttpHeaders.AUTHORIZATION, this.basicAuthHeader)
                .header(HttpHeaders.IF_MATCH, "*") // Required for OData updates to handle concurrency
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody) // Set the request body
                .retrieve()
                .onStatus(status -> status.equals(HttpStatus.UNAUTHORIZED), response -> {
                    log.error("Unauthorized access during update for Job No: {}. Check credentials.", jobNo);
                    return Mono.error(new RuntimeException("Unauthorized access to Business Central API during update. Status: " + response.statusCode()));
                })
                .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(), response -> {
                    log.error("Error updating Job No: {} in Business Central API. Status: {}", jobNo, response.statusCode());
                    return response.bodyToMono(String.class)
                            .flatMap(body -> {
                                log.error("Error body during update: {}", body);
                                return Mono.error(new RuntimeException("Error from Business Central API during update: " + response.statusCode() + " - " + body));
                            });
                })
                .toBodilessEntity() // We don't need the response body for a successful update (usually 204 No Content)
                .doOnSuccess(response -> log.info("Successfully updated status for Job No: {} in Business Central. Status code: {}", jobNo, response.getStatusCode()))
                .doOnError(error -> log.error("Error during Business Central API update call for Job No: {}", jobNo, error))
                .then(); // Convert to Mono<Void> to signal completion
    }
}
