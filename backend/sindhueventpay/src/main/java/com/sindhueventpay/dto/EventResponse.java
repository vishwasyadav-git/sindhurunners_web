package com.sindhueventpay.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class EventResponse {
    private final String eventCode;
    private final String eventName;
    private final String description;
    private final LocalDate eventDate;
    private final LocalDateTime registrationClosesAt;
    private final boolean isActive;
    private final List<EventCategoryResponse> categories;
}
