package com.sindhueventpay.Repository;

import com.sindhueventpay.models.Event;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventRepository extends JpaRepository<Event, Long> {
    Event save(Event event);

    Event findByEventName(String eventName);
}
