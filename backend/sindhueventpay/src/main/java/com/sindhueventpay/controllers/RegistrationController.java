package com.sindhueventpay.controllers;

import com.sindhueventpay.dto.ApiResponse;
import com.sindhueventpay.dto.RegistrationResponse;
import com.sindhueventpay.dto.RegistrationStatusResponse;
import com.sindhueventpay.services.RegistrationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * REST controller for registration endpoints.
 *
 * <p>Base path: {@code /api/v1}
 *
 * <h3>Endpoints:</h3>
 * <ul>
 *   <li>{@code POST /api/v1/events/{eventCode}/registrations} — create registration + Razorpay order</li>
 *   <li>{@code GET  /api/v1/registrations/{registrationId}}  — poll registration status</li>
 * </ul>
 *
 * <p>The registration creation endpoint accepts {@code multipart/form-data} because
 * it includes both form fields and the Aadhaar document file upload.
 */
@RestController
@RequestMapping("/api/v1")
@Slf4j
public class RegistrationController {

    @Autowired
    private RegistrationService registrationService;

    // ─────────────────────────────────────────────────────────────────────────
    // POST /api/v1/events/{eventCode}/registrations
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Creates a new registration for an event.
     *
     * <p>Accepts a multipart form with:
     * <ul>
     *   <li>{@code fullName}         — registrant's full name (2–100 chars)</li>
     *   <li>{@code email}            — valid email address</li>
     *   <li>{@code mobileNumber}     — 10-digit Indian mobile number</li>
     *   <li>{@code aadhaarDocument}  — JPG, PNG, or PDF file, max 5 MB</li>
     * </ul>
     *
     * <p>On success, returns Razorpay order details for Checkout integration.
     * The registration is in {@code PENDING_PAYMENT} status at this point.
     *
     * @param eventCode       event identifier from the URL
     * @param fullName        registrant's full name
     * @param email           registrant's email
     * @param mobileNumber    registrant's mobile number
     * @param aadhaarDocument Aadhaar document file
     * @return 201 Created with Razorpay order details
     */
    @PostMapping(
            value = "/events/{eventCode}/registrations",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<ApiResponse<RegistrationResponse>> createRegistration(
            @PathVariable String eventCode,
            @RequestParam String fullName,
            @RequestParam String email,
            @RequestParam String mobileNumber,
            @RequestPart("aadhaarDocument") MultipartFile aadhaarDocument) {

        log.info("Registration request received. eventCode=[{}]", eventCode);

        RegistrationResponse response = registrationService.initiateRegistration(
                eventCode, fullName, email, mobileNumber, aadhaarDocument);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/v1/registrations/{registrationId}
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns the current status of a registration by its internal UUID.
     *
     * <p>Used by the frontend to poll for status after payment completes,
     * or to show the success page details.
     *
     * @param registrationId UUID of the registration
     * @return registration status including registrationNumber if PAID
     */
    @GetMapping("/registrations/{registrationId}")
    public ResponseEntity<ApiResponse<RegistrationStatusResponse>> getRegistrationStatus(
            @PathVariable String registrationId) {

        RegistrationStatusResponse response =
                registrationService.getRegistrationStatus(registrationId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
