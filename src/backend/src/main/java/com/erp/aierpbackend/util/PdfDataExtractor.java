package com.erp.aierpbackend.util;

import com.erp.aierpbackend.service.OcrService;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import net.sourceforge.tess4j.TesseractException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.*;

@Component
@Slf4j
public class PdfDataExtractor {

    private final OcrService ocrService;

    @Autowired
    public PdfDataExtractor(OcrService ocrService) {
        this.ocrService = ocrService;
    }

    @Data
    public static class ExtractedPdfData {
        private String documentType;
        private String documentNo;
        private String accountNo;
        private String customerName;
        private List<ExtractedItem> items = new ArrayList<>();
    }

    @Data
    @AllArgsConstructor
    public static class ExtractedItem {
        private String description;
        private String quantity;
    }

    // Sales Quote Extraction (Unchanged)
    public ExtractedPdfData extractSalesQuoteFields(String filePath) throws IOException {
        log.info("Extracting data from Sales Quote PDF: {}", filePath);
        String text = extractTextFromPDF(filePath);
        ExtractedPdfData data = new ExtractedPdfData();
        data.setDocumentType("SalesQuote");

        data.setDocumentNo(match(text, "SALES QUOTE\\s*(SQ\\d+)"));
        data.setAccountNo(match(text, "Account No\\s*:?\\s*(\\d+)"));
        data.setCustomerName(match(text, "Attn:\\s*([A-Z\\s]+)\\n", Pattern.MULTILINE));

        List<String[]> rawItems = matchItems(text, "DESCRIPTION\\s+QTY[\\s\\S]+?(?=Subtotal)", false);
        for (String[] rawItem : rawItems) {
            data.getItems().add(new ExtractedItem(rawItem[0], rawItem[1]));
        }

        log.debug("Extracted Sales Quote Data: {}", data);
        return data;
    }

    // Job Consumption Extraction (OCR) - Calls matchItems
    public ExtractedPdfData extractJobConsumptionFields(String filePath) throws IOException, TesseractException {
        log.info("Extracting data from Job Consumption PDF using OCR: {}", filePath);
        String text = ocrService.performOcr(filePath);
        ExtractedPdfData data = new ExtractedPdfData();
        data.setDocumentType("JobConsumption");

        data.setDocumentNo(match(text, "Job No\\s*(J\\d+)"));
        data.setAccountNo("N/A");
        data.setCustomerName("N/A");

        List<String[]> rawItems = matchItems(text, null, true); // Pass null pattern, jobFormat=true
        for (String[] rawItem : rawItems) {
            data.getItems().add(new ExtractedItem(rawItem[0], rawItem[1]));
        }

        log.debug("Extracted Job Consumption Data (via OCR): {}", data);
        return data;
    }

    // Proforma Invoice Extraction (Unchanged)
    public ExtractedPdfData extractProformaInvoiceFields(String filePath) throws IOException {
        log.info("Extracting data from Proforma Invoice PDF: {}", filePath);
        String text = extractTextFromPDF(filePath);
        ExtractedPdfData data = new ExtractedPdfData();
        data.setDocumentType("ProformaInvoice");

        data.setDocumentNo(match(text, "Tax Invoice No\\s*(\\d+)"));
        data.setAccountNo(match(text, "Account No\\s*:?\\s*(\\d+)"));
        data.setCustomerName(match(text, "^([A-Z\\s&'.-]+?)\\s+Tax Invoice No", Pattern.MULTILINE));

        List<String[]> rawItems = matchItems(text, "DESCRIPTION\\s+ITEM CODE\\s+QTY[\\s\\S]+?(?=VAT Analysis)", false);
        for (String[] rawItem : rawItems) {
            data.getItems().add(new ExtractedItem(rawItem[0], rawItem[1]));
        }

        log.debug("Extracted Proforma Invoice Data: {}", data);
        return data;
    }

    // --- Helper Methods (extractTextFromPDF, match - Unchanged) ---
    private static String extractTextFromPDF(String filePath) throws IOException {
        // ... (Keep existing implementation)
        File pdfFile = new File(filePath);
        if (!pdfFile.exists()) {
            log.error("PDF file not found: {}", filePath);
            throw new IOException("PDF file not found: " + filePath);
        }
        try (PDDocument document = Loader.loadPDF(pdfFile)) {
            if (document.isEncrypted()) {
                log.warn("PDF document is encrypted and cannot be processed: {}", filePath);
                return "";
            }
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);
            log.trace("Extracted text from {}: {}", filePath, text.substring(0, Math.min(text.length(), 500)) + "...");
            return text;
        } catch (IOException e) {
            log.error("Error loading or parsing PDF file: {}", filePath, e);
            throw e;
        }
    }

    private static String match(String text, String pattern) {
        return match(text, pattern, 0);
    }

    private static String match(String text, String pattern, int flags) {
        // ... (Keep existing implementation)
         Pattern p = Pattern.compile(pattern, flags);
         Matcher m = p.matcher(text);
         if (m.find() && m.groupCount() >= 1) {
             String result = m.group(1).trim();
             log.trace("Regex match found for pattern '{}': {}", pattern, result);
             return result;
         }
         log.warn("Regex match not found for pattern '{}'", pattern);
         return "Not found";
    }

    // --- matchItems Method (with updated regex) ---
    private List<String[]> matchItems(String text, String blockPattern, boolean jobFormat) {
        log.debug("Entering matchItems: jobFormat={}, blockPattern='{}'", jobFormat, blockPattern);
        List<String[]> items = new ArrayList<>();
        String itemBlockText;

        if (jobFormat) {
            itemBlockText = text;
            log.debug("Processing full OCR text for Job Consumption items.");
        } else {
            // ... (block finding logic remains the same)
            if (blockPattern == null || blockPattern.isEmpty()) {
                 log.error("Block pattern is null or empty for non-jobFormat extraction.");
                 return items; // Cannot proceed without a pattern
            }
            int blockFlags = Pattern.DOTALL;
            Pattern block = Pattern.compile(blockPattern, blockFlags);
            Matcher m = block.matcher(text);
            if (m.find()) {
                itemBlockText = m.group();
            } else {
                log.warn("Item block NOT FOUND using pattern: {}", blockPattern);
                return items; // This was the original return for the else block
            }
        } // This is the closing brace for the `if (jobFormat) { ... } else { ... }` block

        String[] lines = itemBlockText.split("\\r?\\n");
        StringBuilder currentDescription = new StringBuilder();
        String currentQty = null;
        boolean itemsSectionStarted = !jobFormat;

        // --- REGEX CHANGE HERE (v3) ---
        // Capture description greedily up to the last space before the final number group.
        // Group 1: Description (greedy, captures everything before the last number)
        // Group 2: Quantity (digits, optionally with a single decimal point)
        Pattern jobItemLinePattern = Pattern.compile("^(.+)\\s+([\\d]+(?:\\.[\\d]+)?)$"); // Added MULTILINE? No, split handles lines.
        // --------------------------

        // Pattern for other documents (Quote, Invoice) - Assuming this one is okay
        Pattern quoteInvoiceItemLinePattern = Pattern.compile("^(.+?)\\s+(\\d+)\\s+(?:\\d+\\s*%|\\d+)?\\s+[\\d,]+(?:\\.\\d+)?\\s+[\\d,]+(?:\\.\\d+)?$");


        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || line.contains("--- Page Break ---")) continue;

            if (jobFormat && !itemsSectionStarted) {
                String upperLine = line.toUpperCase();
                 if (upperLine.contains("DESCRIPTION") && upperLine.contains("QTY")) {
                    itemsSectionStarted = true;
                    log.debug("Item section header found (simplified check), starting item processing.");
                    continue;
                } else {
                    log.trace("Skipping line before item header: '{}'", line); // Trace level might be better
                    continue;
                }
            }

            if (!itemsSectionStarted) continue;

            Matcher lineMatcher = jobFormat ? jobItemLinePattern.matcher(line) : quoteInvoiceItemLinePattern.matcher(line);

            if (lineMatcher.find()) {
                 if (currentDescription.length() > 0 && currentQty != null) {
                    items.add(new String[]{currentDescription.toString().trim(), currentQty});
                    log.trace("Added item: Desc='{}', Qty='{}'", currentDescription.toString().trim(), currentQty);
                    currentDescription.setLength(0);
                }

                // Capture new item using the UPDATED regex groups
                String descriptionPart = lineMatcher.group(1).trim(); // Group 1 is Description
                currentQty = lineMatcher.group(2).trim(); // Group 2 is Quantity

                // Remove trailing ".00" or ".0" if quantity is whole number from OCR
                if (currentQty.endsWith(".00")) {
                    currentQty = currentQty.substring(0, currentQty.length() - 3);
                } else if (currentQty.endsWith(".0")) {
                     currentQty = currentQty.substring(0, currentQty.length() - 2);
                }

                currentDescription.append(descriptionPart);
                log.debug("Regex matched line: '{}'. Starting new item: Desc='{}', Qty='{}'", line, descriptionPart, currentQty);

                 if (currentQty.equals("0")) {
                    log.warn("⚠️ Suspicious item: '{}' has quantity 0. Might be a regex/OCR issue.", currentDescription.toString().trim());
                }

            } else if (currentDescription.length() > 0) { // Append to existing description only
                // ... (footer checking logic remains the same)
                 boolean isFooterLine = line.matches("(?i).*Subtotal.*") ||
                                       line.matches("(?i).*VAT Analysis.*") ||
                                       line.matches("(?i).*Total.*") ||
                                       line.matches("(?i)INSTRUCTED\\s+BY.*") ||
                                       line.matches("(?i)RECEIVED\\s+BY.*") ||
                                       line.matches("(?i)SIGNATURE.*") ||
                                       line.matches("(?i)DATE.*") ||
                                       line.matches("(?i).*Page \\d+ of \\d+.*") ||
                                       line.matches("(?i).*WAYBILL.*") ||
                                       line.matches("(?i).*COURIER.*") ||
                                       line.matches("(?i).*Water Pumps Boreholes.*"); // Added generic footer text

                if (isFooterLine) {
                    if (currentDescription.length() > 0 && currentQty != null) {
                        items.add(new String[]{currentDescription.toString().trim(), currentQty});
                        log.trace("Added item (footer detected): Desc='{}', Qty='{}'", currentDescription.toString().trim(), currentQty);
                    }
                    currentDescription.setLength(0);
                    currentQty = null;
                    itemsSectionStarted = false;
                    log.debug("Footer line detected ('{}'), stopping item processing.", line);
                    break;
                } else if (!line.matches("^\\s*\\d*\\.?\\d+\\s*$")) {
                    // Only append if it's not just a number (prevents appending stray amounts to description)
                    // AND also ensure it doesn't look like the start of the next item description which might lack quantity
                    // (This second check is hard without knowing the next item's format, so we rely on the main regex first)
                    currentDescription.append(" ").append(line.trim());
                    log.trace("Appended to description: '{}'", line.trim());
                } else {
                    log.debug("Ignoring numeric line while capturing description: '{}'", line);
                }
            } else {
                 log.trace("Line ignored (after header, no match, no current item): '{}'", line); // Trace level
            }
        }

        // Add the last item processed
        if (currentDescription.length() > 0 && currentQty != null) {
            items.add(new String[]{currentDescription.toString().trim(), currentQty});
            log.trace("Added last processed item: Desc='{}', Qty='{}'", currentDescription.toString().trim(), currentQty);
        } else if (itemsSectionStarted && !items.isEmpty()) { // Check if we started and added items
             log.debug("Loop finished. Last item added previously or none active at end.");
        } else if(itemsSectionStarted && items.isEmpty()){
             log.warn("Loop finished, items section started, but no items were extracted. Check OCR quality and regex.");
        }

        return items;
    } // End of matchItems method

    public List<byte[]> convertPdfToImages(String filePath, float dpi) throws IOException {
        log.info("Converting PDF to images: {} at {} DPI", filePath, dpi);
        List<byte[]> imageList = new ArrayList<>();
        File pdfFile = new File(filePath);

        if (!pdfFile.exists()) {
            log.error("PDF file not found for image conversion: {}", filePath);
            throw new IOException("PDF file not found: " + filePath);
        }

        try (PDDocument document = Loader.loadPDF(pdfFile)) {
            if (document.isEncrypted()) {
                log.warn("PDF document is encrypted and cannot be converted to images: {}", filePath);
                return imageList; // Return empty list
            }
            PDFRenderer pdfRenderer = new PDFRenderer(document);
            for (int page = 0; page < document.getNumberOfPages(); ++page) {
                // Render image with specified DPI, higher DPI means better quality for OCR/LLM
                // but larger image size. 300 DPI is a common standard.
                BufferedImage bim = pdfRenderer.renderImageWithDPI(page, dpi, ImageType.RGB);

                // Convert BufferedImage to byte array (PNG format)
                try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                    ImageIO.write(bim, "png", baos);
                    imageList.add(baos.toByteArray());
                    log.debug("Converted page {} of {} to PNG image.", page + 1, filePath);
                }
            }
        } catch (IOException e) {
            log.error("Error loading PDF or converting pages to images for file: {}", filePath, e);
            throw e; // Re-throw to be handled by the caller
        }
        log.info("Successfully converted {} pages from PDF {} to images.", imageList.size(), filePath);
        return imageList;
    }
} // End of PdfDataExtractor class
