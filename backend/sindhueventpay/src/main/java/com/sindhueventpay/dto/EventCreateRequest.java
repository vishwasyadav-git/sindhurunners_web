package com.sindhueventpay.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Request DTO for creating a new Event.
 */
@Data
public class EventCreateRequest {

    @NotBlank(message = "Event code is required")
    private String eventCode;

    @NotBlank(message = "Event name is required")
    private String eventName;

    private String description;

    @NotNull(message = "Registration fee is required")
    @Min(value = 0, message = "Registration fee cannot be negative")
    private BigDecimal registrationFee;

    private String currency = "INR";

    @Min(value = 1, message = "Max registrations must be at least 1")
    private int maxRegistrations;

    private LocalDate startDate;

    private LocalDate endDate;

    private boolean registrationOpen = true;
}
