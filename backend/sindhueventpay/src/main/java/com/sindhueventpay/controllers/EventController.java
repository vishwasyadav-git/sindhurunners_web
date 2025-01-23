package com.sindhueventpay.controllers;

import com.sindhueventpay.Repository.EventRepository;
import com.sindhueventpay.exceptions.ResourceNotFoundException;
import com.sindhueventpay.models.Event;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/events")
public class EventController {
    @Autowired
    private EventRepository eventRepository;

    @PostMapping
    public ResponseEntity<Event> createEvent(@RequestBody Event event) {
        Event savedEvent = eventRepository.save(event);
        return ResponseEntity.status(HttpStatus.CREATED).body(savedEvent);
    }

//    @GetMapping
//    public List<Event> getAllEvents() {
//        return eventRepository.findAll();
//    }

    @GetMapping("/{id}")
    public ResponseEntity<Event> getEventById(@PathVariable Long id) {
        return eventRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with id: " + id));
    }
}

