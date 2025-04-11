package com.erp.aierpbackend.service;

import com.erp.aierpbackend.config.RabbitMQConfig;
import com.erp.aierpbackend.dto.gemini.GeminiResponse;
import com.erp.aierpbackend.dto.ocr.ExtractedInvoiceDTO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class OcrService {

    private final Tesseract tesseract; // Injected via OcrConfig
    private final GeminiClientService geminiClientService;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule()); // For parsing Gemini JSON

    // Define common date patterns likely found in invoices
    private static final List<DateTimeFormatter> DATE_FORMATTERS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE, // YYYY-MM-DD
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),
            DateTimeFormatter.ofPattern("dd-MMM-yyyy"),
            DateTimeFormatter.ofPattern("dd MMMM yyyy"),
            DateTimeFormatter.ofPattern("MMMM dd, yyyy")
            // Add more patterns as needed
    );

    // Pattern to extract numbers (potentially with commas and decimals)
    private static final Pattern AMOUNT_PATTERN = Pattern.compile("[\\d,]+(?:\\.\\d+)?");


    public Mono<Void> processInvoicePdf(byte[] pdfData) {
        log.info("Starting OCR process for uploaded PDF ({} bytes)", pdfData.length);
        return Mono.fromCallable(() -> extractTextFromPdf(pdfData))
                .flatMap(rawText -> {
                    log.info("OCR completed. Raw text length: {}", rawText.length());
                    log.debug("Raw OCR Text Snippet:\n---\n{}\n---", rawText.substring(0, Math.min(rawText.length(), 500)));
                    return callGeminiForExtraction(rawText);
                })
                .map(this::parseAndCleanGeminiResponse) // Parse, clean, and normalize
                .flatMap(extractedDTO -> {
                    log.info("Successfully extracted and cleaned invoice data for Invoice No: {}", extractedDTO.getInvoiceNo());
                    publishToRabbitMQ(extractedDTO);
                    return Mono.empty(); // Signal completion
                })
                .doOnError(e -> log.error("Error during OCR processing pipeline", e))
                .then(); // Convert Mono<ExtractedInvoiceDTO> to Mono<Void> for completion signal
    }

    private String extractTextFromPdf(byte[] pdfData) throws IOException, TesseractException {
        StringBuilder fullText = new StringBuilder();
        // Pass the byte array directly to Loader.loadPDF
        try (PDDocument document = Loader.loadPDF(pdfData)) {
            PDFRenderer pdfRenderer = new PDFRenderer(document);
            int pageCount = document.getNumberOfPages();
            log.debug("PDF has {} pages.", pageCount);

            for (int page = 0; page < pageCount; ++page) {
                // Render page to an image (adjust DPI as needed for quality vs performance)
                BufferedImage bufferedImage = pdfRenderer.renderImageWithDPI(page, 300, ImageType.GRAY);
                log.debug("Performing OCR on page {}", page + 1);
                String pageText = tesseract.doOCR(bufferedImage);
                fullText.append(pageText).append("\n\n--- Page Break ---\n\n"); // Add separator
            }
            log.info("Finished text extraction from all PDF pages.");
        } catch (IOException e) {
            log.error("Failed to load or render PDF document.", e);
            throw e; // Re-throw to be caught by the pipeline
        } catch (TesseractException e) {
            log.error("Tesseract OCR failed.", e);
            throw e; // Re-throw
        }
        return fullText.toString();
    }

    private Mono<String> callGeminiForExtraction(String rawText) {
        String prompt = buildGeminiPrompt(rawText);
        log.debug("Calling Gemini for structured data extraction.");
        return geminiClientService.callGeminiApi(prompt)
                .map(this::extractContentFromGeminiResponse)
                .switchIfEmpty(Mono.error(new RuntimeException("Received empty response from Gemini.")));
    }

    private String buildGeminiPrompt(String rawText) {
        // More sophisticated prompting might be needed depending on Gemini's capabilities and invoice variations
        return "Extract the following fields from the invoice text below. Provide the output as a JSON object with keys: " +
               "'invoiceNo' (string), 'invoiceDate' (string, attempt YYYY-MM-DD format), 'totalAmount' (string, numeric value only), " +
               "'customerName' (string), 'customerNo' (string), 'lineItems' (array of objects, each with 'description' (string), " +
               "'quantity' (string), 'unitPrice' (string), 'lineTotal' (string)), and 'signatureDetected' (boolean, true if a signature is mentioned or visually implied, false otherwise). " +
               "If a field is not found, use null or an empty string/array as appropriate.\n\n" +
               "Invoice Text:\n```\n" +
               rawText + "\n```\n\n" +
               "JSON Output:";
    }

     private String extractContentFromGeminiResponse(GeminiResponse response) {
        if (response == null || response.getCandidates() == null || response.getCandidates().isEmpty() ||
            response.getCandidates().get(0).getContent() == null || response.getCandidates().get(0).getContent().getParts() == null ||
            response.getCandidates().get(0).getContent().getParts().isEmpty()) {
            log.error("Invalid or empty Gemini response structure: {}", response);
            throw new RuntimeException("Invalid or empty content received from Gemini.");
        }
        // Assuming the first part of the first candidate contains the JSON string
        String rawContent = response.getCandidates().get(0).getContent().getParts().get(0).getText();
        log.debug("Raw content from Gemini: {}", rawContent);

        // Sometimes Gemini wraps the JSON in ```json ... ```, try to extract it
        Matcher matcher = Pattern.compile("```json\\s*([\\s\\S]*?)\\s*```").matcher(rawContent);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        // Otherwise, assume the whole text is the JSON (or attempt to find JSON boundaries)
        int firstBrace = rawContent.indexOf('{');
        int lastBrace = rawContent.lastIndexOf('}');
        if (firstBrace != -1 && lastBrace != -1 && lastBrace > firstBrace) {
            return rawContent.substring(firstBrace, lastBrace + 1).trim();
        }

        log.warn("Could not extract JSON block from Gemini response. Returning raw content.");
        return rawContent; // Fallback to raw content if JSON extraction fails
    }


    private ExtractedInvoiceDTO parseAndCleanGeminiResponse(String geminiJson) {
        try {
            log.debug("Attempting to parse Gemini JSON response.");
            // Use a temporary DTO or Map to handle potentially messy string inputs from Gemini
            TempInvoiceData tempDto = objectMapper.readValue(geminiJson, TempInvoiceData.class);
            log.info("Successfully parsed Gemini response.");

            ExtractedInvoiceDTO.ExtractedInvoiceDTOBuilder builder = ExtractedInvoiceDTO.builder();

            // Clean and normalize fields
            builder.invoiceNo(normalizeString(tempDto.invoiceNo));
            builder.invoiceDate(parseDate(tempDto.invoiceDate));
            builder.totalAmount(parseAmount(tempDto.totalAmount));
            builder.customerName(normalizeString(tempDto.customerName));
            builder.customerNo(normalizeString(tempDto.customerNo));
            builder.signatureDetected(tempDto.signatureDetected != null && tempDto.signatureDetected); // Handle null boolean

            List<ExtractedInvoiceDTO.LineItem> cleanedLineItems = new ArrayList<>();
            if (tempDto.lineItems != null) {
                for (TempLineItem tempItem : tempDto.lineItems) {
                    cleanedLineItems.add(ExtractedInvoiceDTO.LineItem.builder()
                            .description(normalizeString(tempItem.description))
                            .quantity(parseInteger(tempItem.quantity))
                            .unitPrice(parseAmount(tempItem.unitPrice))
                            .lineTotal(parseAmount(tempItem.lineTotal))
                            .build());
                }
            }
            builder.lineItems(cleanedLineItems);
            // Raw text is not part of Gemini response, would need to pass it through if required

            return builder.build();

        } catch (JsonProcessingException e) {
            log.error("Failed to parse JSON response from Gemini: {}", geminiJson, e);
            throw new RuntimeException("Failed to parse structured data from Gemini response.", e);
        } catch (Exception e) {
            log.error("Error during cleaning/normalization of extracted data: {}", geminiJson, e);
            throw new RuntimeException("Error processing extracted data.", e);
        }
    }

     // --- Helper methods for cleaning/normalization ---

    private String normalizeString(String input) {
        return input == null ? null : input.trim();
    }

    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) {
            return null;
        }
        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                return LocalDate.parse(dateStr.trim(), formatter);
            } catch (DateTimeParseException e) {
                // Ignore and try next format
            }
        }
        log.warn("Could not parse date string: '{}'. Returning null.", dateStr);
        return null; // Or throw exception if date is mandatory
    }

    private BigDecimal parseAmount(String amountStr) {
        if (amountStr == null || amountStr.isBlank()) {
            return null;
        }
        Matcher matcher = AMOUNT_PATTERN.matcher(amountStr.trim().replace(",", "")); // Remove commas
        if (matcher.find()) {
            try {
                return new BigDecimal(matcher.group(0));
            } catch (NumberFormatException e) {
                log.warn("Could not parse amount string after regex match: '{}'. Raw: '{}'", matcher.group(0), amountStr);
            }
        } else {
             log.warn("Could not find numeric value in amount string: '{}'", amountStr);
        }
        return null; // Or throw exception
    }

     private Integer parseInteger(String intStr) {
        if (intStr == null || intStr.isBlank()) {
            return null;
        }
        try {
            // Remove any non-digit characters just in case (e.g., ".0")
             String cleanedIntStr = intStr.replaceAll("[^\\d]", "");
             if (cleanedIntStr.isEmpty()) return null;
            return Integer.parseInt(cleanedIntStr);
        } catch (NumberFormatException e) {
            log.warn("Could not parse integer string: '{}'", intStr);
            return null; // Or throw exception
        }
    }


    private void publishToRabbitMQ(ExtractedInvoiceDTO dto) {
        try {
            log.info("Publishing extracted invoice data to queue '{}' with routing key '{}'",
                    RabbitMQConfig.OCR_INVOICE_DATA_QUEUE_NAME, RabbitMQConfig.OCR_INVOICE_DATA_ROUTING_KEY);
            rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_NAME, RabbitMQConfig.OCR_INVOICE_DATA_ROUTING_KEY, dto);
        } catch (Exception e) {
            // Log error but potentially don't fail the whole process? Depends on requirements.
            log.error("Failed to publish extracted invoice data to RabbitMQ for Invoice No: {}", dto.getInvoiceNo(), e);
            // Consider adding to a retry queue or dead-letter queue if publishing is critical
        }
    }

    // --- Temporary DTOs for flexible JSON parsing from Gemini ---
    // Allows Gemini to return fields as strings, we handle parsing/conversion
    private static class TempInvoiceData {
        public String invoiceNo;
        public String invoiceDate;
        public String totalAmount;
        public String customerName;
        public String customerNo;
        public List<TempLineItem> lineItems;
        public Boolean signatureDetected; // Use Boolean to handle potential null
    }

    private static class TempLineItem {
        public String description;
        public String quantity;
        public String unitPrice;
        public String lineTotal;
    }
}
