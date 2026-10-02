# Import and export

The Arabic student template columns are رقم الطالب، الاسم الكامل، المرحلة، الصف، الشعبة، القاعة، هاتف ولي الأمر. The file has a title and instruction rows above the column header. Import locates the header row and reads inline-string and shared-string XLSX workbooks.

Validation rejects missing number/name/classification, duplicates already in Room, duplicates inside the workbook and structure names that do not match configured data. The preview lists totals and row errors. Confirmation is enabled only for an error-free file, and all rows are inserted in one Room transaction.

Exports are genuine `.xlsx` OpenXML packages, not renamed CSV files. Arabic text is UTF-8 and worksheets use RTL layout and styled headers. Class and hall columns contain readable names instead of internal IDs. SAF lets the operator choose internal storage, SD card, USB/OTG or another document provider without broad storage permission.
