package com.erp.aierpbackend.dto.gemini;

import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GeminiResponse {
    private List<GeminiCandidate> candidates;
    // Add promptFeedback field if needed (contains safetyRatings)
    // private GeminiPromptFeedback promptFeedback;
}

// Define GeminiPromptFeedback class if you need to parse it
// @Data
// @NoArgsConstructor
// @JsonIgnoreProperties(ignoreUnknown = true)
// class GeminiPromptFeedback {
//     private List<GeminiSafetyRating> safetyRatings;
// }
