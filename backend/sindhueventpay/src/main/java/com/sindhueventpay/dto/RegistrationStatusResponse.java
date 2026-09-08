package com.sindhueventpay.dto;

import com.sindhueventpay.enums.RegistrationStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class RegistrationStatusResponse {

    private final String registrationId;
    private final String registrationNumber;
    private final RegistrationStatus status;
    private final String eventName;
    private final String eventCode;
    private final String fullName;
    private final String email;
    private final String categoryName;
    private final BigDecimal amount;
    private final String currency;
}
