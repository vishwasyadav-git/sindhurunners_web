package com.sindhueventpay.exceptions;

import com.sindhueventpay.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.stream.Collectors;

/**
 * Centralised exception handler. All exceptions are translated into the
 * standardised {@link ApiResponse} envelope so callers always receive a
 * consistent JSON shape.
 *
 * <p>Internal stack traces are never exposed to the client. Sensitive
 * exceptions are logged at WARN level with a correlation ID for tracing.
 */
@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // ─────────────────────────────────────────────────────────────────────────
    // Domain exceptions
    // ─────────────────────────────────────────────────────────────────────────

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(ResourceNotFoundException ex) {
        log.warn("Resource not found: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("RESOURCE_NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(EventFullException.class)
    public ResponseEntity<ApiResponse<Void>> handleEventFull(EventFullException ex) {
        log.info("Event full: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiResponse.error("EVENT_FULL", ex.getMessage()));
    }

    @ExceptionHandler(EventClosedException.class)
    public ResponseEntity<ApiResponse<Void>> handleEventClosed(EventClosedException ex) {
        log.info("Event closed: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiResponse.error("EVENT_REGISTRATION_CLOSED", ex.getMessage()));
    }

    @ExceptionHandler(PaymentVerificationException.class)
    public ResponseEntity<ApiResponse<Void>> handlePaymentVerification(PaymentVerificationException ex) {
        // Log at WARN — this could indicate a security event (forged callback)
        log.warn("Payment verification failed: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("PAYMENT_VERIFICATION_FAILED",
                        "Payment verification failed. Please contact support if you believe this is an error."));
    }

    @ExceptionHandler(S3UploadException.class)
    public ResponseEntity<ApiResponse<Void>> handleS3Upload(S3UploadException ex) {
        log.error("S3 upload failed: {}", ex.getMessage(), ex.getCause());
        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.error("DOCUMENT_UPLOAD_FAILED",
                        "Could not upload your document. Please try again in a moment."));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Validation exceptions
    // ─────────────────────────────────────────────────────────────────────────

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String details = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("VALIDATION_ERROR", details));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("VALIDATION_ERROR", ex.getMessage()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleFileTooLarge(MaxUploadSizeExceededException ex) {
        return ResponseEntity
                .status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ApiResponse.error("FILE_TOO_LARGE",
                        "Uploaded file exceeds the maximum allowed size of 5 MB."));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Database exceptions
    // ─────────────────────────────────────────────────────────────────────────

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiResponse.error("DUPLICATE_ENTRY",
                        "A conflicting record already exists. " +
                        "If you already registered, please check your email."));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Catch-all
    // ─────────────────────────────────────────────────────────────────────────

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneric(Exception ex) {
        // Never expose internal exception message to the client
        log.error("Unhandled exception: {}", ex.getMessage(), ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("INTERNAL_SERVER_ERROR",
                        "An unexpected error occurred. Please try again or contact support."));
    }
}
