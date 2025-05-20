package com.erp.aierpbackend.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
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

        // Check if the file is a valid PDF by examining the first few bytes
        try {
            byte[] fileBytes = Files.readAllBytes(pdfFile.toPath());
            if (fileBytes.length < 4 ||
                fileBytes[0] != '%' || fileBytes[1] != 'P' ||
                fileBytes[2] != 'D' || fileBytes[3] != 'F') {
                log.error("File is not a valid PDF (missing PDF header): {}", filePath);
                // Create a simple image with error message as fallback
                BufferedImage errorImage = createErrorImage("Invalid PDF file");
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(errorImage, "png", baos);
                imageList.add(baos.toByteArray());
                return imageList;
            }
        } catch (IOException e) {
            log.error("Error reading file to check PDF header: {}", filePath, e);
        }

        try (PDDocument document = Loader.loadPDF(pdfFile)) {
            if (document.isEncrypted()) {
                log.warn("PDF document is encrypted and cannot be converted to images: {}", filePath);
                // Create a simple image with error message as fallback
                BufferedImage errorImage = createErrorImage("Encrypted PDF file");
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(errorImage, "png", baos);
                imageList.add(baos.toByteArray());
                return imageList;
            }

            int pageCount = document.getNumberOfPages();
            if (pageCount == 0) {
                log.warn("PDF document has 0 pages: {}", filePath);
                // Create a simple image with error message as fallback
                BufferedImage errorImage = createErrorImage("PDF has 0 pages");
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(errorImage, "png", baos);
                imageList.add(baos.toByteArray());
                return imageList;
            }

            PDFRenderer pdfRenderer = new PDFRenderer(document);
            for (int page = 0; page < pageCount; ++page) {
                try {
                    // Render image with specified DPI, higher DPI means better quality for OCR/LLM
                    // but larger image size. 300 DPI is a common standard.
                    BufferedImage bim = pdfRenderer.renderImageWithDPI(page, dpi, ImageType.RGB);

                    // Convert BufferedImage to byte array (PNG format)
                    try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                        ImageIO.write(bim, "png", baos);
                        imageList.add(baos.toByteArray());
                        log.debug("Converted page {} of {} to PNG image.", page + 1, filePath);
                    }
                } catch (Exception pageException) {
                    log.error("Error rendering page {} of PDF: {}", page + 1, filePath, pageException);
                    // Create an error image for this page
                    BufferedImage errorImage = createErrorImage("Error rendering page " + (page + 1));
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    ImageIO.write(errorImage, "png", baos);
                    imageList.add(baos.toByteArray());
                }
            }
        } catch (IOException e) {
            log.error("Error loading PDF or converting pages to images for file: {}", filePath, e);

            // Create a simple image with error message as fallback
            try {
                BufferedImage errorImage = createErrorImage("Error loading PDF: " + e.getMessage());
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(errorImage, "png", baos);
                imageList.add(baos.toByteArray());
                return imageList;
            } catch (Exception fallbackError) {
                log.error("Error creating fallback error image: {}", fallbackError.getMessage(), fallbackError);
                throw e; // Re-throw the original error if we can't even create a fallback
            }
        }
        log.info("Successfully converted {} pages from PDF {} to images.", imageList.size(), filePath);
        return imageList;
    }

    /**
     * Creates a simple image with an error message.
     * This is used as a fallback when PDF conversion fails.
     *
     * @param errorMessage The error message to display in the image
     * @return A BufferedImage containing the error message
     */
    private BufferedImage createErrorImage(String errorMessage) {
        // Create a simple image with the error message
        int width = 800;
        int height = 600;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();

        // Set background color
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, width, height);

        // Set text properties
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2d.setColor(Color.RED);
        g2d.setFont(new Font("Arial", Font.BOLD, 24));

        // Draw error title
        String errorTitle = "PDF Conversion Error";
        int titleWidth = g2d.getFontMetrics().stringWidth(errorTitle);
        g2d.drawString(errorTitle, (width - titleWidth) / 2, height / 3);

        // Draw error message
        g2d.setColor(Color.BLACK);
        g2d.setFont(new Font("Arial", Font.PLAIN, 18));
        int messageWidth = g2d.getFontMetrics().stringWidth(errorMessage);
        g2d.drawString(errorMessage, (width - messageWidth) / 2, height / 2);

        // Draw additional information
        g2d.setFont(new Font("Arial", Font.ITALIC, 14));
        String additionalInfo = "Please check the document and try again.";
        int infoWidth = g2d.getFontMetrics().stringWidth(additionalInfo);
        g2d.drawString(additionalInfo, (width - infoWidth) / 2, 2 * height / 3);

        g2d.dispose();
        return image;
    }
}
