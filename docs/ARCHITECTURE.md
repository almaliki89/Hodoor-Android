# Architecture

The app is a single offline Android module using Kotlin, Compose, Material 3, Hilt, Room, Coroutines/Flow and DataStore. Presentation code observes immutable UI state from ViewModels. Repositories and use cases own business operations; Compose screens do not write directly to Room.

Data flow:

```text
Compose -> ViewModel -> Use case / Repository -> Room or DataStore
CameraX -> ML Kit QR-only analyzer -> RegisterAttendanceUseCase -> Room transaction
Room Flow -> ViewModel state -> Compose
```

Room is the durable business source of truth. DataStore stores small preferences and salted PIN material. All imports, exports, backups and report generation run off the UI thread. Hilt supplies the database, DAOs, repositories and services.

The navigation UI uses a bottom bar on phones and a rail at 700dp or wider. Arabic RTL is applied at the activity root. Operator mode excludes reports/settings and makes student search read-only.
