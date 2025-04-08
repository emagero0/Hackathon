package com.erp.aierpbackend.dto.gemini;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GeminiContent {
    private List<GeminiPart> parts;
    private String role; // Optional, e.g., "user" or "model"
}
