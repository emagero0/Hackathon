package com.erp.aierpbackend.service;

import com.erp.aierpbackend.dto.dynamics.*;
import com.erp.aierpbackend.dto.gemini.GeminiDiscrepancy;
import com.erp.aierpbackend.dto.gemini.GeminiVerificationResult;
import com.erp.aierpbackend.util.PdfImageConverter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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

        // Step 1: Process Sales Quote with LLM
        String extractedSalesQuoteNo = null;
        try {
            log.info("Processing Sales Quote for Job No: {}", jobNo);
            Path salesQuotePath = findDocumentPath(jobNo, SALES_QUOTE_BASE_NAME);
            List<byte[]> salesQuoteImages = getDocumentImages(salesQuotePath);

            if (!salesQuoteImages.isEmpty()) {
                Map<String, String> identifiers = llmProxyService.extractDocumentIdentifiers(jobNo, "SalesQuote", salesQuoteImages);
                extractedSalesQuoteNo = identifiers.get(SALES_QUOTE_NUMBER_KEY);

                if (!isNotFound(extractedSalesQuoteNo)) {
                    log.info("LLM extracted Sales Quote No: {} for Job No: {}", extractedSalesQuoteNo, jobNo);
                } else {
                    finalDiscrepancies.add("Sales Quote: Document number could not be extracted by LLM for Job No: " + jobNo);
                    log.warn("Sales Quote: Document number could not be extracted by LLM for Job No: {}", jobNo);
                }
            } else {
                finalDiscrepancies.add("Sales Quote document (PDF/Image) not found or is empty for Job No: " + jobNo);
            }
        } catch (IOException e) {
            log.error("Error processing Sales Quote document for job {}: {}", jobNo, e.getMessage(), e);
            finalDiscrepancies.add("Critical Error during Sales Quote document handling: " + e.getMessage());
        }

        // Step 2: Process Proforma Invoice with LLM
        String extractedProformaInvoiceNo = null;
        try {
            log.info("Processing Proforma Invoice for Job No: {}", jobNo);
            Path proformaInvoicePath = findDocumentPath(jobNo, PROFORMA_INVOICE_BASE_NAME);
            List<byte[]> proformaInvoiceImages = getDocumentImages(proformaInvoicePath);

            if (!proformaInvoiceImages.isEmpty()) {
                Map<String, String> identifiers = llmProxyService.extractDocumentIdentifiers(jobNo, "ProformaInvoice", proformaInvoiceImages);
                extractedProformaInvoiceNo = identifiers.get(PROFORMA_INVOICE_NUMBER_KEY);

                if (!isNotFound(extractedProformaInvoiceNo)) {
                    log.info("LLM extracted Proforma Invoice No: {} for Job No: {}", extractedProformaInvoiceNo, jobNo);
                } else {
                    finalDiscrepancies.add("Proforma Invoice: Document number could not be extracted by LLM for Job No: " + jobNo);
                    log.warn("Proforma Invoice: Document number could not be extracted by LLM for Job No: {}", jobNo);
                }
            } else {
                finalDiscrepancies.add("Proforma Invoice document (PDF/Image) not found or is empty for Job No: " + jobNo);
            }
        } catch (IOException e) {
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
                            Path salesQuotePath = findDocumentPath(jobNo, SALES_QUOTE_BASE_NAME);
                            List<byte[]> salesQuoteImages = getDocumentImages(salesQuotePath);

                            if (!salesQuoteImages.isEmpty()) {
                                Map<String, Object> erpDataForQuote = Map.of(
                                        "salesQuoteHeader", bcSalesQuote,
                                        "salesQuoteLines", bcSalesQuoteLines
                                );
                                GeminiVerificationResult quoteResult = llmProxyService.verifyDocument(jobNo, "SalesQuote", salesQuoteImages, erpDataForQuote);
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
                            Path proformaInvoicePath = findDocumentPath(jobNo, PROFORMA_INVOICE_BASE_NAME);
                            List<byte[]> proformaInvoiceImages = getDocumentImages(proformaInvoicePath);

                            if (!proformaInvoiceImages.isEmpty()) {
                                Map<String, Object> erpDataForInvoice = Map.of(
                                        "salesInvoiceHeader", bcSalesInvoice,
                                        "salesInvoiceLines", bcSalesInvoiceLines
                                );
                                GeminiVerificationResult invoiceResult = llmProxyService.verifyDocument(jobNo, "ProformaInvoice", proformaInvoiceImages, erpDataForInvoice);
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
                            Path jobConsumptionPath = findDocumentPath(jobNo, JOB_CONSUMPTION_BASE_NAME);
                            List<byte[]> jobConsumptionImages = getDocumentImages(jobConsumptionPath);

                            if (!jobConsumptionImages.isEmpty()) {
                                Map<String, Object> erpDataForJobConsumption = Map.of(
                                        "jobLedgerEntries", bcJobLedgerEntries
                                );
                                GeminiVerificationResult jobConsumptionResult = llmProxyService.verifyDocument(jobNo, "JobConsumption", jobConsumptionImages, erpDataForJobConsumption);
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
