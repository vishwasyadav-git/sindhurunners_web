package com.sindhueventpay.services;

import com.sindhueventpay.Repository.EventCategoryRepository;
import com.sindhueventpay.Repository.EventRepository;
import com.sindhueventpay.dto.EventCategoryResponse;
import com.sindhueventpay.dto.EventResponse;
import com.sindhueventpay.exceptions.EventClosedException;
import com.sindhueventpay.exceptions.ResourceNotFoundException;
import com.sindhueventpay.dto.EventCreateRequest;
import com.sindhueventpay.models.Event;
import com.sindhueventpay.models.EventCategory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class EventService {

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private EventCategoryRepository eventCategoryRepository;

    public EventResponse getEventByCode(String eventCode) {
        Event event = findEventOrThrow(eventCode);
        List<EventCategory> categories = eventCategoryRepository.findByEventId(event.getId());
        return toResponse(event, categories);
    }

    public Event validateAndGetEvent(String eventCode) {
        Event event = findEventOrThrow(eventCode);

        if (!event.isActive()) {
            throw new EventClosedException(eventCode);
        }

        return event;
    }

    public List<EventResponse> getAllOpenEvents() {
        return eventRepository.findByIsActiveTrue()
                .stream()
                .map(event -> {
                    List<EventCategory> categories = eventCategoryRepository.findByEventId(event.getId());
                    return toResponse(event, categories);
                })
                .collect(Collectors.toList());
    }

    @Transactional
    public EventResponse createEvent(EventCreateRequest request) {
        if (eventRepository.findByEventCode(request.getEventCode()).isPresent()) {
            throw new IllegalArgumentException("Event code already exists: " + request.getEventCode());
        }

        Event event = new Event();
        event.setEventCode(request.getEventCode());
        event.setEventName(request.getEventName());
        event.setDescription(request.getDescription());
        event.setEventDate(request.getStartDate()); // From old code
        event.setRegistrationClosesAt(request.getEndDate() != null ? request.getEndDate().atTime(23, 59) : null);
        event.setActive(request.isRegistrationOpen());

        Event savedEvent = eventRepository.save(event);
        return toResponse(savedEvent, List.of());
    }

    private Event findEventOrThrow(String eventCode) {
        return eventRepository.findByEventCode(eventCode)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with code: " + eventCode));
    }

    public EventResponse toResponse(Event event, List<EventCategory> categories) {
        List<EventCategoryResponse> categoryResponses = categories.stream()
                .map(cat -> EventCategoryResponse.builder()
                        .id(cat.getId())
                        .categoryName(cat.getCategoryName())
                        .minAge(cat.getMinAge())
                        .maxAge(cat.getMaxAge())
                        .fee(cat.getFee())
                        .build())
                .collect(Collectors.toList());

        return EventResponse.builder()
                .eventCode(event.getEventCode())
                .eventName(event.getEventName())
                .description(event.getDescription())
                .eventDate(event.getEventDate())
                .registrationClosesAt(event.getRegistrationClosesAt())
                .isActive(event.isActive())
                .categories(categoryResponses)
                .build();
    }
}
