package com.erp.aierpbackend.dto.gemini;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GeminiPart {
    private String text;
    // Can also include inlineData for images, etc. if needed later
}
