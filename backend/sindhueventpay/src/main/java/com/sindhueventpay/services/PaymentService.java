package com.sindhueventpay.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sindhueventpay.Repository.EventCategoryRepository;
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
import com.sindhueventpay.models.EventCategory;
import com.sindhueventpay.models.Payment;
import com.sindhueventpay.models.Registration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

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
    private EventCategoryRepository eventCategoryRepository;

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

    public RegistrationStatusResponse verifyAndComplete(PaymentVerifyRequest request) {
        razorpayService.verifyPaymentSignature(
                request.getRazorpayOrderId(),
                request.getRazorpayPaymentId(),
                request.getRazorpaySignature());

        log.info("Frontend payment verify. orderId=[{}] paymentId=[{}]",
                request.getRazorpayOrderId(), request.getRazorpayPaymentId());

        return getSelf().processPaymentCaptured(
                request.getRazorpayOrderId(),
                request.getRazorpayPaymentId(),
                request.getRazorpaySignature());
    }

    public void processWebhook(byte[] rawBody, String signature) {
        razorpayService.verifyWebhookSignature(rawBody, signature);

        JsonNode payload;
        try {
            payload = objectMapper.readTree(rawBody);
        } catch (Exception e) {
            log.error("Failed to parse webhook payload: {}", e.getMessage());
            throw new PaymentVerificationException("Invalid webhook payload format.");
        }

        String eventType = payload.path("event").asText();
        log.info("Razorpay webhook received. event=[{}]", eventType);

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

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public RegistrationStatusResponse processPaymentCaptured(String razorpayOrderId,
                                                              String razorpayPaymentId,
                                                              String razorpaySignature) {
        try {
            return doProcessPaymentCaptured(razorpayOrderId, razorpayPaymentId, razorpaySignature);
        } catch (DataIntegrityViolationException e) {
            log.info("Duplicate payment event (idempotent). orderId=[{}] paymentId=[{}]",
                    razorpayOrderId, razorpayPaymentId);
            return fetchFinalStatus(razorpayOrderId);
        }
    }

    private RegistrationStatusResponse doProcessPaymentCaptured(String razorpayOrderId,
                                                                  String razorpayPaymentId,
                                                                  String razorpaySignature) {
        Payment payment = paymentRepository.findByRazorpayOrderId(razorpayOrderId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Payment record not found for order: " + razorpayOrderId));

        if (payment.getStatus() == PaymentStatus.CAPTURED) {
            log.info("Payment already captured (idempotent). orderId=[{}]", razorpayOrderId);
            return fetchFinalStatus(razorpayOrderId);
        }

        Registration registration = registrationRepository
                .findByIdWithLock(payment.getRegistrationId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Registration not found: " + payment.getRegistrationId()));

        if (registration.getStatus() == RegistrationStatus.PAID) {
            log.info("Registration already PAID (idempotent). registrationId=[{}]",
                    registration.getId());
            Event ev = eventRepository.findById(registration.getEventId()).orElseThrow();
            EventCategory cat = eventCategoryRepository.findById(registration.getCategoryId()).orElseThrow();
            return registrationService.toStatusResponse(registration, ev, cat);
        }

        Event event = eventRepository
                .findById(registration.getEventId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Event not found for registration: " + registration.getId()));
        EventCategory category = eventCategoryRepository.findById(registration.getCategoryId()).orElseThrow();

        // Generate Alphanumeric ID
        String shortId = java.util.UUID.randomUUID().toString().substring(0, 5).toUpperCase();
        String registrationNumber = String.format("%s-%s", event.getEventCode(), shortId);

        payment.setRazorpayPaymentId(razorpayPaymentId);
        payment.setRazorpaySignature(razorpaySignature);
        payment.setStatus(PaymentStatus.CAPTURED);
        paymentRepository.save(payment);

        registration.setStatus(RegistrationStatus.PAID);
        registration.setRegistrationNumber(registrationNumber);
        registrationRepository.save(registration);

        log.info("Payment captured. registrationId=[{}] registrationNumber=[{}] orderId=[{}] paymentId=[{}]",
                registration.getId(), registrationNumber, razorpayOrderId, razorpayPaymentId);

        return registrationService.toStatusResponse(registration, event, category);
    }

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

    private RegistrationStatusResponse fetchFinalStatus(String razorpayOrderId) {
        Payment payment = paymentRepository.findByRazorpayOrderId(razorpayOrderId).orElseThrow();
        Registration reg = registrationRepository.findById(payment.getRegistrationId()).orElseThrow();
        Event event = eventRepository.findById(reg.getEventId()).orElseThrow();
        EventCategory category = eventCategoryRepository.findById(reg.getCategoryId()).orElseThrow();
        return registrationService.toStatusResponse(reg, event, category);
    }
}
