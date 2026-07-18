package com.example.util

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.util.Log
import java.io.File
import java.io.FileOutputStream

object PdfGenerator {
    private const val TAG = "PdfGenerator"

    fun generatePdf(
        context: Context,
        fileName: String,
        documentTitle: String,
        rawText: String
    ): File? {
        try {
            val pdfDocument = PdfDocument()
            val paint = Paint()

            // Standard A4 dimensions: 595 x 842 points
            val pageWidth = 595
            val pageHeight = 842
            val margin = 50f
            val maxLineWidth = pageWidth - (margin * 2)
            val maxPageHeight = pageHeight - margin

            var pageNumber = 1
            var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
            var page = pdfDocument.startPage(pageInfo)
            var canvas = page.canvas

            var x = margin
            var y = margin + 20f

            // 1. Draw Document Header Title
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.textSize = 16f
            paint.color = Color.BLACK
            canvas.drawText(documentTitle, x, y, paint)
            y += 20f

            // 2. Draw Divider Line
            paint.strokeWidth = 1.5f
            paint.color = Color.rgb(180, 180, 180)
            canvas.drawLine(x, y, pageWidth - margin, y, paint)
            y += 25f

            // Setup Body Text Paint
            paint.color = Color.rgb(33, 33, 33)
            val bodyTextSize = 10f
            val headerTextSize = 11f
            val lineSpacing = 15f

            // Split the raw text by line breaks to respect formatting structures
            val rawLines = rawText.split("\n")

            for (rawLine in rawLines) {
                val trimmed = rawLine.trim()
                if (trimmed.isEmpty()) {
                    y += 8f // Smaller line spacing for paragraph breaks
                    continue
                }

                // Format headings specifically (e.g. Markdown bold/headers or lines starting with uppercase)
                val isHeader = trimmed.startsWith("#") || trimmed.startsWith("##") || trimmed.startsWith("###") ||
                        (trimmed.length < 50 && trimmed.contains(":") && trimmed.uppercase() == trimmed)

                var textToDraw = trimmed
                if (trimmed.startsWith("#")) {
                    textToDraw = trimmed.replace(Regex("^#+\\s*"), "")
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    paint.textSize = headerTextSize + 1f
                } else if (trimmed.startsWith("**") && trimmed.endsWith("**")) {
                    textToDraw = trimmed.replace("**", "")
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    paint.textSize = bodyTextSize
                } else {
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                    paint.textSize = bodyTextSize
                }

                // If content overflows page, finalize page and start a new one
                if (y > maxPageHeight) {
                    pdfDocument.finishPage(page)
                    pageNumber++
                    pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                    page = pdfDocument.startPage(pageInfo)
                    canvas = page.canvas
                    y = margin + 20f
                }

                // Draw bulleted items or wrap standard paragraphs
                val words = textToDraw.split(" ")
                var currentLine = ""

                for (word in words) {
                    val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
                    val textWidth = paint.measureText(testLine)

                    if (textWidth > maxLineWidth) {
                        canvas.drawText(currentLine, x, y, paint)
                        y += lineSpacing

                        // Handle page overflow within the word wrapping loop
                        if (y > maxPageHeight) {
                            pdfDocument.finishPage(page)
                            pageNumber++
                            pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                            page = pdfDocument.startPage(pageInfo)
                            canvas = page.canvas
                            y = margin + 20f
                        }
                        currentLine = word
                    } else {
                        currentLine = testLine
                    }
                }

                if (currentLine.isNotEmpty()) {
                    canvas.drawText(currentLine, x, y, paint)
                    y += lineSpacing
                }
            }

            pdfDocument.finishPage(page)

            // Save PDF inside cache directory for easy sharing/viewing via FileProvider
            val cacheDir = File(context.cacheDir, "jobcraft")
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }
            val pdfFile = File(cacheDir, fileName)
            
            FileOutputStream(pdfFile).use { fos ->
                pdfDocument.writeTo(fos)
            }
            pdfDocument.close()

            Log.i(TAG, "Generated PDF: ${pdfFile.absolutePath} (Size: ${pdfFile.length()})")
            return pdfFile
        } catch (e: Exception) {
            Log.e(TAG, "Error generating PDF: ${e.message}", e)
            return null
        }
    }
}
