package com.sindhueventpay.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * JPA entity for the {@code events} table.
 *
 * <p>{@code registrationCount} is the single source of truth for how many
 * PAID registrations exist for this event. It is incremented atomically
 * inside a DB transaction using a pessimistic write lock on this row —
 * see {@code RegistrationService.confirmPayment}.
 *
 * <p>This entity replaces the skeletal {@code Event} that previously only had
 * {@code id}, {@code eventName}, {@code date}, and {@code registrationFee}.
 * The table is created fresh via Flyway (V1 migration).
 */
@Entity
@Table(name = "events")
@Getter
@Setter
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Short unique code used in API URLs, e.g. {@code EVT2026}. */
    @Column(name = "event_code", nullable = false, unique = true, length = 50)
    private String eventCode;

    @Column(name = "event_name", nullable = false, length = 255)
    private String eventName;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /** Registration fee in full INR (not paise). Stored as DECIMAL(10,2). */
    @Column(name = "registration_fee", nullable = false, precision = 10, scale = 2)
    private BigDecimal registrationFee;

    @Column(name = "currency", nullable = false, length = 10)
    private String currency = "INR";

    /** Hard cap on total registrations for this event. */
    @Column(name = "max_registrations", nullable = false)
    private int maxRegistrations;

    /**
     * Current count of PAID registrations.
     * Incremented atomically under a pessimistic write lock.
     * Never update this field outside a locked transaction.
     */
    @Column(name = "registration_count", nullable = false)
    private int registrationCount = 0;

    /** Manual switch: {@code true} = accepting registrations, {@code false} = closed. */
    @Column(name = "registration_open", nullable = false)
    private boolean registrationOpen = true;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
