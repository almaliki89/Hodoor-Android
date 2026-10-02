# Testing

Host-side tests cover attendance threshold policy, school-day calculation, PIN hashing, stable status values, import issue mapping and 100,000-record aggregation. Android instrumentation tests create an in-memory Room database and verify unique constraints, student deletion, class edit propagation and deletion protection.

Routine checks:

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug
```

Device checks:

```powershell
adb -s emulator-5554 shell am instrument -w com.omega.attendance.test/androidx.test.runner.AndroidJUnitRunner
```

The debug settings screen can populate 1,000 fake students and 100,000 attendance records on an empty database. Measure QR lookup, search, dashboard, daily/monthly reports, PDF/XLSX, backup and restore. The generator intentionally refuses to run when students already exist. Avoid Gradle's connected test task on a device whose app data must be preserved; some test flows reinstall the app.

Camera, vibration, printing, USB removal, storage exhaustion, process restart after restore and sustained rapid scanning require physical-device testing and cannot be certified by JVM tests.
