package com.erp.aierpbackend.service;

import com.erp.aierpbackend.dto.dynamics.JobAttachmentLinksDTO;
import com.erp.aierpbackend.entity.JobDocument;
import com.erp.aierpbackend.repository.JobDocumentRepository;
import com.erp.aierpbackend.service.BusinessCentralService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for handling job attachments from Business Central and SharePoint.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class JobAttachmentService {

    private final BusinessCentralService businessCentralService;
    private final SharePointService sharePointService;
    private final JobDocumentService jobDocumentService;
    private final JobDocumentRepository jobDocumentRepository;

    // Document type mapping patterns - expanded to catch more variations
    private static final Pattern SALES_QUOTE_PATTERN = Pattern.compile("(?i).*sales.*quote.*|.*quote.*|.*SQ.*|.*Sales.*|.*Quotation.*");
    private static final Pattern PROFORMA_INVOICE_PATTERN = Pattern.compile("(?i).*proforma.*invoice.*|.*invoice.*|.*PI.*|.*Proforma.*");
    private static final Pattern JOB_CONSUMPTION_PATTERN = Pattern.compile("(?i).*job.*shipment.*|.*job.*consumption.*|.*shipment.*|.*JC.*|.*Job.*|.*Consumption.*");

    // Document type constants
    public static final String SALES_QUOTE_TYPE = "SalesQuote";
    public static final String PROFORMA_INVOICE_TYPE = "ProformaInvoice";
    public static final String JOB_CONSUMPTION_TYPE = "JobConsumption";
    public static final String UNCLASSIFIED_TYPE = "UNCLASSIFIED";

    /**
     * Fetches job attachments from Business Central and downloads them from SharePoint.
     * Stores the documents in the database.
     *
     * @param jobNo The job number
     * @return Flux of downloaded document types
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public Flux<String> fetchAndStoreJobAttachments(String jobNo) {
        log.info("Fetching and storing job attachments for Job No: '{}'", jobNo);

        // Log transaction information if possible
        try {
            log.debug("Transaction active: {}, Transaction name: {}",
                    TransactionSynchronizationManager.isActualTransactionActive(),
                    TransactionSynchronizationManager.getCurrentTransactionName());
        } catch (Exception e) {
            log.debug("Could not log transaction details: {}", e.getMessage());
        }

        // Add a small delay to ensure any previous transactions are fully committed
        try {
            log.debug("Adding a small delay before fetching attachments to ensure previous transactions are committed");
            Thread.sleep(1000); // 1 second delay
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            log.warn("Sleep interrupted before fetching attachments", ie);
        }

        // Check which documents already exist in the database
        boolean salesQuoteExists = jobDocumentService.documentExists(jobNo, SALES_QUOTE_TYPE);
        boolean proformaInvoiceExists = jobDocumentService.documentExists(jobNo, PROFORMA_INVOICE_TYPE);
        boolean jobConsumptionExists = jobDocumentService.documentExists(jobNo, JOB_CONSUMPTION_TYPE);

        // If all required documents already exist, no need to fetch from SharePoint
        if (salesQuoteExists && proformaInvoiceExists && jobConsumptionExists) {
            log.info("All required documents already exist in database for Job No: {}", jobNo);
            return Flux.just(SALES_QUOTE_TYPE, PROFORMA_INVOICE_TYPE, JOB_CONSUMPTION_TYPE);
        }

        log.info("Fetching attachment links from Business Central for Job No: {}", jobNo);
        return businessCentralService.fetchJobAttachmentLinks(jobNo)
                .flatMapMany(attachmentLinks -> {
                    if (attachmentLinks == null) {
                        log.warn("No attachment links found for Job No: {}", jobNo);
                        return Flux.empty();
                    }

                    String[] sharePointUrls = attachmentLinks.getSharePointUrls();
                    if (sharePointUrls.length == 0) {
                        log.warn("No SharePoint URLs found in attachment links for Job No: {}", jobNo);
                        return Flux.empty();
                    }

                    log.info("Found {} SharePoint URLs for Job No: {}: {}", sharePointUrls.length, jobNo, String.join(", ", sharePointUrls));

                    // Log the raw File_Links field for debugging
                    log.debug("Raw File_Links field for Job No: {}: {}", jobNo, attachmentLinks.getFileLinks());

                    // Clean up the URLs - remove any that are obviously invalid
                    List<String> validUrls = new ArrayList<>();
                    for (String url : sharePointUrls) {
                        if (url != null && !url.trim().isEmpty() &&
                            (url.contains("sharepoint.com") || url.contains("dayliff") || url.contains("http"))) {

                            // Fix specific URL encoding issues
                            String cleanUrl = url.trim();

                            // Fix double-encoded spaces (%2520 instead of %20)
                            if (cleanUrl.contains("%2520")) {
                                cleanUrl = cleanUrl.replace("%2520", "%20");
                                log.info("Fixed double-encoded spaces in URL: {} -> {}", url, cleanUrl);
                            }

                            // Fix specific issue with jOB%20SHIPMENT_124.pdf
                            if (cleanUrl.contains("jOB%2520SHIPMENT_124.pdf")) {
                                cleanUrl = cleanUrl.replace("jOB%2520SHIPMENT_124.pdf", "jOB%20SHIPMENT_124.pdf");
                                log.info("Fixed URL encoding for job shipment document: {}", cleanUrl);
                            }

                            // Log the URL for debugging
                            log.debug("Processing SharePoint URL: {}", cleanUrl);

                            validUrls.add(cleanUrl);
                            log.debug("Added valid URL: {}", cleanUrl);
                        } else {
                            log.warn("Filtered out invalid SharePoint URL for Job No: {}: '{}'", jobNo, url);
                        }
                    }

                    if (validUrls.isEmpty()) {
                        log.warn("No valid SharePoint URLs found after filtering for Job No: {}", jobNo);
                        return Flux.empty();
                    }

                    log.info("After filtering, found {} valid SharePoint URLs for Job No: {}: {}",
                            validUrls.size(), jobNo, String.join(", ", validUrls));

                    return Flux.fromIterable(validUrls)
                            .map(url -> {
                                log.debug("Processing SharePoint URL for Job No: {}: {}", jobNo, url);
                                return url;
                            })
                            .flatMap(url -> downloadAndStoreDocument(jobNo, url));
                })
                .onErrorResume(e -> {
                    log.error("Error fetching and storing job attachments for Job No: {}", jobNo, e);
                    return Flux.error(new RuntimeException("Failed to fetch and store job attachments for job " + jobNo, e));
                });
    }

    /**
     * Downloads a document from SharePoint and stores it in the database.
     *
     * @param jobNo The job number
     * @param sharePointUrl The SharePoint URL
     * @return Mono containing the document type
     */
    private Mono<String> downloadAndStoreDocument(String jobNo, String sharePointUrl) {
        log.info("Processing document from SharePoint URL: {} for Job No: {}", sharePointUrl, jobNo);

        // Special handling for job J069023 and the problematic file
        if (jobNo.equals("J069023") && sharePointUrl.contains("jOB%20SHIPMENT_124.pdf")) {
            log.info("Special handling for job J069023 and file jOB%20SHIPMENT_124.pdf");
        }

        final String fileName = extractFileName(sharePointUrl);
        log.debug("Extracted file name: {} for Job No: {}", fileName, jobNo);

        // Store all documents as UNCLASSIFIED initially
        final String documentType = UNCLASSIFIED_TYPE;
        log.info("Storing document as UNCLASSIFIED for file: {} (Job No: {})", fileName, jobNo);

        // Check if a document with the same job number and filename already exists (regardless of type)
        boolean fileExists = jobDocumentRepository.existsByJobNoAndFileName(jobNo, fileName);

        if (fileExists) {
            log.info("Document with filename {} already exists for Job No: {}", fileName, jobNo);

            // Get the existing document
            Optional<JobDocument> existingDocOpt = jobDocumentRepository.findTopByJobNoAndFileNameOrderByIdDesc(jobNo, fileName);

            if (existingDocOpt.isPresent()) {
                JobDocument existingDoc = existingDocOpt.get();
                log.info("Found existing document with ID: {}, Document Type: '{}', Classified Type: '{}' for Job No: '{}', File Name: '{}'",
                        existingDoc.getId(), existingDoc.getDocumentType(), existingDoc.getClassifiedDocumentType(), jobNo, fileName);

                // If the document is already classified, return its classified type
                if (existingDoc.getClassifiedDocumentType() != null && !existingDoc.getClassifiedDocumentType().isEmpty()) {
                    log.info("Existing document is already classified as '{}', skipping download", existingDoc.getClassifiedDocumentType());
                    return Mono.just(existingDoc.getClassifiedDocumentType());
                }

                // If the document is not classified, return UNCLASSIFIED
                log.info("Existing document is not classified, skipping download");
                return Mono.just(documentType);
            } else {
                // This should not happen since we checked existsByJobNoAndFileName, but just in case
                log.warn("Document existence check returned true but document not found for Job No: '{}', File Name: '{}'", jobNo, fileName);
                // Continue with download to be safe
            }
        }

        log.info("Downloading document of type {} from SharePoint for Job No: {}", documentType, jobNo);

        return sharePointService.downloadFile(sharePointUrl)
                .flatMap(fileData -> {
                    log.info("Successfully downloaded document from SharePoint for Job No: '{}', Document Type: '{}', size: {} bytes",
                            jobNo, documentType, fileData.length);

                    JobDocument document = JobDocument.builder()
                            .jobNo(jobNo)
                            .documentType(documentType)
                            .fileName(fileName)
                            .contentType(determineContentType(fileName))
                            .documentData(fileData)
                            .sourceUrl(sharePointUrl)
                            .build();

                    log.debug("Created JobDocument entity for Job No: '{}', Document Type: '{}', File Name: '{}', Content Type: '{}'",
                            jobNo, documentType, fileName, determineContentType(fileName));

                    return Mono.fromCallable(() -> {
                        log.debug("About to save JobDocument for Job No: '{}', Document Type: '{}'", jobNo, documentType);
                        JobDocument saved = jobDocumentService.saveJobDocument(document);
                        log.debug("JobDocument saved successfully with ID: {} for Job No: '{}', Document Type: '{}'",
                                saved.getId(), jobNo, documentType);

                        // Add a small delay to ensure the document is fully saved
                        try {
                            log.debug("Adding a small delay to ensure document is fully saved");
                            Thread.sleep(500); // 500ms delay
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            log.warn("Sleep interrupted while waiting for document save", ie);
                        }

                        return saved;
                    })
                    .thenReturn(documentType);
                })
                .onErrorResume(e -> {
                    // Special handling for job J069023 and the problematic file
                    if (jobNo.equals("J069023") && sharePointUrl.contains("jOB%20SHIPMENT_124.pdf")) {
                        log.error("Error downloading problematic file jOB%20SHIPMENT_124.pdf for Job No: J069023", e);
                        log.info("Detailed error information: Type={}, Message={}", e.getClass().getName(), e.getMessage());

                        // Log the stack trace for detailed debugging
                        StringBuilder stackTrace = new StringBuilder();
                        for (StackTraceElement element : e.getStackTrace()) {
                            stackTrace.append("\n    at ").append(element.toString());
                        }
                        log.debug("Stack trace for problematic file: {}", stackTrace.toString());
                    } else {
                        log.error("Error downloading document from SharePoint URL: {} for Job No: {}", sharePointUrl, jobNo, e);
                    }
                    return Mono.empty();
                });
    }

    /**
     * Extracts the file name from a SharePoint URL.
     *
     * @param sharePointUrl The SharePoint URL
     * @return The file name
     */
    private String extractFileName(String sharePointUrl) {
        try {
            // Handle URLs with query parameters
            String urlPath = sharePointUrl;
            if (urlPath.contains("?")) {
                urlPath = urlPath.substring(0, urlPath.indexOf("?"));
            }

            // Get the last segment of the URL path
            String[] segments = urlPath.split("/");
            if (segments.length > 0) {
                String lastSegment = segments[segments.length - 1];
                // URL decode the file name
                return java.net.URLDecoder.decode(lastSegment, "UTF-8");
            }

            // Fallback to Paths.get if the above approach fails
            Path path = Paths.get(urlPath);
            return path.getFileName().toString();
        } catch (Exception e) {
            log.warn("Error extracting file name from URL: {}. Using fallback method.", sharePointUrl, e);
            // Fallback: Use the last part of the URL as the file name
            String[] parts = sharePointUrl.split("/");
            return parts.length > 0 ? parts[parts.length - 1] : "unknown_file";
        }
    }

    /**
     * Determines the document type based on the file name.
     *
     * @param fileName The file name
     * @return The document type or null if unknown
     */
    private String determineDocumentType(String fileName) {
        log.debug("Determining document type for file name: {}", fileName);

        // Try to determine document type based on file name patterns
        if (SALES_QUOTE_PATTERN.matcher(fileName).matches()) {
            log.debug("File '{}' matched SALES_QUOTE pattern", fileName);
            return SALES_QUOTE_TYPE;
        } else if (PROFORMA_INVOICE_PATTERN.matcher(fileName).matches()) {
            log.debug("File '{}' matched PROFORMA_INVOICE pattern", fileName);
            return PROFORMA_INVOICE_TYPE;
        } else if (JOB_CONSUMPTION_PATTERN.matcher(fileName).matches()) {
            log.debug("File '{}' matched JOB_CONSUMPTION pattern", fileName);
            return JOB_CONSUMPTION_TYPE;
        }

        // If no pattern matched, try a more aggressive approach with contains
        String lowerFileName = fileName.toLowerCase();
        if (lowerFileName.contains("quote") || lowerFileName.contains("sq") || lowerFileName.contains("sales")) {
            log.debug("File '{}' contains SALES_QUOTE keywords", fileName);
            return SALES_QUOTE_TYPE;
        } else if (lowerFileName.contains("invoice") || lowerFileName.contains("pi") || lowerFileName.contains("proforma")) {
            log.debug("File '{}' contains PROFORMA_INVOICE keywords", fileName);
            return PROFORMA_INVOICE_TYPE;
        } else if (lowerFileName.contains("job") || lowerFileName.contains("jc") || lowerFileName.contains("shipment") ||
                   lowerFileName.contains("consumption")) {
            log.debug("File '{}' contains JOB_CONSUMPTION keywords", fileName);
            return JOB_CONSUMPTION_TYPE;
        }

        // If we still can't determine the type, log a warning and return null
        log.warn("Could not determine document type for file: {}", fileName);
        return null;
    }

    /**
     * Determines the content type based on the file name.
     *
     * @param fileName The file name
     * @return The content type
     */
    private String determineContentType(String fileName) {
        String lowerCaseFileName = fileName.toLowerCase();
        if (lowerCaseFileName.endsWith(".pdf")) {
            return "application/pdf";
        } else if (lowerCaseFileName.endsWith(".png")) {
            return "image/png";
        } else if (lowerCaseFileName.endsWith(".jpg") || lowerCaseFileName.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        return "application/octet-stream";
    }
}
