-- V3: Create payments table
-- One payment record per Razorpay order.
-- razorpay_payment_id is NULL until the payment is actually captured.
-- The UNIQUE constraint on razorpay_payment_id guarantees that a payment event
-- is processed exactly once even if the webhook is delivered multiple times.

CREATE TABLE IF NOT EXISTS payments (
    id                      BIGINT          NOT NULL AUTO_INCREMENT,
    registration_id         VARCHAR(50)         NOT NULL                COMMENT 'FK → registrations.id',
    razorpay_order_id       VARCHAR(100)    NOT NULL                COMMENT 'Razorpay order_id; matches registrations.razorpay_order_id',
    razorpay_payment_id     VARCHAR(100)    DEFAULT NULL            COMMENT 'Razorpay pay_xxx; set only after payment captured. NULL initially.',
    razorpay_signature      VARCHAR(512)    DEFAULT NULL            COMMENT 'HMAC signature from Razorpay for audit trail',
    amount                  DECIMAL(10, 2)  NOT NULL                COMMENT 'Amount in INR (full rupees)',
    currency                VARCHAR(10)     NOT NULL DEFAULT 'INR',
    status                  VARCHAR(20)     NOT NULL DEFAULT 'CREATED'
                                                                    COMMENT 'CREATED | AUTHORIZED | CAPTURED | FAILED | REFUNDED',
    payment_method          VARCHAR(50)     DEFAULT NULL            COMMENT 'e.g. card, upi, netbanking',
    paid_at                 DATETIME        DEFAULT NULL            COMMENT 'Timestamp of payment capture',
    created_at              DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

    -- One Razorpay order maps to exactly one payment record
    CONSTRAINT uk_payments_razorpay_order UNIQUE (razorpay_order_id),

    -- razorpay_payment_id is globally unique when present (NULL allowed multiple times)
    CONSTRAINT uk_payments_razorpay_payment UNIQUE (razorpay_payment_id),

    CONSTRAINT fk_payments_registration
        FOREIGN KEY (registration_id) REFERENCES registrations (id)
        ON DELETE RESTRICT

) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
