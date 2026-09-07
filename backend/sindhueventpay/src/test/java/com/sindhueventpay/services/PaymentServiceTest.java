package com.sindhueventpay.services;

import com.sindhueventpay.Repository.EventRepository;
import com.sindhueventpay.Repository.PaymentRepository;
import com.sindhueventpay.Repository.RegistrationRepository;
import com.sindhueventpay.dto.PaymentVerifyRequest;
import com.sindhueventpay.dto.RegistrationStatusResponse;
import com.sindhueventpay.enums.PaymentStatus;
import com.sindhueventpay.enums.RegistrationStatus;
import com.sindhueventpay.exceptions.PaymentVerificationException;
import com.sindhueventpay.models.Event;
import com.sindhueventpay.models.Payment;
import com.sindhueventpay.models.Registration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link PaymentService}.
 *
 * <p>Focuses on:
 * <ul>
 *   <li>Signature verification delegation</li>
 *   <li>Idempotency: duplicate webhook / duplicate verify call</li>
 *   <li>Registration number generation</li>
 *   <li>Event counter increment</li>
 *   <li>Payment failed webhook handling</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentService")
class PaymentServiceTest {

    @Mock RegistrationRepository registrationRepository;
    @Mock PaymentRepository paymentRepository;
    @Mock EventRepository eventRepository;
    @Mock RazorpayService razorpayService;
    @Mock RegistrationService registrationService;

    @InjectMocks PaymentService paymentService;

    private Event event;
    private Registration registration;
    private Payment payment;

    @BeforeEach
    void setUp() {
        event = new Event();
        event.setId(1L);
        event.setEventCode("EVT2026");
        event.setEventName("Goa to Sawantwadi Run 2026");
        event.setRegistrationFee(new BigDecimal("500.00"));
        event.setCurrency("INR");
        event.setMaxRegistrations(1000);
        event.setRegistrationCount(5);

        registration = new Registration();
        registration.setId("reg-uuid-001");
        registration.setEventId(1L);
        registration.setFullName("Omkar Paradkar");
        registration.setEmail("omkar@example.com");
        registration.setMobileNumber("9876543210");
        registration.setStatus(RegistrationStatus.PENDING_PAYMENT);

        payment = new Payment();
        payment.setId(1L);
        payment.setRegistrationId("reg-uuid-001");
        payment.setRazorpayOrderId("order_test123");
        payment.setAmount(new BigDecimal("500.00"));
        payment.setCurrency("INR");
        payment.setStatus(PaymentStatus.CREATED);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // verifyAndComplete
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("verifyAndComplete (frontend callback)")
    class VerifyAndComplete {

        @Test
        @DisplayName("should throw PaymentVerificationException when signature is invalid")
        void shouldThrowOnInvalidSignature() {
            doThrow(new PaymentVerificationException("Bad signature"))
                    .when(razorpayService).verifyPaymentSignature(any(), any(), any());

            PaymentVerifyRequest req = new PaymentVerifyRequest();
            req.setRegistrationId("reg-uuid-001");
            req.setRazorpayOrderId("order_test123");
            req.setRazorpayPaymentId("pay_test456");
            req.setRazorpaySignature("invalid_sig");

            assertThatThrownBy(() -> paymentService.verifyAndComplete(req))
                    .isInstanceOf(PaymentVerificationException.class);
        }

        @Test
        @DisplayName("should process payment and return PAID status with registration number")
        void shouldCompletePaymentSuccessfully() {
            doNothing().when(razorpayService).verifyPaymentSignature(any(), any(), any());

            when(paymentRepository.findByRazorpayOrderId("order_test123"))
                    .thenReturn(Optional.of(payment));
            when(registrationRepository.findByIdWithLock("reg-uuid-001"))
                    .thenReturn(Optional.of(registration));
            lenient().when(eventRepository.findById(1L)).thenReturn(Optional.of(event));
            lenient().when(eventRepository.findByIdWithLock(1L))
                    .thenReturn(Optional.of(event));
            when(paymentRepository.save(any())).thenReturn(payment);
            when(registrationRepository.save(any())).thenReturn(registration);
            when(eventRepository.save(any())).thenReturn(event);

            RegistrationStatusResponse mockResponse = RegistrationStatusResponse.builder()
                    .registrationId("reg-uuid-001")
                    .registrationNumber("EVT2026-000006")
                    .status(RegistrationStatus.PAID)
                    .eventName("Goa to Sawantwadi Run 2026")
                    .eventCode("EVT2026")
                    .fullName("Omkar Paradkar")
                    .amount(new BigDecimal("500.00"))
                    .currency("INR")
                    .build();
            when(registrationService.toStatusResponse(any(), any())).thenReturn(mockResponse);

            PaymentVerifyRequest req = new PaymentVerifyRequest();
            req.setRegistrationId("reg-uuid-001");
            req.setRazorpayOrderId("order_test123");
            req.setRazorpayPaymentId("pay_test456");
            req.setRazorpaySignature("valid_sig");

            RegistrationStatusResponse result = paymentService.verifyAndComplete(req);

            assertThat(result).isNotNull();
            assertThat(result.getStatus()).isEqualTo(RegistrationStatus.PAID);
            assertThat(result.getRegistrationNumber()).isEqualTo("EVT2026-000006");

            // Verify event counter was incremented
            verify(eventRepository).save(argThat(e -> e.getRegistrationCount() == 6));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Idempotency
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Idempotency")
    class Idempotency {

        @Test
        @DisplayName("should return idempotent success when payment already CAPTURED")
        void shouldBeIdempotentWhenAlreadyCaptured() {
            payment.setStatus(PaymentStatus.CAPTURED);
            payment.setRazorpayPaymentId("pay_test456");
            registration.setStatus(RegistrationStatus.PAID);
            registration.setRegistrationNumber("EVT2026-000006");

            when(paymentRepository.findByRazorpayOrderId("order_test123"))
                    .thenReturn(Optional.of(payment));
            when(registrationRepository.findById("reg-uuid-001"))
                    .thenReturn(Optional.of(registration));
            when(eventRepository.findById(1L)).thenReturn(Optional.of(event));

            RegistrationStatusResponse mockResponse = RegistrationStatusResponse.builder()
                    .registrationId("reg-uuid-001")
                    .registrationNumber("EVT2026-000006")
                    .status(RegistrationStatus.PAID)
                    .build();
            when(registrationService.toStatusResponse(any(), any())).thenReturn(mockResponse);

            RegistrationStatusResponse result =
                    paymentService.processPaymentCaptured("order_test123", "pay_test456", null);

            assertThat(result.getStatus()).isEqualTo(RegistrationStatus.PAID);
            assertThat(result.getRegistrationNumber()).isEqualTo("EVT2026-000006");

            // Verify no save was called (idempotent)
            verify(eventRepository, never()).save(any());
        }

        @Test
        @DisplayName("should return idempotent result when registration already PAID")
        void shouldBeIdempotentWhenRegistrationPaid() {
            registration.setStatus(RegistrationStatus.PAID);
            registration.setRegistrationNumber("EVT2026-000006");

            when(paymentRepository.findByRazorpayOrderId("order_test123"))
                    .thenReturn(Optional.of(payment));
            when(registrationRepository.findByIdWithLock("reg-uuid-001"))
                    .thenReturn(Optional.of(registration));
            when(eventRepository.findById(1L)).thenReturn(Optional.of(event));

            RegistrationStatusResponse mockResponse = RegistrationStatusResponse.builder()
                    .status(RegistrationStatus.PAID)
                    .registrationNumber("EVT2026-000006")
                    .build();
            when(registrationService.toStatusResponse(any(), any())).thenReturn(mockResponse);

            RegistrationStatusResponse result =
                    paymentService.processPaymentCaptured("order_test123", "pay_test456", null);

            assertThat(result.getStatus()).isEqualTo(RegistrationStatus.PAID);
            verify(eventRepository, never()).save(any());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Registration number generation
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Registration number generation")
    class RegistrationNumberGeneration {

        @Test
        @DisplayName("should generate EVT2026-000006 when registration count is 5")
        void shouldGenerateCorrectRegistrationNumber() {
            event.setRegistrationCount(5); // next will be 6

            when(paymentRepository.findByRazorpayOrderId("order_test123"))
                    .thenReturn(Optional.of(payment));
            when(registrationRepository.findByIdWithLock("reg-uuid-001"))
                    .thenReturn(Optional.of(registration));
            lenient().when(eventRepository.findById(1L)).thenReturn(Optional.of(event));
            lenient().when(eventRepository.findByIdWithLock(1L))
                    .thenReturn(Optional.of(event));
            when(paymentRepository.save(any())).thenReturn(payment);
            when(registrationRepository.save(any())).thenReturn(registration);
            when(eventRepository.save(any())).thenReturn(event);
            when(registrationService.toStatusResponse(any(), any()))
                    .thenAnswer(inv -> {
                        Registration r = inv.getArgument(0);
                        return RegistrationStatusResponse.builder()
                                .registrationNumber(r.getRegistrationNumber())
                                .status(r.getStatus())
                                .build();
                    });

            paymentService.processPaymentCaptured("order_test123", "pay_test456", null);

            // Capture the registration that was saved
            verify(registrationRepository).save(argThat(r ->
                    "EVT2026-000006".equals(r.getRegistrationNumber()) &&
                    r.getStatus() == RegistrationStatus.PAID
            ));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Payment failed webhook
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("handlePaymentFailed")
    class PaymentFailed {

        @Test
        @DisplayName("should mark payment and registration as FAILED")
        void shouldMarkAsFailed() {
            when(paymentRepository.findByRazorpayOrderId("order_test123"))
                    .thenReturn(Optional.of(payment));
            when(registrationRepository.findByIdWithLock("reg-uuid-001"))
                    .thenReturn(Optional.of(registration));
            when(paymentRepository.save(any())).thenReturn(payment);
            when(registrationRepository.save(any())).thenReturn(registration);

            paymentService.handlePaymentFailed("order_test123");

            verify(paymentRepository).save(argThat(p -> p.getStatus() == PaymentStatus.FAILED));
            verify(registrationRepository).save(argThat(r -> r.getStatus() == RegistrationStatus.PAYMENT_FAILED));
        }

        @Test
        @DisplayName("should NOT mark as failed when payment is already CAPTURED")
        void shouldNotOverrideCapture() {
            payment.setStatus(PaymentStatus.CAPTURED);
            when(paymentRepository.findByRazorpayOrderId("order_test123"))
                    .thenReturn(Optional.of(payment));

            paymentService.handlePaymentFailed("order_test123");

            verify(paymentRepository, never()).save(any());
            verify(registrationRepository, never()).save(any());
        }
    }
}
