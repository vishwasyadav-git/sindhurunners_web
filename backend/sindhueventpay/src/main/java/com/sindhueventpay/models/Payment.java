package com.sindhueventpay.models;

import com.sindhueventpay.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * JPA entity for the {@code payments} table.
 *
 * <p>One payment record is created when a Razorpay order is created (status = CREATED).
 * It is updated to CAPTURED when either the frontend callback or the Razorpay webhook
 * confirms successful payment.
 *
 * <p><strong>Idempotency guarantee:</strong> the {@code razorpayPaymentId} column has
 * a UNIQUE constraint in the DB schema. If the same Razorpay payment success event
 * (from frontend or webhook) arrives more than once, the second attempt will either
 * find the record already CAPTURED (early return) or fail with a unique constraint
 * violation (caught and treated as idempotent success).
 */
@Entity
@Table(name = "payments")
@Getter
@Setter
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** FK to the registration this payment belongs to. */
    @Column(name = "registration_id", nullable = false, length = 36)
    private String registrationId;

    /** Razorpay {@code order_id}, e.g. {@code order_xxx}. Set at creation. */
    @Column(name = "razorpay_order_id", nullable = false, unique = true, length = 100)
    private String razorpayOrderId;

    /**
     * Razorpay {@code payment_id}, e.g. {@code pay_xxx}.
     * Null until the payment is captured. Has a UNIQUE constraint.
     * MySQL allows multiple NULLs in a UNIQUE column.
     */
    @Column(name = "razorpay_payment_id", unique = true, length = 100)
    private String razorpayPaymentId;

    /**
     * Razorpay HMAC signature — stored for audit purposes.
     * Never log this value in plain text.
     */
    @Column(name = "razorpay_signature", length = 512)
    private String razorpaySignature;

    /** Amount in INR (full rupees). Same as {@code Event.registrationFee}. */
    @Column(name = "amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 10)
    private String currency = "INR";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PaymentStatus status = PaymentStatus.CREATED;

    /** Payment method reported by Razorpay, e.g. {@code card}, {@code upi}. */
    @Column(name = "payment_method", length = 50)
    private String paymentMethod;

    /** Timestamp when the payment was captured (null until CAPTURED). */
    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
