package com.sindhueventpay.enums;

/**
 * Lifecycle states of a Razorpay payment record.
 *
 * <p>Maps closely to Razorpay payment statuses but is stored independently
 * so the domain model does not depend on Razorpay's exact terminology.
 */
public enum PaymentStatus {

    /**
     * Razorpay order has been created; payment has not started.
     */
    CREATED,

    /**
     * Payment authorized by user's bank; awaiting capture.
     * Relevant mainly for two-step payment flows.
     */
    AUTHORIZED,

    /**
     * Payment captured and funds settled. The final success state.
     * Registration is marked PAID only when this state is reached.
     */
    CAPTURED,

    /**
     * Payment failed on the Razorpay side (declined, timeout, etc.).
     */
    FAILED,

    /**
     * Payment was refunded after capture.
     */
    REFUNDED
}
