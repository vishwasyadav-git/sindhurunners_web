-- V2: Seed Data for Sindhu Young Champions 2026

INSERT INTO events (event_code, event_name, description, event_date, registration_closes_at, is_active)
VALUES ('SYC2026', 'Sindhu Young Champions 2026 – 3rd Edition', 'Sindhu Young Champions 2026 is a youth running event organized by Sindhu Runners.', '2026-12-27', '2026-11-30 23:59:59', TRUE);

SET @event_id = LAST_INSERT_ID();

INSERT INTO event_categories (event_id, category_name, min_age, max_age, fee) VALUES
(@event_id, '1.5 KM', 7, 9, 500.00),
(@event_id, '3 KM', 10, 12, 600.00),
(@event_id, '5 KM', 13, 15, 700.00),
(@event_id, '10 KM', 16, 19, 800.00);
