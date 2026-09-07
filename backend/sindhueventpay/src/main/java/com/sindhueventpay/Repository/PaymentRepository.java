package com.sindhueventpay.Repository;

import com.sindhueventpay.models.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Repository for the {@link Payment} entity.
 *
 * <p>The UNIQUE constraints on {@code razorpayOrderId} and {@code razorpayPaymentId}
 * are enforced at both the DB schema level (V3 migration) and via the entity's
 * {@code @Column(unique = true)} annotation.
 *
 * <p>Webhook idempotency relies on the fact that an attempt to save a payment
 * with a duplicate {@code razorpayPaymentId} will throw
 * {@link org.springframework.dao.DataIntegrityViolationException}, which is
 * caught in {@link com.sindhueventpay.services.PaymentService} and treated as
 * an idempotent success.
 */
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    /**
     * Find payment record by Razorpay order ID.
     * Used in both the verify endpoint and webhook handler.
     */
    Optional<Payment> findByRazorpayOrderId(String razorpayOrderId);

    /**
     * Find payment by Razorpay payment ID (pay_xxx).
     * Used to detect duplicate payment events (idempotency check).
     */
    Optional<Payment> findByRazorpayPaymentId(String razorpayPaymentId);
}
