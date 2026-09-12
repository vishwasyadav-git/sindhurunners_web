package com.sindhueventpay.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegistrationStatsResponse {
    private long totalRegistrations;
    private BigDecimal totalRevenue;
    private Map<String, Long> categoryBreakdown;
    private Map<String, Long> genderBreakdown;
}
