-- V4: Add performance indexes
-- All UNIQUE constraints from V2/V3 are already indexed by MySQL.
-- These are additional non-unique indexes to speed up common query patterns.

-- registrations.event_id: used in every query that lists registrations for an event
CREATE INDEX idx_registrations_event_id   ON registrations (event_id);

-- registrations.status: used to count PAID / PENDING_PAYMENT registrations
CREATE INDEX idx_registrations_status     ON registrations (status);

-- registrations.email: used for duplicate-check / lookup by registrant email
CREATE INDEX idx_registrations_email      ON registrations (email);

-- registrations.mobile_number: used for lookup by mobile
CREATE INDEX idx_registrations_mobile     ON registrations (mobile_number);

-- registrations.created_at: used for admin dashboards sorted by time
CREATE INDEX idx_registrations_created_at ON registrations (created_at);

-- payments.registration_id: used to fetch all payments for a registration
CREATE INDEX idx_payments_registration_id ON payments (registration_id);

-- payments.status: used to filter CAPTURED / FAILED payments
CREATE INDEX idx_payments_status          ON payments (status);
