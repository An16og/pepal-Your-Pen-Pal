-- V6: Add user_name column to settings for personalized greeting
ALTER TABLE settings ADD COLUMN IF NOT EXISTS user_name VARCHAR(100) DEFAULT NULL;
