package com.sindhueventpay.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when Razorpay signature or webhook HMAC verification fails.
 *
 * <p>This indicates a tampered or forged payment callback and must be treated
 * as a security event. The global exception handler logs this at WARN level
 * without leaking internal details to the caller.
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class PaymentVerificationException extends RuntimeException {
    public PaymentVerificationException(String message) {
        super(message);
    }
}
