package com.sindhueventpay.controllers;

import com.sindhueventpay.dto.ApiResponse;
import com.sindhueventpay.dto.RegistrationRequestDto;
import com.sindhueventpay.dto.RegistrationResponse;
import com.sindhueventpay.dto.RegistrationStatusResponse;
import com.sindhueventpay.services.RegistrationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@Slf4j
public class RegistrationController {

    @Autowired
    private RegistrationService registrationService;

    @PostMapping(
            value = "/events/{eventCode}/registrations",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<ApiResponse<RegistrationResponse>> createRegistration(
            @PathVariable String eventCode,
            @ModelAttribute RegistrationRequestDto request) {

        log.info("Registration request received. eventCode=[{}]", eventCode);

        RegistrationResponse response = registrationService.initiateRegistration(eventCode, request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    @GetMapping("/registrations/{registrationId}")
    public ResponseEntity<ApiResponse<RegistrationStatusResponse>> getRegistrationStatus(
            @PathVariable String registrationId) {

        RegistrationStatusResponse response =
                registrationService.getRegistrationStatus(registrationId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
