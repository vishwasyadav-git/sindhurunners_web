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
    private final com.sindhueventpay.services.AdminDashboardService adminDashboardService;

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

    @GetMapping("/{id}/stats")
    public ResponseEntity<ApiResponse<com.sindhueventpay.dto.RegistrationStatsResponse>> getEventStats(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(adminDashboardService.getEventStats(id)));
    }

    @GetMapping("/{id}/registrations")
    public ResponseEntity<ApiResponse<org.springframework.data.domain.Page<com.sindhueventpay.dto.AdminRegistrationResponse>>> getRegistrations(
            @PathVariable Long id,
            @RequestParam(required = false) String gender,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) com.sindhueventpay.enums.RegistrationStatus status,
            org.springframework.data.domain.Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(adminDashboardService.getRegistrations(id, gender, categoryId, status, pageable)));
    }

    @GetMapping("/{id}/export")
    public ResponseEntity<byte[]> exportRegistrationsCsv(
            @PathVariable Long id,
            @RequestParam(required = false) String gender,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) com.sindhueventpay.enums.RegistrationStatus status) {
        
        String csvData = adminDashboardService.generateCsvExport(id, gender, categoryId, status);
        byte[] csvBytes = csvData.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        
        // Add UTF-8 BOM so Excel opens it correctly without mangling characters
        byte[] bom = new byte[] { (byte) 0xEF, (byte) 0xBB, (byte) 0xBF };
        byte[] finalBytes = new byte[bom.length + csvBytes.length];
        System.arraycopy(bom, 0, finalBytes, 0, bom.length);
        System.arraycopy(csvBytes, 0, finalBytes, bom.length, csvBytes.length);

        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.set(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=registrations_event_" + id + ".csv");
        headers.set(org.springframework.http.HttpHeaders.CONTENT_TYPE, "text/csv; charset=UTF-8");

        return ResponseEntity.ok()
                .headers(headers)
                .body(finalBytes);
    }
}
