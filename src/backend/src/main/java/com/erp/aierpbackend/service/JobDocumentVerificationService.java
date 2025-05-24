package com.erp.aierpbackend.service;

import com.erp.aierpbackend.dto.ClassifyAndVerifyResultDTO;
import com.erp.aierpbackend.dto.dynamics.*;
import com.erp.aierpbackend.dto.gemini.GeminiDiscrepancy;
import com.erp.aierpbackend.dto.gemini.GeminiVerificationResult;
import com.erp.aierpbackend.entity.JobDocument;
import com.erp.aierpbackend.util.PdfImageConverter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(propagation = Propagation.REQUIRED)
public class JobDocumentVerificationService {

    private final BusinessCentralService businessCentralService;
    private final BusinessCentralWebService businessCentralWebService;
    private final LLMProxyService llmProxyService;
    private final PdfImageConverter pdfImageConverter;
    private final JobDocumentService jobDocumentService;
    private final JobAttachmentService jobAttachmentService;

    // Document type constants
    private static final String SALES_QUOTE_TYPE = JobAttachmentService.SALES_QUOTE_TYPE;
    private static final String PROFORMA_INVOICE_TYPE = JobAttachmentService.PROFORMA_INVOICE_TYPE;
    private static final String JOB_CONSUMPTION_TYPE = JobAttachmentService.JOB_CONSUMPTION_TYPE;

    // Legacy constants for backward compatibility
    private static final String JOB_CONSUMPTION_BASE_NAME = "Job Consumption";
    private static final String PROFORMA_INVOICE_BASE_NAME = "ProformaInvoice";
    private static final String SALES_QUOTE_BASE_NAME = "Sales quote";
    private static final String PDF_BASE_PATH = "../Jobs 2nd check pdf";
    private static final String[] SUPPORTED_EXTENSIONS = {".pdf", ".png", ".jpg", ".jpeg"};

    // Keys for document identifiers from LLM service
    private static final String SALES_QUOTE_NUMBER_KEY = "salesQuoteNo";
    private static final String PROFORMA_INVOICE_NUMBER_KEY = "proformaInvoiceNo";
    private static final String JOB_CONSUMPTION_NUMBER_KEY = "jobConsumptionNo";

    // Thread-safe cache for extracted document identifiers
    private final Map<String, Map<String, String>> extractedIdentifiersCache = new ConcurrentHashMap<>();

    /**
     * Verifies job documents using the combined classification and verification approach.
     * This method keeps documents as UNCLASSIFIED in the system but uses the LLM to identify
     * the document type and perform verification in a single step.
     *
     * @param legacyLedgerEntry The legacy ledger entry
     * @param jobNo The job number
     * @return List of discrepancies found during verification
     */
    public List<String> verifyJobDocumentsWithCombinedApproach(JobLedgerEntryDTO legacyLedgerEntry, String jobNo) {
        log.info("Starting document verification with combined approach for Job No: {}", jobNo);
        List<String> finalDiscrepancies = new ArrayList<>();
        boolean bcChecksPerformedOverall = false;

        // First, check if the job exists in Business Central
        try {
            var jobListEntry = businessCentralService.fetchJobListEntry(jobNo).block();
            if (jobListEntry == null) {
                log.error("Job No: {} does not exist in Business Central. Cannot proceed with verification.", jobNo);
                finalDiscrepancies.add("Critical Error: Job No: " + jobNo + " does not exist in Business Central.");
                return finalDiscrepancies;
            } else {
                log.info("Job No: {} exists in Business Central. Proceeding with verification.", jobNo);
            }
        } catch (Exception e) {
            log.error("Error checking if Job No: {} exists in Business Central: {}", jobNo, e.getMessage(), e);
            // Continue with verification even if we can't check if the job exists
        }

        // Check if any documents exist in the database
        List<JobDocument> allDocuments = jobDocumentService.getJobDocuments(jobNo);

        // If no documents exist, try to fetch from SharePoint
        if (allDocuments.isEmpty()) {
            log.info("No documents found in database for Job No: {}. Fetching from SharePoint...", jobNo);
            try {
                List<String> downloadedDocumentTypes = jobAttachmentService.fetchAndStoreJobAttachments(jobNo)
                        .collectList()
                        .block();

                if (downloadedDocumentTypes == null || downloadedDocumentTypes.isEmpty()) {
                    log.error("No documents were downloaded from SharePoint for Job No: {}. This is a critical issue.", jobNo);
                    finalDiscrepancies.add("Critical: No documents could be downloaded from SharePoint for Job No: " + jobNo);
                    return finalDiscrepancies;
                }

                // Refresh the document list after download
                allDocuments = jobDocumentService.getJobDocuments(jobNo);
                if (allDocuments.isEmpty()) {
                    log.error("Still no documents found after SharePoint download for Job No: {}", jobNo);
                    finalDiscrepancies.add("Critical: No documents available after SharePoint download for Job No: " + jobNo);
                    return finalDiscrepancies;
                }
            } catch (Exception e) {
                log.error("Error fetching job attachments from Business Central/SharePoint for Job No: {}", jobNo, e);
                finalDiscrepancies.add("Error fetching documents from Business Central/SharePoint: " + e.getMessage());
                return finalDiscrepancies;
            }
        }

        // Collect all document images
        List<byte[]> allDocumentImages = new ArrayList<>();
        for (JobDocument document : allDocuments) {
            try {
                byte[] documentData = document.getDocumentData();
                if (documentData == null || documentData.length == 0) {
                    log.warn("Document data is empty for document ID: {}, Job No: '{}'", document.getId(), jobNo);
                    continue;
                }

                String contentType = document.getContentType();
                List<byte[]> images;

                if (contentType != null && contentType.equals("application/pdf")) {
                    // For PDF files, convert to images
                    try {
                        // Create a temporary file
                        Path tempFile = Files.createTempFile("temp_pdf_", ".pdf");
                        Files.write(tempFile, documentData);

                        // Convert PDF to images
                        images = pdfImageConverter.convertPdfToImages(tempFile.toString(), 300f);

                        // Delete the temporary file
                        Files.delete(tempFile);
                    } catch (IOException e) {
                        log.error("Error converting PDF to images for document ID: {}: {}", document.getId(), e.getMessage(), e);
                        // Use the raw PDF data as a fallback
                        images = List.of(documentData);
                    }
                } else {
                    // For non-PDF files, use as is
                    images = List.of(documentData);
                }

                allDocumentImages.addAll(images);
                log.info("Added {} images from document ID: {} for Job No: {}",
                        images.size(), document.getId(), jobNo);
            } catch (Exception e) {
                log.error("Error converting document ID: {} to images for Job No: {}: {}",
                        document.getId(), jobNo, e.getMessage(), e);
                // Continue with other documents
            }
        }

        if (allDocumentImages.isEmpty()) {
            log.error("No document images could be extracted for Job No: {}", jobNo);
            finalDiscrepancies.add("Critical: No document images could be extracted for Job No: " + jobNo);
            return finalDiscrepancies;
        }

        // First, perform document classification with LLM to identify document types
        try {
            // Create a minimal ERP data map with just the job number for initial classification
            Map<String, Object> initialData = new HashMap<>();
            initialData.put("jobNo", jobNo);

            // Send documents to LLM for classification
            log.info("Sending documents to LLM for classification for Job No: {}", jobNo);
            ClassifyAndVerifyResultDTO classificationResult = llmProxyService.classifyAndVerifyDocument(jobNo, allDocumentImages, initialData);

            if (classificationResult == null) {
                log.error("Classification returned null result for Job No: {}", jobNo);
                finalDiscrepancies.add("Error: Document classification failed for Job No: " + jobNo);
                return finalDiscrepancies;
            }

            String documentType = classificationResult.getDocumentType();
            log.info("Document classified as '{}' for Job No: {} with confidence: {}",
                    documentType, jobNo, classificationResult.getClassificationConfidence());

            // Extract document numbers from field confidences
            String extractedSalesQuoteNo = null;
            String extractedProformaInvoiceNo = null;

            if (classificationResult.getFieldConfidences() != null) {
                for (ClassifyAndVerifyResultDTO.FieldConfidenceDTO field : classificationResult.getFieldConfidences()) {
                    if (field.getFieldName().contains("Quote No") || field.getFieldName().contains("Quote Number")) {
                        extractedSalesQuoteNo = field.getExtractedValue();
                        log.info("Extracted Sales Quote No: {} from document for Job No: {}", extractedSalesQuoteNo, jobNo);
                    } else if (field.getFieldName().contains("Invoice No") || field.getFieldName().contains("Invoice Number")) {
                        extractedProformaInvoiceNo = field.getExtractedValue();
                        log.info("Extracted Proforma Invoice No: {} from document for Job No: {}", extractedProformaInvoiceNo, jobNo);
                    }
                }
            }

            // Check for missing identifiers and provide specific error messages
            if (isNotFound(extractedSalesQuoteNo)) {
                finalDiscrepancies.add("Cannot find Sales Quote Number from Sales Quote document");
                log.warn("Cannot find Sales Quote Number from Sales Quote document for Job No: {}", jobNo);
            }

            if (isNotFound(extractedProformaInvoiceNo)) {
                finalDiscrepancies.add("Cannot find Tax Invoice Number from Proforma Invoice document - please check Proforma Invoice");
                log.warn("Cannot find Tax Invoice Number from Proforma Invoice document for Job No: {}", jobNo);
            }

            // If we have missing identifiers, return early
            if (!finalDiscrepancies.isEmpty()) {
                log.warn("Missing required document identifiers for Job No: {}. Cannot proceed with verification.", jobNo);
                return finalDiscrepancies;
            }

            // Now fetch Business Central data using the extracted document numbers
            log.info("Using extracted document numbers for verification - Sales Quote No: {}, Proforma Invoice No: {}, Job No: {}",
                    extractedSalesQuoteNo, extractedProformaInvoiceNo, jobNo);

            var bcDataTuple = businessCentralService.fetchAllVerificationData(
                    extractedSalesQuoteNo, extractedProformaInvoiceNo, jobNo).block();

            if (bcDataTuple == null) {
                log.error("Failed to fetch verification data from Business Central for Job No: {}", jobNo);
                finalDiscrepancies.add("Critical Error: Failed to fetch required data from Business Central.");
                return finalDiscrepancies;
            }

            // Prepare all ERP data for the verification
            Map<String, Object> allErpData = new HashMap<>();

            // Add Sales Quote data if available
            SalesQuoteDTO bcSalesQuote = bcDataTuple.getT1();
            List<SalesQuoteLineDTO> bcSalesQuoteLines = Optional.ofNullable(bcDataTuple.getT2()).orElse(Collections.emptyList());
            if (bcSalesQuote != null) {
                log.info("Found Sales Quote data in Business Central for Quote No: {}, Job No: {}", bcSalesQuote.getNo(), jobNo);
                allErpData.put("salesQuoteHeader", bcSalesQuote);
                allErpData.put("salesQuoteLines", bcSalesQuoteLines);
            } else {
                log.warn("No Sales Quote data found in Business Central for extracted Quote No: {}, Job No: {}",
                        extractedSalesQuoteNo, jobNo);
            }

            // Add Proforma Invoice data if available
            SalesInvoiceDTO bcSalesInvoice = bcDataTuple.getT3();
            List<SalesInvoiceLineDTO> bcSalesInvoiceLines = Optional.ofNullable(bcDataTuple.getT4()).orElse(Collections.emptyList());
            if (bcSalesInvoice != null) {
                log.info("Found Proforma Invoice data in Business Central for Invoice No: {}, Job No: {}", bcSalesInvoice.getNo(), jobNo);
                allErpData.put("salesInvoiceHeader", bcSalesInvoice);
                allErpData.put("salesInvoiceLines", bcSalesInvoiceLines);
            } else {
                log.warn("No Proforma Invoice data found in Business Central for extracted Invoice No: {}, Job No: {}",
                        extractedProformaInvoiceNo, jobNo);
            }

            // Add Job Consumption data if available
            List<JobLedgerEntryDTO> bcJobLedgerEntries = Optional.ofNullable(bcDataTuple.getT5()).orElse(Collections.emptyList());
            if (bcJobLedgerEntries != null && !bcJobLedgerEntries.isEmpty()) {
                log.info("Found {} Job Ledger Entries in Business Central for Job No: {}", bcJobLedgerEntries.size(), jobNo);
                allErpData.put("jobLedgerEntries", bcJobLedgerEntries);
            } else {
                log.warn("No Job Ledger Entries found in Business Central for Job No: {}", jobNo);
            }

            // Now perform the verification with the fetched data
            try {
                log.info("Performing verification with fetched Business Central data for Job No: {}", jobNo);
                ClassifyAndVerifyResultDTO result = llmProxyService.classifyAndVerifyDocument(jobNo, allDocumentImages, allErpData);

                // Process the result
                if (result != null) {
                    log.info("Verification completed for document type '{}' for Job No: {} with confidence: {}",
                            result.getDocumentType(), jobNo, result.getOverallVerificationConfidence());

                    // Add discrepancies from the result
                    if (result.getDiscrepancies() != null && !result.getDiscrepancies().isEmpty()) {
                        log.info("Found {} discrepancies during verification for Job No: {}", result.getDiscrepancies().size(), jobNo);
                        for (ClassifyAndVerifyResultDTO.DiscrepancyDTO discrepancy : result.getDiscrepancies()) {
                            String discrepancyMessage = String.format("%s: Document value '%s' does not match ERP value '%s' (Severity: %s)",
                                    discrepancy.getFieldName(),
                                    discrepancy.getDocumentValue(),
                                    discrepancy.getErpValue(),
                                    discrepancy.getSeverity());
                            finalDiscrepancies.add(discrepancyMessage);
                        }
                    } else {
                        log.info("No discrepancies found during verification for Job No: {}", jobNo);
                    }

                    bcChecksPerformedOverall = true;
                } else {
                    log.error("Verification returned null result for Job No: {}", jobNo);
                    finalDiscrepancies.add("Error: Verification failed for Job No: " + jobNo);
                }
            } catch (Exception e) {
                log.error("Error during verification for Job No: {}: {}",
                        jobNo, e.getMessage(), e);
                finalDiscrepancies.add("Critical Error during verification: " + e.getMessage());
            }

        } catch (Exception e) {
            log.error("Error fetching/processing Business Central data for Job No: {}: {}",
                    jobNo, e.getMessage(), e);
            finalDiscrepancies.add("Critical Error: Failed during Business Central data interaction: " + e.getMessage());
        }

        // Final Result and BC Update
        log.debug("Finalizing verification for Job No: {}", jobNo);
        if (finalDiscrepancies.isEmpty() && bcChecksPerformedOverall) {
            log.info("SUCCESS: All document verifications passed for Job No: {}. Attempting to update BC.", jobNo);
            try {
                String formattedDate = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
                String verificationComment = "Verified by AI LLM Service - All documents passed verification";

                try {
                    // Try the Web Service first for full update
                    log.info("Attempting to update all verification fields using Web Service for Job No: {}", jobNo);
                    businessCentralWebService.updateAllVerificationFields(jobNo, verificationComment).block();
                    log.info("Successfully updated all verification fields using Web Service for Job No: {}", jobNo);
                } catch (Exception webServiceException) {
                    // If Web Service fails, fall back to just updating the date field
                    log.warn("Web Service update failed for Job No: {}, falling back to date field update: {}",
                            jobNo, webServiceException.getMessage());

                    businessCentralService.updateJobCardField(jobNo, "_x0032_nd_Check_Date", formattedDate).block();
                    log.info("Successfully updated '_x0032_nd_Check_Date' in BC for Job No: {}", jobNo);
                }
            } catch (Exception updateException) {
                log.error("Failed to update BC fields for Job No: {} after successful verification: {}",
                        jobNo, updateException.getMessage(), updateException);

                // Check for specific Business Central error messages
                String errorMessage = updateException.getMessage();
                if (errorMessage != null && errorMessage.toLowerCase().contains("1st check date must have a value")) {
                    finalDiscrepancies.add("Failed to update BC because job doesn't have first check date");
                } else if (errorMessage != null && errorMessage.toLowerCase().contains("unauthorized")) {
                    finalDiscrepancies.add("Failed to update BC due to authentication error");
                } else {
                    finalDiscrepancies.add("Warning: Verification passed, but failed to update Business Central status: " +
                            errorMessage);
                }
            }
        } else if (finalDiscrepancies.isEmpty() && !bcChecksPerformedOverall) {
            log.warn("Verification for Job No: {} resulted in no discrepancies, but not all Business Central checks were performed or completed. BC status not updated.", jobNo);
            finalDiscrepancies.add("Info: Document checks found no issues, but full Business Central validation was incomplete.");
        } else {
            log.warn("FAILURE: Document verification found discrepancies for Job No: {}. Count: {}", jobNo, finalDiscrepancies.size());
        }

        if (!finalDiscrepancies.isEmpty()) {
            log.warn("Final discrepancies for Job No: {}", jobNo);
            finalDiscrepancies.forEach(d -> log.warn("- {}", d));
        } else if (bcChecksPerformedOverall) {
            log.info("Verification completed successfully with no discrepancies for Job No: {}", jobNo);
        }

        return finalDiscrepancies;
    }

    public List<String> verifyJobDocuments(JobLedgerEntryDTO legacyLedgerEntry, String jobNo) {
        log.info("Starting document verification for Job No: {}", jobNo);
        List<String> finalDiscrepancies = new ArrayList<>();
        boolean bcChecksPerformedOverall = false;

        // First, check if the job exists in Business Central
        try {
            var jobListEntry = businessCentralService.fetchJobListEntry(jobNo).block();
            if (jobListEntry == null) {
                log.error("Job No: {} does not exist in Business Central. Cannot proceed with verification.", jobNo);
                finalDiscrepancies.add("Critical Error: Job No: " + jobNo + " does not exist in Business Central.");
                return finalDiscrepancies;
            } else {
                log.info("Job No: {} exists in Business Central. Proceeding with verification.", jobNo);
            }
        } catch (Exception e) {
            log.error("Error checking if Job No: {} exists in Business Central: {}", jobNo, e.getMessage(), e);
            // Continue with verification even if we can't check if the job exists
        }

        // Check if documents already exist in the database
        boolean salesQuoteExists = jobDocumentService.documentExists(jobNo, SALES_QUOTE_TYPE);
        boolean proformaInvoiceExists = jobDocumentService.documentExists(jobNo, PROFORMA_INVOICE_TYPE);
        boolean jobConsumptionExists = jobDocumentService.documentExists(jobNo, JOB_CONSUMPTION_TYPE);

        // Check for any UNCLASSIFIED documents that might have been classified but not updated
        log.info("Checking for UNCLASSIFIED documents that need updating for Job No: '{}'", jobNo);
        List<JobDocument> unclassifiedDocs = jobDocumentService.getJobDocumentsByType(jobNo, "UNCLASSIFIED");

        if (!unclassifiedDocs.isEmpty()) {
            log.info("Found {} UNCLASSIFIED documents for Job No: '{}', checking if any need updating",
                    unclassifiedDocs.size(), jobNo);

            int updatedCount = 0;
            for (JobDocument doc : unclassifiedDocs) {
                if (doc.getClassifiedDocumentType() != null && !doc.getClassifiedDocumentType().isEmpty()) {
                    String classifiedType = doc.getClassifiedDocumentType();
                    log.info("Found document with UNCLASSIFIED type but classified as '{}' for Job No: '{}', Document ID: {}. Updating document type.",
                            classifiedType, jobNo, doc.getId());

                    // Update document type to match classified type
                    doc.setDocumentType(classifiedType);
                    jobDocumentService.saveJobDocument(doc);
                    updatedCount++;

                    // Update existence flags based on the classified type
                    if (SALES_QUOTE_TYPE.equals(classifiedType)) {
                        salesQuoteExists = true;
                    } else if (PROFORMA_INVOICE_TYPE.equals(classifiedType)) {
                        proformaInvoiceExists = true;
                    } else if (JOB_CONSUMPTION_TYPE.equals(classifiedType)) {
                        jobConsumptionExists = true;
                    }
                }
            }

            if (updatedCount > 0) {
                log.info("Updated {} UNCLASSIFIED documents for Job No: '{}'", updatedCount, jobNo);

                // No need for delay with proper transaction management
                log.debug("Document type updates committed with proper transaction management");
            } else {
                log.info("No UNCLASSIFIED documents needed updating for Job No: '{}'", jobNo);
            }
        } else {
            log.info("No UNCLASSIFIED documents found for Job No: '{}'", jobNo);
        }

        // If any required document is missing, try to fetch from SharePoint
        if (!salesQuoteExists || !proformaInvoiceExists || !jobConsumptionExists) {
            log.info("Some documents are missing in database for Job No: {}. Fetching from SharePoint...", jobNo);
            try {
                List<String> downloadedDocumentTypes = jobAttachmentService.fetchAndStoreJobAttachments(jobNo)
                        .collectList()
                        .block();

                if (downloadedDocumentTypes != null && !downloadedDocumentTypes.isEmpty()) {
                    log.info("Successfully downloaded {} documents from SharePoint for Job No: {}: {}",
                            downloadedDocumentTypes.size(), jobNo, downloadedDocumentTypes);

                    // Update document existence flags
                    salesQuoteExists = salesQuoteExists || downloadedDocumentTypes.contains(SALES_QUOTE_TYPE);
                    proformaInvoiceExists = proformaInvoiceExists || downloadedDocumentTypes.contains(PROFORMA_INVOICE_TYPE);
                    jobConsumptionExists = jobConsumptionExists || downloadedDocumentTypes.contains(JOB_CONSUMPTION_TYPE);
                } else {
                    log.error("No documents were downloaded from SharePoint for Job No: {}. This is a critical issue.", jobNo);
                    log.error("Please check if the job exists in Business Central and has documents attached.");
                    log.error("Attempting to continue verification with available data, but results may be incomplete.");

                    // Add a specific discrepancy for missing documents
                    finalDiscrepancies.add("Critical: No documents could be downloaded from SharePoint for Job No: " + jobNo);
                }
            } catch (Exception e) {
                log.error("Error fetching job attachments from Business Central/SharePoint for Job No: {}", jobNo, e);
                log.error("Detailed error: Type={}, Message={}", e.getClass().getName(), e.getMessage());
                finalDiscrepancies.add("Error fetching documents from Business Central/SharePoint: " + e.getMessage());
            }
        } else {
            log.info("All required documents already exist in database for Job No: {}", jobNo);
        }

        // Step 1: Process Sales Quote with LLM
        String extractedSalesQuoteNo = null;
        try {
            log.info("Processing Sales Quote for Job No: {}", jobNo);
            List<byte[]> salesQuoteImages = getDocumentImagesFromDatabase(jobNo, SALES_QUOTE_TYPE);

            // Only fall back to legacy file system if not found in database and not downloaded from SharePoint
            if (salesQuoteImages.isEmpty() && !salesQuoteExists) {
                log.info("Sales Quote not found in database for Job No: {}. Trying legacy file system...", jobNo);
                try {
                    Path salesQuotePath = findDocumentPath(jobNo, SALES_QUOTE_BASE_NAME);
                    salesQuoteImages = getDocumentImages(salesQuotePath);
                    log.info("Found Sales Quote in legacy file system for Job No: {}", jobNo);
                } catch (IOException e) {
                    log.warn("Sales Quote not found in legacy file system for Job No: {}", jobNo);
                }
            }

            if (!salesQuoteImages.isEmpty()) {
                Map<String, String> identifiers = llmProxyService.extractDocumentIdentifiers(jobNo, SALES_QUOTE_TYPE, salesQuoteImages);
                extractedSalesQuoteNo = identifiers.get(SALES_QUOTE_NUMBER_KEY);

                if (!isNotFound(extractedSalesQuoteNo)) {
                    log.info("LLM extracted Sales Quote No: {} for Job No: {}", extractedSalesQuoteNo, jobNo);
                } else {
                    finalDiscrepancies.add("Cannot find Sales Quote Number from Sales Quote document");
                    log.warn("Cannot find Sales Quote Number from Sales Quote document for Job No: {}", jobNo);
                }
            } else {
                finalDiscrepancies.add("Sales Quote document not found or is empty for Job No: " + jobNo);
            }
        } catch (Exception e) {
            log.error("Error processing Sales Quote document for job {}: {}", jobNo, e.getMessage(), e);
            finalDiscrepancies.add("Critical Error during Sales Quote document handling: " + e.getMessage());
        }

        // Step 2: Process Proforma Invoice with LLM
        String extractedProformaInvoiceNo = null;
        try {
            log.info("Processing Proforma Invoice for Job No: {}", jobNo);
            List<byte[]> proformaInvoiceImages = getDocumentImagesFromDatabase(jobNo, PROFORMA_INVOICE_TYPE);

            // Only fall back to legacy file system if not found in database and not downloaded from SharePoint
            if (proformaInvoiceImages.isEmpty() && !proformaInvoiceExists) {
                log.info("Proforma Invoice not found in database for Job No: {}. Trying legacy file system...", jobNo);
                try {
                    Path proformaInvoicePath = findDocumentPath(jobNo, PROFORMA_INVOICE_BASE_NAME);
                    proformaInvoiceImages = getDocumentImages(proformaInvoicePath);
                    log.info("Found Proforma Invoice in legacy file system for Job No: {}", jobNo);
                } catch (IOException e) {
                    log.warn("Proforma Invoice not found in legacy file system for Job No: {}", jobNo);
                }
            }

            if (!proformaInvoiceImages.isEmpty()) {
                Map<String, String> identifiers = llmProxyService.extractDocumentIdentifiers(jobNo, PROFORMA_INVOICE_TYPE, proformaInvoiceImages);
                extractedProformaInvoiceNo = identifiers.get(PROFORMA_INVOICE_NUMBER_KEY);

                if (!isNotFound(extractedProformaInvoiceNo)) {
                    log.info("LLM extracted Proforma Invoice No: {} for Job No: {}", extractedProformaInvoiceNo, jobNo);
                } else {
                    finalDiscrepancies.add("Cannot find Tax Invoice Number from Proforma Invoice document - please check Proforma Invoice");
                    log.warn("Cannot find Tax Invoice Number from Proforma Invoice document for Job No: {}", jobNo);
                }
            } else {
                finalDiscrepancies.add("Proforma Invoice document not found or is empty for Job No: " + jobNo);
            }
        } catch (Exception e) {
            log.error("Error processing Proforma Invoice document for job {}: {}", jobNo, e.getMessage(), e);
            finalDiscrepancies.add("Critical Error during Proforma Invoice document handling: " + e.getMessage());
        }

        // Step 3: Fetch Business Central data using extracted document numbers
        if (!isNotFound(extractedSalesQuoteNo) && !isNotFound(extractedProformaInvoiceNo)) {
            try {
                var bcDataTuple = businessCentralService.fetchAllVerificationData(
                        extractedSalesQuoteNo, extractedProformaInvoiceNo, jobNo
                ).block();

                if (bcDataTuple != null) {
                    // Step 4: Verify Sales Quote with LLM
                    SalesQuoteDTO bcSalesQuote = bcDataTuple.getT1();
                    List<SalesQuoteLineDTO> bcSalesQuoteLines = Optional.ofNullable(bcDataTuple.getT2()).orElse(Collections.emptyList());

                    if (bcSalesQuote != null) {
                        try {
                            List<byte[]> salesQuoteImages = getDocumentImagesFromDatabase(jobNo, SALES_QUOTE_TYPE);

                            // Only fall back to legacy file system if not found in database and not downloaded from SharePoint
                            if (salesQuoteImages.isEmpty() && !salesQuoteExists) {
                                log.info("Sales Quote not found in database for Job No: {}. Trying legacy file system...", jobNo);
                                try {
                                    Path salesQuotePath = findDocumentPath(jobNo, SALES_QUOTE_BASE_NAME);
                                    salesQuoteImages = getDocumentImages(salesQuotePath);
                                    log.info("Found Sales Quote in legacy file system for Job No: {}", jobNo);
                                } catch (IOException e) {
                                    log.warn("Sales Quote not found in legacy file system for Job No: {}", jobNo);
                                }
                            }

                            if (!salesQuoteImages.isEmpty()) {
                                Map<String, Object> erpDataForQuote = Map.of(
                                        "salesQuoteHeader", bcSalesQuote,
                                        "salesQuoteLines", bcSalesQuoteLines
                                );
                                GeminiVerificationResult quoteResult = llmProxyService.verifyDocument(jobNo, SALES_QUOTE_TYPE, salesQuoteImages, erpDataForQuote);
                                finalDiscrepancies.addAll(transformGeminiDiscrepancies(quoteResult, "Sales Quote"));
                                logGeminiResult(quoteResult, "Sales Quote", jobNo);
                                bcChecksPerformedOverall = true;
                            }
                        } catch (IOException e) {
                            log.error("Error verifying Sales Quote document for job {}: {}", jobNo, e.getMessage(), e);
                            finalDiscrepancies.add("Critical Error during Sales Quote verification: " + e.getMessage());
                        }
                    } else {
                        finalDiscrepancies.add("Sales Quote: BC data not found for extracted number: " + extractedSalesQuoteNo);
                        log.warn("Sales Quote: BC data not found for extracted number: {} for job {}", extractedSalesQuoteNo, jobNo);
                    }

                    // Step 5: Verify Proforma Invoice with LLM
                    SalesInvoiceDTO bcSalesInvoice = bcDataTuple.getT3();
                    List<SalesInvoiceLineDTO> bcSalesInvoiceLines = Optional.ofNullable(bcDataTuple.getT4()).orElse(Collections.emptyList());

                    if (bcSalesInvoice != null) {
                        try {
                            List<byte[]> proformaInvoiceImages = getDocumentImagesFromDatabase(jobNo, PROFORMA_INVOICE_TYPE);

                            // Only fall back to legacy file system if not found in database and not downloaded from SharePoint
                            if (proformaInvoiceImages.isEmpty() && !proformaInvoiceExists) {
                                log.info("Proforma Invoice not found in database for Job No: {}. Trying legacy file system...", jobNo);
                                try {
                                    Path proformaInvoicePath = findDocumentPath(jobNo, PROFORMA_INVOICE_BASE_NAME);
                                    proformaInvoiceImages = getDocumentImages(proformaInvoicePath);
                                    log.info("Found Proforma Invoice in legacy file system for Job No: {}", jobNo);
                                } catch (IOException e) {
                                    log.warn("Proforma Invoice not found in legacy file system for Job No: {}", jobNo);
                                }
                            }

                            if (!proformaInvoiceImages.isEmpty()) {
                                Map<String, Object> erpDataForInvoice = Map.of(
                                        "salesInvoiceHeader", bcSalesInvoice,
                                        "salesInvoiceLines", bcSalesInvoiceLines
                                );
                                GeminiVerificationResult invoiceResult = llmProxyService.verifyDocument(jobNo, PROFORMA_INVOICE_TYPE, proformaInvoiceImages, erpDataForInvoice);
                                finalDiscrepancies.addAll(transformGeminiDiscrepancies(invoiceResult, "Proforma Invoice"));
                                logGeminiResult(invoiceResult, "Proforma Invoice", jobNo);
                                bcChecksPerformedOverall = true;
                            }
                        } catch (IOException e) {
                            log.error("Error verifying Proforma Invoice document for job {}: {}", jobNo, e.getMessage(), e);
                            finalDiscrepancies.add("Critical Error during Proforma Invoice verification: " + e.getMessage());
                        }
                    } else {
                        finalDiscrepancies.add("Proforma Invoice: BC data not found for extracted number: " + extractedProformaInvoiceNo);
                        log.warn("Proforma Invoice: BC data not found for extracted number: {} for job {}", extractedProformaInvoiceNo, jobNo);
                    }

                    // Step 6: Verify Job Consumption with LLM
                    List<JobLedgerEntryDTO> bcJobLedgerEntries = Optional.ofNullable(bcDataTuple.getT5()).orElse(Collections.emptyList());

                    if (bcJobLedgerEntries != null && !bcJobLedgerEntries.isEmpty()) {
                        try {
                            List<byte[]> jobConsumptionImages = getDocumentImagesFromDatabase(jobNo, JOB_CONSUMPTION_TYPE);

                            // Only fall back to legacy file system if not found in database and not downloaded from SharePoint
                            if (jobConsumptionImages.isEmpty() && !jobConsumptionExists) {
                                log.info("Job Consumption not found in database for Job No: {}. Trying legacy file system...", jobNo);
                                try {
                                    Path jobConsumptionPath = findDocumentPath(jobNo, JOB_CONSUMPTION_BASE_NAME);
                                    jobConsumptionImages = getDocumentImages(jobConsumptionPath);
                                    log.info("Found Job Consumption in legacy file system for Job No: {}", jobNo);
                                } catch (IOException e) {
                                    log.warn("Job Consumption not found in legacy file system for Job No: {}", jobNo);
                                }
                            }

                            if (!jobConsumptionImages.isEmpty()) {
                                Map<String, Object> erpDataForJobConsumption = Map.of(
                                        "jobLedgerEntries", bcJobLedgerEntries
                                );
                                GeminiVerificationResult jobConsumptionResult = llmProxyService.verifyDocument(jobNo, JOB_CONSUMPTION_TYPE, jobConsumptionImages, erpDataForJobConsumption);
                                finalDiscrepancies.addAll(transformGeminiDiscrepancies(jobConsumptionResult, "Job Consumption"));
                                logGeminiResult(jobConsumptionResult, "Job Consumption", jobNo);
                                bcChecksPerformedOverall = true;
                            }
                        } catch (IOException e) {
                            log.error("Error verifying Job Consumption document for job {}: {}", jobNo, e.getMessage(), e);
                            finalDiscrepancies.add("Critical Error during Job Consumption verification: " + e.getMessage());
                        }
                    } else {
                        finalDiscrepancies.add("Job Consumption: BC job ledger entries not found for job: " + jobNo);
                        log.warn("Job Consumption: BC job ledger entries not found for job: {}", jobNo);
                    }
                } else {
                    log.error("Failed to fetch verification data tuple from Business Central, Job No: {}", jobNo);
                    finalDiscrepancies.add("Critical Error: Failed to fetch required data from Business Central.");
                }
            } catch (Exception bcException) {
                log.error("Error fetching/processing Business Central data, Job No: {}. {}", jobNo, bcException.getMessage(), bcException);
                finalDiscrepancies.add("Critical Error: Failed during Business Central data interaction. " + bcException.getMessage());
            }
        } else {
            log.warn("Skipping Business Central checks for Job No: {} due to missing document numbers.", jobNo);

            // Add specific error messages for missing identifiers
            if (isNotFound(extractedSalesQuoteNo)) {
                finalDiscrepancies.add("Cannot find Sales Quote Number from Sales Quote document");
            }
            if (isNotFound(extractedProformaInvoiceNo)) {
                finalDiscrepancies.add("Cannot find Tax Invoice Number from Proforma Invoice document - please check Proforma Invoice");
            }
        }

        // Final Result and BC Update
        log.debug("Finalizing verification for Job No: {}", jobNo);
        if (finalDiscrepancies.isEmpty() && bcChecksPerformedOverall) {
            log.info("SUCCESS: All document verifications passed for Job No: {}. Attempting to update BC.", jobNo);
            try {
                // Update all verification fields in Business Central
                String verificationComment = "Verified by AI LLM Service - All documents passed verification";
                businessCentralService.updateAllVerificationFields(jobNo, verificationComment).block();
                log.info("Successfully updated all verification fields in BC for Job No: {}", jobNo);
            } catch (Exception updateException) {
                log.error("Failed to update verification fields in BC for Job No: {} after successful verification: {}",
                        jobNo, updateException.getMessage(), updateException);

                // Check for specific Business Central error messages
                String errorMessage = updateException.getMessage();
                if (errorMessage != null && errorMessage.toLowerCase().contains("1st check date must have a value")) {
                    finalDiscrepancies.add("Failed to update BC because job doesn't have first check date");
                } else if (errorMessage != null && errorMessage.toLowerCase().contains("unauthorized")) {
                    finalDiscrepancies.add("Failed to update BC due to authentication error");
                } else {
                    finalDiscrepancies.add("Warning: Verification passed, but failed to update Business Central status: " +
                            errorMessage);
                }
            }
        } else if (finalDiscrepancies.isEmpty() && !bcChecksPerformedOverall) {
            log.warn("Verification for Job No: {} resulted in no discrepancies, but not all Business Central checks were performed or completed. BC status not updated.", jobNo);
            finalDiscrepancies.add("Info: Document checks found no issues, but full Business Central validation was incomplete.");
        } else {
            log.warn("FAILURE: Document verification found discrepancies for Job No: {}. Count: {}", jobNo, finalDiscrepancies.size());

            // Update Business Central with failure details
            try {
                String verificationComment = "Verification FAILED - " + String.join("; ", finalDiscrepancies);
                businessCentralService.updateAllVerificationFields(jobNo, verificationComment, false).block();
                log.info("Successfully updated BC with failure details for Job No: {}", jobNo);
            } catch (Exception updateException) {
                log.error("Failed to update BC with failure details for Job No: {}: {}",
                        jobNo, updateException.getMessage(), updateException);

                // Check for specific Business Central error messages
                String errorMessage = updateException.getMessage();
                if (errorMessage != null && errorMessage.toLowerCase().contains("1st check date must have a value")) {
                    finalDiscrepancies.add("Failed to update BC because job doesn't have first check date");
                } else if (errorMessage != null && errorMessage.toLowerCase().contains("unauthorized")) {
                    finalDiscrepancies.add("Failed to update BC due to authentication error");
                } else {
                    finalDiscrepancies.add("Warning: Failed to update Business Central with verification results: " +
                            errorMessage);
                }
            }
        }

        if (!finalDiscrepancies.isEmpty()) {
            log.warn("Final discrepancies for Job No: {}", jobNo);
            finalDiscrepancies.forEach(d -> log.warn("- {}", d));
        } else if (bcChecksPerformedOverall) {
            log.info("Verification completed successfully with no discrepancies for Job No: {}", jobNo);
        }

        return finalDiscrepancies;
    }

    /**
     * Gets document images from the database.
     *
     * @param jobNo The job number
     * @param documentType The document type
     * @return List of document images as byte arrays
     */
    private List<byte[]> getDocumentImagesFromDatabase(String jobNo, String documentType) {
        log.info("Getting document images from database for Job No: '{}', Document Type: '{}'", jobNo, documentType);

        // Normalize parameters if needed
        String normalizedJobNo = jobNo.trim();
        String normalizedDocumentType = documentType.trim();

        if (!normalizedJobNo.equals(jobNo) || !normalizedDocumentType.equals(documentType)) {
            log.debug("Normalized parameters - Original Job No: '{}', Normalized: '{}', Original Document Type: '{}', Normalized: '{}'",
                    jobNo, normalizedJobNo, documentType, normalizedDocumentType);
        }

        // Log transaction information if possible
        try {
            log.debug("Transaction active: {}, Transaction name: {}",
                    TransactionSynchronizationManager.isActualTransactionActive(),
                    TransactionSynchronizationManager.getCurrentTransactionName());
        } catch (Exception e) {
            log.debug("Could not log transaction details: {}", e.getMessage());
        }

        // Find document by either document type or classified document type (unified search)
        Optional<JobDocument> documentOpt = jobDocumentService.getJobDocument(normalizedJobNo, normalizedDocumentType);

        // If still not found, try to find UNCLASSIFIED documents and use one as a fallback
        if (documentOpt.isEmpty()) {
            log.info("Document not found by document type or classified document type, checking UNCLASSIFIED documents for Job No: '{}'",
                    normalizedJobNo);

            List<JobDocument> unclassifiedDocs = jobDocumentService.getJobDocumentsByType(normalizedJobNo, "UNCLASSIFIED");
            if (!unclassifiedDocs.isEmpty()) {
                log.info("Found {} UNCLASSIFIED documents for Job No: '{}', using first one as fallback",
                        unclassifiedDocs.size(), normalizedJobNo);

                // Use the first UNCLASSIFIED document as a fallback
                documentOpt = Optional.of(unclassifiedDocs.get(0));

                // Log that we're using an UNCLASSIFIED document
                log.info("Using UNCLASSIFIED document with ID: {} as fallback for requested document type: '{}'",
                        documentOpt.get().getId(), normalizedDocumentType);

                // Attempt to update the document type for future reference
                try {
                    JobDocument doc = documentOpt.get();
                    doc.setClassifiedDocumentType(normalizedDocumentType);
                    jobDocumentService.saveJobDocument(doc);
                    log.info("Updated UNCLASSIFIED document ID: {} with classified type: '{}'",
                            doc.getId(), normalizedDocumentType);
                } catch (Exception e) {
                    log.warn("Failed to update UNCLASSIFIED document with classified type: '{}'. Error: {}",
                            normalizedDocumentType, e.getMessage());
                    // Continue with the document we have
                }
            }
        }

        if (documentOpt.isPresent()) {
            JobDocument document = documentOpt.get();
            log.info("Found document in database with ID: {}, Job No: '{}', Document Type: '{}', Classified Type: '{}', File Name: '{}'",
                    document.getId(), document.getJobNo(), document.getDocumentType(),
                    document.getClassifiedDocumentType(), document.getFileName());

            byte[] documentData = document.getDocumentData();

            if (documentData == null || documentData.length == 0) {
                log.warn("Document data is empty for Job No: '{}', Document Type: '{}'", normalizedJobNo, normalizedDocumentType);
                return Collections.emptyList();
            }

            log.debug("Document data size: {} bytes", documentData.length);

            String contentType = document.getContentType();
            if (contentType != null && contentType.equals("application/pdf")) {
                try {
                    // For PDF files, convert to images
                    log.debug("Converting PDF to images for Job No: '{}', Document Type: '{}'", normalizedJobNo, normalizedDocumentType);

                    // Create a temporary file
                    Path tempFile = Files.createTempFile("temp_pdf_", ".pdf");
                    Files.write(tempFile, documentData);
                    log.debug("Created temporary PDF file: {}", tempFile);

                    // Convert PDF to images
                    List<byte[]> images = pdfImageConverter.convertPdfToImages(tempFile.toString(), 300f);
                    log.debug("Converted PDF to {} images", images.size());

                    // Delete the temporary file
                    Files.delete(tempFile);
                    log.debug("Deleted temporary PDF file");

                    // Check if we got any images
                    if (images.isEmpty()) {
                        log.warn("PDF conversion returned no images for Job No: '{}', Document Type: '{}'", normalizedJobNo, normalizedDocumentType);
                        return Collections.emptyList();
                    }

                    // If we only got one image and it's small, it might be an error image
                    if (images.size() == 1 && images.get(0).length < 50000) {
                        log.warn("PDF conversion may have returned an error image for Job No: '{}', Document Type: '{}', Image size: {} bytes",
                                normalizedJobNo, normalizedDocumentType, images.get(0).length);
                        // We'll still return it, but log a warning
                    }

                    log.info("Successfully converted PDF to {} images for Job No: '{}', Document Type: '{}'",
                            images.size(), normalizedJobNo, normalizedDocumentType);
                    return images;
                } catch (IOException e) {
                    log.error("Error converting PDF to images for Job No: '{}', Document Type: '{}'", normalizedJobNo, normalizedDocumentType, e);
                    // Return the raw PDF data as a fallback
                    log.debug("Returning raw PDF data as fallback");
                    return List.of(documentData);
                }
            } else {
                // For image files, return as is
                log.debug("Document is not a PDF, returning as is. Content type: '{}'", contentType);
                return List.of(documentData);
            }
        } else {
            log.warn("Document not found in database for Job No: '{}', Document Type: '{}'", normalizedJobNo, normalizedDocumentType);
            return Collections.emptyList();
        }
    }

    /**
     * Legacy method to get document images from the file system.
     */
    private List<byte[]> getDocumentImages(Path documentPath) throws IOException {
        if (documentPath == null) {
            log.warn("Document path is null, cannot get images.");
            throw new IOException("Document path is null.");
        }
        String fileName = documentPath.getFileName().toString().toLowerCase();
        if (fileName.endsWith(".pdf")) {
            // For PDF files, use our PDF to image converter
            // Convert PDF to images with 300 DPI for good quality
            return pdfImageConverter.convertPdfToImages(documentPath.toString(), 300f);
        } else if (fileName.endsWith(".png") || fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) {
            return List.of(Files.readAllBytes(documentPath));
        } else {
            log.error("Unsupported document file type for image conversion: {}", fileName);
            throw new IOException("Unsupported document file type: " + fileName);
        }
    }

    private List<String> transformGeminiDiscrepancies(GeminiVerificationResult result, String documentContext) {
        if (result == null || result.getDiscrepancies() == null || result.getDiscrepancies().isEmpty()) {
            log.info("No discrepancies found by LLM for {}.", documentContext);
            return Collections.emptyList();
        }
        List<String> discrepancyStrings = new ArrayList<>();
        for (GeminiDiscrepancy discrepancy : result.getDiscrepancies()) {
            discrepancyStrings.add(discrepancy.getDescription());
        }
        log.info("Transformed {} LLM discrepancies for {}.", discrepancyStrings.size(), documentContext);
        return discrepancyStrings;
    }

    private void logGeminiResult(GeminiVerificationResult result, String documentContext, String jobNo) {
        if (result == null) {
            log.warn("LLM result is null for {} on Job No: {}", documentContext, jobNo);
            return;
        }
        log.info("LLM Verification Result for [{} - Job {}]: Overall Confidence: {}",
                documentContext, jobNo, result.getOverallVerificationConfidence());
        if (result.getFieldConfidences() != null) {
            result.getFieldConfidences().forEach(fc ->
                    log.debug("  Field: {}, Extracted: '{}', VerificationConfidence: {}, ExtractionConfidence: {}",
                            fc.getFieldName(), fc.getExtractedValue(), fc.getVerificationConfidence(), fc.getExtractionConfidence())
            );
        }
        if (result.getExtractedValues() != null && !result.getExtractedValues().isEmpty()) {
            log.debug("  Extracted Values by LLM: {}", result.getExtractedValues());
        }
    }

    private Path findDocumentPath(String jobNo, String documentBaseName) throws IOException {
        Path dirPath = Paths.get(PDF_BASE_PATH, jobNo);
        if (!Files.isDirectory(dirPath)) {
            throw new IOException("Directory not found for Job No: " + jobNo + " at path: " + dirPath.toAbsolutePath());
        }
        for (String ext : SUPPORTED_EXTENSIONS) {
            Path potentialPath = dirPath.resolve(documentBaseName + ext);
            if (Files.isRegularFile(potentialPath)) {
                log.info("Found document for job {}: {}", jobNo, potentialPath.toAbsolutePath());
                return potentialPath;
            }
        }
        throw new IOException("Document with base name '" + documentBaseName + "' (PDF or Image) not found in directory: " + dirPath.toAbsolutePath());
    }

    private boolean isNotFound(String value) {
        return value == null || value.isBlank() || "Not found".equalsIgnoreCase(value.trim());
    }

    /**
     * Extracts all document identifiers from job documents.
     * This method processes all documents for a job and extracts key identifiers
     * (sales quote number, invoice number) before any Business Central checks.
     *
     * @param jobNo The job number
     * @return Map of document types to their extracted identifiers
     */
    public Map<String, String> extractAllDocumentIdentifiers(String jobNo) {
        log.info("Starting extraction of all document identifiers for Job No: {}", jobNo);
        Map<String, String> extractedIdentifiers = new HashMap<>();

        // Check if we already have cached identifiers for this job
        if (extractedIdentifiersCache.containsKey(jobNo)) {
            log.info("Using cached document identifiers for Job No: {}", jobNo);
            return new HashMap<>(extractedIdentifiersCache.get(jobNo));
        }

        // Implement a retry mechanism to handle potential transaction isolation issues
        int maxRetries = 3;
        int retryCount = 0;
        List<JobDocument> allDocuments = Collections.emptyList();

        while (allDocuments.isEmpty() && retryCount < maxRetries) {
            if (retryCount > 0) {
                log.info("Retry attempt {} of {} for getting documents for Job No: {}",
                        retryCount, maxRetries, jobNo);

                // Add a small delay before retrying to allow any pending transactions to complete
                try {
                    Thread.sleep(1000 * retryCount); // Increasing delay with each retry
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("Interrupted while waiting to retry document retrieval: {}", e.getMessage());
                }
            }

            // Check if any documents exist in the database
            allDocuments = jobDocumentService.getJobDocuments(jobNo);
            retryCount++;
        }

        // If no documents exist after retries, try to fetch from SharePoint
        if (allDocuments.isEmpty()) {
            log.info("No documents found in database after {} retries for Job No: {}. Fetching from SharePoint...",
                    maxRetries, jobNo);
            try {
                List<String> downloadedDocumentTypes = jobAttachmentService.fetchAndStoreJobAttachments(jobNo)
                        .collectList()
                        .block();

                if (downloadedDocumentTypes == null || downloadedDocumentTypes.isEmpty()) {
                    log.error("No documents were downloaded from SharePoint for Job No: {}. This is a critical issue.", jobNo);
                    return extractedIdentifiers; // Return empty map
                }

                // Refresh the document list after download
                allDocuments = jobDocumentService.getJobDocuments(jobNo);
                if (allDocuments.isEmpty()) {
                    log.error("Still no documents found after SharePoint download for Job No: {}", jobNo);
                    return extractedIdentifiers; // Return empty map
                }
            } catch (Exception e) {
                log.error("Error fetching job attachments from Business Central/SharePoint for Job No: {}", jobNo, e);
                return extractedIdentifiers; // Return empty map
            }
        }

        // Collect all document images
        List<byte[]> allDocumentImages = new ArrayList<>();
        for (JobDocument document : allDocuments) {
            try {
                byte[] documentData = document.getDocumentData();
                if (documentData == null || documentData.length == 0) {
                    log.warn("Document data is empty for document ID: {}, Job No: '{}'", document.getId(), jobNo);
                    continue;
                }

                String contentType = document.getContentType();
                List<byte[]> images;

                if (contentType != null && contentType.equals("application/pdf")) {
                    // For PDF files, convert to images
                    try {
                        // Create a temporary file
                        Path tempFile = Files.createTempFile("temp_pdf_", ".pdf");
                        Files.write(tempFile, documentData);

                        // Convert PDF to images
                        images = pdfImageConverter.convertPdfToImages(tempFile.toString(), 300f);

                        // Delete the temporary file
                        Files.delete(tempFile);
                    } catch (IOException e) {
                        log.error("Error converting PDF to images for document ID: {}: {}", document.getId(), e.getMessage(), e);
                        // Use the raw PDF data as a fallback
                        images = List.of(documentData);
                    }
                } else {
                    // For non-PDF files, use as is
                    images = List.of(documentData);
                }

                allDocumentImages.addAll(images);
                log.info("Added {} images from document ID: {} for Job No: {}",
                        images.size(), document.getId(), jobNo);
            } catch (Exception e) {
                log.error("Error converting document ID: {} to images for Job No: {}: {}",
                        document.getId(), jobNo, e.getMessage(), e);
                // Continue with other documents
            }
        }

        if (allDocumentImages.isEmpty()) {
            log.error("No document images could be extracted for Job No: {}", jobNo);
            return extractedIdentifiers; // Return empty map
        }

        // Send all documents to LLM for classification and identifier extraction
        try {
            // Create a minimal ERP data map with just the job number for initial classification
            Map<String, Object> initialData = new HashMap<>();
            initialData.put("jobNo", jobNo);

            // Send documents to LLM for classification and identifier extraction
            log.info("Sending documents to LLM for classification and identifier extraction for Job No: {}", jobNo);
            ClassifyAndVerifyResultDTO result = llmProxyService.classifyAndVerifyDocument(jobNo, allDocumentImages, initialData);

            if (result == null) {
                log.error("Classification returned null result for Job No: {}", jobNo);
                return extractedIdentifiers; // Return empty map
            }

            // Extract document numbers from field confidences
            if (result.getFieldConfidences() != null) {
                for (ClassifyAndVerifyResultDTO.FieldConfidenceDTO field : result.getFieldConfidences()) {
                    if (field.getFieldName().contains("Quote No") || field.getFieldName().contains("Quote Number") ||
                            field.getFieldName().contains("Sales Quote Number")) {
                        String salesQuoteNo = field.getExtractedValue();
                        if (!isNotFound(salesQuoteNo)) {
                            extractedIdentifiers.put(SALES_QUOTE_NUMBER_KEY, salesQuoteNo);
                            log.info("Extracted Sales Quote No: {} from document for Job No: {}", salesQuoteNo, jobNo);
                        }
                    } else if (field.getFieldName().contains("Invoice No") || field.getFieldName().contains("Invoice Number") ||
                            field.getFieldName().contains("Tax Invoice Number")) {
                        String proformaInvoiceNo = field.getExtractedValue();
                        if (!isNotFound(proformaInvoiceNo)) {
                            extractedIdentifiers.put(PROFORMA_INVOICE_NUMBER_KEY, proformaInvoiceNo);
                            log.info("Extracted Proforma Invoice No: {} from document for Job No: {}", proformaInvoiceNo, jobNo);
                        }
                    } else if (field.getFieldName().contains("Job Consumption No") || field.getFieldName().contains("Job Shipment No")) {
                        String jobConsumptionNo = field.getExtractedValue();
                        if (!isNotFound(jobConsumptionNo)) {
                            extractedIdentifiers.put(JOB_CONSUMPTION_NUMBER_KEY, jobConsumptionNo);
                            log.info("Extracted Job Consumption No: {} from document for Job No: {}", jobConsumptionNo, jobNo);
                        }
                    }
                }
            }

            // Check if we need to extract from the raw response (for the new format)
            if (extractedIdentifiers.isEmpty() && result.getRawLlmResponse() != null) {
                String rawResponse = result.getRawLlmResponse();
                log.info("Attempting to extract identifiers from raw LLM response for Job No: {}", jobNo);

                // Try to extract document type and identifier from the raw response
                try {
                    // First, look for JSON array pattern in the response
                    Pattern arrayPattern = Pattern.compile("\\[\\s*\\{[\\s\\S]*?\\}\\s*\\]");
                    Matcher arrayMatcher = arrayPattern.matcher(rawResponse);

                    if (arrayMatcher.find()) {
                        String arrayStr = arrayMatcher.group(0);
                        log.debug("Found JSON array in raw response: {}", arrayStr);

                        // Try to parse the array as JSON
                        try {
                            ObjectMapper objectMapper = new ObjectMapper();
                            JsonNode jsonArray = objectMapper.readTree(arrayStr);

                            if (jsonArray.isArray()) {
                                log.info("Successfully parsed JSON array with {} elements", jsonArray.size());

                                // Process each element in the array
                                for (JsonNode item : jsonArray) {
                                    if (item.has("document_type") && item.has("identifier_value")) {
                                        String documentType = item.get("document_type").asText();
                                        String identifierLabel = item.has("identifier_label") ?
                                                item.get("identifier_label").asText() : "";
                                        String identifierValue = item.get("identifier_value").asText();

                                        log.info("Extracted from JSON array - Document Type: {}, Label: {}, Value: {}",
                                                documentType, identifierLabel, identifierValue);

                                        if (documentType.toLowerCase().contains("sales quote")) {
                                            extractedIdentifiers.put(SALES_QUOTE_NUMBER_KEY, identifierValue);
                                            log.info("Extracted Sales Quote No: {} from JSON array for Job No: {}",
                                                    identifierValue, jobNo);
                                        } else if (documentType.toLowerCase().contains("proforma invoice")) {
                                            extractedIdentifiers.put(PROFORMA_INVOICE_NUMBER_KEY, identifierValue);
                                            log.info("Extracted Proforma Invoice No: {} from JSON array for Job No: {}",
                                                    identifierValue, jobNo);
                                        } else if (documentType.toLowerCase().contains("job shipment")) {
                                            extractedIdentifiers.put(JOB_CONSUMPTION_NUMBER_KEY, identifierValue);
                                            log.info("Extracted Job Shipment No: {} from JSON array for Job No: {}",
                                                    identifierValue, jobNo);
                                        }
                                    }
                                }
                            }
                        } catch (Exception e) {
                            log.warn("Failed to parse JSON array: {}", e.getMessage());
                            // Continue to fallback extraction
                        }
                    }

                    // Fallback: Look for individual JSON objects if array parsing failed
                    if (extractedIdentifiers.isEmpty()) {
                        Pattern jsonPattern = Pattern.compile("\\{[\\s\\S]*?\"document_type\"[\\s\\S]*?\"identifier_value\"[\\s\\S]*?\\}");
                        Matcher jsonMatcher = jsonPattern.matcher(rawResponse);

                        while (jsonMatcher.find()) {
                            String jsonStr = jsonMatcher.group(0);
                            log.debug("Found JSON object in raw response: {}", jsonStr);

                            // Extract document type
                            Pattern docTypePattern = Pattern.compile("\"document_type\"\\s*:\\s*\"([^\"]+)\"");
                            Matcher docTypeMatcher = docTypePattern.matcher(jsonStr);

                            // Extract identifier value
                            Pattern identifierPattern = Pattern.compile("\"identifier_value\"\\s*:\\s*\"([^\"]+)\"");
                            Matcher identifierMatcher = identifierPattern.matcher(jsonStr);

                            if (docTypeMatcher.find() && identifierMatcher.find()) {
                                String documentType = docTypeMatcher.group(1);
                                String identifierValue = identifierMatcher.group(1);

                                log.info("Extracted from raw response - Document Type: {}, Identifier Value: {}",
                                        documentType, identifierValue);

                                if (documentType.toLowerCase().contains("sales quote")) {
                                    extractedIdentifiers.put(SALES_QUOTE_NUMBER_KEY, identifierValue);
                                    log.info("Extracted Sales Quote No: {} from raw response for Job No: {}",
                                            identifierValue, jobNo);
                                } else if (documentType.toLowerCase().contains("proforma invoice")) {
                                    extractedIdentifiers.put(PROFORMA_INVOICE_NUMBER_KEY, identifierValue);
                                    log.info("Extracted Proforma Invoice No: {} from raw response for Job No: {}",
                                            identifierValue, jobNo);
                                } else if (documentType.toLowerCase().contains("job shipment")) {
                                    extractedIdentifiers.put(JOB_CONSUMPTION_NUMBER_KEY, identifierValue);
                                    log.info("Extracted Job Shipment No: {} from raw response for Job No: {}",
                                            identifierValue, jobNo);
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    log.error("Error extracting identifiers from raw LLM response for Job No: {}: {}",
                            jobNo, e.getMessage(), e);
                }
            }

            // If we didn't get all the identifiers we need, try processing individual document types
            if (!extractedIdentifiers.containsKey(SALES_QUOTE_NUMBER_KEY) || !extractedIdentifiers.containsKey(PROFORMA_INVOICE_NUMBER_KEY)) {
                log.info("Not all required identifiers extracted from combined processing. Trying individual document types for Job No: {}", jobNo);

                // Process Sales Quote
                if (!extractedIdentifiers.containsKey(SALES_QUOTE_NUMBER_KEY)) {
                    List<byte[]> salesQuoteImages = getDocumentImagesFromDatabase(jobNo, SALES_QUOTE_TYPE);
                    if (!salesQuoteImages.isEmpty()) {
                        Map<String, String> identifiers = llmProxyService.extractDocumentIdentifiers(jobNo, SALES_QUOTE_TYPE, salesQuoteImages);
                        String salesQuoteNo = identifiers.get(SALES_QUOTE_NUMBER_KEY);
                        if (!isNotFound(salesQuoteNo)) {
                            extractedIdentifiers.put(SALES_QUOTE_NUMBER_KEY, salesQuoteNo);
                            log.info("Extracted Sales Quote No: {} from individual document for Job No: {}", salesQuoteNo, jobNo);
                        }
                    }
                }

                // Process Proforma Invoice
                if (!extractedIdentifiers.containsKey(PROFORMA_INVOICE_NUMBER_KEY)) {
                    List<byte[]> proformaInvoiceImages = getDocumentImagesFromDatabase(jobNo, PROFORMA_INVOICE_TYPE);
                    if (!proformaInvoiceImages.isEmpty()) {
                        Map<String, String> identifiers = llmProxyService.extractDocumentIdentifiers(jobNo, PROFORMA_INVOICE_TYPE, proformaInvoiceImages);
                        String proformaInvoiceNo = identifiers.get(PROFORMA_INVOICE_NUMBER_KEY);
                        if (!isNotFound(proformaInvoiceNo)) {
                            extractedIdentifiers.put(PROFORMA_INVOICE_NUMBER_KEY, proformaInvoiceNo);
                            log.info("Extracted Proforma Invoice No: {} from individual document for Job No: {}", proformaInvoiceNo, jobNo);
                        }
                    }
                }
            }

        } catch (Exception e) {
            log.error("Error during document identifier extraction for Job No: {}: {}", jobNo, e.getMessage(), e);
        }

        // Cache the extracted identifiers for future use
        if (!extractedIdentifiers.isEmpty()) {
            extractedIdentifiersCache.put(jobNo, new HashMap<>(extractedIdentifiers));
            log.info("Cached extracted identifiers for Job No: {}: {}", jobNo, extractedIdentifiers);
        }

        return extractedIdentifiers;
    }

    /**
     * Verifies job documents using previously extracted identifiers.
     * This method uses the identifiers extracted in the previous step to fetch Business Central data
     * and complete the verification process.
     *
     * @param jobNo The job number
     * @return List of discrepancies found during verification
     */
    public List<String> verifyJobDocumentsWithExtractedIdentifiers(String jobNo) {
        log.info("Starting document verification with extracted identifiers for Job No: {}", jobNo);
        List<String> finalDiscrepancies = new ArrayList<>();
        boolean bcChecksPerformedOverall = false;

        // First, check if the job exists in Business Central
        try {
            var jobListEntry = businessCentralService.fetchJobListEntry(jobNo).block();
            if (jobListEntry == null) {
                log.error("Job No: {} does not exist in Business Central. Cannot proceed with verification.", jobNo);
                finalDiscrepancies.add("Critical Error: Job No: " + jobNo + " does not exist in Business Central.");
                return finalDiscrepancies;
            } else {
                log.info("Job No: {} exists in Business Central. Proceeding with verification.", jobNo);
            }
        } catch (Exception e) {
            log.error("Error checking if Job No: {} exists in Business Central: {}", jobNo, e.getMessage(), e);
            // Continue with verification even if we can't check if the job exists
        }

        // Get the extracted identifiers from cache or extract them if not available
        Map<String, String> extractedIdentifiers = extractedIdentifiersCache.containsKey(jobNo) ?
                extractedIdentifiersCache.get(jobNo) : extractAllDocumentIdentifiers(jobNo);

        if (extractedIdentifiers.isEmpty()) {
            log.error("No document identifiers could be extracted for Job No: {}", jobNo);
            finalDiscrepancies.add("Critical Error: No document identifiers could be extracted for Job No: " + jobNo);
            return finalDiscrepancies;
        }

        // Get the extracted document numbers
        String extractedSalesQuoteNo = extractedIdentifiers.get(SALES_QUOTE_NUMBER_KEY);
        String extractedProformaInvoiceNo = extractedIdentifiers.get(PROFORMA_INVOICE_NUMBER_KEY);

        log.info("Using extracted document numbers for verification - Sales Quote No: {}, Proforma Invoice No: {}, Job No: {}",
                extractedSalesQuoteNo, extractedProformaInvoiceNo, jobNo);

        // Check if we have the required document numbers
        if (isNotFound(extractedSalesQuoteNo) || isNotFound(extractedProformaInvoiceNo)) {
            log.error("Missing required document numbers for Job No: {}. Sales Quote No: {}, Proforma Invoice No: {}",
                    jobNo, extractedSalesQuoteNo, extractedProformaInvoiceNo);
            if (isNotFound(extractedSalesQuoteNo)) {
                finalDiscrepancies.add("Critical Error: Sales Quote number could not be extracted for Job No: " + jobNo);
            }
            if (isNotFound(extractedProformaInvoiceNo)) {
                finalDiscrepancies.add("Critical Error: Proforma Invoice number could not be extracted for Job No: " + jobNo);
            }
            return finalDiscrepancies;
        }

        // Fetch Business Central data using the extracted document numbers
        try {
            log.info("Fetching Business Central data using extracted document numbers for Job No: {}", jobNo);
            var bcDataTuple = businessCentralService.fetchAllVerificationData(
                    extractedSalesQuoteNo, extractedProformaInvoiceNo, jobNo).block();

            if (bcDataTuple == null) {
                log.error("Failed to fetch verification data from Business Central for Job No: {}", jobNo);
                finalDiscrepancies.add("Critical Error: Failed to fetch required data from Business Central.");
                return finalDiscrepancies;
            }

            // Collect all document images for verification
            List<byte[]> allDocumentImages = new ArrayList<>();
            List<JobDocument> allDocuments = jobDocumentService.getJobDocuments(jobNo);
            for (JobDocument document : allDocuments) {
                try {
                    byte[] documentData = document.getDocumentData();
                    if (documentData == null || documentData.length == 0) {
                        continue;
                    }

                    String contentType = document.getContentType();
                    List<byte[]> images;

                    if (contentType != null && contentType.equals("application/pdf")) {
                        // For PDF files, convert to images
                        try {
                            Path tempFile = Files.createTempFile("temp_pdf_", ".pdf");
                            Files.write(tempFile, documentData);
                            images = pdfImageConverter.convertPdfToImages(tempFile.toString(), 300f);
                            Files.delete(tempFile);
                        } catch (IOException e) {
                            images = List.of(documentData);
                        }
                    } else {
                        images = List.of(documentData);
                    }

                    allDocumentImages.addAll(images);
                } catch (Exception e) {
                    log.error("Error processing document ID: {} for Job No: {}: {}",
                            document.getId(), jobNo, e.getMessage(), e);
                }
            }

            if (allDocumentImages.isEmpty()) {
                log.error("No document images could be extracted for verification for Job No: {}", jobNo);
                finalDiscrepancies.add("Critical Error: No document images could be extracted for verification for Job No: " + jobNo);
                return finalDiscrepancies;
            }

            // Prepare all ERP data for the verification
            Map<String, Object> allErpData = new HashMap<>();

            // Add Sales Quote data if available
            SalesQuoteDTO bcSalesQuote = bcDataTuple.getT1();
            List<SalesQuoteLineDTO> bcSalesQuoteLines = Optional.ofNullable(bcDataTuple.getT2()).orElse(Collections.emptyList());
            if (bcSalesQuote != null) {
                log.info("Found Sales Quote data in Business Central for Quote No: {}, Job No: {}", bcSalesQuote.getNo(), jobNo);
                allErpData.put("salesQuoteHeader", bcSalesQuote);
                allErpData.put("salesQuoteLines", bcSalesQuoteLines);
            } else {
                log.warn("No Sales Quote data found in Business Central for extracted Quote No: {}, Job No: {}",
                        extractedSalesQuoteNo, jobNo);
                finalDiscrepancies.add("Warning: Sales Quote data not found in Business Central for Quote No: " + extractedSalesQuoteNo);
            }

            // Add Proforma Invoice data if available
            SalesInvoiceDTO bcSalesInvoice = bcDataTuple.getT3();
            List<SalesInvoiceLineDTO> bcSalesInvoiceLines = Optional.ofNullable(bcDataTuple.getT4()).orElse(Collections.emptyList());
            if (bcSalesInvoice != null) {
                log.info("Found Proforma Invoice data in Business Central for Invoice No: {}, Job No: {}", bcSalesInvoice.getNo(), jobNo);
                allErpData.put("salesInvoiceHeader", bcSalesInvoice);
                allErpData.put("salesInvoiceLines", bcSalesInvoiceLines);
            } else {
                log.warn("No Proforma Invoice data found in Business Central for extracted Invoice No: {}, Job No: {}",
                        extractedProformaInvoiceNo, jobNo);
                finalDiscrepancies.add("Warning: Proforma Invoice data not found in Business Central for Invoice No: " + extractedProformaInvoiceNo);
            }

            // Add Job Consumption data if available
            List<JobLedgerEntryDTO> bcJobLedgerEntries = Optional.ofNullable(bcDataTuple.getT5()).orElse(Collections.emptyList());
            if (bcJobLedgerEntries != null && !bcJobLedgerEntries.isEmpty()) {
                log.info("Found {} Job Ledger Entries in Business Central for Job No: {}", bcJobLedgerEntries.size(), jobNo);
                allErpData.put("jobLedgerEntries", bcJobLedgerEntries);
            } else {
                log.warn("No Job Ledger Entries found in Business Central for Job No: {}", jobNo);
                finalDiscrepancies.add("Warning: No Job Ledger Entries found in Business Central for Job No: " + jobNo);
            }

            // Now perform the verification with the fetched data
            try {
                log.info("Performing verification with fetched Business Central data for Job No: {}", jobNo);
                ClassifyAndVerifyResultDTO result = llmProxyService.classifyAndVerifyDocument(jobNo, allDocumentImages, allErpData);

                // Process the result
                if (result != null) {
                    log.info("Verification completed for Job No: {} with confidence: {}",
                            jobNo, result.getOverallVerificationConfidence());

                    // Add discrepancies from the result
                    if (result.getDiscrepancies() != null && !result.getDiscrepancies().isEmpty()) {
                        log.info("Found {} discrepancies during verification for Job No: {}", result.getDiscrepancies().size(), jobNo);
                        for (ClassifyAndVerifyResultDTO.DiscrepancyDTO discrepancy : result.getDiscrepancies()) {
                            String discrepancyMessage = String.format("%s: Document value '%s' does not match ERP value '%s' (Severity: %s)",
                                    discrepancy.getFieldName(),
                                    discrepancy.getDocumentValue(),
                                    discrepancy.getErpValue(),
                                    discrepancy.getSeverity());
                            finalDiscrepancies.add(discrepancyMessage);
                        }
                    } else {
                        log.info("No discrepancies found during verification for Job No: {}", jobNo);
                    }

                    bcChecksPerformedOverall = true;
                } else {
                    log.error("Verification returned null result for Job No: {}", jobNo);
                    finalDiscrepancies.add("Error: Verification failed for Job No: " + jobNo);
                }
            } catch (Exception e) {
                log.error("Error during verification for Job No: {}: {}", jobNo, e.getMessage(), e);
                finalDiscrepancies.add("Critical Error during verification: " + e.getMessage());
            }

        } catch (Exception e) {
            log.error("Error fetching/processing Business Central data for Job No: {}: {}", jobNo, e.getMessage(), e);
            finalDiscrepancies.add("Critical Error: Failed during Business Central data interaction: " + e.getMessage());
        }

        // Final Result and BC Update
        log.debug("Finalizing verification for Job No: {}", jobNo);
        if (finalDiscrepancies.isEmpty() && bcChecksPerformedOverall) {
            log.info("SUCCESS: All document verifications passed for Job No: {}. Attempting to update BC.", jobNo);
            try {
                // First try using the Web Service approach
                String verificationComment = "Verified by AI LLM Service - All documents passed cross-document verification";
                log.info("Attempting to update verification fields using Web Service for Job No: {}", jobNo);

                try {
                    // Try the Web Service first
                    businessCentralWebService.updateAllVerificationFields(jobNo, verificationComment).block();
                    log.info("Successfully updated all verification fields using Web Service for Job No: {}", jobNo);
                } catch (Exception webServiceException) {
                    // If Web Service fails, fall back to the OData API
                    log.warn("Web Service update failed for Job No: {}, falling back to OData API: {}",
                            jobNo, webServiceException.getMessage());

                    businessCentralService.updateAllVerificationFields(jobNo, verificationComment).block();
                    log.info("Successfully updated all verification fields using OData API for Job No: {}", jobNo);
                }
            } catch (Exception updateException) {
                log.error("Failed to update verification fields in BC for Job No: {} after successful verification: {}",
                        jobNo, updateException.getMessage(), updateException);

                // Check for specific Business Central error messages
                String errorMessage = updateException.getMessage();
                if (errorMessage != null && errorMessage.toLowerCase().contains("1st check date must have a value")) {
                    finalDiscrepancies.add("Failed to update BC because job doesn't have first check date");
                } else if (errorMessage != null && errorMessage.toLowerCase().contains("unauthorized")) {
                    finalDiscrepancies.add("Failed to update BC due to authentication error");
                } else {
                    finalDiscrepancies.add("Warning: Verification passed, but failed to update Business Central status: " +
                            errorMessage);
                }
            }
        } else if (finalDiscrepancies.isEmpty() && !bcChecksPerformedOverall) {
            log.warn("Verification for Job No: {} resulted in no discrepancies, but not all Business Central checks were performed or completed. BC status not updated.", jobNo);
            finalDiscrepancies.add("Info: Document checks found no issues, but full Business Central validation was incomplete.");
        } else {
            log.warn("FAILURE: Document verification found discrepancies for Job No: {}. Count: {}", jobNo, finalDiscrepancies.size());

            // Update Business Central with failure details
            try {
                String verificationComment = "Verification FAILED - " + String.join("; ", finalDiscrepancies);
                log.info("Attempting to update BC with failure details for Job No: {}", jobNo);

                try {
                    // Try the Web Service first
                    businessCentralWebService.updateAllVerificationFields(jobNo, verificationComment, false).block();
                    log.info("Successfully updated BC with failure details using Web Service for Job No: {}", jobNo);
                } catch (Exception webServiceException) {
                    // If Web Service fails, fall back to the OData API
                    log.warn("Web Service update failed for Job No: {}, falling back to OData API: {}",
                            jobNo, webServiceException.getMessage());

                    businessCentralService.updateAllVerificationFields(jobNo, verificationComment, false).block();
                    log.info("Successfully updated BC with failure details using OData API for Job No: {}", jobNo);
                }
            } catch (Exception updateException) {
                log.error("Failed to update BC with failure details for Job No: {}: {}",
                        jobNo, updateException.getMessage(), updateException);

                // Check for specific Business Central error messages
                String errorMessage = updateException.getMessage();
                if (errorMessage != null && errorMessage.toLowerCase().contains("1st check date must have a value")) {
                    finalDiscrepancies.add("Failed to update BC because job doesn't have first check date");
                } else if (errorMessage != null && errorMessage.toLowerCase().contains("unauthorized")) {
                    finalDiscrepancies.add("Failed to update BC due to authentication error");
                } else {
                    finalDiscrepancies.add("Warning: Failed to update Business Central with verification results: " +
                            errorMessage);
                }
            }
        }

        if (!finalDiscrepancies.isEmpty()) {
            log.warn("Final discrepancies for Job No: {}", jobNo);
            finalDiscrepancies.forEach(d -> log.warn("- {}", d));
        } else if (bcChecksPerformedOverall) {
            log.info("Verification completed successfully with no discrepancies for Job No: {}", jobNo);
        }

        return finalDiscrepancies;
    }
}
