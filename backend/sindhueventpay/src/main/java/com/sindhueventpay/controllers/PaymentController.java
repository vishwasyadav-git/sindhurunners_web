package com.sindhueventpay.controllers;

import com.sindhueventpay.dto.ApiResponse;
import com.sindhueventpay.dto.PaymentVerifyRequest;
import com.sindhueventpay.dto.RegistrationStatusResponse;
import com.sindhueventpay.services.PaymentService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for payment endpoints.
 *
 * <p>Base path: {@code /api/v1/payments}
 *
 * <h3>Endpoints:</h3>
 * <ul>
 *   <li>{@code POST /api/v1/payments/verify}  — verify payment from frontend Razorpay callback</li>
 *   <li>{@code POST /api/v1/payments/webhook} — receive and process Razorpay webhook events</li>
 * </ul>
 *
 * <h3>Security notes:</h3>
 * <ul>
 *   <li>Both endpoints independently verify Razorpay HMAC signatures.</li>
 *   <li>The webhook endpoint reads the raw body as {@code byte[]} to ensure the
 *       HMAC is computed over the exact bytes sent by Razorpay, not a
 *       serialised/deserialised representation.</li>
 *   <li>Webhook processing is idempotent (multiple deliveries = safe).</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/payments")
@Slf4j
public class PaymentController {

    @Autowired
    private PaymentService paymentService;

    // ─────────────────────────────────────────────────────────────────────────
    // POST /api/v1/payments/verify
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Verifies a Razorpay payment from the frontend callback.
     *
     * <p>Called by the frontend after the Razorpay Checkout {@code handler}
     * callback fires with {@code response.razorpay_payment_id},
     * {@code response.razorpay_order_id}, and {@code response.razorpay_signature}.
     *
     * <p>This endpoint:
     * <ol>
     *   <li>Validates the request body fields.</li>
     *   <li>Verifies the Razorpay HMAC signature server-side.</li>
     *   <li>Marks the payment as CAPTURED and the registration as PAID.</li>
     *   <li>Returns the registration number.</li>
     * </ol>
     *
     * @param request contains razorpayOrderId, razorpayPaymentId, and razorpaySignature
     * @return registration status with registrationNumber
     */
    @PostMapping("/verify")
    public ResponseEntity<ApiResponse<RegistrationStatusResponse>> verifyPayment(
            @Valid @RequestBody PaymentVerifyRequest request) {

        log.info("Payment verify request. orderId=[{}]", request.getRazorpayOrderId());

        RegistrationStatusResponse response = paymentService.verifyAndComplete(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POST /api/v1/payments/webhook
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Receives and processes Razorpay webhook events.
     *
     * <p>Razorpay signs webhooks using HMAC-SHA256 with the webhook secret.
     * The signature is in the {@code X-Razorpay-Signature} header.
     *
     * <p><strong>Raw body requirement:</strong> The request body is read as
     * {@code byte[]} here. Spring MVC will not parse it as JSON — it is passed
     * raw to the service which computes the HMAC over the original bytes.
     * Any intermediate parsing (ObjectMapper, etc.) would invalidate the HMAC.
     *
     * <p>Webhook events handled:
     * <ul>
     *   <li>{@code payment.captured} — marks registration as PAID (idempotent)</li>
     *   <li>{@code payment.failed}   — marks registration as PAYMENT_FAILED</li>
     *   <li>All others              — logged and ignored</li>
     * </ul>
     *
     * @param rawBody   raw webhook request body (used for HMAC computation)
     * @param signature value of the {@code X-Razorpay-Signature} header
     * @return 200 OK on success (Razorpay expects a 200 to stop retrying)
     */
    @PostMapping("/webhook")
    public ResponseEntity<ApiResponse<Void>> handleWebhook(
            @RequestBody byte[] rawBody,
            @RequestHeader("X-Razorpay-Signature") String signature) {

        log.info("Razorpay webhook received.");

        paymentService.processWebhook(rawBody, signature);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
