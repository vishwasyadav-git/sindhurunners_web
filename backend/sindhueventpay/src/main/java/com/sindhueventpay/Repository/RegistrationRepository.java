package com.sindhueventpay.Repository;

import com.sindhueventpay.enums.RegistrationStatus;
import com.sindhueventpay.models.Registration;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * Repository for the {@link Registration} entity.
 *
 * <p>The PK type is {@code String} (UUID stored as CHAR 36).
 *
 * <p>{@code findByIdWithLock} acquires a pessimistic write lock on the registration
 * row during payment processing to prevent concurrent modifications (e.g., webhook
 * and frontend callback racing to mark the same registration PAID).
 */
public interface RegistrationRepository extends JpaRepository<Registration, String> {

    /**
     * Find registration by Razorpay order ID.
     * Used in webhook processing where only the order ID is available.
     */
    Optional<Registration> findByRazorpayOrderId(String razorpayOrderId);

    /**
     * Find registration by ID with a pessimistic write lock.
     * Must be called inside an active {@code @Transactional} method.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Registration r WHERE r.id = :id")
    Optional<Registration> findByIdWithLock(@Param("id") String id);

    /**
     * Check if a PAID registration already exists for the given email on the given event.
     * Useful as a soft duplicate check (email + event combination).
     */
    boolean existsByEmailIdAndEventIdAndStatus(String emailId, Long eventId, RegistrationStatus status);

    /** Count PAID registrations for an event (admin dashboard). */
    long countByEventIdAndStatus(Long eventId, RegistrationStatus status);
}
