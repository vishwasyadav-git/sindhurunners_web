package com.sindhueventpay.services;

import com.razorpay.Order;
import com.sindhueventpay.Repository.EventRepository;
import com.sindhueventpay.Repository.PaymentRepository;
import com.sindhueventpay.Repository.RegistrationRepository;
import com.sindhueventpay.config.AppProperties;
import com.sindhueventpay.dto.RegistrationResponse;
import com.sindhueventpay.dto.RegistrationStatusResponse;
import com.sindhueventpay.enums.PaymentStatus;
import com.sindhueventpay.enums.RegistrationStatus;
import com.sindhueventpay.exceptions.ResourceNotFoundException;
import com.sindhueventpay.models.Event;
import com.sindhueventpay.models.Payment;
import com.sindhueventpay.models.Registration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Core registration orchestration service.
 *
 * <h3>Registration creation flow (initiateRegistration):</h3>
 * <ol>
 *   <li>Validate event (open, not full).</li>
 *   <li>Validate input fields.</li>
 *   <li>Upload Aadhaar document to S3 (UUID-keyed, private bucket).</li>
 *   <li>Create {@link Registration} record (status = PENDING_PAYMENT).</li>
 *   <li>Create Razorpay order.</li>
 *   <li>Create {@link Payment} record (status = CREATED).</li>
 *   <li>Return Razorpay order details to frontend.</li>
 * </ol>
 *
 * <h3>Idempotency:</h3>
 * The registration creation is NOT idempotent by default. A double-click on the
 * Register button will create two separate PENDING_PAYMENT registrations (each with
 * its own Razorpay order). Frontend should disable the button after the first click.
 *
 * <h3>Concurrency:</h3>
 * The event capacity check at step 1 is a soft check (non-locked read). The hard
 * guarantee against overbooking is enforced in {@link PaymentService#processPaymentCaptured}
 * which locks the event row before incrementing the counter.
 */
@Service
@Slf4j
public class RegistrationService {

    @Autowired
    private EventService eventService;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private RegistrationRepository registrationRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private S3Service s3Service;

    @Autowired
    private RazorpayService razorpayService;

    @Autowired
    private AppProperties appProperties;

    // ─────────────────────────────────────────────────────────────────────────
    // Registration initiation
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Initiates registration for an event.
     *
     * <p>S3 upload is performed BEFORE the DB transaction to avoid holding
     * a DB connection open during a potentially slow S3 API call. If the S3
     * upload fails, no DB record is created and the user should retry.
     *
     * @param eventCode       the event code from the URL path
     * @param fullName        registrant's full name
     * @param email           registrant's email
     * @param mobileNumber    registrant's mobile number
     * @param aadhaarDocument the Aadhaar document file
     * @return DTO containing Razorpay order details for Checkout
     */
    @Transactional
    public RegistrationResponse initiateRegistration(String eventCode,
                                                     String fullName,
                                                     String email,
                                                     String mobileNumber,
                                                     MultipartFile aadhaarDocument) {
        // 1. Validate event: throws EventClosedException / EventFullException
        Event event = eventService.validateAndGetEvent(eventCode);

        // 2. Validate input
        validateInput(fullName, email, mobileNumber);

        // 3. Generate UUID for registration (used as S3 key segment and Razorpay receipt)
        Registration registration = new Registration();
        registration.setId(java.util.UUID.randomUUID().toString());
        registration.setEventId(event.getId());
        registration.setFullName(fullName.trim());
        registration.setEmail(email.trim().toLowerCase());
        registration.setMobileNumber(mobileNumber.trim());
        registration.setStatus(RegistrationStatus.PENDING_PAYMENT);

        String registrationId = registration.getId();

        log.info("Registration initiated. id=[{}] eventCode=[{}] email=[REDACTED]",
                registrationId, eventCode);

        // 4. Upload Aadhaar document to S3 (throws S3UploadException on failure)
        String s3Key = s3Service.uploadAadhaarDocument(aadhaarDocument, eventCode, registrationId);
        registration.setAadhaarS3Key(s3Key);

        // 5. Create Razorpay order
        long amountInPaise = RazorpayService.toPaise(event.getRegistrationFee());
        Order razorpayOrder;
        try {
            razorpayOrder = razorpayService.createOrder(registrationId, amountInPaise, event.getCurrency());
        } catch (Exception e) {
            // Razorpay order failed — clean up the S3 document
//            s3Service.deleteDocument(s3Key);
            throw e;
        }

        String razorpayOrderId = razorpayOrder.get("id");

        // 6. Update registration with Razorpay order ID
        registration.setRazorpayOrderId(razorpayOrderId);
        registrationRepository.save(registration);

        // 7. Create payment record
        Payment payment = new Payment();
        payment.setRegistrationId(registrationId);
        payment.setRazorpayOrderId(razorpayOrderId);
        payment.setAmount(event.getRegistrationFee());
        payment.setCurrency(event.getCurrency());
        payment.setStatus(PaymentStatus.CREATED);
        paymentRepository.save(payment);

        log.info("Payment record created. registrationId=[{}] razorpayOrderId=[{}]",
                registrationId, razorpayOrderId);

        // 8. Build and return response for frontend Checkout
        return RegistrationResponse.builder()
                .registrationId(registrationId)
                .razorpayOrderId(razorpayOrderId)
                .razorpayKeyId(appProperties.getRazorpay().getKeyId())
                .amountInPaise(amountInPaise)
                .currency(event.getCurrency())
                .eventName(event.getEventName())
                .fullName(registration.getFullName())
                .email(registration.getEmail())
                .description("Sindhu Runners — " + event.getEventName())
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Status query
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns current registration status by internal UUID.
     *
     * @param registrationId the UUID of the registration
     * @return status response DTO
     * @throws ResourceNotFoundException if not found
     */
    public RegistrationStatusResponse getRegistrationStatus(String registrationId) {
        Registration reg = registrationRepository.findById(registrationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Registration not found: " + registrationId));

        Event event = eventRepository.findById(reg.getEventId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Event not found for registration: " + registrationId));

        return toStatusResponse(reg, event);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Shared mapper
    // ─────────────────────────────────────────────────────────────────────────

    public RegistrationStatusResponse toStatusResponse(Registration reg, Event event) {
        return RegistrationStatusResponse.builder()
                .registrationId(reg.getId())
                .registrationNumber(reg.getRegistrationNumber())
                .status(reg.getStatus())
                .eventName(event.getEventName())
                .eventCode(event.getEventCode())
                .fullName(reg.getFullName())
                .email(reg.getEmail())
                .amount(event.getRegistrationFee())
                .currency(event.getCurrency())
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Input validation
    // ─────────────────────────────────────────────────────────────────────────

    private void validateInput(String fullName, String email, String mobileNumber) {
        if (fullName == null || fullName.isBlank() || fullName.trim().length() < 2) {
            throw new IllegalArgumentException("Full name must be at least 2 characters.");
        }
        if (fullName.trim().length() > 100) {
            throw new IllegalArgumentException("Full name must not exceed 100 characters.");
        }
        if (email == null || !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new IllegalArgumentException("A valid email address is required.");
        }
        if (email.length() > 150) {
            throw new IllegalArgumentException("Email must not exceed 150 characters.");
        }
        if (mobileNumber == null || !mobileNumber.matches("^[6-9]\\d{9}$")) {
            throw new IllegalArgumentException(
                    "A valid 10-digit Indian mobile number starting with 6–9 is required.");
        }
    }
}
