package com.sindhueventpay.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when the requested event has reached its maximum registration capacity.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class EventFullException extends RuntimeException {
    public EventFullException(String eventCode) {
        super("Event '" + eventCode + "' has reached maximum registration capacity.");
    }
}
