package com.erp.aierpbackend.service;

import lombok.extern.slf4j.Slf4j;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
@Slf4j
public class OcrService {

    // Inject Tesseract data path from application.properties or environment variable
    @Value("${tesseract.datapath:}") // Default to empty if not set
    private String tesseractDataPath;

    public String performOcr(String pdfFilePath) throws IOException, TesseractException {
        log.info("Performing OCR on PDF: {}", pdfFilePath);
        File pdfFile = new File(pdfFilePath);
        if (!pdfFile.exists()) {
            log.error("PDF file not found for OCR: {}", pdfFilePath);
            throw new IOException("PDF file not found: " + pdfFilePath);
        }

        ITesseract tesseract = new Tesseract();

        // Set Tesseract data path (important!)
        // Option 1: Use injected value (preferred if set)
        if (tesseractDataPath != null && !tesseractDataPath.isEmpty()) {
             log.info("Setting Tesseract data path from configuration: {}", tesseractDataPath);
             tesseract.setDatapath(tesseractDataPath);
        } else {
             // Option 2: Rely on TESSDATA_PREFIX environment variable (less explicit)
             log.warn("Tesseract data path not configured via property. Relying on TESSDATA_PREFIX environment variable.");
             // If TESSDATA_PREFIX is not set correctly, Tess4J might fail here.
        }

        // Set language (e.g., English)
        tesseract.setLanguage("eng");
        // Set DPI for better quality rendering (adjust as needed)
        int dpi = 300;

        StringBuilder ocrResult = new StringBuilder();

        try (PDDocument document = Loader.loadPDF(pdfFile)) {
            PDFRenderer pdfRenderer = new PDFRenderer(document);
            int pageCount = document.getNumberOfPages();
            log.debug("PDF has {} pages. Performing OCR page by page.", pageCount);

            for (int page = 0; page < pageCount; ++page) {
                log.debug("Processing OCR for page {}", page + 1);
                BufferedImage bufferedImage = pdfRenderer.renderImageWithDPI(page, dpi, ImageType.GRAY);

                // Perform OCR directly on the BufferedImage
                String pageText = tesseract.doOCR(bufferedImage);
                ocrResult.append(pageText);
                ocrResult.append("\n--- Page Break ---\n"); // Add separator for clarity
            }
        } catch (IOException e) {
            log.error("Error loading or rendering PDF for OCR: {}", pdfFilePath, e);
            throw e;
        } catch (TesseractException e) {
            log.error("Tesseract OCR processing failed for PDF: {}", pdfFilePath, e);
            throw e;
        }

        log.info("OCR completed for PDF: {}. Extracted text length: {}", pdfFilePath, ocrResult.length());
        log.trace("OCR Result Preview for {}: {}", pdfFilePath, ocrResult.substring(0, Math.min(ocrResult.length(), 500)) + "...");
        return ocrResult.toString();
    }
}
