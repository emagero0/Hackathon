package com.erp.aierpbackend.service;

// Import new DTOs and generic wrapper
import com.erp.aierpbackend.dto.dynamics.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference; // Import for generic response types
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.http.MediaType; // Import MediaType
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple5; // Import Tuple5
import reactor.util.function.Tuples; // Import Tuples

import java.nio.charset.StandardCharsets;
import java.time.LocalDate; // Import LocalDate
import java.time.LocalDateTime; // Import LocalDateTime
import java.time.format.DateTimeFormatter; // Import DateTimeFormatter
import java.util.Collections; // Import Collections
import java.util.HashMap;
import java.util.Map;
import java.util.Base64;
import java.util.List;

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
    private static final ParameterizedTypeReference<ODataListResponseWrapper<JobListDTO>> JOBS_PENDING_SECOND_CHECK_TYPE =
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
                .onStatus(status -> status.equals(HttpStatus.UNAUTHORIZED), response ->
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

        // Special handling for job J069026
        if ("J069026".equals(jobNo)) {
            log.error("SPECIAL DIAGNOSTIC: Fetching Job List entry for problematic job J069026");
        }

        String endpointPath = "/Job_List";
        String filterQuery = "No eq '" + jobNo + "'";
        String filterQueryParam = "$filter";

        // Log the full URL being requested for debugging
        String fullUrl = this.oDataBaseUrl + endpointPath + "?" + filterQueryParam + "=" + filterQuery;

        // Special handling for job J069026
        if ("J069026".equals(jobNo)) {
            log.error("SPECIAL DIAGNOSTIC: Business Central URL for Job List entry for problematic job J069026: {}", fullUrl);
        }

        return this.webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(endpointPath)
                        .queryParam(filterQueryParam, filterQuery)
                        .build())
                .header(HttpHeaders.AUTHORIZATION, this.basicAuthHeader)
                .retrieve()
                .onStatus(status -> status.equals(HttpStatus.UNAUTHORIZED), response -> {
                    // Special handling for job J069026
                    if ("J069026".equals(jobNo)) {
                        log.error("SPECIAL DIAGNOSTIC: UNAUTHORIZED (401) response for Job List entry for problematic job J069026");
                    }
                    return handleError(response, "Unauthorized access fetching Job List for Job No: " + jobNo);
                })
                .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(), response -> {
                    // Special handling for job J069026
                    if ("J069026".equals(jobNo)) {
                        log.error("SPECIAL DIAGNOSTIC: Error response ({}) for Job List entry for problematic job J069026",
                                response.statusCode().value());
                    }
                    return handleError(response, "Error fetching Job List for Job No: " + jobNo);
                })
                .bodyToMono(JOB_LIST_TYPE)
                .doOnSuccess(wrapper -> {
                    // Special handling for job J069026
                    if ("J069026".equals(jobNo)) {
                        if (wrapper == null) {
                            log.error("SPECIAL DIAGNOSTIC: Received null response wrapper for Job List entry for problematic job J069026");
                        } else if (wrapper.getValue() == null) {
                            log.error("SPECIAL DIAGNOSTIC: Received wrapper with null value array for Job List entry for problematic job J069026");
                        } else if (wrapper.getValue().isEmpty()) {
                            log.error("SPECIAL DIAGNOSTIC: Received wrapper with empty value array for Job List entry for problematic job J069026");
                        } else {
                            log.error("SPECIAL DIAGNOSTIC: Successfully received response with {} Job List entries for problematic job J069026",
                                    wrapper.getValue().size());
                        }
                    }
                })
                .map(wrapper -> {
                    JobListDTO result = extractSingleResult(wrapper, "Job List entry", jobNo);
                    // Special handling for job J069026
                    if ("J069026".equals(jobNo)) {
                        if (result == null) {
                            log.error("SPECIAL DIAGNOSTIC: No Job List entry found for problematic job J069026. Job may not exist in Business Central.");
                        } else {
                            log.error("SPECIAL DIAGNOSTIC: Successfully extracted Job List entry for problematic job J069026: {}", result);
                        }
                    }
                    return result;
                })
                .onErrorResume(e -> {
                    log.error("Failed to fetch job list entry for job {}: {}", jobNo, e.getMessage());

                    // Special handling for job J069026
                    if ("J069026".equals(jobNo)) {
                        log.error("SPECIAL DIAGNOSTIC: Failed to fetch Job List entry for problematic job J069026: {}", e.getMessage());
                        log.error("SPECIAL DIAGNOSTIC: Error type for problematic job J069026: {}", e.getClass().getName());

                        // Log stack trace for detailed debugging
                        StringBuilder stackTrace = new StringBuilder();
                        for (StackTraceElement element : e.getStackTrace()) {
                            stackTrace.append("\n    at ").append(element.toString());
                        }
                        log.error("SPECIAL DIAGNOSTIC: Stack trace for Job List entry error for problematic job J069026: {}", stackTrace.toString());
                    }

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
                .onStatus(status -> status.equals(HttpStatus.UNAUTHORIZED), response -> handleError(response, "Unauthorized access fetching Sales Quote: " + quoteNo))
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
                .onStatus(status -> status.equals(HttpStatus.UNAUTHORIZED), response -> handleError(response, "Unauthorized access fetching Sales Quote Lines: " + quoteNo))
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
                .onStatus(status -> status.equals(HttpStatus.UNAUTHORIZED), response -> handleError(response, "Unauthorized access fetching Sales Invoice: " + invoiceNo))
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
                .onStatus(status -> status.equals(HttpStatus.UNAUTHORIZED), response -> handleError(response, "Unauthorized access fetching Sales Invoice Lines: " + invoiceNo))
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
                .onStatus(status -> status.equals(HttpStatus.UNAUTHORIZED), response -> handleError(response, "Unauthorized access fetching Sales Invoice Lines by Job No: " + jobNo))
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

        // Special handling for job J069026
        if ("J069026".equals(jobNo)) {
            log.error("SPECIAL DIAGNOSTIC: Fetching Job Attachment Links for problematic job J069026");
        }

        String endpointPath = "/JobAttachmentLinks";
        String filterQuery = "No eq '" + jobNo + "'";
        String filterQueryParam = "$filter";

        // Log the full URL being requested for debugging
        String fullUrl = this.oDataBaseUrl + endpointPath + "?" + filterQueryParam + "=" + filterQuery;
        log.info("Requesting Job Attachment Links with URL: {}", fullUrl);

        // Special handling for job J069026
        if ("J069026".equals(jobNo)) {
            log.error("SPECIAL DIAGNOSTIC: Business Central URL for problematic job J069026: {}", fullUrl);
        }

        return this.webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(endpointPath)
                        .queryParam(filterQueryParam, filterQuery)
                        .build())
                .header(HttpHeaders.AUTHORIZATION, this.basicAuthHeader)
                .retrieve()
                .onStatus(status -> status.equals(HttpStatus.UNAUTHORIZED), response -> {
                    log.error("UNAUTHORIZED (401) response fetching Job Attachment Links for Job No: {}", jobNo);

                    // Special handling for job J069026
                    if ("J069026".equals(jobNo)) {
                        log.error("SPECIAL DIAGNOSTIC: UNAUTHORIZED (401) response for problematic job J069026");
                    }

                    return handleError(response, "Unauthorized access fetching Job Attachment Links for Job No: " + jobNo);
                })
                .onStatus(status -> status.is4xxClientError(), response -> {
                    log.error("4xx Client Error ({}) fetching Job Attachment Links for Job No: {}",
                            response.statusCode().value(), jobNo);

                    // Special handling for job J069026
                    if ("J069026".equals(jobNo)) {
                        log.error("SPECIAL DIAGNOSTIC: 4xx Client Error ({}) for problematic job J069026",
                                response.statusCode().value());
                    }

                    return handleError(response, "Client error fetching Job Attachment Links for Job No: " + jobNo);
                })
                .onStatus(status -> status.is5xxServerError(), response -> {
                    log.error("5xx Server Error ({}) fetching Job Attachment Links for Job No: {}",
                            response.statusCode().value(), jobNo);

                    // Special handling for job J069026
                    if ("J069026".equals(jobNo)) {
                        log.error("SPECIAL DIAGNOSTIC: 5xx Server Error ({}) for problematic job J069026",
                                response.statusCode().value());
                    }

                    return handleError(response, "Server error fetching Job Attachment Links for Job No: " + jobNo);
                })
                .bodyToMono(JOB_ATTACHMENT_LINKS_TYPE)
                .doOnSuccess(wrapper -> {
                    if (wrapper == null) {
                        log.error("Received null response wrapper for Job Attachment Links for Job No: {}", jobNo);

                        // Special handling for job J069026
                        if ("J069026".equals(jobNo)) {
                            log.error("SPECIAL DIAGNOSTIC: Received null response wrapper for problematic job J069026");
                        }
                    } else if (wrapper.getValue() == null) {
                        log.error("Received wrapper with null value array for Job Attachment Links for Job No: {}", jobNo);

                        // Special handling for job J069026
                        if ("J069026".equals(jobNo)) {
                            log.error("SPECIAL DIAGNOSTIC: Received wrapper with null value array for problematic job J069026");
                        }
                    } else if (wrapper.getValue().isEmpty()) {
                        log.error("Received wrapper with empty value array for Job Attachment Links for Job No: {}", jobNo);

                        // Special handling for job J069026
                        if ("J069026".equals(jobNo)) {
                            log.error("SPECIAL DIAGNOSTIC: Received wrapper with empty value array for problematic job J069026");
                        }
                    } else {
                        log.info("Successfully received response with {} Job Attachment Links for Job No: {}",
                                wrapper.getValue().size(), jobNo);

                        // Special handling for job J069026
                        if ("J069026".equals(jobNo)) {
                            log.error("SPECIAL DIAGNOSTIC: Successfully received response with {} Job Attachment Links for problematic job J069026",
                                    wrapper.getValue().size());
                        }
                    }
                })
                .map(wrapper -> {
                    JobAttachmentLinksDTO result = extractSingleResult(wrapper, "Job Attachment Links", jobNo);
                    if (result == null) {
                        log.error("No Job Attachment Links found for Job No: {}. Job may not exist in Business Central.", jobNo);

                        // Special handling for job J069026
                        if ("J069026".equals(jobNo)) {
                            log.error("SPECIAL DIAGNOSTIC: No Job Attachment Links found for problematic job J069026. Job may not exist in Business Central.");
                        }
                    } else {
                        log.info("Successfully extracted Job Attachment Links for Job No: {}, File_Links: '{}'",
                                jobNo, result.getFileLinks());

                        // Special handling for job J069026
                        if ("J069026".equals(jobNo)) {
                            log.error("SPECIAL DIAGNOSTIC: Successfully extracted Job Attachment Links for problematic job J069026, File_Links: '{}'",
                                    result.getFileLinks());
                        }
                    }
                    return result;
                })
                .onErrorResume(e -> {
                    log.error("Failed to fetch job attachment links for job {}: {}", jobNo, e.getMessage());
                    log.error("Error type: {}", e.getClass().getName());

                    // Special handling for job J069026
                    if ("J069026".equals(jobNo)) {
                        log.error("SPECIAL DIAGNOSTIC: Failed to fetch job attachment links for problematic job J069026: {}", e.getMessage());
                        log.error("SPECIAL DIAGNOSTIC: Error type for problematic job J069026: {}", e.getClass().getName());
                    }

                    // Log stack trace for detailed debugging
                    StringBuilder stackTrace = new StringBuilder();
                    for (StackTraceElement element : e.getStackTrace()) {
                        stackTrace.append("\n    at ").append(element.toString());
                    }
                    log.error("Stack trace for job attachment links error: {}", stackTrace.toString());
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
                .bodyToMono(String.class) // Get the full response body as a string
                .flatMap(responseBody -> {
                    log.debug("Response body: {}", responseBody);

                    // Extract the ETag from the response body JSON
                    String etag = extractETagFromResponseBody(responseBody);

                    if (etag == null) {
                        log.error("ETag not found in response body for Job_Card Job No: {}. Cannot perform PATCH.", jobNo);
                        return Mono.error(new RuntimeException("ETag not found for Job_Card update."));
                    }

                    log.info("Successfully extracted ETag from response body for Job No: {}: {}", jobNo, etag);

                    // Step 2: Perform the PATCH request with the ETag
                    Map<String, String> requestBody = Map.of(fieldName, value);
                    return this.webClient.patch()
                            .uri(jobCardEndpointPath)
                            .header(HttpHeaders.AUTHORIZATION, this.basicAuthHeader)
                            .header(HttpHeaders.IF_MATCH, etag) // Use the extracted ETag
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(requestBody)
                            .retrieve()
                            .onStatus(status -> status.equals(HttpStatus.UNAUTHORIZED), response ->
                                handleError(response, "Unauthorized access updating Job_Card for Job No: " + jobNo)
                            )
                            .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(), response ->
                                handleError(response, "Error updating Job_Card for Job No: " + jobNo)
                            )
                            .toBodilessEntity()
                            .doOnSuccess(response -> log.info("Successfully updated Job_Card for Job No: {}. Field: {}, Status code: {}", jobNo, fieldName, response.getStatusCode()))
                            .then(); // Convert to Mono<Void>
                })
                .doOnError(error -> {
                    log.error("Error during Job_Card update process for Job No: {}", jobNo, error);
                    log.error("Error details: {}", error.getMessage());
                    if (error.getCause() != null) {
                        log.error("Caused by: {}", error.getCause().getMessage());
                    }
                })
                .onErrorResume(e -> {
                    // Try a different approach for updating the field
                    log.warn("Standard PATCH update failed for Job No: {}, Field: {}. Error: {}", jobNo, fieldName, e.getMessage());

                    // If this is a verification field, try using the direct update method
                    if (fieldName.equals("_x0032_nd_Check_Date") ||
                        fieldName.equals("_x0032_nd_Check_Time") ||
                        fieldName.equals("_x0032_nd_Check_By") ||
                        fieldName.equals("Verification_Comment")) {

                        log.info("Attempting to update verification field using direct method for Job No: {}", jobNo);

                        // Get current date and time for any missing fields
                        LocalDate currentDate = LocalDate.now();
                        String formattedDate = currentDate.format(DateTimeFormatter.ISO_LOCAL_DATE);
                        java.time.LocalTime currentTime = java.time.LocalTime.now();
                        String formattedTime = currentTime.format(DateTimeFormatter.ofPattern("HH:mm:ss"));

                        // Set default values for fields not being updated
                        String dateValue = fieldName.equals("_x0032_nd_Check_Date") ? value : formattedDate;
                        String timeValue = fieldName.equals("_x0032_nd_Check_Time") ? value : formattedTime;
                        String byValue = fieldName.equals("_x0032_nd_Check_By") ? value : "AI LLM Service";
                        String commentValue = fieldName.equals("Verification_Comment") ? value : "";

                        return updateJobVerificationFieldsDirectly(jobNo, dateValue, byValue, timeValue, commentValue);
                    }

                    // For non-verification fields, just return the error
                    return Mono.error(new RuntimeException("Failed to update Job_Card field " + fieldName + " for job " + jobNo, e));
                });
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

    /**
     * Updates all verification-related fields on the Job_Card entity in Business Central.
     * This method updates the 2nd Check Date, 2nd Check By, 2nd Check Time, and Verification Comment fields.
     *
     * @param jobNo The Job_No of the job to update.
     * @param comment The verification comment to set.
     * @return A Mono indicating completion or error.
     */
    public Mono<Void> updateAllVerificationFields(String jobNo, String comment) {
        log.info("Updating all verification fields for Job No: {}", jobNo);

        // Get current date and time
        LocalDate currentDate = LocalDate.now();
        String formattedDate = currentDate.format(DateTimeFormatter.ISO_LOCAL_DATE); // Format as YYYY-MM-DD

        // Get current time
        java.time.LocalTime currentTime = java.time.LocalTime.now();
        String formattedTime = currentTime.format(DateTimeFormatter.ofPattern("HH:mm:ss")); // Format as HH:MM:SS

        // Need to fetch ETag first for PATCH
        String jobCardEndpointPath = "/Job_Card(No='" + jobNo + "')";

        // First, fetch the current entity to get the ETag
        return this.webClient.get()
                .uri(jobCardEndpointPath)
                .header(HttpHeaders.AUTHORIZATION, this.basicAuthHeader)
                .retrieve()
                .bodyToMono(String.class) // Get the full response body as a string
                .flatMap(responseBody -> {
                    log.debug("Response body: {}", responseBody);

                    // Extract the ETag from the response body JSON
                    String etag = extractETagFromResponseBody(responseBody);

                    if (etag == null) {
                        log.error("ETag not found in response body for Job_Card Job No: {}. Cannot perform PATCH.", jobNo);

                        // Try the direct approach using a custom function in Business Central as fallback
                        log.info("Falling back to direct update method for Job No: {}", jobNo);
                        return updateJobVerificationFieldsDirectly(jobNo, formattedDate, "AI LLM Service", formattedTime, comment);
                    }

                    log.info("Successfully extracted ETag from response body for Job No: {}: {}", jobNo, etag);

                    // Create a map with all fields to update in a single PATCH request
                    Map<String, String> requestBody = new HashMap<>();
                    requestBody.put("_x0032_nd_Check_Date", formattedDate);
                    requestBody.put("_x0032_nd_Check_Time", formattedTime);
                    requestBody.put("_x0032_nd_Check_By", "AI LLM Service");

                    // Add comment if provided
                    if (comment != null && !comment.isEmpty()) {
                        requestBody.put("Verification_Comment", comment);
                    }

                    // Perform a single PATCH request with all fields
                    return this.webClient.patch()
                            .uri(jobCardEndpointPath)
                            .header(HttpHeaders.AUTHORIZATION, this.basicAuthHeader)
                            .header(HttpHeaders.IF_MATCH, etag) // Use the extracted ETag
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(requestBody)
                            .retrieve()
                            .onStatus(status -> status.equals(HttpStatus.UNAUTHORIZED), response ->
                                handleError(response, "Unauthorized access updating Job_Card for Job No: " + jobNo)
                            )
                            .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(), response ->
                                handleError(response, "Error updating Job_Card for Job No: " + jobNo)
                            )
                            .toBodilessEntity()
                            .doOnSuccess(response -> log.info("Successfully updated all verification fields for Job No: {}. Status code: {}",
                                jobNo, response.getStatusCode()))
                            .then(); // Convert to Mono<Void>
                })
                .onErrorResume(e -> {
                    log.error("Error during Job_Card update process for Job No: {}", jobNo, e);

                    // Try the direct approach using a custom function in Business Central as fallback
                    log.warn("Update with ETag failed, falling back to direct update method for Job No: {}", jobNo);
                    return updateJobVerificationFieldsDirectly(jobNo, formattedDate, "AI LLM Service", formattedTime, comment)
                            .onErrorResume(directError -> {
                                log.warn("Direct update also failed, falling back to individual field updates for Job No: {}", jobNo);

                                // Try updating each field individually in sequence
                                return updateJobCardField(jobNo, "_x0032_nd_Check_Date", formattedDate)
                                        .then(updateJobCardField(jobNo, "_x0032_nd_Check_By", "AI LLM Service"))
                                        .then(updateJobCardField(jobNo, "_x0032_nd_Check_Time", formattedTime))
                                        .then(updateJobCardField(jobNo, "Verification_Comment", comment))
                                        .doOnSuccess(v -> log.info("Successfully updated all verification fields using fallback method for Job No: {}", jobNo))
                                        .onErrorResume(fallbackError -> {
                                            log.error("All update methods failed for Job No: {}", jobNo, fallbackError);
                                            return Mono.error(new RuntimeException("Failed to update verification fields for job " + jobNo, fallbackError));
                                        });
                            });
                });
    }

    /**
     * Updates all verification fields directly using a custom Business Central function.
     * This method is designed to bypass the ETag requirement by using a custom endpoint.
     *
     * @param jobNo The job number
     * @param checkDate The check date in ISO format (YYYY-MM-DD)
     * @param checkBy The name of the checker
     * @param checkTime The check time in format HH:MM:SS
     * @param comment The verification comment
     * @return A Mono indicating completion or error
     */
    private Mono<Void> updateJobVerificationFieldsDirectly(String jobNo, String checkDate, String checkBy, String checkTime, String comment) {
        log.info("Directly updating verification fields for Job No: {}", jobNo);

        // Create a request body with all the fields
        String requestBody = String.format(
            "{"
            + "\"jobNo\": \"%s\","
            + "\"checkDate\": \"%s\","
            + "\"checkBy\": \"%s\","
            + "\"checkTime\": \"%s\","
            + "\"comment\": \"%s\""
            + "}",
            jobNo, checkDate, checkBy, checkTime, comment
        );

        // Use a custom endpoint that doesn't require ETag
        // Note: This endpoint needs to be created in Business Central
        String updateEndpointPath = "/UpdateJobVerificationFields";

        return this.webClient.post()
                .uri(updateEndpointPath)
                .header(HttpHeaders.AUTHORIZATION, this.basicAuthHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .retrieve()
                .onStatus(status -> status.equals(HttpStatus.UNAUTHORIZED), response ->
                    handleError(response, "Unauthorized access directly updating verification fields for Job No: " + jobNo)
                )
                .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(), response ->
                    handleError(response, "Error directly updating verification fields for Job No: " + jobNo)
                )
                .toBodilessEntity()
                .doOnSuccess(response -> log.info("Successfully directly updated verification fields for Job No: {}, Status code: {}",
                    jobNo, response.getStatusCode()))
                .then() // Convert to Mono<Void>
                .doOnError(error -> {
                    log.error("Error during direct verification fields update for Job No: {}", jobNo);
                    log.error("Error details: {}", error.getMessage());
                    if (error.getCause() != null) {
                        log.error("Caused by: {}", error.getCause().getMessage());
                    }
                });
    }

    // --- Helper Methods ---

    /** Handles HTTP errors from WebClient calls. */
    private Mono<? extends Throwable> handleError(org.springframework.web.reactive.function.client.ClientResponse response, String contextMessage) {
        return response.bodyToMono(String.class)
                .defaultIfEmpty("[No response body]")
                .flatMap(body -> {
                    String errorMessage = String.format("%s. Status: %s, Body: %s", contextMessage, response.statusCode(), body);
                    log.error(errorMessage);

                    // Log additional details about the request
                    log.error("Request details - URI: {}, Method: {}",
                            response.request() != null ? response.request().getURI() : "Unknown",
                            response.request() != null ? response.request().getMethod() : "Unknown");

                    // Log headers if available
                    if (response.headers() != null) {
                        log.error("Response headers: {}", response.headers().asHttpHeaders());
                    }

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

    /**
     * Extracts the ETag from the OData response body.
     * The ETag is typically found in the "@odata.etag" field of the response JSON.
     *
     * @param responseBody The response body as a string
     * @return The ETag value, or null if not found
     */
    private String extractETagFromResponseBody(String responseBody) {
        if (responseBody == null || responseBody.isEmpty()) {
            log.error("Response body is null or empty, cannot extract ETag");
            return null;
        }

        try {
            // Parse the JSON response using Jackson
            ObjectMapper mapper = new ObjectMapper();
            JsonNode rootNode = mapper.readTree(responseBody);

            // Get the @odata.etag field
            JsonNode etagNode = rootNode.get("@odata.etag");
            if (etagNode == null) {
                log.error("@odata.etag field not found in response body");
                return null;
            }

            String etag = etagNode.asText();
            log.info("Extracted ETag: {}", etag);

            // The ETag is already in the correct format (W/"...") as seen in the screenshot
            return etag;
        } catch (Exception e) {
            log.error("Error extracting ETag from response body: {}", e.getMessage(), e);

            // Fallback to string parsing if JSON parsing fails
            try {
                // Look for the "@odata.etag" field in the JSON
                int etagIndex = responseBody.indexOf("\"@odata.etag\":");
                if (etagIndex == -1) {
                    log.error("@odata.etag field not found in response body");
                    return null;
                }

                // Extract the value after "@odata.etag":
                int valueStartIndex = responseBody.indexOf("\"", etagIndex + 14) + 1; // +14 to skip "@odata.etag":
                if (valueStartIndex == 0) { // indexOf returns -1 if not found, so +1 would be 0
                    log.error("Could not find start of ETag value");
                    return null;
                }

                int valueEndIndex = responseBody.indexOf("\"", valueStartIndex);
                if (valueEndIndex == -1) {
                    log.error("Could not find end of ETag value");
                    return null;
                }

                String etag = responseBody.substring(valueStartIndex, valueEndIndex);
                log.info("Extracted ETag using fallback method: {}", etag);
                return etag;
            } catch (Exception fallbackError) {
                log.error("Fallback ETag extraction also failed: {}", fallbackError.getMessage());
                return null;
            }
        }
    }

    /**
     * Fetches jobs pending second check from Business Central.
     * Filters jobs by Job_Class_Code = 'SALE'.
     *
     * @return A Flux of JobListDTO objects representing jobs pending second check
     */
    public Flux<JobListDTO> fetchJobsPendingSecondCheck() {
        log.info("Fetching jobs pending second check with Job_Class_Code = 'SALE'");

        String endpointPath = "/JobsPendingSecondCheck";
        String filterQuery = "Job_Class_Code eq 'SALE'";
        String filterQueryParam = "$filter";

        // Log the full URL being requested for debugging
        String fullUrl = this.oDataBaseUrl + endpointPath + "?" + filterQueryParam + "=" + filterQuery;
        log.info("Requesting Jobs Pending Second Check with URL: {}", fullUrl);

        return this.webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(endpointPath)
                        .queryParam(filterQueryParam, filterQuery)
                        .build())
                .header(HttpHeaders.AUTHORIZATION, this.basicAuthHeader)
                .retrieve()
                .onStatus(status -> status.equals(HttpStatus.UNAUTHORIZED), response ->
                    handleError(response, "Unauthorized access fetching Jobs Pending Second Check")
                )
                .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(), response ->
                    handleError(response, "Error fetching Jobs Pending Second Check")
                )
                .bodyToMono(JOBS_PENDING_SECOND_CHECK_TYPE)
                .flatMapMany(wrapper -> {
                    if (wrapper != null && wrapper.getValue() != null) {
                        log.info("Successfully fetched {} jobs pending second check", wrapper.getValue().size());
                        // Log each job for debugging
                        wrapper.getValue().forEach(job -> {
                            log.info("Pending job: No={}, FirstCheckDate={}, SecondCheckBy={}, Description={}",
                                    job.getNo(), job.getFirstCheckDate(), job.getSecondCheckBy(), job.getDescription());
                        });
                        return Flux.fromIterable(wrapper.getValue());
                    } else {
                        log.warn("Received null or empty wrapper for Jobs Pending Second Check");
                        return Flux.empty();
                    }
                })
                .onErrorResume(e -> {
                    log.error("Failed to fetch jobs pending second check: {}", e.getMessage());
                    return Flux.error(new RuntimeException("Failed to fetch jobs pending second check", e));
                });
    }
}
