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
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)

            // Standard A4 dimensions: 595 x 842 points
            val pageWidth = 595
            val pageHeight = 842
            val margin = 42f
            val printableWidth = pageWidth - (margin * 2)
            val maxPageHeight = pageHeight - margin - 20f

            // Executive Palette
            val primaryColor = Color.rgb(27, 77, 137)     // #1B4D89 Dark Executive Blue
            val secondaryColor = Color.rgb(43, 76, 126)   // #2B4C7E Dark Slate Blue
            val bodyColor = Color.rgb(34, 34, 34)         // #222222 Charcoal
            val mutedColor = Color.rgb(85, 85, 85)        // #555555 Muted Gray
            val dividerColor = Color.rgb(27, 77, 137)      // Accent line under headers

            var pageNumber = 1
            var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
            var page = pdfDocument.startPage(pageInfo)
            var canvas = page.canvas

            var y = margin + 10f

            fun checkNewPage(neededHeight: Float) {
                if (y + neededHeight > maxPageHeight) {
                    // Draw Footer on previous page
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
                    paint.textSize = 8f
                    paint.color = mutedColor
                    canvas.drawText("Page $pageNumber", pageWidth - margin - 30f, pageHeight - 20f, paint)

                    pdfDocument.finishPage(page)
                    pageNumber++
                    pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                    page = pdfDocument.startPage(pageInfo)
                    canvas = page.canvas
                    y = margin + 10f
                }
            }

            val rawLines = rawText.split("\n")

            for (rawLine in rawLines) {
                val trimmed = rawLine.trim()

                if (trimmed.isEmpty()) {
                    y += 6f
                    continue
                }

                // 1. MAIN HEADER / NAME (# Name)
                if (trimmed.startsWith("# ")) {
                    val nameText = trimmed.removePrefix("# ").replace("**", "").uppercase()
                    checkNewPage(28f)
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    paint.textSize = 17f
                    paint.color = primaryColor
                    
                    canvas.drawText(nameText, margin, y, paint)
                    y += 22f
                    continue
                }

                // 2. SECTION HEADING (## SECTION)
                if (trimmed.startsWith("## ")) {
                    val sectionText = trimmed.removePrefix("## ").replace("**", "").uppercase()
                    y += 10f // Space above section heading
                    checkNewPage(26f)

                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    paint.textSize = 11.5f
                    paint.color = primaryColor

                    canvas.drawText(sectionText, margin, y, paint)
                    y += 5f

                    // Draw Horizontal Rule under section header
                    paint.strokeWidth = 1.2f
                    paint.color = dividerColor
                    canvas.drawLine(margin, y, pageWidth - margin, y, paint)
                    y += 12f
                    continue
                }

                // 3. SUBSECTION / TITLE LINE WITH DATE OR LOCATION SPLIT (| separator)
                // e.g. "**Data & Technology Specialist** | *Sep 2025 – Present*"
                if (trimmed.contains("|") && (trimmed.startsWith("**") || trimmed.startsWith("###"))) {
                    val parts = trimmed.split("|", limit = 2)
                    val leftText = parts[0].trim().replace("**", "").replace("###", "").trim()
                    val rightText = parts[1].trim().replace("*", "").trim()

                    checkNewPage(16f)

                    // Draw Left Text (Role Title or Degree)
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    paint.textSize = 10f
                    if (leftText.uppercase() == leftText) {
                        paint.color = secondaryColor
                    } else {
                        paint.color = bodyColor
                    }
                    canvas.drawText(leftText, margin, y, paint)

                    // Draw Right Text (Date range or location, right aligned)
                    if (rightText.isNotEmpty()) {
                        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
                        paint.textSize = 9.5f
                        paint.color = mutedColor
                        val textWidth = paint.measureText(rightText)
                        canvas.drawText(rightText, pageWidth - margin - textWidth, y, paint)
                    }

                    y += 14f
                    continue
                }

                // 4. SUBHEAD WITHOUT PIPE (e.g. "**Vantix** | *Nairobi, Kenya*" or "**Company Name**")
                if (trimmed.startsWith("### ")) {
                    val subheadText = trimmed.removePrefix("### ").replace("**", "")
                    checkNewPage(15f)
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    paint.textSize = 10f
                    paint.color = secondaryColor
                    canvas.drawText(subheadText, margin, y, paint)
                    y += 14f
                    continue
                }

                // 5. BULLET ITEMS (- item, • item,  item)
                val isBullet = trimmed.startsWith("- ") || trimmed.startsWith("• ") || trimmed.startsWith("* ") || trimmed.startsWith(" ")
                if (isBullet) {
                    val bulletContent = trimmed.substring(2).trim()

                    // Check if bullet has bold lead-in e.g. "**Languages & Frameworks:** Python..."
                    val indentX = margin + 12f
                    val maxBulletWidth = printableWidth - 12f

                    checkNewPage(14f)

                    // Draw bullet symbol
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    paint.textSize = 10f
                    paint.color = primaryColor
                    canvas.drawText("•", margin + 2f, y, paint)

                    // Draw bullet text with hanging indent
                    val words = bulletContent.split(" ")
                    var currentLine = ""
                    var lineY = y

                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                    paint.textSize = 9.5f
                    paint.color = bodyColor

                    for (word in words) {
                        val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
                        
                        // Parse bold tags within bullet if present
                        val cleanTestLine = testLine.replace("**", "")
                        val textWidth = paint.measureText(cleanTestLine)

                        if (textWidth > maxBulletWidth) {
                            drawFormattedLine(canvas, paint, currentLine, indentX, lineY, bodyColor, primaryColor)
                            lineY += 13f

                            if (lineY > maxPageHeight) {
                                pdfDocument.finishPage(page)
                                pageNumber++
                                pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                                page = pdfDocument.startPage(pageInfo)
                                canvas = page.canvas
                                lineY = margin + 10f
                            }
                            currentLine = word
                        } else {
                            currentLine = testLine
                        }
                    }

                    if (currentLine.isNotEmpty()) {
                        drawFormattedLine(canvas, paint, currentLine, indentX, lineY, bodyColor, primaryColor)
                        lineY += 13f
                    }

                    y = lineY + 1f
                    continue
                }

                // 6. CONTACT BAR / TAGLINE OR REGULAR PARAGRAPH
                val isTaglineOrContact = trimmed.contains("•") || trimmed.contains("@") || trimmed.contains("linkedin.com") || trimmed.contains("+254")
                if (isTaglineOrContact) {
                    checkNewPage(14f)
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                    paint.textSize = 9f
                    paint.color = secondaryColor

                    val words = trimmed.split(" ")
                    var currentLine = ""
                    var lineY = y

                    for (word in words) {
                        val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
                        val textWidth = paint.measureText(testLine)

                        if (textWidth > printableWidth) {
                            canvas.drawText(currentLine, margin, lineY, paint)
                            lineY += 12f
                            currentLine = word
                        } else {
                            currentLine = testLine
                        }
                    }
                    if (currentLine.isNotEmpty()) {
                        canvas.drawText(currentLine, margin, lineY, paint)
                        lineY += 12f
                    }
                    y = lineY + 2f
                    continue
                }

                // 7. STANDARD BODY PARAGRAPH
                checkNewPage(14f)
                val words = trimmed.split(" ")
                var currentLine = ""
                var lineY = y

                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                paint.textSize = 9.5f
                paint.color = bodyColor

                for (word in words) {
                    val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
                    val cleanTestLine = testLine.replace("**", "")
                    val textWidth = paint.measureText(cleanTestLine)

                    if (textWidth > printableWidth) {
                        drawFormattedLine(canvas, paint, currentLine, margin, lineY, bodyColor, primaryColor)
                        lineY += 13f

                        if (lineY > maxPageHeight) {
                            pdfDocument.finishPage(page)
                            pageNumber++
                            pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                            page = pdfDocument.startPage(pageInfo)
                            canvas = page.canvas
                            lineY = margin + 10f
                        }
                        currentLine = word
                    } else {
                        currentLine = testLine
                    }
                }

                if (currentLine.isNotEmpty()) {
                    drawFormattedLine(canvas, paint, currentLine, margin, lineY, bodyColor, primaryColor)
                    lineY += 13f
                }

                y = lineY + 2f
            }

            // Draw Footer on last page
            if (pageNumber > 1) {
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
                paint.textSize = 8f
                paint.color = mutedColor
                canvas.drawText("Page $pageNumber", pageWidth - margin - 30f, pageHeight - 20f, paint)
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

            Log.i(TAG, "Generated Executive CV PDF: ${pdfFile.absolutePath} (Size: ${pdfFile.length()})")
            return pdfFile
        } catch (e: Exception) {
            Log.e(TAG, "Error generating PDF: ${e.message}", e)
            return null
        }
    }

    /**
     * Helper to draw text with inline **bold** lead-ins (e.g. "**Languages & Frameworks:** Python...")
     */
    private fun drawFormattedLine(
        canvas: android.graphics.Canvas,
        paint: Paint,
        text: String,
        startX: Float,
        y: Float,
        normalColor: Int,
        boldColor: Int
    ) {
        if (!text.contains("**")) {
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            paint.color = normalColor
            canvas.drawText(text, startX, y, paint)
            return
        }

        var currentX = startX
        val parts = text.split("**")

        for (i in parts.indices) {
            val part = parts[i]
            if (part.isEmpty()) continue

            val isBold = (i % 2 == 1) // Odd index parts were inside ** ... **
            if (isBold) {
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                paint.color = boldColor
            } else {
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                paint.color = normalColor
            }

            canvas.drawText(part, currentX, y, paint)
            currentX += paint.measureText(part)
        }
    }
}

