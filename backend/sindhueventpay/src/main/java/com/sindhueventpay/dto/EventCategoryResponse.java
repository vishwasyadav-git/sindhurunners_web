package com.sindhueventpay.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class EventCategoryResponse {
    private final Long id;
    private final String categoryName;
    private final int minAge;
    private final int maxAge;
    private final BigDecimal fee;
}
