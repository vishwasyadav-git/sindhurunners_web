package com.sindhueventpay.controllers;

import com.sindhueventpay.dto.ApiResponse;
import com.sindhueventpay.dto.EventResponse;
import com.sindhueventpay.services.EventService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
}
