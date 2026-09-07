package com.sindhueventpay.controllers;

import com.sindhueventpay.dto.ApiResponse;
import com.sindhueventpay.dto.EventResponse;
import com.sindhueventpay.services.EventService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

/**
 * REST controller for event information endpoints.
 *
 * <p>Base path: {@code /api/v1/events}
 *
 * <p>Existing endpoint {@code POST /api/events} (admin event creation) is
 * intentionally preserved in this class for backward compatibility.
 * All new endpoints follow the {@code /api/v1/} convention.
 */
@RestController
@RequestMapping("/api/v1/events")
public class EventController {

    @Autowired
    private EventService eventService;

    /**
     * Returns a list of all events currently open for registration.
     *
     * @return list of event responses
     */
    @GetMapping
    public ResponseEntity<ApiResponse<java.util.List<EventResponse>>> getAllEvents() {
        java.util.List<EventResponse> response = eventService.getAllOpenEvents();
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Returns event details by event code.
     *
     * <p>Used by the registration page to display event info and check availability.
     *
     * @param eventCode short event identifier, e.g. {@code EVT2026}
     * @return event details including fee, dates, available slots, and open status
     */
    @GetMapping("/{eventCode}")
    public ResponseEntity<ApiResponse<EventResponse>> getEvent(@PathVariable String eventCode) {
        EventResponse response = eventService.getEventByCode(eventCode);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Creates a new event.
     *
     * @param request event creation details
     * @return created event response
     */
    @PostMapping
    public ResponseEntity<ApiResponse<EventResponse>> createEvent(@Valid @RequestBody com.sindhueventpay.dto.EventCreateRequest request) {
        EventResponse response = eventService.createEvent(request);
        return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }
}
