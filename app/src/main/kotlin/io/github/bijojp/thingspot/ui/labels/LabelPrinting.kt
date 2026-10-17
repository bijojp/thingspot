package io.github.bijojp.thingspot.ui.labels

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.print.pdf.PrintedPdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import io.github.bijojp.thingspot.domain.LabelCode
import java.io.FileOutputStream
import java.io.IOException

/** One Label to print: its code, and the Place name (null for a blank Label to write on). */
data class PrintableLabel(val code: LabelCode, val placeName: String?)

/**
 * Sends Labels to Android's print dialog, which also offers "Save as PDF". The sheet adapts
 * to whatever paper the user picks; on A4 that's 21 Labels of 63.5 × 38.1 mm (a common
 * sticker-sheet size), and dashed cut lines make plain paper work too.
 */
fun printLabels(context: Context, jobName: String, labels: List<PrintableLabel>) {
    val printManager = context.getSystemService(PrintManager::class.java)
    printManager.print(jobName, LabelPrintAdapter(context, labels), null)
}

private class LabelPrintAdapter(private val context: Context, private val labels: List<PrintableLabel>) : PrintDocumentAdapter() {
    private var attributes: PrintAttributes? = null

    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes,
        cancellationSignal: CancellationSignal,
        callback: LayoutResultCallback,
        extras: Bundle?,
    ) {
        if (cancellationSignal.isCanceled) {
            callback.onLayoutCancelled()
            return
        }
        attributes = newAttributes
        val media = newAttributes.mediaSize ?: PrintAttributes.MediaSize.ISO_A4
        val sheet = LabelSheet(media.widthMils * 72f / 1000f, media.heightMils * 72f / 1000f)
        val info = PrintDocumentInfo.Builder("thingspot-labels.pdf")
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            .setPageCount(sheet.pagesFor(labels.size))
            .build()
        callback.onLayoutFinished(info, newAttributes != oldAttributes)
    }

    override fun onWrite(
        pages: Array<out PageRange>,
        destination: ParcelFileDescriptor,
        cancellationSignal: CancellationSignal,
        callback: WriteResultCallback,
    ) {
        val pdf = PrintedPdfDocument(context, attributes ?: return callback.onWriteFailed("No layout"))
        try {
            val sheet = LabelSheet(pdf.pageWidth.toFloat(), pdf.pageHeight.toFloat())
            labels.chunked(sheet.perPage).forEachIndexed { index, pageLabels ->
                if (cancellationSignal.isCanceled) {
                    callback.onWriteCancelled()
                    return
                }
                val page = pdf.startPage(index)
                sheet.draw(page.canvas, pageLabels)
                pdf.finishPage(page)
            }
            FileOutputStream(destination.fileDescriptor).use { pdf.writeTo(it) }
            callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
        } catch (e: IOException) {
            callback.onWriteFailed(e.message)
        } finally {
            pdf.close()
        }
    }
}

/** Lays Labels out in a centred grid of fixed-size cells. Units are PDF points (1/72 inch). */
private class LabelSheet(private val pageWidth: Float, private val pageHeight: Float) {
    private val columns = ((pageWidth - 2 * MARGIN) / CELL_WIDTH).toInt().coerceAtLeast(1)
    private val rows = ((pageHeight - 2 * MARGIN) / CELL_HEIGHT).toInt().coerceAtLeast(1)
    val perPage = columns * rows

    fun pagesFor(count: Int) = ((count + perPage - 1) / perPage).coerceAtLeast(1)

    private val left = (pageWidth - columns * CELL_WIDTH) / 2
    private val top = (pageHeight - rows * CELL_HEIGHT) / 2

    private val cutLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 0.5f
        color = Color.rgb(190, 190, 190)
        pathEffect = DashPathEffect(floatArrayOf(3f, 3f), 0f)
    }
    private val ink = Paint().apply { color = Color.BLACK }
    private val writeOnLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 0.5f
        color = Color.rgb(120, 120, 120)
    }
    private val namePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 10f
        typeface = Typeface.DEFAULT_BOLD
    }
    private val codePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 9f
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }
    private val brandPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 6.5f
        color = Color.GRAY
    }
    private val writer = QRCodeWriter()
    private val hints = mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 1)

    fun draw(canvas: Canvas, labels: List<PrintableLabel>) {
        labels.forEachIndexed { index, label ->
            val x = left + (index % columns) * CELL_WIDTH
            val y = top + (index / columns) * CELL_HEIGHT
            canvas.drawRect(x, y, x + CELL_WIDTH, y + CELL_HEIGHT, cutLine)
            drawLabel(canvas, label, x, y)
        }
    }

    private fun drawLabel(canvas: Canvas, label: PrintableLabel, x: Float, y: Float) {
        // QR code as vector squares, so it prints sharp at any resolution.
        val matrix = writer.encode(label.code.qrContent, BarcodeFormat.QR_CODE, 0, 0, hints)
        val module = QR_SIZE / matrix.width
        val qrLeft = x + PADDING
        val qrTop = y + (CELL_HEIGHT - QR_SIZE) / 2
        for (row in 0 until matrix.height) {
            for (column in 0 until matrix.width) {
                if (matrix[column, row]) {
                    val l = qrLeft + column * module
                    val t = qrTop + row * module
                    canvas.drawRect(l, t, l + module, t + module, ink)
                }
            }
        }

        val textLeft = qrLeft + QR_SIZE + 8f
        val textWidth = (x + CELL_WIDTH - PADDING - textLeft).toInt()
        var cursor = y + 14f
        if (label.placeName != null) {
            val name = StaticLayout.Builder.obtain(label.placeName, 0, label.placeName.length, namePaint, textWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setEllipsize(TextUtils.TruncateAt.END)
                .setMaxLines(3)
                .build()
            canvas.save()
            canvas.translate(textLeft, cursor)
            name.draw(canvas)
            canvas.restore()
        } else {
            // A blank Label: a line to write the Place on by hand.
            val lineY = cursor + 22f
            canvas.drawLine(textLeft, lineY, textLeft + textWidth, lineY, writeOnLine)
        }
        cursor = y + CELL_HEIGHT - 22f
        canvas.drawText(label.code.display, textLeft, cursor, codePaint)
        canvas.drawText(BRAND, textLeft, cursor + 10f, brandPaint)
    }

    private companion object {
        const val CELL_WIDTH = 180f // 63.5 mm
        const val CELL_HEIGHT = 108f // 38.1 mm
        const val MARGIN = 24f
        const val PADDING = 10f
        const val QR_SIZE = 84f
        const val BRAND = "ThingSpot"
    }
}
