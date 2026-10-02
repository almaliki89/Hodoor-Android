# Reporting

Daily reports use a left join from active students to attendance, so absent students appear without creating explicit absence rows. Status filtering remains in SQL. Range reports group counts by student in SQLite.

Weekly, monthly and custom reports calculate absence from school days minus present, late and excused counts. Friday and Saturday are weekends by default; explicit `school_calendar` entries override this for school days, exams, holidays and closures.

PDF uses Android `PdfDocument` and `StaticLayout` with RTL text direction for shaped Arabic text. Landscape pages include the school name, Arabic title, date range, summary, repeated table header, alternating row color, generation time and page numbers. A report with no rows displays an Arabic empty state.

XLSX is a genuine OpenXML package with Arabic sheet names and column labels, right-to-left sheet view, frozen header, filters, widths, print settings, colors and numeric attendance counts. Student exports show grade/class/hall names rather than database IDs. Files are written through SAF and checked after creation. Android share and print actions handle transfer and printing. Export queries are refreshed for the selected period immediately before file generation.
