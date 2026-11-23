-- Production readiness: admin TOTP sirlari AES-256-GCM bilan shifrlanadi ("enc:v1:" + base64). Shifrmatn uzunroq.
ALTER TABLE admins ALTER COLUMN totp_secret TYPE VARCHAR(255);
