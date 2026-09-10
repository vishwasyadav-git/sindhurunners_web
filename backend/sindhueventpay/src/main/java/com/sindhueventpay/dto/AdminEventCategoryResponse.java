package com.sindhueventpay.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class AdminEventCategoryResponse {
    private Long id;
    private String categoryName;
    private int minAge;
    private int maxAge;
    private BigDecimal fee;
}
