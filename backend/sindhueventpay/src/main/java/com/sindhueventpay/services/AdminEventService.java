package com.sindhueventpay.services;

import com.sindhueventpay.Repository.EventCategoryRepository;
import com.sindhueventpay.Repository.EventRepository;
import com.sindhueventpay.dto.AdminEventCategoryRequest;
import com.sindhueventpay.dto.AdminEventCategoryResponse;
import com.sindhueventpay.dto.AdminEventRequest;
import com.sindhueventpay.dto.AdminEventResponse;
import com.sindhueventpay.exceptions.ResourceNotFoundException;
import com.sindhueventpay.models.Event;
import com.sindhueventpay.models.EventCategory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminEventService {

    private final EventRepository eventRepository;
    private final EventCategoryRepository eventCategoryRepository;

    @Transactional
    public AdminEventResponse createEvent(AdminEventRequest request) {
        if (eventRepository.existsByEventCode(request.getEventCode())) {
            throw new IllegalArgumentException("Event code already exists: " + request.getEventCode());
        }

        Event event = new Event();
        event.setEventCode(request.getEventCode());
        event.setEventName(request.getEventName());
        event.setDescription(request.getDescription());
        event.setEventDate(request.getEventDate());
        event.setRegistrationClosesAt(request.getRegistrationClosesAt());
        event.setActive(request.isActive());

        Event savedEvent = eventRepository.save(event);

        List<EventCategory> categories = request.getCategories().stream().map(req -> {
            EventCategory category = new EventCategory();
            category.setEventId(savedEvent.getId());
            category.setCategoryName(req.getCategoryName());
            category.setMinAge(req.getMinAge());
            category.setMaxAge(req.getMaxAge());
            category.setFee(req.getFee());
            return category;
        }).collect(Collectors.toList());

        eventCategoryRepository.saveAll(categories);

        return buildResponse(savedEvent, categories);
    }

    @Transactional(readOnly = true)
    public List<AdminEventResponse> getAllEvents() {
        return eventRepository.findAll().stream().map(event -> {
            List<EventCategory> categories = eventCategoryRepository.findByEventId(event.getId());
            return buildResponse(event, categories);
        }).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public AdminEventResponse getEventById(Long id) {
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with ID: " + id));
        List<EventCategory> categories = eventCategoryRepository.findByEventId(event.getId());
        return buildResponse(event, categories);
    }

    @Transactional
    public AdminEventResponse updateEvent(Long id, AdminEventRequest request) {
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with ID: " + id));

        if (!event.getEventCode().equals(request.getEventCode()) &&
                eventRepository.existsByEventCode(request.getEventCode())) {
            throw new IllegalArgumentException("Event code already exists: " + request.getEventCode());
        }

        event.setEventCode(request.getEventCode());
        event.setEventName(request.getEventName());
        event.setDescription(request.getDescription());
        event.setEventDate(request.getEventDate());
        event.setRegistrationClosesAt(request.getRegistrationClosesAt());
        event.setActive(request.isActive());

        Event savedEvent = eventRepository.save(event);

        // Category sync
        List<EventCategory> existingCategories = eventCategoryRepository.findByEventId(savedEvent.getId());
        Map<Long, EventCategory> existingCategoryMap = existingCategories.stream()
                .collect(Collectors.toMap(EventCategory::getId, c -> c));

        List<EventCategory> toSave = new java.util.ArrayList<>();
        
        for (AdminEventCategoryRequest catReq : request.getCategories()) {
            if (catReq.getId() != null && existingCategoryMap.containsKey(catReq.getId())) {
                // Update existing
                EventCategory existing = existingCategoryMap.get(catReq.getId());
                existing.setCategoryName(catReq.getCategoryName());
                existing.setMinAge(catReq.getMinAge());
                existing.setMaxAge(catReq.getMaxAge());
                existing.setFee(catReq.getFee());
                toSave.add(existing);
                existingCategoryMap.remove(catReq.getId()); // Remove from map to track deletions
            } else {
                // Add new
                EventCategory newCategory = new EventCategory();
                newCategory.setEventId(savedEvent.getId());
                newCategory.setCategoryName(catReq.getCategoryName());
                newCategory.setMinAge(catReq.getMinAge());
                newCategory.setMaxAge(catReq.getMaxAge());
                newCategory.setFee(catReq.getFee());
                toSave.add(newCategory);
            }
        }

        // The remaining entries in existingCategoryMap are omitted from request, thus deleted
        if (!existingCategoryMap.isEmpty()) {
            eventCategoryRepository.deleteAll(existingCategoryMap.values());
        }

        eventCategoryRepository.saveAll(toSave);

        return buildResponse(savedEvent, toSave);
    }

    @Transactional
    public void deleteEvent(Long id) {
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with ID: " + id));
        
        // Soft delete
        event.setActive(false);
        eventRepository.save(event);
        log.info("Soft-deleted event with ID: {}", id);
    }

    private AdminEventResponse buildResponse(Event event, List<EventCategory> categories) {
        List<AdminEventCategoryResponse> categoryResponses = categories.stream()
                .map(c -> AdminEventCategoryResponse.builder()
                        .id(c.getId())
                        .categoryName(c.getCategoryName())
                        .minAge(c.getMinAge())
                        .maxAge(c.getMaxAge())
                        .fee(c.getFee())
                        .build())
                .collect(Collectors.toList());

        return AdminEventResponse.builder()
                .id(event.getId())
                .eventCode(event.getEventCode())
                .eventName(event.getEventName())
                .description(event.getDescription())
                .eventDate(event.getEventDate())
                .registrationClosesAt(event.getRegistrationClosesAt())
                .isActive(event.isActive())
                .createdAt(event.getCreatedAt())
                .updatedAt(event.getUpdatedAt())
                .categories(categoryResponses)
                .build();
    }
}
