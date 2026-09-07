package com.sindhueventpay.services;

import com.sindhueventpay.Repository.EventRepository;
import com.sindhueventpay.dto.EventResponse;
import com.sindhueventpay.exceptions.EventClosedException;
import com.sindhueventpay.exceptions.EventFullException;
import com.sindhueventpay.exceptions.ResourceNotFoundException;
import com.sindhueventpay.models.Event;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Service for event lookup and validation.
 *
 * <p>Read operations do not need a transaction. Write operations (incrementing
 * registration count) are performed in {@link RegistrationService} which owns
 * the transaction boundary.
 */
@Service
public class EventService {

    @Autowired
    private EventRepository eventRepository;

    /**
     * Retrieves event details by event code for the registration page.
     *
     * @param eventCode case-sensitive event code, e.g. {@code EVT2026}
     * @return event response DTO
     * @throws ResourceNotFoundException if no event with the given code exists
     */
    public EventResponse getEventByCode(String eventCode) {
        Event event = findEventOrThrow(eventCode);
        return toResponse(event);
    }

    /**
     * Validates that registration for the event is currently possible.
     * Throws a specific domain exception for each failure case.
     *
     * @param eventCode event code
     * @return the Event entity (loaded from DB for use by the caller)
     * @throws ResourceNotFoundException if event not found
     * @throws EventClosedException      if registration is closed
     * @throws EventFullException        if max registrations reached
     */
    public Event validateAndGetEvent(String eventCode) {
        Event event = findEventOrThrow(eventCode);

        if (!event.isRegistrationOpen()) {
            throw new EventClosedException(eventCode);
        }

        if (event.getRegistrationCount() >= event.getMaxRegistrations()) {
            throw new EventFullException(eventCode);
        }

        return event;
    }

    // ─────────────────────────────────────────────────────────────────────────

    private Event findEventOrThrow(String eventCode) {
        return eventRepository.findByEventCode(eventCode)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Event not found with code: " + eventCode));
    }

    public EventResponse toResponse(Event event) {
        return EventResponse.builder()
                .eventCode(event.getEventCode())
                .eventName(event.getEventName())
                .description(event.getDescription())
                .registrationFee(event.getRegistrationFee())
                .currency(event.getCurrency())
                .startDate(event.getStartDate())
                .endDate(event.getEndDate())
                .registrationOpen(event.isRegistrationOpen())
                .availableSlots(event.getMaxRegistrations() - event.getRegistrationCount())
                .build();
    }
}
