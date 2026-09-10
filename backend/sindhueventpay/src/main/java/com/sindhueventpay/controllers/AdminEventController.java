package com.sindhueventpay.controllers;

import com.sindhueventpay.dto.AdminEventRequest;
import com.sindhueventpay.dto.AdminEventResponse;
import com.sindhueventpay.dto.ApiResponse;
import com.sindhueventpay.services.AdminEventService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/events")
@RequiredArgsConstructor
public class AdminEventController {

    private final AdminEventService adminEventService;

    @PostMapping
    public ResponseEntity<ApiResponse<AdminEventResponse>> createEvent(
            @Valid @RequestBody AdminEventRequest request) {
        AdminEventResponse response = adminEventService.createEvent(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<AdminEventResponse>>> getAllEvents() {
        List<AdminEventResponse> response = adminEventService.getAllEvents();
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AdminEventResponse>> getEventById(@PathVariable Long id) {
        AdminEventResponse response = adminEventService.getEventById(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<AdminEventResponse>> updateEvent(
            @PathVariable Long id,
            @Valid @RequestBody AdminEventRequest request) {
        AdminEventResponse response = adminEventService.updateEvent(id, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteEvent(@PathVariable Long id) {
        adminEventService.deleteEvent(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
