package com.sindhueventpay.models;

import com.sindhueventpay.enums.RegistrationStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "registrations")
@Getter
@Setter
public class Registration {

    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false, columnDefinition = "CHAR(36)")
    private String id;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Column(name = "registration_number", length = 50, unique = true)
    private String registrationNumber;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Column(name = "dob", nullable = false)
    private LocalDate dob;

    @Column(name = "gender", nullable = false, length = 10)
    private String gender;

    @Column(name = "school_name", nullable = false, length = 255)
    private String schoolName;

    @Column(name = "t_shirt_size", nullable = false, length = 10)
    private String tShirtSize;

    @Column(name = "mobile_number", nullable = false, length = 15)
    private String mobileNumber;

    @Column(name = "email_id", nullable = false, length = 150)
    private String emailId;

    @Column(name = "address", nullable = false, length = 500)
    private String address;

    @Column(name = "city", nullable = false, length = 100)
    private String city;

    @Column(name = "district", nullable = false, length = 100)
    private String district;

    @Column(name = "state", nullable = false, length = 100)
    private String state;

    @Column(name = "pin_code", nullable = false, length = 10)
    private String pinCode;

    @Column(name = "emergency_contact_name", nullable = false, length = 100)
    private String emergencyContactName;

    @Column(name = "emergency_contact_number", nullable = false, length = 15)
    private String emergencyContactNumber;

    @Column(name = "document_s3_key", nullable = false, length = 500)
    private String documentS3Key;

    @Column(name = "consent_parent", nullable = false, columnDefinition = "BOOLEAN")
    private boolean consentParent;

    @Column(name = "consent_info", nullable = false, columnDefinition = "BOOLEAN")
    private boolean consentInfo;

    @Column(name = "consent_fit", nullable = false, columnDefinition = "BOOLEAN")
    private boolean consentFit;

    @Column(name = "consent_terms", nullable = false, columnDefinition = "BOOLEAN")
    private boolean consentTerms;

    @Column(name = "consent_media", nullable = false, columnDefinition = "BOOLEAN")
    private boolean consentMedia;

    @Column(name = "calculated_age", nullable = false)
    private int calculatedAge;

    @Column(name = "calculated_fee", nullable = false, precision = 10, scale = 2)
    private BigDecimal calculatedFee;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private RegistrationStatus status = RegistrationStatus.PENDING_PAYMENT;

    @Column(name = "razorpay_order_id", nullable = false, length = 100, unique = true)
    private String razorpayOrderId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    private void generateId() {
        if (this.id == null) {
            this.id = UUID.randomUUID().toString();
        }
    }
}
