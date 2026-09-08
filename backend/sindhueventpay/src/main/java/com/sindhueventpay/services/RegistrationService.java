package com.sindhueventpay.services;

import com.razorpay.Order;
import com.sindhueventpay.Repository.EventCategoryRepository;
import com.sindhueventpay.Repository.EventRepository;
import com.sindhueventpay.Repository.PaymentRepository;
import com.sindhueventpay.Repository.RegistrationRepository;
import com.sindhueventpay.config.AppProperties;
import com.sindhueventpay.dto.RegistrationRequestDto;
import com.sindhueventpay.dto.RegistrationResponse;
import com.sindhueventpay.dto.RegistrationStatusResponse;
import com.sindhueventpay.enums.PaymentStatus;
import com.sindhueventpay.enums.RegistrationStatus;
import com.sindhueventpay.exceptions.ResourceNotFoundException;
import com.sindhueventpay.models.Event;
import com.sindhueventpay.models.EventCategory;
import com.sindhueventpay.models.Payment;
import com.sindhueventpay.models.Registration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Period;
import java.util.List;

@Service
@Slf4j
public class RegistrationService {

    @Autowired
    private EventService eventService;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private EventCategoryRepository eventCategoryRepository;

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

    @Transactional
    public RegistrationResponse initiateRegistration(String eventCode, RegistrationRequestDto request) {
        Event event = eventService.validateAndGetEvent(eventCode);

        // Calculate age
        int calculatedAge = Period.between(request.getDob(), event.getEventDate()).getYears();

        // Find Category
        List<EventCategory> categories = eventCategoryRepository.findByEventId(event.getId());
        EventCategory matchedCategory = categories.stream()
                .filter(c -> calculatedAge >= c.getMinAge() && calculatedAge <= c.getMaxAge())
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No eligible category found for age: " + calculatedAge));

        // Create UUID
        Registration registration = new Registration();
        registration.setId(java.util.UUID.randomUUID().toString());
        String registrationId = registration.getId();

        // Upload to S3
        String s3Key = s3Service.uploadAadhaarDocument(
                request.getAadhaarDocument(), eventCode, request.getMobileNumber(), request.getFullName());

        // Set all fields
        registration.setEventId(event.getId());
        registration.setCategoryId(matchedCategory.getId());
        registration.setFullName(request.getFullName().trim());
        registration.setDob(request.getDob());
        registration.setGender(request.getGender());
        registration.setSchoolName(request.getSchoolName());
        registration.setTShirtSize(request.getTShirtSize());
        registration.setMobileNumber(request.getMobileNumber().trim());
        registration.setEmailId(request.getEmailId().trim().toLowerCase());
        registration.setAddress(request.getAddress());
        registration.setCity(request.getCity());
        registration.setDistrict(request.getDistrict());
        registration.setState(request.getState());
        registration.setPinCode(request.getPinCode());
        registration.setEmergencyContactName(request.getEmergencyContactName());
        registration.setEmergencyContactNumber(request.getEmergencyContactNumber());
        registration.setDocumentS3Key(s3Key);
        
        registration.setConsentParent(request.isConsentParent());
        registration.setConsentInfo(request.isConsentInfo());
        registration.setConsentFit(request.isConsentFit());
        registration.setConsentTerms(request.isConsentTerms());
        registration.setConsentMedia(request.isConsentMedia());

        registration.setCalculatedAge(calculatedAge);
        registration.setCalculatedFee(matchedCategory.getFee());
        registration.setStatus(RegistrationStatus.PENDING_PAYMENT);

        // Razorpay Order
        long amountInPaise = RazorpayService.toPaise(matchedCategory.getFee());
        Order razorpayOrder;
        try {
            razorpayOrder = razorpayService.createOrder(registrationId, amountInPaise, "INR");
        } catch (Exception e) {
            throw e;
        }

        String razorpayOrderId = razorpayOrder.get("id");
        registration.setRazorpayOrderId(razorpayOrderId);
        registrationRepository.save(registration);

        // Payment record
        Payment payment = new Payment();
        payment.setRegistrationId(registrationId);
        payment.setRazorpayOrderId(razorpayOrderId);
        payment.setAmount(matchedCategory.getFee());
        payment.setStatus(PaymentStatus.CREATED);
        paymentRepository.save(payment);

        return RegistrationResponse.builder()
                .registrationId(registrationId)
                .razorpayOrderId(razorpayOrderId)
                .razorpayKeyId(appProperties.getRazorpay().getKeyId())
                .amountInPaise(amountInPaise)
                .currency("INR")
                .eventName(event.getEventName())
                .fullName(registration.getFullName())
                .email(registration.getEmailId())
                .description("Sindhu Runners — " + event.getEventName())
                .build();
    }

    public RegistrationStatusResponse getRegistrationStatus(String registrationId) {
        Registration reg = registrationRepository.findById(registrationId)
                .orElseThrow(() -> new ResourceNotFoundException("Registration not found"));

        Event event = eventRepository.findById(reg.getEventId())
                .orElseThrow(() -> new ResourceNotFoundException("Event not found"));

        EventCategory category = eventCategoryRepository.findById(reg.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));

        return toStatusResponse(reg, event, category);
    }

    public RegistrationStatusResponse toStatusResponse(Registration reg, Event event, EventCategory category) {
        return RegistrationStatusResponse.builder()
                .registrationId(reg.getId())
                .registrationNumber(reg.getRegistrationNumber())
                .status(reg.getStatus())
                .eventName(event.getEventName())
                .eventCode(event.getEventCode())
                .fullName(reg.getFullName())
                .email(reg.getEmailId())
                .categoryName(category.getCategoryName())
                .amount(reg.getCalculatedFee())
                .currency("INR")
                .build();
    }
}
