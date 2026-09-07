package com.sindhueventpay.dto;

import com.sindhueventpay.enums.RegistrationStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * Response DTO for registration status queries and payment verification.
 *
 * <p>Used by:
 * <ul>
 *   <li>{@code POST /api/v1/payments/verify} — after successful payment</li>
 *   <li>{@code GET /api/v1/registrations/{registrationId}} — status polling</li>
 * </ul>
 *
 * <p>The {@code registrationNumber} field is only populated when
 * {@code status == PAID}. Do not expose Aadhaar-related fields.
 */
@Getter
@Builder
public class RegistrationStatusResponse {

    /** Internal UUID — safe to share with the registrant. */
    private final String registrationId;

    /**
     * Human-readable registration number, e.g. {@code EVT2026-000042}.
     * Null until payment is verified.
     */
    private final String registrationNumber;

    private final RegistrationStatus status;
    private final String eventName;
    private final String eventCode;
    private final String fullName;
    private final String email;

    /** Registration fee in INR (display purposes). */
    private final BigDecimal amount;
    private final String currency;
}
