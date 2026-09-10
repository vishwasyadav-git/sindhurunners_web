package com.sindhueventpay.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class AdminEventRequest {

    private Long id; // Included for updates


    @NotBlank(message = "Event code is required")
    private String eventCode;

    @NotBlank(message = "Event name is required")
    private String eventName;

    private String description;

    @NotNull(message = "Event date is required")
    private LocalDate eventDate;

    @NotNull(message = "Registration closing date is required")
    private LocalDateTime registrationClosesAt;

    private boolean isActive = true;

    @Valid
    @NotNull(message = "Categories cannot be null")
    private List<AdminEventCategoryRequest> categories;
}
