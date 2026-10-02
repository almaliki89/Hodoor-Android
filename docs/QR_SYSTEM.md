# QR system

Cards contain only `STD:<random UUID>`. Names, phones and class data are never encoded.

The analyzer requests QR format only and uses `STRATEGY_KEEP_ONLY_LATEST`. Each image is closed on task completion. The ViewModel serializes registrations and resets feedback after 900ms. The database transaction performs lookup, duplicate check, snapshot resolution, late policy and insert. Even concurrent scans are stopped by the unique attendance index; an ignored insert is converted to a duplicate result.

Scanner outcomes are ready, processing, success, late, duplicate, unknown and error. The scanner remains active after each outcome. The screen stays awake only while the scanner composable is active.

Physical validation still requires a real device test under entrance lighting, including damaged cards, prolonged card presentation, rapid different cards and camera permission denial.
