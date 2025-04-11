package com.erp.aierpbackend.config;

import net.sourceforge.tess4j.Tesseract;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Configuration
public class OcrConfig {

    private static final Logger log = LoggerFactory.getLogger(OcrConfig.class);

    @Value("${tesseract.path}")
    private String tesseractPath;

    // Optional: Define datapath explicitly if needed
    // @Value("${tesseract.datapath:#{null}}") // Default to null if not set
    // private String tesseractDataPath;

    @Bean
    public Tesseract tesseract() {
        Tesseract tesseract = new Tesseract();
        log.info("Configuring Tesseract with path: {}", tesseractPath);
        tesseract.setDatapath(tesseractPath); // Tess4J expects the parent dir of 'tessdata'

        // Explicitly set language if needed (e.g., English)
        tesseract.setLanguage("eng");

        // Set Page Segmentation Mode (PSM) if needed - PSM_AUTO is often a good default
        // tesseract.setPageSegMode(1); // Example: PSM_AUTO_OSD

        // Set OCR Engine Mode (OEM) if needed - OEM_LSTM_ONLY is often good
        // tesseract.setOcrEngineMode(1); // Example: OEM_LSTM_ONLY

		// Add any other Tesseract configurations here

		log.info("Tesseract bean configured using path: {}", tesseractPath); // Adjusted log message
		return tesseract;
	}
}
