-- V1: Initial Schema for Sindhu Young Champions 2026

CREATE TABLE IF NOT EXISTS users (
    id          BIGINT          NOT NULL AUTO_INCREMENT,
    name        VARCHAR(255)                            COMMENT 'Full name of the user',
    email       VARCHAR(255)                            COMMENT 'Email address',
    phone       VARCHAR(50)                             COMMENT 'Contact phone number',
    created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS events (
    id                      BIGINT          NOT NULL AUTO_INCREMENT,
    event_code              VARCHAR(50)     NOT NULL UNIQUE,
    event_name              VARCHAR(255)    NOT NULL,
    description             TEXT,
    event_date              DATE            NOT NULL,
    registration_closes_at  DATETIME        NOT NULL,
    is_active               BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at              DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS event_categories (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    event_id        BIGINT          NOT NULL,
    category_name   VARCHAR(50)     NOT NULL,
    min_age         INT             NOT NULL,
    max_age         INT             NOT NULL,
    fee             DECIMAL(10, 2)  NOT NULL,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    FOREIGN KEY (event_id) REFERENCES events(id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS registrations (
    id                          CHAR(36)        NOT NULL,
    event_id                    BIGINT          NOT NULL,
    category_id                 BIGINT          NOT NULL,
    registration_number         VARCHAR(50)     UNIQUE,
    
    full_name                   VARCHAR(100)    NOT NULL,
    dob                         DATE            NOT NULL,
    gender                      VARCHAR(10)     NOT NULL,
    school_name                 VARCHAR(255)    NOT NULL,
    t_shirt_size                VARCHAR(10)     NOT NULL,
    
    mobile_number               VARCHAR(15)     NOT NULL,
    email_id                    VARCHAR(150)    NOT NULL,
    
    address                     VARCHAR(500)    NOT NULL,
    city                        VARCHAR(100)    NOT NULL,
    district                    VARCHAR(100)    NOT NULL,
    state                       VARCHAR(100)    NOT NULL,
    pin_code                    VARCHAR(10)     NOT NULL,
    
    emergency_contact_name      VARCHAR(100)    NOT NULL,
    emergency_contact_number    VARCHAR(15)     NOT NULL,
    
    document_s3_key             VARCHAR(500)    NOT NULL,
    
    consent_parent              BOOLEAN         NOT NULL DEFAULT FALSE,
    consent_info                BOOLEAN         NOT NULL DEFAULT FALSE,
    consent_fit                 BOOLEAN         NOT NULL DEFAULT FALSE,
    consent_terms               BOOLEAN         NOT NULL DEFAULT FALSE,
    consent_media               BOOLEAN         NOT NULL DEFAULT FALSE,
    
    calculated_age              INT             NOT NULL,
    calculated_fee              DECIMAL(10, 2)  NOT NULL,
    
    status                      VARCHAR(30)     NOT NULL DEFAULT 'PENDING_PAYMENT',
    razorpay_order_id           VARCHAR(100)    NOT NULL UNIQUE,
    
    created_at                  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    PRIMARY KEY (id),
    FOREIGN KEY (event_id) REFERENCES events(id),
    FOREIGN KEY (category_id) REFERENCES event_categories(id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS payments (
    id                      BIGINT          NOT NULL AUTO_INCREMENT,
    registration_id         CHAR(36)        NOT NULL,
    razorpay_payment_id     VARCHAR(100),
    razorpay_order_id       VARCHAR(100)    NOT NULL,
    razorpay_signature      VARCHAR(255),
    amount                  DECIMAL(10, 2)  NOT NULL,
    status                  VARCHAR(30)     NOT NULL,
    error_code              VARCHAR(50),
    error_description       VARCHAR(500),
    created_at              DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    FOREIGN KEY (registration_id) REFERENCES registrations(id),
    UNIQUE KEY uk_rzp_payment_id (razorpay_payment_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
