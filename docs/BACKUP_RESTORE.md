# Backup and restore

A new .omega backup encrypts a ZIP payload with AES-256-GCM. Its key is derived from the operator's passphrase with PBKDF2-HMAC-SHA256 and a random salt. The payload contains:

- `database.sqlite` after a full WAL checkpoint;
- `settings.json`;
- `metadata.json` with backup/app/schema versions, date, school name and database size.

Creation succeeds only after reopening, decrypting and validating the selected URI. Restore accepts both encrypted .omega files and legacy unencrypted ZIP files. It checks required metadata and the SQLite header, extracts into app cache, and creates an encrypted pre-restore safety file. It then closes Room, replaces only the resolved app database file, removes that database's WAL/SHM sidecars, restores settings and restarts the process.

Keep the passphrase and backups on more than one physical device. The passphrase is never stored by the app, so losing it makes encrypted backups unrecoverable. Do not unplug removable storage while the progress indicator is visible. Legacy ZIP backups contain unencrypted student data and should be replaced with encrypted backups.
