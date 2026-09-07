-- V1: Create events table
-- Stores all running events organised by Sindhu Runners.
-- event_code is the human-readable identifier used in URLs (e.g. EVT2026).
-- registration_count is incremented atomically inside a transaction on payment success.
-- max_registrations enforces capacity; registration_open is a manual on/off switch.

CREATE TABLE IF NOT EXISTS events (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    event_code          VARCHAR(50)     NOT NULL                COMMENT 'Short unique code e.g. EVT2026; used in API URLs',
    event_name          VARCHAR(255)    NOT NULL                COMMENT 'Display name of the event',
    description         TEXT                                    COMMENT 'Full description shown on the registration page',
    registration_fee    DECIMAL(10, 2)  NOT NULL                COMMENT 'Fee in INR (full rupees, not paise)',
    currency            VARCHAR(10)     NOT NULL DEFAULT 'INR'  COMMENT 'ISO currency code',
    max_registrations   INT             NOT NULL                COMMENT 'Hard cap on total allowed registrations',
    registration_count  INT             NOT NULL DEFAULT 0      COMMENT 'Current confirmed (PAID) registrations; updated atomically',
    registration_open   TINYINT(1)      NOT NULL DEFAULT 1      COMMENT '1 = accepting registrations, 0 = closed',
    start_date          DATE                                    COMMENT 'Event start date',
    end_date            DATE                                    COMMENT 'Event end date',
    created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

    -- event_code must be globally unique; also used as URL path segment
    CONSTRAINT uk_events_event_code UNIQUE (event_code)

) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
