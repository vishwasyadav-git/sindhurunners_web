package com.sindhueventpay.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class AdminEventResponse {
    private Long id;
    private String eventCode;
    private String eventName;
    private String description;
    private LocalDate eventDate;
    private LocalDateTime registrationClosesAt;
    private boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<AdminEventCategoryResponse> categories;
}
