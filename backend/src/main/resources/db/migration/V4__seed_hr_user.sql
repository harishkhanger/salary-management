-- V4: the single seeded HR user (session-based auth; no roles by design).
-- Password is BCrypt-hashed; the plaintext lives in the README run instructions.
INSERT INTO hr_users (username, password_hash, name)
VALUES ('hr', '$2a$10$Dhuz9biZcATSWK8fpX7nMOPKJ99zvVliftXAHkdEV/mEzuPBuF1s.', 'HR Manager');
