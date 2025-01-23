package com.sindhueventpay.services;

import com.sindhueventpay.Repository.EventRepository;
import com.sindhueventpay.Repository.RegistrationRepository;
import com.sindhueventpay.Repository.UserRepository;
import com.sindhueventpay.exceptions.ResourceNotFoundException;
import com.sindhueventpay.models.Event;
import com.sindhueventpay.models.Registration;
import com.sindhueventpay.models.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RegistrationService {
    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private RegistrationRepository registrationRepository;

    public List<Event> getAllEvents() {
        return eventRepository.findAll();
    }

    public Registration registerUserForEvent(Long userId, Long eventId, String paymentStatus) {
        // Validate User
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        // Validate Event
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with id: " + eventId));

        // Register the User for the Event
        Registration registration = new Registration();
        registration.setUserId(user.getId());
        registration.setEventId(event.getId());
        registration.setPaymentStatus(paymentStatus);

        return registrationRepository.save(registration);
    }
}

