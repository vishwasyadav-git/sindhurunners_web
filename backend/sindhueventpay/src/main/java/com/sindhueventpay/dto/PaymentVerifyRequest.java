package com.sindhueventpay.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Request body for {@code POST /api/v1/payments/verify}.
 *
 * <p>Called by the frontend after the Razorpay Checkout {@code handler} callback
 * fires. The backend must independently verify the HMAC signature before
 * trusting this data — never rely on the frontend's assertion that payment succeeded.
 */
@Getter
@Setter
@NoArgsConstructor
public class PaymentVerifyRequest {

    @NotBlank(message = "registrationId is required")
    private String registrationId;

    @NotBlank(message = "razorpayOrderId is required")
    private String razorpayOrderId;

    @NotBlank(message = "razorpayPaymentId is required")
    private String razorpayPaymentId;

    @NotBlank(message = "razorpaySignature is required")
    private String razorpaySignature;
}
