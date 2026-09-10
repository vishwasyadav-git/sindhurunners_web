package com.sindhueventpay.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class AdminEventCategoryRequest {

    private Long id; // Null for new categories, populated for existing ones

    @NotBlank(message = "Category name is required")
    private String categoryName;

    @NotNull(message = "Minimum age is required")
    private Integer minAge;

    @NotNull(message = "Maximum age is required")
    private Integer maxAge;

    @NotNull(message = "Fee is required")
    @DecimalMin(value = "0.0", message = "Fee must be greater than or equal to 0")
    private BigDecimal fee;
}
