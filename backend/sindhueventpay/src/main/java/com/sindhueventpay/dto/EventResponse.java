package com.sindhueventpay.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Response DTO for {@code GET /api/v1/events/{eventCode}}.
 * Exposes only what the registration page needs — no internal IDs.
 */
@Getter
@Builder
public class EventResponse {

    private final String eventCode;
    private final String eventName;
    private final String description;

    /** Registration fee in full INR. */
    private final BigDecimal registrationFee;
    private final String currency;
    private final LocalDate startDate;
    private final LocalDate endDate;
    private final boolean registrationOpen;

    /** Available slots = maxRegistrations - registrationCount */
    private final int availableSlots;
}
