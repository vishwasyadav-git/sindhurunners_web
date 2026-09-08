package com.sindhueventpay.Repository;

import com.sindhueventpay.models.Event;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * Repository for the {@link Event} entity.
 *
 * <p>{@code findByEventCodeWithLock} is used inside the payment-success transaction
 * to acquire a pessimistic write lock on the event row before incrementing
 * {@code registrationCount}. This prevents two concurrent transactions from
 * generating the same registration number or double-counting.
 */
public interface EventRepository extends JpaRepository<Event, Long> {

    /** Find event by its short human-readable code (e.g. {@code EVT2026}). */
    Optional<Event> findByEventCode(String eventCode);

    /** Find all events that are currently active. */
    java.util.List<Event> findByIsActiveTrue();

    /**
     * Find event by code with a pessimistic write lock (SELECT … FOR UPDATE).
     *
     * <p>Call this inside a {@code @Transactional} method ONLY.
     * The lock is held until the enclosing transaction commits or rolls back.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM Event e WHERE e.eventCode = :eventCode")
    Optional<Event> findByEventCodeWithLock(@Param("eventCode") String eventCode);

    /**
     * Find event by primary key with a pessimistic write lock.
     * Used in the payment-success transaction to lock the event row before
     * incrementing {@code registrationCount}.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM Event e WHERE e.id = :id")
    Optional<Event> findByIdWithLock(@Param("id") Long id);
}
