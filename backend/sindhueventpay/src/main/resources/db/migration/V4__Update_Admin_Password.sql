-- V4: Fix the admin password hash to match "admin123"

UPDATE users 
SET password = '$2a$10$sHcB4H5p8JxNM3jBUZsh9.opJ1XlPlnOOgziq29kdgQx1aqOviKPW' 
WHERE email = 'admin@sindhurunners.com';
