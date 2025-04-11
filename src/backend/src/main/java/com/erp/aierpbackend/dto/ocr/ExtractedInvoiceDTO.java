package com.erp.aierpbackend.dto.ocr;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data // Lombok annotation for getters, setters, toString, equals, hashCode
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExtractedInvoiceDTO {

    private String invoiceNo;
    private LocalDate invoiceDate; // Standardized date format
    private BigDecimal totalAmount; // Standardized numeric format
    private String customerName;
    private String customerNo;
    private List<LineItem> lineItems;
    private boolean signatureDetected;
    private String rawOcrText; // Include raw text for potential debugging or further processing

    // Inner class for line items
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class LineItem {
        private String description;
        private Integer quantity;
        private BigDecimal unitPrice;
        private BigDecimal lineTotal;
        // Add other relevant fields like item code if needed
    }
}
