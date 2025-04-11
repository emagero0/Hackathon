package com.erp.aierpbackend.controller;

import com.erp.aierpbackend.dto.NlpRequest;
import com.erp.aierpbackend.dto.NlpResponse;
import com.erp.aierpbackend.service.NlpService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import reactor.core.publisher.Mono; // Added import
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/nlp")
public class NlpController {

    private static final Logger log = LoggerFactory.getLogger(NlpController.class);

    @Autowired
    private NlpService nlpService;

    @PostMapping("/verify")
    public Mono<ResponseEntity<NlpResponse>> verifyDocument(@RequestBody NlpRequest request) { // Return type is now Mono
        log.info("Received NLP verification request: {}", request.getMetadata()); // Log metadata for tracking

        // Call the async service method (updated method name)
        return nlpService.verifyDocumentAndPersist(request)
            .map(response -> {
                // Executed when the Mono completes successfully
                log.info("NLP verification successful for request: {}", request.getMetadata().getOrDefault("documentId", "N/A"));
                return ResponseEntity.ok(response);
            })
            .onErrorResume(e -> {
                // Executed if the Mono completes with an error
                log.error("Error during NLP verification for request: {}", request.getMetadata().getOrDefault("documentId", "N/A"), e);
                // Consider returning a more specific error response DTO in the body
                return Mono.just(ResponseEntity.internalServerError().build());
            });
    }
}
