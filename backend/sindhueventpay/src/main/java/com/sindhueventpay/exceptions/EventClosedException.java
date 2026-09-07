package com.sindhueventpay.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when an attempt is made to register for an event that is not currently
 * accepting registrations (registrationOpen == false or event dates have passed).
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class EventClosedException extends RuntimeException {
    public EventClosedException(String eventCode) {
        super("Registration for event '" + eventCode + "' is currently closed.");
    }
}
