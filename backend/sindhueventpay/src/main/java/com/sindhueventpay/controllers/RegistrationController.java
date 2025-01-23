package com.sindhueventpay.controllers;

import com.sindhueventpay.Repository.UserRepository;
import com.sindhueventpay.models.Event;
import com.sindhueventpay.models.User;
import com.sindhueventpay.services.RegistrationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;



@RestController
@RequestMapping("/api")
public class RegistrationController {
    @Autowired
    private RegistrationService registrationService;

    @Autowired
    private UserRepository userRepository;

    @GetMapping("/events")
    public List<Event> getAllEvents() {
        return registrationService.getAllEvents();
    }

    @PostMapping("/register")
    public ResponseEntity<User> registerUser(@RequestBody User user) {
        User savedUser = userRepository.save(user);
        return ResponseEntity.ok(savedUser);
    }

    @PostMapping("/register-event")
    public ResponseEntity<String> registerForEvent(@RequestParam Long userId,
                                                   @RequestParam Long eventId,
                                                   @RequestParam String paymentStatus) {
        registrationService.registerUserForEvent(userId, eventId, paymentStatus);
        return ResponseEntity.ok("Registration successful");
    }
}

