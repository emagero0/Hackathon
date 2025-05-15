package com.erp.aierpbackend.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for converting PDF files to images.
 * This is a simplified version of the PDF to image conversion functionality
 * that was previously in PdfDataExtractor.
 */
@Component
@Slf4j
public class PdfImageConverter {

    /**
     * Converts a PDF file to a list of PNG images, one per page.
     *
     * @param filePath The path to the PDF file
     * @param dpi The DPI (dots per inch) for the rendered images
     * @return A list of byte arrays, each containing a PNG image
     * @throws IOException If there's an error reading or processing the PDF
     */
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
}
