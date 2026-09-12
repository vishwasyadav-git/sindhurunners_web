-- V3: Add password and role to users table, and seed initial admin

ALTER TABLE users 
ADD COLUMN password VARCHAR(255) NULL AFTER phone,
ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'USER' AFTER password;

-- Seed an initial admin user
-- Password is 'admin123' hashed with bcrypt (strength 10)
INSERT INTO users (name, email, phone, password, role)
VALUES ('Admin User', 'admin@sindhurunners.com', '9999999999', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIvi', 'ADMIN');
