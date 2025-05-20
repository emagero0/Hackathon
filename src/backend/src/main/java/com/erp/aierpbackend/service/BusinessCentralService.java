package com.erp.aierpbackend.service;

// Import new DTOs and generic wrapper
import com.erp.aierpbackend.dto.dynamics.*;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference; // Import for generic response types
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.http.HttpMethod; // Import HttpMethod
import org.springframework.http.MediaType; // Import MediaType
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple5; // Import Tuple5
import reactor.util.function.Tuples; // Import Tuples

import java.nio.charset.StandardCharsets;
import java.time.LocalDate; // Import LocalDate
import java.time.format.DateTimeFormatter; // Import DateTimeFormatter
import java.util.Collections; // Import Collections
import java.util.Map;
import java.util.Base64;
import java.util.List;
import java.util.Optional; // Import Optional

@Service
@Slf4j
public class BusinessCentralService {

    private final WebClient webClient;
    private final String oDataBaseUrl;
    private final String basicAuthHeader;

    // Define the generic type reference for list responses
    private static final ParameterizedTypeReference<ODataListResponseWrapper<JobLedgerEntryDTO>> JOB_LEDGER_LIST_TYPE =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<ODataListResponseWrapper<JobListDTO>> JOB_LIST_TYPE =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<ODataListResponseWrapper<SalesQuoteDTO>> SALES_QUOTE_LIST_TYPE =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<ODataListResponseWrapper<SalesQuoteLineDTO>> SALES_QUOTE_LINE_LIST_TYPE =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<ODataListResponseWrapper<SalesInvoiceDTO>> SALES_INVOICE_LIST_TYPE =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<ODataListResponseWrapper<SalesInvoiceLineDTO>> SALES_INVOICE_LINE_LIST_TYPE =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<ODataListResponseWrapper<JobAttachmentLinksDTO>> JOB_ATTACHMENT_LINKS_TYPE =
            new ParameterizedTypeReference<>() {};

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
                        .queryParam(filterQueryParam, filterQuery)
                        .build())
                .header(HttpHeaders.AUTHORIZATION, this.basicAuthHeader)
                .retrieve()
                .onStatus(HttpStatus.UNAUTHORIZED::equals, response -> // Use method reference
                    handleError(response, "Unauthorized access fetching Job Ledger Entries for Job No: " + jobNo)
                )
                .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(), response -> // Use method reference
                    handleError(response, "Error fetching Job Ledger Entries for Job No: " + jobNo)
                )
                .bodyToMono(JOB_LEDGER_LIST_TYPE) // Use ParameterizedTypeReference
                .flatMapMany(wrapper -> extractListResult(wrapper, "Job Ledger Entries", jobNo)) // Use helper
                .doOnError(error -> log.error("Error in Job Ledger Entries fetch pipeline for Job No: {}", jobNo, error))
                .onErrorResume(e -> {
                    log.error("Failed to fetch job ledger entries for job {}: {}", jobNo, e.getMessage());
                    return Flux.error(new RuntimeException("Failed to fetch job ledger entries for job " + jobNo, e));
                });
    }

    // --- New Fetch Methods ---

    /**
     * Fetches a single Job List entry based on Job Number.
     * Expects zero or one result.
     */
    public Mono<JobListDTO> fetchJobListEntry(String jobNo) {
        log.info("Fetching Job List entry for Job No: {}", jobNo);
        String endpointPath = "/Job_List";
        String filterQuery = "No eq '" + jobNo + "'";
        String filterQueryParam = "$filter";

        return this.webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(endpointPath)
                        .queryParam(filterQueryParam, filterQuery)
                        .build())
                .header(HttpHeaders.AUTHORIZATION, this.basicAuthHeader)
                .retrieve()
                .onStatus(HttpStatus.UNAUTHORIZED::equals, response ->
                    handleError(response, "Unauthorized access fetching Job List for Job No: " + jobNo)
                )
                .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(), response ->
                    handleError(response, "Error fetching Job List for Job No: " + jobNo)
                )
                .bodyToMono(JOB_LIST_TYPE)
                .map(wrapper -> extractSingleResult(wrapper, "Job List entry", jobNo)) // Use helper
                .onErrorResume(e -> {
                    log.error("Failed to fetch job list entry for job {}: {}", jobNo, e.getMessage());
                    return Mono.error(new RuntimeException("Failed to fetch job list entry for job " + jobNo, e));
                });
    }

    /** Fetches a single Sales Quote header based on Quote Number. */
    public Mono<SalesQuoteDTO> fetchSalesQuote(String quoteNo) {
        log.info("Fetching Sales Quote header for No: {}", quoteNo);
        String endpointPath = "/Sales_Quote";
        String filterQuery = "No eq '" + quoteNo + "'";
        String filterQueryParam = "$filter";

        return this.webClient.get()
                .uri(uriBuilder -> uriBuilder.path(endpointPath).queryParam(filterQueryParam, filterQuery).build())
                .header(HttpHeaders.AUTHORIZATION, this.basicAuthHeader)
                .retrieve()
                .onStatus(HttpStatus.UNAUTHORIZED::equals, response -> handleError(response, "Unauthorized access fetching Sales Quote: " + quoteNo))
                .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(), response -> handleError(response, "Error fetching Sales Quote: " + quoteNo))
                .bodyToMono(SALES_QUOTE_LIST_TYPE)
                .map(wrapper -> extractSingleResult(wrapper, "Sales Quote", quoteNo))
                .onErrorResume(e -> Mono.error(new RuntimeException("Failed to fetch Sales Quote " + quoteNo, e)));
    }

    /** Fetches all Sales Quote lines for a given Quote Number. */
    public Flux<SalesQuoteLineDTO> fetchSalesQuoteLines(String quoteNo) {
        log.info("Fetching Sales Quote Lines for Document No: {}", quoteNo);
        String endpointPath = "/Sales_QuoteSalesLines";
        String filterQuery = "Document_No eq '" + quoteNo + "'";
        String filterQueryParam = "$filter";

        return this.webClient.get()
                .uri(uriBuilder -> uriBuilder.path(endpointPath).queryParam(filterQueryParam, filterQuery).build())
                .header(HttpHeaders.AUTHORIZATION, this.basicAuthHeader)
                .retrieve()
                .onStatus(HttpStatus.UNAUTHORIZED::equals, response -> handleError(response, "Unauthorized access fetching Sales Quote Lines: " + quoteNo))
                .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(), response -> handleError(response, "Error fetching Sales Quote Lines: " + quoteNo))
                .bodyToMono(SALES_QUOTE_LINE_LIST_TYPE)
                .flatMapMany(wrapper -> extractListResult(wrapper, "Sales Quote Lines", quoteNo))
                .onErrorResume(e -> Flux.error(new RuntimeException("Failed to fetch Sales Quote Lines for " + quoteNo, e)));
    }

    /** Fetches a single Sales Invoice header based on Invoice Number. */
    public Mono<SalesInvoiceDTO> fetchSalesInvoice(String invoiceNo) {
        log.info("Fetching Sales Invoice header for No: {}", invoiceNo);
        String endpointPath = "/Sales_Invoice";
        String filterQuery = "No eq '" + invoiceNo + "'";
        String filterQueryParam = "$filter";

        return this.webClient.get()
                .uri(uriBuilder -> uriBuilder.path(endpointPath).queryParam(filterQueryParam, filterQuery).build())
                .header(HttpHeaders.AUTHORIZATION, this.basicAuthHeader)
                .retrieve()
                .onStatus(HttpStatus.UNAUTHORIZED::equals, response -> handleError(response, "Unauthorized access fetching Sales Invoice: " + invoiceNo))
                .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(), response -> handleError(response, "Error fetching Sales Invoice: " + invoiceNo))
                .bodyToMono(SALES_INVOICE_LIST_TYPE)
                .map(wrapper -> extractSingleResult(wrapper, "Sales Invoice", invoiceNo))
                .onErrorResume(e -> Mono.error(new RuntimeException("Failed to fetch Sales Invoice " + invoiceNo, e)));
    }

    /** Fetches all Sales Invoice lines for a given Invoice Number. */
    public Flux<SalesInvoiceLineDTO> fetchSalesInvoiceLines(String invoiceNo) {
        log.info("Fetching Sales Invoice Lines for Document No: {}", invoiceNo);
        String endpointPath = "/Sales_InvoiceSalesLines";
        String filterQuery = "Document_No eq '" + invoiceNo + "'";
        String filterQueryParam = "$filter";

        return this.webClient.get()
                .uri(uriBuilder -> uriBuilder.path(endpointPath).queryParam(filterQueryParam, filterQuery).build())
                .header(HttpHeaders.AUTHORIZATION, this.basicAuthHeader)
                .retrieve()
                .onStatus(HttpStatus.UNAUTHORIZED::equals, response -> handleError(response, "Unauthorized access fetching Sales Invoice Lines: " + invoiceNo))
                .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(), response -> handleError(response, "Error fetching Sales Invoice Lines: " + invoiceNo))
                .bodyToMono(SALES_INVOICE_LINE_LIST_TYPE)
                .flatMapMany(wrapper -> extractListResult(wrapper, "Sales Invoice Lines", invoiceNo))
                .onErrorResume(e -> Flux.error(new RuntimeException("Failed to fetch Sales Invoice Lines for " + invoiceNo, e)));
    }

    /** Fetches Sales Invoice lines filtered by Job Number. */
     public Flux<SalesInvoiceLineDTO> fetchSalesInvoiceLinesByJobNo(String jobNo) {
        log.info("Fetching Sales Invoice Lines for Job No: {}", jobNo);
        String endpointPath = "/Sales_InvoiceSalesLines";
        String filterQuery = "Job_No eq '" + jobNo + "'"; // Filter by Job_No
        String filterQueryParam = "$filter";

        return this.webClient.get()
                .uri(uriBuilder -> uriBuilder.path(endpointPath).queryParam(filterQueryParam, filterQuery).build())
                .header(HttpHeaders.AUTHORIZATION, this.basicAuthHeader)
                .retrieve()
                .onStatus(HttpStatus.UNAUTHORIZED::equals, response -> handleError(response, "Unauthorized access fetching Sales Invoice Lines by Job No: " + jobNo))
                .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(), response -> handleError(response, "Error fetching Sales Invoice Lines by Job No: " + jobNo))
                .bodyToMono(SALES_INVOICE_LINE_LIST_TYPE)
                .flatMapMany(wrapper -> extractListResult(wrapper, "Sales Invoice Lines by Job No", jobNo))
                .onErrorResume(e -> Flux.error(new RuntimeException("Failed to fetch Sales Invoice Lines for Job No " + jobNo, e)));
    }


    /**
     * Fetches all necessary data for verification concurrently.
     * Returns a Mono containing a Tuple5 with the results.
     * Individual elements in the tuple can be null or empty lists if fetching failed or no data was found.
     */
    public Mono<Tuple5<SalesQuoteDTO, List<SalesQuoteLineDTO>, SalesInvoiceDTO, List<SalesInvoiceLineDTO>, List<JobLedgerEntryDTO>>>
    fetchAllVerificationData(String quoteNo, String invoiceNo, String jobNo) {
        log.info("Fetching all verification data for Quote: {}, Invoice: {}, Job: {}", quoteNo, invoiceNo, jobNo);

        // Fetch individual pieces, allowing empty/error results
        Mono<SalesQuoteDTO> salesQuoteMono = fetchSalesQuote(quoteNo)
                .onErrorResume(e -> {
                    log.warn("Failed to fetch Sales Quote {}: {}", quoteNo, e.getMessage());
                    return Mono.empty(); // Return empty Mono on error
                });
        Mono<List<SalesQuoteLineDTO>> salesQuoteLinesMono = fetchSalesQuoteLines(quoteNo)
                .collectList()
                .onErrorResume(e -> {
                    log.warn("Failed to fetch Sales Quote Lines for {}: {}", quoteNo, e.getMessage());
                    return Mono.just(Collections.emptyList()); // Return empty list on error
                });
        Mono<SalesInvoiceDTO> salesInvoiceMono = fetchSalesInvoice(invoiceNo)
                 .onErrorResume(e -> {
                    log.warn("Failed to fetch Sales Invoice {}: {}", invoiceNo, e.getMessage());
                    return Mono.empty();
                });
        // Fetch Sales Invoice Lines by JOB NO as requested in the prompt
        Mono<List<SalesInvoiceLineDTO>> salesInvoiceLinesMono = fetchSalesInvoiceLinesByJobNo(jobNo)
                .collectList()
                .onErrorResume(e -> {
                    log.warn("Failed to fetch Sales Invoice Lines for Job {}: {}", jobNo, e.getMessage());
                    return Mono.just(Collections.emptyList());
                });
        Mono<List<JobLedgerEntryDTO>> jobLedgerEntriesMono = fetchJobLedgerEntries(jobNo)
                .collectList()
                .onErrorResume(e -> {
                    log.warn("Failed to fetch Job Ledger Entries for {}: {}", jobNo, e.getMessage());
                    return Mono.just(Collections.emptyList());
                });

        // Combine results using Mono.zip
        return Mono.zip(
                salesQuoteMono.defaultIfEmpty(new SalesQuoteDTO()), // Provide default empty object if fetch failed/returned empty
                salesQuoteLinesMono,
                salesInvoiceMono.defaultIfEmpty(new SalesInvoiceDTO()),
                salesInvoiceLinesMono,
                jobLedgerEntriesMono
        ).map(tuple -> {
            // Replace default empty objects with null for clarity downstream
            SalesQuoteDTO sq = tuple.getT1().getNo() == null ? null : tuple.getT1();
            SalesInvoiceDTO si = tuple.getT3().getNo() == null ? null : tuple.getT3();
            log.info("Finished fetching all verification data for Job: {}. Quote found: {}, Invoice found: {}, SQ Lines: {}, SI Lines: {}, Ledger Entries: {}",
                     jobNo, sq != null, si != null, tuple.getT2().size(), tuple.getT4().size(), tuple.getT5().size());
            return Tuples.of(sq, tuple.getT2(), si, tuple.getT4(), tuple.getT5());
        });
    }


    /**
     * Fetches job attachment links from Business Central.
     *
     * @param jobNo The job number to fetch attachments for
     * @return A Mono containing the JobAttachmentLinksDTO
     */
    public Mono<JobAttachmentLinksDTO> fetchJobAttachmentLinks(String jobNo) {
        log.info("Fetching Job Attachment Links for Job No: {}", jobNo);

        String endpointPath = "/JobAttachmentLinks";
        String filterQuery = "No eq '" + jobNo + "'";
        String filterQueryParam = "$filter";

        return this.webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(endpointPath)
                        .queryParam(filterQueryParam, filterQuery)
                        .build())
                .header(HttpHeaders.AUTHORIZATION, this.basicAuthHeader)
                .retrieve()
                .onStatus(HttpStatus.UNAUTHORIZED::equals, response ->
                    handleError(response, "Unauthorized access fetching Job Attachment Links for Job No: " + jobNo)
                )
                .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(), response ->
                    handleError(response, "Error fetching Job Attachment Links for Job No: " + jobNo)
                )
                .bodyToMono(JOB_ATTACHMENT_LINKS_TYPE)
                .map(wrapper -> extractSingleResult(wrapper, "Job Attachment Links", jobNo))
                .onErrorResume(e -> {
                    log.error("Failed to fetch job attachment links for job {}: {}", jobNo, e.getMessage());
                    return Mono.error(new RuntimeException("Failed to fetch job attachment links for job " + jobNo, e));
                });
    }

    // --- Update Method ---

    /**
     * Updates a specific field on the Job_Card entity in Business Central using PATCH.
     *
     * @param jobNo The Job_No of the job to update.
     * @param fieldName The OData field name to update (e.g., "_x0032_nd_Check_Date").
     * @param value The new value for the field.
     * @return A Mono indicating completion or error.
     */
    public Mono<Void> updateJobCardField(String jobNo, String fieldName, String value) {
        log.info("Attempting to update Job_Card for Job No: {}. Field: {}, Value: {}", jobNo, fieldName, value);

        // Need to fetch ETag first for PATCH
        String jobCardEndpointPath = "/Job_Card(No='" + jobNo + "')";

        // Step 1: Fetch the current entity to get the ETag
        return this.webClient.get()
                .uri(jobCardEndpointPath)
                .header(HttpHeaders.AUTHORIZATION, this.basicAuthHeader)
                .retrieve()
                .toEntity(String.class) // Fetch response headers
                .flatMap(entity -> {
                    String etag = entity.getHeaders().getETag();
                    if (etag == null) {
                        log.error("ETag not found for Job_Card Job No: {}. Cannot perform PATCH.", jobNo);
                        return Mono.error(new RuntimeException("ETag not found for Job_Card update."));
                    }
                    log.debug("Obtained ETag for Job_Card {}: {}", jobNo, etag);

                    // Step 2: Perform the PATCH request with the ETag
                    Map<String, String> requestBody = Map.of(fieldName, value);
                    return this.webClient.patch()
                            .uri(jobCardEndpointPath)
                            .header(HttpHeaders.AUTHORIZATION, this.basicAuthHeader)
                            .header(HttpHeaders.IF_MATCH, etag) // Use the fetched ETag
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(requestBody)
                            .retrieve()
                            .onStatus(HttpStatus.UNAUTHORIZED::equals, response ->
                                handleError(response, "Unauthorized access updating Job_Card for Job No: " + jobNo)
                            )
                            .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(), response ->
                                handleError(response, "Error updating Job_Card for Job No: " + jobNo)
                            )
                            .toBodilessEntity()
                            .doOnSuccess(response -> log.info("Successfully updated Job_Card for Job No: {}. Field: {}, Status code: {}", jobNo, fieldName, response.getStatusCode()))
                            .then(); // Convert to Mono<Void>
                })
                .doOnError(error -> log.error("Error during Job_Card update process for Job No: {}", jobNo, error))
                .onErrorResume(e -> Mono.error(new RuntimeException("Failed to update Job_Card for " + jobNo, e)));
    }

    /**
     * Specific method to update the second check date on the Job_Card.
     * @param jobNo The Job Number.
     * @param date The date to set.
     * @return A Mono indicating completion or error.
     */
    public Mono<Void> updateSecondCheckDate(String jobNo, LocalDate date) {
        String formattedDate = date.format(DateTimeFormatter.ISO_LOCAL_DATE); // Format as YYYY-MM-DD
        return updateJobCardField(jobNo, "_x0032_nd_Check_Date", formattedDate);
    }

    // --- Helper Methods ---

    /** Handles HTTP errors from WebClient calls. */
    private Mono<? extends Throwable> handleError(org.springframework.web.reactive.function.client.ClientResponse response, String contextMessage) {
        return response.bodyToMono(String.class)
                .defaultIfEmpty("[No response body]")
                .flatMap(body -> {
                    String errorMessage = String.format("%s. Status: %s, Body: %s", contextMessage, response.statusCode(), body);
                    log.error(errorMessage);
                    // Consider creating a specific exception type
                    return Mono.error(new RuntimeException(errorMessage));
                });
    }

    /** Extracts a single expected result from an OData list wrapper. */
    private <T> T extractSingleResult(ODataListResponseWrapper<T> wrapper, String entityName, String identifier) {
        if (wrapper != null && wrapper.getValue() != null && !wrapper.getValue().isEmpty()) {
            if (wrapper.getValue().size() > 1) {
                log.warn("Expected 1 {} for ID: {}, but found {}. Returning first.", entityName, identifier, wrapper.getValue().size());
            }
            log.debug("Successfully extracted single {} for ID: {}", entityName, identifier);
            return wrapper.getValue().get(0);
        } else {
            log.warn("No {} found for ID: {}", entityName, identifier);
            return null; // Or throw specific exception if required
        }
    }

     /** Extracts a list of results from an OData list wrapper. */
    private <T> Flux<T> extractListResult(ODataListResponseWrapper<T> wrapper, String entityName, String identifier) {
        if (wrapper != null && wrapper.getValue() != null) {
            log.debug("Successfully extracted {} {} items for ID: {}", wrapper.getValue().size(), entityName, identifier);
            return Flux.fromIterable(wrapper.getValue());
        } else {
            log.warn("Received null or empty wrapper for {} for ID: {}", entityName, identifier);
            return Flux.empty();
        }
    }
}
