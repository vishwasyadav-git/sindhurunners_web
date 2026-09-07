package com.sindhueventpay.enums;

/**
 * Lifecycle states of a registration record.
 *
 * <p>State transitions:
 * <pre>
 * PENDING_PAYMENT → PAYMENT_PROCESSING → PAID
 *                                      → PAYMENT_FAILED
 * PENDING_PAYMENT → CANCELLED
 * </pre>
 *
 * <p><strong>Only PAID registrations receive a registrationNumber.</strong>
 */
public enum RegistrationStatus {

    /**
     * Registration created; Razorpay order created; awaiting user payment.
     */
    PENDING_PAYMENT,

    /**
     * Payment initiated by user; Razorpay checkout opened; not yet confirmed.
     * This is a transient state used to prevent double-submission.
     */
    PAYMENT_PROCESSING,

    /**
     * Payment successfully captured and verified by Razorpay signature/webhook.
     * The registration number has been assigned.
     */
    PAID,

    /**
     * Payment failed or was declined on the Razorpay side.
     */
    PAYMENT_FAILED,

    /**
     * Registration was cancelled (e.g., abandoned, admin action).
     */
    CANCELLED
}
