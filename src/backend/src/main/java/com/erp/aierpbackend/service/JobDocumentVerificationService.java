package com.erp.aierpbackend.service;

import com.erp.aierpbackend.dto.dynamics.*;
import com.erp.aierpbackend.dto.gemini.GeminiDiscrepancy;
import com.erp.aierpbackend.dto.gemini.GeminiVerificationResult;
import com.erp.aierpbackend.entity.JobDocument;
import com.erp.aierpbackend.util.PdfImageConverter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class JobDocumentVerificationService {

    private final BusinessCentralService businessCentralService;
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

    public List<String> verifyJobDocuments(JobLedgerEntryDTO legacyLedgerEntry, String jobNo) {
        log.info("Starting document verification for Job No: {}", jobNo);
        List<String> finalDiscrepancies = new ArrayList<>();
        boolean bcChecksPerformedOverall = false;

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

                // Add a delay to ensure updates are committed
                try {
                    log.debug("Adding a delay to ensure document type updates are committed");
                    Thread.sleep(2000); // 2 second delay
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log.warn("Sleep interrupted while waiting for document updates to commit", ie);
                }
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
                    log.warn("No documents were downloaded from SharePoint for Job No: {}", jobNo);
                }
            } catch (Exception e) {
                log.error("Error fetching job attachments from Business Central/SharePoint for Job No: {}", jobNo, e);
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
                    finalDiscrepancies.add("Sales Quote: Document number could not be extracted by LLM for Job No: " + jobNo);
                    log.warn("Sales Quote: Document number could not be extracted by LLM for Job No: {}", jobNo);
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
                    finalDiscrepancies.add("Proforma Invoice: Document number could not be extracted by LLM for Job No: " + jobNo);
                    log.warn("Proforma Invoice: Document number could not be extracted by LLM for Job No: {}", jobNo);
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
            finalDiscrepancies.add("Missing document numbers: Cannot perform Business Central verification without both Sales Quote and Proforma Invoice numbers.");
        }

        // Final Result and BC Update
        log.debug("Finalizing verification for Job No: {}", jobNo);
        if (finalDiscrepancies.isEmpty() && bcChecksPerformedOverall) {
            log.info("SUCCESS: All document verifications passed for Job No: {}. Attempting to update BC.", jobNo);
            try {
                String formattedDate = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
                businessCentralService.updateJobCardField(jobNo, "_x0032_nd_Check_Date", formattedDate).block();
                log.info("Successfully updated '_x0032_nd_Check_Date' in BC for Job No: {}", jobNo);
            } catch (Exception updateException) {
                log.error("Failed to update '_x0032_nd_Check_Date' in BC for Job No: {} after successful verification. {}", jobNo, updateException.getMessage(), updateException);
                finalDiscrepancies.add("Warning: Verification passed, but failed to update Business Central status. " + updateException.getMessage());
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

        // First try to find by document type
        Optional<JobDocument> documentOpt = jobDocumentService.getJobDocument(normalizedJobNo, normalizedDocumentType);

        // If not found, try to find by classified document type
        if (documentOpt.isEmpty()) {
            log.info("Document not found by document type, trying to find by classified document type for Job No: '{}', Document Type: '{}'",
                    normalizedJobNo, normalizedDocumentType);

            // Use the new method to find by classified document type
            documentOpt = jobDocumentService.getJobDocumentByClassifiedType(normalizedJobNo, normalizedDocumentType);

            if (documentOpt.isPresent()) {
                log.info("Found document by classified document type for Job No: '{}', Document Type: '{}'",
                        normalizedJobNo, normalizedDocumentType);
            }
        }

        // If still not found, try to find UNCLASSIFIED documents and check if any have been classified
        if (documentOpt.isEmpty()) {
            log.info("Document not found by document type or classified document type, checking UNCLASSIFIED documents for Job No: '{}'",
                    normalizedJobNo);

            List<JobDocument> unclassifiedDocs = jobDocumentService.getJobDocumentsByType(normalizedJobNo, "UNCLASSIFIED");
            if (!unclassifiedDocs.isEmpty()) {
                log.info("Found {} UNCLASSIFIED documents for Job No: '{}', checking if any need classification",
                        unclassifiedDocs.size(), normalizedJobNo);

                // If we have unclassified documents, we should try to classify them
                // This is a fallback mechanism - ideally documents should be classified during download
                // For now, we'll just log this and continue with the search
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
}
