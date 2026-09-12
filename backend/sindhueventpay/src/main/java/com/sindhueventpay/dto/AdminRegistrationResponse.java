package com.sindhueventpay.dto;

import com.sindhueventpay.enums.RegistrationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminRegistrationResponse {
    private String id;
    private String registrationNumber;
    private String fullName;
    private String emailId;
    private String mobileNumber;
    private String gender;
    private int calculatedAge;
    private String categoryName;
    private BigDecimal calculatedFee;
    private RegistrationStatus status;
    private String documentUrl;
    private LocalDateTime createdAt;
}
