package com.sindhueventpay.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * Unified API response envelope used by all endpoints.
 *
 * <p>Every response from this API — success or error — is wrapped in this class
 * so frontend clients always deal with a consistent JSON structure:
 * <pre>
 * {
 *   "success": true,
 *   "data": { ... },
 *   "timestamp": "2026-09-06T10:30:00"
 * }
 * </pre>
 * or
 * <pre>
 * {
 *   "success": false,
 *   "errorCode": "EVENT_FULL",
 *   "message": "Event has reached maximum capacity.",
 *   "timestamp": "2026-09-06T10:30:00"
 * }
 * </pre>
 *
 * @param <T> type of the payload in the {@code data} field (null for error responses).
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private final boolean success;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final T data;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final String errorCode;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final String message;

    private final LocalDateTime timestamp;

    // ─────────────────────────────────────────────────────────────────────────
    // Factory methods
    // ─────────────────────────────────────────────────────────────────────────

    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .data(data)
                .timestamp(LocalDateTime.now())
                .build();
    }

    public static <T> ApiResponse<T> error(String errorCode, String message) {
        return ApiResponse.<T>builder()
                .success(false)
                .errorCode(errorCode)
                .message(message)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
