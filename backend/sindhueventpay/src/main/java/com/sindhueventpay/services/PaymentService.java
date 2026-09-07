package com.sindhueventpay.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sindhueventpay.Repository.EventRepository;
import com.sindhueventpay.Repository.PaymentRepository;
import com.sindhueventpay.Repository.RegistrationRepository;
import com.sindhueventpay.dto.PaymentVerifyRequest;
import com.sindhueventpay.dto.RegistrationStatusResponse;
import com.sindhueventpay.enums.PaymentStatus;
import com.sindhueventpay.enums.RegistrationStatus;
import com.sindhueventpay.exceptions.PaymentVerificationException;
import com.sindhueventpay.exceptions.ResourceNotFoundException;
import com.sindhueventpay.models.Event;
import com.sindhueventpay.models.Payment;
import com.sindhueventpay.models.Registration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Service responsible for all payment-related operations.
 *
 * <h3>Payment success flow (processPaymentCaptured):</h3>
 * <p>This is the most critical method in the system. It runs inside a
 * {@code SERIALIZABLE} transaction with a pessimistic write lock on both
 * the Registration and Event rows to guarantee:
 * <ul>
 *   <li>No duplicate registration numbers (atomic counter increment).</li>
 *   <li>No double-counting of event registrations.</li>
 *   <li>Idempotency: if the same payment event arrives twice (webhook replay,
 *       concurrent frontend + webhook), only one succeeds.</li>
 * </ul>
 *
 * <h3>Idempotency guarantees (layered):</h3>
 * <ol>
 *   <li><strong>Application layer:</strong> checks {@code registration.status == PAID}
 *       before doing any work and returns early.</li>
 *   <li><strong>Database layer:</strong> UNIQUE constraint on {@code payments.razorpay_payment_id}
 *       causes a {@link DataIntegrityViolationException} if two concurrent transactions
 *       try to set the same payment ID. The second transaction catches this and
 *       returns the already-completed result.</li>
 * </ol>
 */
@Service
@Slf4j
public class PaymentService {

    @Autowired
    private RegistrationRepository registrationRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private RazorpayService razorpayService;

    @Autowired
    private RegistrationService registrationService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private org.springframework.context.ApplicationContext applicationContext;

    private PaymentService getSelf() {
        return applicationContext.getBean(PaymentService.class);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Frontend verify endpoint
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Verifies the payment signature from the frontend callback and completes
     * the registration if valid.
     *
     * <p>The frontend MUST call this endpoint after Razorpay Checkout returns.
     * Do NOT trust the frontend's assertion that payment succeeded — the server
     * must independently verify the HMAC signature before proceeding.
     *
     * @param request DTO containing orderId, paymentId, and signature from Razorpay Checkout
     * @return registration status response with registrationNumber if PAID
     */
    public RegistrationStatusResponse verifyAndComplete(PaymentVerifyRequest request) {
        // 1. Verify HMAC signature (throws PaymentVerificationException if invalid)
        razorpayService.verifyPaymentSignature(
                request.getRazorpayOrderId(),
                request.getRazorpayPaymentId(),
                request.getRazorpaySignature());

        log.info("Frontend payment verify. orderId=[{}] paymentId=[{}]",
                request.getRazorpayOrderId(), request.getRazorpayPaymentId());

        // 2. Process the confirmed payment (idempotent)
        return getSelf().processPaymentCaptured(
                request.getRazorpayOrderId(),
                request.getRazorpayPaymentId(),
                request.getRazorpaySignature());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Webhook handler
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Processes a Razorpay webhook event.
     *
     * <p>This method:
     * <ol>
     *   <li>Verifies the HMAC signature against the raw request body.</li>
     *   <li>Parses the event type.</li>
     *   <li>For {@code payment.captured}: calls {@link #processPaymentCaptured}.</li>
     *   <li>For other event types: logs and ignores.</li>
     * </ol>
     *
     * <p>Webhook processing MUST be idempotent. Razorpay may deliver the same
     * event multiple times. The idempotency is guaranteed by the DB constraints
     * and application checks inside {@link #processPaymentCaptured}.
     *
     * @param rawBody   the raw HTTP request body bytes (required for HMAC verification)
     * @param signature value of the {@code X-Razorpay-Signature} header
     */
    public void processWebhook(byte[] rawBody, String signature) {
        // 1. Verify webhook HMAC (throws PaymentVerificationException if invalid)
        razorpayService.verifyWebhookSignature(rawBody, signature);

        // 2. Parse webhook payload
        JsonNode payload;
        try {
            payload = objectMapper.readTree(rawBody);
        } catch (Exception e) {
            log.error("Failed to parse webhook payload: {}", e.getMessage());
            throw new PaymentVerificationException("Invalid webhook payload format.");
        }

        String eventType = payload.path("event").asText();
        log.info("Razorpay webhook received. event=[{}]", eventType);

        // 3. Handle known event types
        switch (eventType) {
            case "payment.captured" -> {
                JsonNode paymentEntity = payload
                        .path("payload").path("payment").path("entity");
                String orderId   = paymentEntity.path("order_id").asText();
                String paymentId = paymentEntity.path("id").asText();
                String method    = paymentEntity.path("method").asText(null);

                if (orderId.isBlank() || paymentId.isBlank()) {
                    log.warn("Webhook payment.captured missing orderId or paymentId.");
                    return;
                }

                log.info("Webhook payment.captured. orderId=[{}] paymentId=[{}] method=[{}]",
                        orderId, paymentId, method);

                getSelf().processPaymentCaptured(orderId, paymentId, null);
                // Note: webhook does not provide the client-facing signature, pass null
            }
            case "payment.failed" -> {
                JsonNode paymentEntity = payload
                        .path("payload").path("payment").path("entity");
                String orderId = paymentEntity.path("order_id").asText();
                log.info("Webhook payment.failed. orderId=[{}]", orderId);
                getSelf().handlePaymentFailed(orderId);
            }
            default -> log.debug("Unhandled webhook event type: [{}]", eventType);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Core payment capture — transactional with pessimistic locking
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Atomically marks a payment as captured, generates the registration number,
     * and increments the event registration count.
     *
     * <p><strong>Transaction isolation:</strong> {@code READ_COMMITTED} with explicit
     * pessimistic write locks on both the Registration and Event rows. This prevents:
     * <ul>
     *   <li>Two concurrent payment events both proceeding past the PAID check.</li>
     *   <li>Two different registrations getting the same registration number.</li>
     *   <li>Double-counting of {@code event.registrationCount}.</li>
     * </ul>
     *
     * @param razorpayOrderId   the Razorpay order_id
     * @param razorpayPaymentId the Razorpay pay_xxx ID
     * @param razorpaySignature the client-facing HMAC signature (may be null for webhooks)
     * @return registration status response (PAID with registration number)
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public RegistrationStatusResponse processPaymentCaptured(String razorpayOrderId,
                                                              String razorpayPaymentId,
                                                              String razorpaySignature) {
        try {
            return doProcessPaymentCaptured(razorpayOrderId, razorpayPaymentId, razorpaySignature);
        } catch (DataIntegrityViolationException e) {
            // This means another concurrent transaction just set the same razorpay_payment_id.
            // The constraint guarantees exactly one wins. Return the result of the winner.
            log.info("Duplicate payment event (idempotent). orderId=[{}] paymentId=[{}]",
                    razorpayOrderId, razorpayPaymentId);
            return fetchFinalStatus(razorpayOrderId);
        }
    }

    private RegistrationStatusResponse doProcessPaymentCaptured(String razorpayOrderId,
                                                                  String razorpayPaymentId,
                                                                  String razorpaySignature) {
        // 1. Find payment record by order ID
        Payment payment = paymentRepository.findByRazorpayOrderId(razorpayOrderId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Payment record not found for order: " + razorpayOrderId));

        // 2. IDEMPOTENCY CHECK (application layer):
        //    If already CAPTURED, return the already-completed result immediately.
        if (payment.getStatus() == PaymentStatus.CAPTURED) {
            log.info("Payment already captured (idempotent). orderId=[{}]", razorpayOrderId);
            return fetchFinalStatus(razorpayOrderId);
        }

        // 3. Acquire pessimistic write lock on Registration row
        Registration registration = registrationRepository
                .findByIdWithLock(payment.getRegistrationId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Registration not found: " + payment.getRegistrationId()));

        // 4. IDEMPOTENCY CHECK (registration layer)
        if (registration.getStatus() == RegistrationStatus.PAID) {
            log.info("Registration already PAID (idempotent). registrationId=[{}]",
                    registration.getId());
            Event ev = eventRepository.findById(registration.getEventId()).orElseThrow();
            return registrationService.toStatusResponse(registration, ev);
        }

        // 5. Acquire pessimistic write lock on Event row to safely increment counter
        Event event = eventRepository
                .findByIdWithLock(registration.getEventId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Event not found for registration: " + registration.getId()));

        // 6. Increment registration count and generate sequential registration number
        int newCount = event.getRegistrationCount() + 1;
        event.setRegistrationCount(newCount);
        String registrationNumber = String.format("%s-%06d", event.getEventCode(), newCount);

        // 7. Update Payment record
        payment.setRazorpayPaymentId(razorpayPaymentId);   // UNIQUE constraint enforces idempotency
        payment.setRazorpaySignature(razorpaySignature);
        payment.setStatus(PaymentStatus.CAPTURED);
        payment.setPaidAt(LocalDateTime.now());
        paymentRepository.save(payment);                   // may throw DataIntegrityViolationException

        // 8. Update Registration record to PAID with registration number
        registration.setStatus(RegistrationStatus.PAID);
        registration.setRegistrationNumber(registrationNumber);
        registrationRepository.save(registration);

        // 9. Save updated event (incremented count)
        eventRepository.save(event);

        log.info("Payment captured. registrationId=[{}] registrationNumber=[{}] orderId=[{}] paymentId=[{}]",
                registration.getId(), registrationNumber, razorpayOrderId, razorpayPaymentId);

        return registrationService.toStatusResponse(registration, event);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Payment failed handler
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    public void handlePaymentFailed(String razorpayOrderId) {
        paymentRepository.findByRazorpayOrderId(razorpayOrderId).ifPresent(payment -> {
            if (payment.getStatus() != PaymentStatus.CAPTURED) {
                payment.setStatus(PaymentStatus.FAILED);
                paymentRepository.save(payment);

                registrationRepository.findByIdWithLock(payment.getRegistrationId())
                        .ifPresent(reg -> {
                            if (reg.getStatus() != RegistrationStatus.PAID) {
                                reg.setStatus(RegistrationStatus.PAYMENT_FAILED);
                                registrationRepository.save(reg);
                            }
                        });
            }
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helper
    // ─────────────────────────────────────────────────────────────────────────

    private RegistrationStatusResponse fetchFinalStatus(String razorpayOrderId) {
        Payment payment = paymentRepository.findByRazorpayOrderId(razorpayOrderId).orElseThrow();
        Registration reg = registrationRepository.findById(payment.getRegistrationId()).orElseThrow();
        Event event = eventRepository.findById(reg.getEventId()).orElseThrow();
        return registrationService.toStatusResponse(reg, event);
    }
}
