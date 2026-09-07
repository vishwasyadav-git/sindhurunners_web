-- V2: Create registrations table
-- Internal primary key is a UUID (CHAR 36) — never expose raw sequential IDs.
-- registration_number (e.g. EVT2026-000042) is generated ONLY after payment is verified (PAID).
-- aadhaar_s3_key stores the S3 object key — never a public URL.
-- razorpay_order_id is set at creation (PENDING_PAYMENT) and must be unique.

CREATE TABLE IF NOT EXISTS registrations (
    id                  VARCHAR(36)        NOT NULL                COMMENT 'Internal UUID primary key; safe to share in URLs but not meaningful to users',
    event_id            BIGINT          NOT NULL                COMMENT 'FK → events.id',
    registration_number VARCHAR(50)     DEFAULT NULL            COMMENT 'Human-readable event-specific ID, e.g. EVT2026-000042. NULL until PAID.',
    full_name           VARCHAR(100)    NOT NULL                COMMENT 'Registrant full name',
    email               VARCHAR(150)    NOT NULL                COMMENT 'Registrant email',
    mobile_number       VARCHAR(15)     NOT NULL                COMMENT 'Registrant mobile (10-digit Indian format)',
    status              VARCHAR(30)     NOT NULL DEFAULT 'PENDING_PAYMENT'
                                                                COMMENT 'PENDING_PAYMENT | PAYMENT_PROCESSING | PAID | PAYMENT_FAILED | CANCELLED',
    aadhaar_s3_key      VARCHAR(500)    DEFAULT NULL            COMMENT 'S3 object key for Aadhaar document. NULL until uploaded. Never a public URL.',
    razorpay_order_id   VARCHAR(100)    NOT NULL                COMMENT 'Razorpay order_id set at registration creation',
    created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

    -- registration_number must be unique when non-null. MySQL permits multiple NULLs in UNIQUE columns.
    CONSTRAINT uk_registrations_number UNIQUE (registration_number),

    -- One Razorpay order maps to exactly one registration
    CONSTRAINT uk_registrations_razorpay_order UNIQUE (razorpay_order_id),

    CONSTRAINT fk_registrations_event
        FOREIGN KEY (event_id) REFERENCES events (id)
        ON DELETE RESTRICT

) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
