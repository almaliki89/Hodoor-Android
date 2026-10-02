package com.omega.attendance.export.pdf

import android.content.*
import android.net.Uri
import android.os.*
import android.print.*
import java.io.FileOutputStream

object FileActions {
    fun open(context: Context, uri: Uri, mime: String) { context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) }
    fun share(context: Context, uri: Uri, mime: String) { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "مشاركة الملف")) }
    fun print(context: Context, uri: Uri, title: String) { (context.getSystemService(Context.PRINT_SERVICE) as PrintManager).print(title, UriPrintAdapter(context, uri, title), null) }
}

private class UriPrintAdapter(private val context: Context, private val uri: Uri, private val title: String) : PrintDocumentAdapter() {
    override fun onLayout(oldAttributes: PrintAttributes?, newAttributes: PrintAttributes, cancellationSignal: CancellationSignal, callback: LayoutResultCallback, extras: Bundle?) { if (cancellationSignal.isCanceled) callback.onLayoutCancelled() else callback.onLayoutFinished(PrintDocumentInfo.Builder(title).setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).build(), true) }
    override fun onWrite(pages: Array<out PageRange>, destination: android.os.ParcelFileDescriptor, cancellationSignal: CancellationSignal, callback: WriteResultCallback) { runCatching { context.contentResolver.openInputStream(uri)!!.use { input -> FileOutputStream(destination.fileDescriptor).use(input::copyTo) } }.onSuccess { callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES)) }.onFailure { callback.onWriteFailed(it.message) } }
}
