package com.erp.aierpbackend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class TriggerVerificationRequest {

    @NotBlank(message = "Job number cannot be blank")
    private String jobNo;
}
