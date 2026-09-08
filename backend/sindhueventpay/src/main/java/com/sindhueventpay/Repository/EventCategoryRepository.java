package com.sindhueventpay.Repository;

import com.sindhueventpay.models.EventCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EventCategoryRepository extends JpaRepository<EventCategory, Long> {
    List<EventCategory> findByEventId(Long eventId);
}
