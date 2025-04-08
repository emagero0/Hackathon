package com.erp.aierpbackend.dto.gemini;

import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true) // Ignore fields not defined
public class GeminiCandidate {
    private GeminiContent content;
    private String finishReason;
    private Integer index;
    private List<GeminiSafetyRating> safetyRatings; // Define GeminiSafetyRating if needed
}
