package com.erp.aierpbackend.controller;

import com.erp.aierpbackend.service.OcrService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Mono;

import java.io.IOException;

@RestController
@RequestMapping("/api/ocr")
@RequiredArgsConstructor
@Slf4j
public class OcrController {

    private final OcrService ocrService;

    @PostMapping(value = "/invoice", consumes = "multipart/form-data")
    public Mono<ResponseEntity<String>> uploadAndProcessInvoice(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            log.warn("Received empty file upload request.");
            return Mono.just(ResponseEntity.badRequest().body("Please select a PDF file to upload."));
        }

        if (!"application/pdf".equalsIgnoreCase(file.getContentType())) {
             log.warn("Received non-PDF file upload: {}", file.getContentType());
             return Mono.just(ResponseEntity.badRequest().body("Only PDF files are supported."));
        }

        log.info("Received invoice PDF upload: {}", file.getOriginalFilename());

        try {
            byte[] fileBytes = file.getBytes();
            // Trigger asynchronous processing
            return ocrService.processInvoicePdf(fileBytes)
                .then(Mono.just(ResponseEntity.accepted().body("Invoice received and queued for processing."))) // Return 202 Accepted immediately
                .onErrorResume(e -> {
                    log.error("Error initiating OCR processing for file: {}", file.getOriginalFilename(), e);
                    // Return an internal server error if initiation fails
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                     .body("Failed to initiate processing for file: " + file.getOriginalFilename() + ". Error: " + e.getMessage()));
                });

        } catch (IOException e) {
            log.error("Failed to read bytes from uploaded file: {}", file.getOriginalFilename(), e);
            return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                             .body("Failed to read uploaded file: " + file.getOriginalFilename()));
        }
    }
}
