package com.sindhueventpay.services;

import com.razorpay.Order;
import com.sindhueventpay.Repository.EventRepository;
import com.sindhueventpay.Repository.PaymentRepository;
import com.sindhueventpay.Repository.RegistrationRepository;
import com.sindhueventpay.config.AppProperties;
import com.sindhueventpay.dto.RegistrationResponse;
import com.sindhueventpay.enums.RegistrationStatus;
import com.sindhueventpay.exceptions.EventClosedException;
import com.sindhueventpay.exceptions.EventFullException;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link RegistrationService}.
 *
 * <p>Uses Mockito to isolate the service from all external dependencies
 * (DB, S3, Razorpay). Tests focus on business logic, validation, and
 * error handling.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RegistrationService")
class RegistrationServiceTest {

    @Mock EventService eventService;
    @Mock EventRepository eventRepository;
    @Mock RegistrationRepository registrationRepository;
    @Mock PaymentRepository paymentRepository;
    @Mock S3Service s3Service;
    @Mock RazorpayService razorpayService;
    @Mock AppProperties appProperties;
    @Mock AppProperties.Razorpay razorpayProps;

    @InjectMocks RegistrationService registrationService;

    private Event openEvent;

    @BeforeEach
    void setUp() {
        openEvent = new Event();
        openEvent.setId(1L);
        openEvent.setEventCode("EVT2026");
        openEvent.setEventName("Goa to Sawantwadi Run 2026");
        openEvent.setRegistrationFee(new BigDecimal("500.00"));
        openEvent.setCurrency("INR");
        openEvent.setMaxRegistrations(1000);
        openEvent.setRegistrationCount(50);
        openEvent.setRegistrationOpen(true);

        lenient().when(appProperties.getRazorpay()).thenReturn(razorpayProps);
        lenient().when(razorpayProps.getKeyId()).thenReturn("rzp_test_key123");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Successful registration
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Successful registration")
    class SuccessfulRegistration {

        @Test
        @DisplayName("should create registration, upload to S3, create Razorpay order and payment record")
        void shouldCreateCompleteRegistration() throws Exception {
            // Arrange
            when(eventService.validateAndGetEvent("EVT2026")).thenReturn(openEvent);

            Registration savedReg = new Registration();
            savedReg.setId("test-uuid-1234");
            savedReg.setEventId(1L);
            savedReg.setFullName("Prasad Korgaonkar");
            savedReg.setEmail("prasad@example.com");
            savedReg.setMobileNumber("9876543210");
            savedReg.setStatus(RegistrationStatus.PENDING_PAYMENT);

            when(registrationRepository.save(any())).thenReturn(savedReg);
            when(s3Service.uploadAadhaarDocument(any(), any(), any()))
                    .thenReturn("events/EVT2026/registrations/test-uuid-1234/aadhaar/document.jpg");

            Order mockOrder = mock(Order.class);
            when(mockOrder.get("id")).thenReturn("order_test123");
            when(razorpayService.createOrder(any(), anyLong(), any())).thenReturn(mockOrder);
            when(paymentRepository.save(any())).thenReturn(new Payment());

            MultipartFile file = new MockMultipartFile(
                    "aadhaarDocument", "aadhaar.jpg", "image/jpeg", new byte[1024]);

            // Act
            RegistrationResponse response = registrationService.initiateRegistration(
                    "EVT2026", "Prasad Korgaonkar", "prasad@example.com", "9876543210", file);

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.getRegistrationId()).isEqualTo("test-uuid-1234");
            assertThat(response.getRazorpayOrderId()).isEqualTo("order_test123");
            assertThat(response.getRazorpayKeyId()).isEqualTo("rzp_test_key123");
            assertThat(response.getAmountInPaise()).isEqualTo(50_000L);
            assertThat(response.getCurrency()).isEqualTo("INR");

            verify(s3Service).uploadAadhaarDocument(eq(file), eq("EVT2026"), eq("test-uuid-1234"));
            verify(paymentRepository).save(any(Payment.class));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Event validation
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Event validation")
    class EventValidation {

        @Test
        @DisplayName("should throw EventClosedException when registration is closed")
        void shouldThrowWhenEventClosed() {
            when(eventService.validateAndGetEvent("EVT2026"))
                    .thenThrow(new EventClosedException("EVT2026"));

            MultipartFile file = new MockMultipartFile("f", "a.jpg", "image/jpeg", new byte[1]);

            assertThatThrownBy(() ->
                    registrationService.initiateRegistration(
                            "EVT2026", "Test User", "test@email.com", "9876543210", file))
                    .isInstanceOf(EventClosedException.class);
        }

        @Test
        @DisplayName("should throw EventFullException when event is at capacity")
        void shouldThrowWhenEventFull() {
            when(eventService.validateAndGetEvent("EVT2026"))
                    .thenThrow(new EventFullException("EVT2026"));

            MultipartFile file = new MockMultipartFile("f", "a.jpg", "image/jpeg", new byte[1]);

            assertThatThrownBy(() ->
                    registrationService.initiateRegistration(
                            "EVT2026", "Test User", "test@email.com", "9876543210", file))
                    .isInstanceOf(EventFullException.class);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Input validation
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Input validation")
    class InputValidation {

        private final MultipartFile validFile =
                new MockMultipartFile("f", "a.jpg", "image/jpeg", new byte[1024]);

        @BeforeEach
        void arrangeEvent() {
            lenient().when(eventService.validateAndGetEvent("EVT2026")).thenReturn(openEvent);
            Registration stub = new Registration();
            stub.setId("stub-id");
            lenient().when(registrationRepository.save(any())).thenReturn(stub);
        }

        @Test
        @DisplayName("should reject blank full name")
        void shouldRejectBlankName() {
            assertThatThrownBy(() ->
                    registrationService.initiateRegistration(
                            "EVT2026", "  ", "a@b.com", "9876543210", validFile))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Full name");
        }

        @Test
        @DisplayName("should reject name shorter than 2 characters")
        void shouldRejectShortName() {
            assertThatThrownBy(() ->
                    registrationService.initiateRegistration(
                            "EVT2026", "A", "a@b.com", "9876543210", validFile))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("should reject invalid email")
        void shouldRejectInvalidEmail() {
            assertThatThrownBy(() ->
                    registrationService.initiateRegistration(
                            "EVT2026", "Valid Name", "not-an-email", "9876543210", validFile))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("email");
        }

        @Test
        @DisplayName("should reject mobile number not starting with 6-9")
        void shouldRejectInvalidMobile() {
            assertThatThrownBy(() ->
                    registrationService.initiateRegistration(
                            "EVT2026", "Valid Name", "a@b.com", "1234567890", validFile))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("mobile");
        }

        @Test
        @DisplayName("should reject mobile number shorter than 10 digits")
        void shouldRejectShortMobile() {
            assertThatThrownBy(() ->
                    registrationService.initiateRegistration(
                            "EVT2026", "Valid Name", "a@b.com", "987654321", validFile))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // S3 failure cleanup
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("S3 failure handling")
    class S3FailureHandling {

        @Test
        @DisplayName("should propagate S3UploadException without leaving orphaned records")
        void shouldPropagateS3Failure() {
            when(eventService.validateAndGetEvent("EVT2026")).thenReturn(openEvent);
            Registration stub = new Registration();
            stub.setId("stub-id");
            when(registrationRepository.save(any())).thenReturn(stub);
            when(s3Service.uploadAadhaarDocument(any(), any(), any()))
                    .thenThrow(new com.sindhueventpay.exceptions.S3UploadException("S3 error", new RuntimeException()));

            MultipartFile file = new MockMultipartFile("f", "a.jpg", "image/jpeg", new byte[1024]);

            assertThatThrownBy(() ->
                    registrationService.initiateRegistration(
                            "EVT2026", "Test User", "test@email.com", "9876543210", file))
                    .isInstanceOf(com.sindhueventpay.exceptions.S3UploadException.class);

            verify(razorpayService, never()).createOrder(any(), anyLong(), any());
        }
    }
}
