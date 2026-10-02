# Security and privacy

The app has no Internet permission and sends no student data to a server. QR cards carry random lookup tokens only. Production code avoids logging names, phones, PINs and QR values.

Admin PINs are stored as PBKDF2-HMAC-SHA256 outputs with a random 24-byte salt and 120,000 iterations; the raw PIN is never persisted. Sensitive modifications are written to `audit_logs`. Operator mode is deliberately limited to dashboard, scanner, today's attendance and read-only student lookup.

Backups and exported reports leave the app only when an administrator selects a destination or share target. Android device encryption, a device screen lock and controlled physical access remain required operational safeguards.
