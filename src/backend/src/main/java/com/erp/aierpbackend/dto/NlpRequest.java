package com.erp.aierpbackend.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.Map;

@Data // Lombok annotation for getters, setters, toString, equals, hashCode
@NoArgsConstructor
@AllArgsConstructor
public class NlpRequest {
    private String cleanedText;
    private Map<String, Object> metadata; // Flexible map for metadata like doc type, timestamp, etc.
}
