package com.sindhueventpay.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * Response DTO returned after a successful registration creation.
 *
 * <p>Contains all information the frontend needs to open the Razorpay Checkout:
 * <ul>
 *   <li>{@code razorpayKeyId} — the public API key sent to Razorpay JS SDK.</li>
 *   <li>{@code razorpayOrderId} — the order ID passed to {@code rzp.open()}.</li>
 *   <li>{@code amountInPaise} — amount in smallest currency unit (paise for INR).
 *       Razorpay Checkout expects this value.</li>
 *   <li>{@code registrationId} — internal UUID used to poll status and call
 *       the verify endpoint after checkout.</li>
 * </ul>
 *
 * <p>The registration is in {@code PENDING_PAYMENT} state at this point.
 * It becomes {@code PAID} only after verified payment.
 */
@Getter
@Builder
public class RegistrationResponse {

    /** Internal UUID of the newly created registration. */
    private final String registrationId;

    /** Razorpay order_id to pass to Checkout. */
    private final String razorpayOrderId;

    /**
     * Public Razorpay Key ID (safe to expose to frontend).
     * Never expose the Key Secret.
     */
    private final String razorpayKeyId;

    /** Amount in paise (INR * 100). Required by Razorpay Checkout. */
    private final long amountInPaise;

    private final String currency;
    private final String eventName;
    private final String fullName;
    private final String email;

    /** Pre-filled description shown in the Razorpay Checkout modal. */
    private final String description;
}
