# Database

Schema version: 1. Exported Room schema is stored under `app/schemas` after compilation.

Core tables are `students`, `academic_years`, `grades`, `classes`, `sections`, `halls`, `attendance`, `school_calendar`, `app_settings`, and `audit_logs`.

Important integrity rules:

- `students.uuid`, `studentNumber`, and `qrValue` are unique.
- `(studentId, attendanceDate, academicYearId)` is unique, providing the final duplicate barrier.
- Attendance stores name/number/classification snapshots so later student moves do not rewrite history.
- Foreign keys use restrict for historical parents and cascade only for calendar rows owned by an academic year.
- Lookup/report columns are indexed.
- Production configuration never calls destructive migration fallback.
- Student hard deletion is an explicit transaction: remove attendance first, then the student, then write an audit entry. Archiving is the non-destructive alternative.
- Class deletion is restricted while students reference it. Hall deletion clears related hall references. Deleting an unused academic year removes its classes, grades, sections and calendar rows in one transaction.
- Default halls and classes are marked as seeded so deleting one does not recreate it at next launch.

Dates are epoch days, arrival time is seconds from local midnight, and absolute event times are epoch milliseconds. Large reports aggregate in SQLite rather than loading raw history for calculation.

Any future schema change must add an explicit `Migration` and a migration test before increasing the database version.
