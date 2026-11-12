package de.proofseal.app.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.util.Base64
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import de.proofseal.app.crypto.CryptoSealResult
import de.proofseal.app.crypto.HardwareCryptoManager
import de.proofseal.app.model.CapturedKeyframe
import de.proofseal.app.model.ProofMode
import de.proofseal.app.model.QrSealPayload
import de.proofseal.app.model.SensorIntegrityReport
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.EnumMap
import java.util.Locale

data class PdfGenerationRequest(
    val protocolId: String,
    val title: String,
    val mode: ProofMode,
    val createdAtLocalFormatted: String,
    val createdAtUtcIso: String,
    val uptimeSpanFormatted: String,
    val deviceModel: String,
    val gpsLocationText: String,
    val primaryOcrValue: String,
    val fullOcrSummary: String,
    val detectedBarcodes: List<String>,
    val witnessName: String,
    val userNotes: String,
    val keyframes: List<CapturedKeyframe>,
    val sensorReport: SensorIntegrityReport,
    val cryptoSeal: CryptoSealResult
)

data class PdfDirectIntegrityExtract(
    val hasProofSealFooter: Boolean,
    val isPdfBinaryStreamIntact: Boolean,
    val qrPayload: QrSealPayload?,
    val expectedPdfSha256: String,
    val actualPdfSha256: String,
    val pdfByteSize: Int,
    val tamperReason: String?
)

object ProofPdfGenerator {

    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 28f

    const val PDF_FOOTER_PREFIX = "\n%%PROOFSEAL_PAYLOAD_JSON:"
    const val PDF_FOOTER_SUFFIX = "%%\n"

    private val COLOR_DARK_HEADER = Color.parseColor("#0F172A")
    private val COLOR_EMERALD_DARK = Color.parseColor("#065F46")
    private val COLOR_EMERALD_BG = Color.parseColor("#D1FAE5")
    private val COLOR_EMERALD_LINE = Color.parseColor("#10B981")
    private val COLOR_AMBER_BG = Color.parseColor("#FEF3C7")
    private val COLOR_AMBER_TEXT = Color.parseColor("#92400E")
    private val COLOR_SLATE_BG = Color.parseColor("#F8FAFC")
    private val COLOR_SLATE_BORDER = Color.parseColor("#CBD5E1")
    private val COLOR_TEXT_PRIMARY = Color.parseColor("#0F172A")
    private val COLOR_TEXT_MUTED = Color.parseColor("#475569")
    private val COLOR_CYAN_LINE = Color.parseColor("#0284C7")

    fun buildQrSealPayload(req: PdfGenerationRequest): QrSealPayload {
        return QrSealPayload(
            version = 2,
            protocolId = req.protocolId,
            timestampUtc = req.createdAtUtcIso,
            uptimeNanos = req.sensorReport.endElapsedNanos,
            deviceModel = req.deviceModel,
            securityLevel = req.cryptoSeal.hardwareSecurityLevel,
            modeTitle = req.mode.shortTitle,
            primaryOcrValue = req.primaryOcrValue.ifBlank { "-" }.take(64),
            imageHashes = req.keyframes.map { it.labeledShortHash },
            keyFingerprint = req.cryptoSeal.publicKeyFingerprint,
            parallaxScore = req.sensorReport.parallaxConfidenceScore,
            isContinuous = req.sensorReport.isContinuous,
            payloadHash = req.cryptoSeal.payloadSha256Hex,
            signatureBase64 = req.cryptoSeal.signatureBase64,
            publicKeyBase64 = req.cryptoSeal.publicKeyBase64
        )
    }

    fun generateProofPdf(
        context: Context,
        request: PdfGenerationRequest,
        cryptoManager: HardwareCryptoManager
    ): File {
        val pdfDir = File(context.filesDir, "pdfs").apply { mkdirs() }
        val sanitizedId = request.protocolId.take(8).uppercase(Locale.ROOT)
        val outFile = File(pdfDir, "ProofSeal_${sanitizedId}_${System.currentTimeMillis()}.pdf")

        val qrPayload = buildQrSealPayload(request)

        val rawPdfStream = ByteArrayOutputStream()
        val pdfDocument = PdfDocument()
        try {
            // ==================== SEITE 1: VISUELLES BEWEIS-PROTOKOLL ====================
            val page1Info = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
            val page1 = pdfDocument.startPage(page1Info)
            drawPage1VisualProtocol(page1.canvas, request)
            pdfDocument.finishPage(page1)

            // ==================== SEITE 2: TECHNISCHES PRÜF-ZERTIFIKAT ====================
            val page2Info = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 2).create()
            val page2 = pdfDocument.startPage(page2Info)
            drawPage2TechnicalCertificate(page2.canvas, request, qrPayload)
            pdfDocument.finishPage(page2)

            pdfDocument.writeTo(rawPdfStream)
        } finally {
            pdfDocument.close()
        }

        val rawPdfBytes = rawPdfStream.toByteArray()
        val pdfStreamSha256 = HardwareCryptoManager.sha256Hex(rawPdfBytes).lowercase(Locale.ROOT)
        val pdfStreamSigBase64 = cryptoManager.signDigestHex(pdfStreamSha256)

        // Strukturierter, kryptografisch signierter Prüf-Footer für die direkte PDF-Datei-Prüfung
        val envelopeJson = JSONObject().apply {
            put("qrSealJson", qrPayload.toCompactJson())
            put("pdfStreamByteLength", rawPdfBytes.size)
            put("pdfStreamSha256", pdfStreamSha256)
            put("pdfStreamSignatureBase64", pdfStreamSigBase64)
        }.toString()

        val encodedEnvelope = Base64.encodeToString(
            envelopeJson.toByteArray(Charsets.UTF_8),
            Base64.NO_WRAP
        )
        val footerBytes = "$PDF_FOOTER_PREFIX$encodedEnvelope$PDF_FOOTER_SUFFIX"
            .toByteArray(Charsets.UTF_8)

        FileOutputStream(outFile).use { fos ->
            fos.write(rawPdfBytes)
            fos.write(footerBytes)
            fos.flush()
        }

        return outFile
    }

    /**
     * Liest und prüft eine komplette PDF-Datei direkt anhand der eingebetteten
     * %%PROOFSEAL_PAYLOAD_JSON:<Base64>%% Signatur-Struktur sowie des Binär-Streams.
     */
    fun verifyPdfFileBytes(
        pdfBytes: ByteArray,
        cryptoManager: HardwareCryptoManager
    ): PdfDirectIntegrityExtract {
        val prefixBytes = PDF_FOOTER_PREFIX.toByteArray(Charsets.UTF_8)
        val suffixBytes = PDF_FOOTER_SUFFIX.trimEnd().toByteArray(Charsets.UTF_8)

        val markerStart = lastIndexOfSubArray(pdfBytes, prefixBytes)
        if (markerStart < 0) {
            return PdfDirectIntegrityExtract(
                hasProofSealFooter = false,
                isPdfBinaryStreamIntact = false,
                qrPayload = null,
                expectedPdfSha256 = "-",
                actualPdfSha256 = HardwareCryptoManager.sha256Hex(pdfBytes).take(32),
                pdfByteSize = pdfBytes.size,
                tamperReason = "Kein ProofSeal-Signaturblock in dieser PDF-Datei gefunden! Das Dokument wurde entweder nicht mit ProofSeal erstellt oder nachträglich mit einem externen PDF-Editor verändert und neu abgespeichert."
            )
        }

        return try {
            val payloadStart = markerStart + prefixBytes.size
            val tailString = String(
                pdfBytes,
                payloadStart,
                pdfBytes.size - payloadStart,
                Charsets.UTF_8
            )
            val endMarkerIdx = tailString.indexOf("%%")
            if (endMarkerIdx <= 0) {
                return PdfDirectIntegrityExtract(
                    hasProofSealFooter = false,
                    isPdfBinaryStreamIntact = false,
                    qrPayload = null,
                    expectedPdfSha256 = "-",
                    actualPdfSha256 = "-",
                    pdfByteSize = pdfBytes.size,
                    tamperReason = "Beschädigter oder unvollständiger ProofSeal-Footer im PDF!"
                )
            }

            val base64Envelope = tailString.substring(0, endMarkerIdx).trim()
            val envelopeJsonStr = String(
                Base64.decode(base64Envelope, Base64.NO_WRAP),
                Charsets.UTF_8
            )
            val envelopeObj = JSONObject(envelopeJsonStr)

            val qrSealRawJson = envelopeObj.getString("qrSealJson")
            val expectedByteLength = envelopeObj.optInt("pdfStreamByteLength", -1)
            val expectedPdfSha256 = envelopeObj.optString("pdfStreamSha256", "").lowercase(Locale.ROOT)
            val pdfStreamSigBase64 = envelopeObj.optString("pdfStreamSignatureBase64", "")

            val qrPayload = QrSealPayload.fromJsonOrNull(qrSealRawJson)
                ?: return PdfDirectIntegrityExtract(
                    hasProofSealFooter = true,
                    isPdfBinaryStreamIntact = false,
                    qrPayload = null,
                    expectedPdfSha256 = expectedPdfSha256,
                    actualPdfSha256 = "-",
                    pdfByteSize = pdfBytes.size,
                    tamperReason = "Ungültiges Zertifikats-JSON im PDF-Footer!"
                )

            // Extrahiere den exakten PDF-Binärkörper vor dem Footer und berechne den SHA-256 Hash
            val actualPdfBodyBytes = pdfBytes.copyOfRange(0, markerStart)
            val actualPdfSha256 = HardwareCryptoManager.sha256Hex(actualPdfBodyBytes).lowercase(Locale.ROOT)

            val lengthMatches = expectedByteLength == actualPdfBodyBytes.size
            val shaMatches = expectedPdfSha256.isNotBlank() && expectedPdfSha256 == actualPdfSha256
            val streamSigValid = cryptoManager.verifySeal(
                payloadHash = expectedPdfSha256,
                signatureBase64 = pdfStreamSigBase64,
                publicKeyBase64 = qrPayload.publicKeyBase64
            )

            if (!lengthMatches || !shaMatches || !streamSigValid) {
                return PdfDirectIntegrityExtract(
                    hasProofSealFooter = true,
                    isPdfBinaryStreamIntact = false,
                    qrPayload = qrPayload,
                    expectedPdfSha256 = expectedPdfSha256,
                    actualPdfSha256 = actualPdfSha256,
                    pdfByteSize = pdfBytes.size,
                    tamperReason = "PDF-Binärstrom wurde nachträglich manipuliert! Der aktuelle PDF-Inhalts-Hash ($actualPdfSha256) weicht vom hardware-signierten Original-Hash ($expectedPdfSha256) ab."
                )
            }

            PdfDirectIntegrityExtract(
                hasProofSealFooter = true,
                isPdfBinaryStreamIntact = true,
                qrPayload = qrPayload,
                expectedPdfSha256 = expectedPdfSha256,
                actualPdfSha256 = actualPdfSha256,
                pdfByteSize = pdfBytes.size,
                tamperReason = null
            )
        } catch (e: Exception) {
            PdfDirectIntegrityExtract(
                hasProofSealFooter = false,
                isPdfBinaryStreamIntact = false,
                qrPayload = null,
                expectedPdfSha256 = "-",
                actualPdfSha256 = "-",
                pdfByteSize = pdfBytes.size,
                tamperReason = "Fehler beim Dekodieren des PDF-Prüfblocks: ${e.localizedMessage}"
            )
        }
    }

    private fun lastIndexOfSubArray(source: ByteArray, target: ByteArray): Int {
        if (target.isEmpty() || source.size < target.size) return -1
        for (i in source.size - target.size downTo 0) {
            var match = true
            for (j in target.indices) {
                if (source[i + j] != target[j]) {
                    match = false
                    break
                }
            }
            if (match) return i
        }
        return -1
    }

    private fun drawPage1VisualProtocol(canvas: Canvas, req: PdfGenerationRequest) {
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1f
            color = COLOR_SLATE_BORDER
        }
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textSize = 13.5f
        }
        val subHeaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#94A3B8")
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            textSize = 8.5f
        }
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_TEXT_MUTED
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textSize = 8.2f
        }
        val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_TEXT_PRIMARY
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            textSize = 8.8f
        }
        val monoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_TEXT_PRIMARY
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textSize = 7.8f
        }

        fillPaint.color = Color.WHITE
        canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), PAGE_HEIGHT.toFloat(), fillPaint)

        // 1. Seriöser Kopfbalken
        fillPaint.color = COLOR_DARK_HEADER
        canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), 58f, fillPaint)

        canvas.drawText(
            "PROOFSEAL – KRYPTOGRAFISCH GESICHERTES BEWEIS-PROTOKOLL",
            MARGIN,
            26f,
            titlePaint
        )
        canvas.drawText(
            "SEITE 1/2 • VISUELLES BEWEIS-PROTOKOLL • MODUS: ${req.mode.title.uppercase(Locale.ROOT)}",
            MARGIN,
            43f,
            subHeaderPaint
        )

        var y = 68f

        // 2. Status-Badge in Grün
        val badgeRect = RectF(MARGIN, y, PAGE_WIDTH - MARGIN, y + 26f)
        fillPaint.color = COLOR_EMERALD_BG
        canvas.drawRoundRect(badgeRect, 5f, 5f, fillPaint)
        strokePaint.color = COLOR_EMERALD_LINE
        strokePaint.strokeWidth = 1.2f
        canvas.drawRoundRect(badgeRect, 5f, 5f, strokePaint)

        val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_EMERALD_DARK
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textSize = 9.5f
        }
        val secBadgeSuffix = if (req.cryptoSeal.isStrongBoxBacked) {
            "STRONGBOX HARDWARE-SE"
        } else {
            "ANDROID HARDWARE-TEE"
        }
        canvas.drawText(
            "✓ INTEGRITÄT VERIFIZIERT | HARDWARE-KEYSTORE SIGNIERT ($secBadgeSuffix)",
            MARGIN + 10f,
            y + 16.5f,
            badgeTextPaint
        )
        y += 34f

        // 3. Strukturierte Metadaten-Box
        val metaHeight = 205f
        val metaRect = RectF(MARGIN, y, PAGE_WIDTH - MARGIN, y + metaHeight)
        fillPaint.color = COLOR_SLATE_BG
        canvas.drawRoundRect(metaRect, 6f, 6f, fillPaint)
        strokePaint.color = COLOR_SLATE_BORDER
        strokePaint.strokeWidth = 1f
        canvas.drawRoundRect(metaRect, 6f, 6f, strokePaint)

        val col1X = MARGIN + 10f
        val col2X = PAGE_WIDTH / 2f + 6f
        var rowY = y + 15f
        val rowStep = 23f

        drawMetaPair(canvas, col1X, rowY, "PROTOKOLL-ID (UUID)", req.protocolId, labelPaint, monoPaint)
        drawMetaPair(
            canvas,
            col2X,
            rowY,
            "BEWEIS-TITEL & MODUS",
            "${truncate(req.title, 28)} (${req.mode.shortTitle})",
            labelPaint,
            valuePaint
        )
        rowY += rowStep

        drawMetaPair(
            canvas,
            col1X,
            rowY,
            "DATUM / UHRZEIT (LOKAL & UTC)",
            "${req.createdAtLocalFormatted} | ${req.createdAtUtcIso}",
            labelPaint,
            valuePaint
        )
        drawMetaPair(
            canvas,
            col2X,
            rowY,
            "MONOTONE HARDWARE-UPTIME-SPANNE",
            req.uptimeSpanFormatted,
            labelPaint,
            monoPaint
        )
        rowY += rowStep

        drawMetaPair(
            canvas,
            col1X,
            rowY,
            "GERÄTEMODELL & HERSTELLER",
            req.deviceModel,
            labelPaint,
            valuePaint
        )
        drawMetaPair(
            canvas,
            col2X,
            rowY,
            "SICHERHEITSCHIP-LEVEL (KEYSTORE)",
            "${req.cryptoSeal.hardwareSecurityLevel} (${req.cryptoSeal.publicKeyFingerprint.take(14)}…)",
            labelPaint,
            valuePaint
        )
        rowY += rowStep

        drawMetaPair(
            canvas,
            col1X,
            rowY,
            "GPS-POSITION (ORTSNACHWEIS)",
            truncate(req.gpsLocationText, 44),
            labelPaint,
            valuePaint
        )
        val continuityLabel = if (req.sensorReport.isContinuous) {
            "LÜCKENLOS (Max Gap ${req.sensorReport.maxGapMillis}ms)"
        } else {
            "LÜCKE ERKANNT (${req.sensorReport.maxGapMillis}ms)"
        }
        drawMetaPair(
            canvas,
            col2X,
            rowY,
            "3D-PARALLAXE-SCORE & SENSORKETTE",
            "${req.sensorReport.parallaxConfidenceScore}% 3D-Echtheit • $continuityLabel",
            labelPaint,
            valuePaint
        )
        rowY += rowStep

        canvas.drawLine(MARGIN + 8f, rowY - 6f, PAGE_WIDTH - MARGIN - 8f, rowY - 6f, strokePaint)

        val ocrHeader = req.mode.primaryValueLabel.uppercase(Locale.ROOT)
        val primaryValDisplay = req.primaryOcrValue.ifBlank { "Kein Primärcode markiert" }
        val barcodeSuffix = if (req.detectedBarcodes.isNotEmpty()) {
            " | Barcodes: ${req.detectedBarcodes.joinToString(", ")}"
        } else {
            ""
        }
        drawMetaPair(
            canvas,
            col1X,
            rowY + 4f,
            "ERKANNTER HAUPTWERT ($ocrHeader)",
            truncate("$primaryValDisplay$barcodeSuffix", 92),
            labelPaint,
            monoPaint
        )
        rowY += rowStep + 2f

        val ocrSnippet = req.fullOcrSummary.replace("\n", " • ").ifBlank { "Keine zusätzlichen OCR-Textblöcke erkannt" }
        drawMetaPair(
            canvas,
            col1X,
            rowY + 4f,
            "AUTOMATISCH EXTRAHIERTER OCR-TEXT",
            truncate(ocrSnippet, 98),
            labelPaint,
            valuePaint
        )
        rowY += rowStep + 2f

        val witnessPart = if (req.witnessName.isNotBlank()) "Zeuge: ${req.witnessName} | " else ""
        val notePart = req.userNotes.ifBlank { "Keine ergänzende Freitext-Notiz hinterlegt." }
        drawMetaPair(
            canvas,
            col1X,
            rowY + 4f,
            "ZEUGE & FREITEXT-PROTOKOLLNOTIZ",
            truncate("$witnessPart$notePart", 98),
            labelPaint,
            valuePaint
        )

        y += metaHeight + 14f

        val sectionTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_TEXT_PRIMARY
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textSize = 10.5f
        }
        canvas.drawText(
            "CHRONOLOGISCHE KEYFRAME-BEWEISFOTOS (MIT MILLISEKUNDEN-OFFSET & BILD-SHA256)",
            MARGIN,
            y,
            sectionTitlePaint
        )
        y += 8f

        val gridWidth = PAGE_WIDTH - 2 * MARGIN
        val cellGap = 10f
        val cellWidth = (gridWidth - cellGap) / 2f
        val cellHeight = 224f

        for (slot in 0 until 4) {
            val col = slot % 2
            val row = slot / 2
            val cellLeft = MARGIN + col * (cellWidth + cellGap)
            val cellTop = y + row * (cellHeight + cellGap)
            val cellRect = RectF(cellLeft, cellTop, cellLeft + cellWidth, cellTop + cellHeight)

            fillPaint.color = COLOR_SLATE_BG
            canvas.drawRoundRect(cellRect, 5f, 5f, fillPaint)
            strokePaint.color = COLOR_SLATE_BORDER
            strokePaint.strokeWidth = 1f
            canvas.drawRoundRect(cellRect, 5f, 5f, strokePaint)

            val kf = req.keyframes.getOrNull(slot)
            if (kf != null) {
                val kfHeaderRect = RectF(cellLeft, cellTop, cellLeft + cellWidth, cellTop + 20f)
                fillPaint.color = COLOR_DARK_HEADER
                canvas.drawRoundRect(kfHeaderRect, 5f, 5f, fillPaint)

                val kfHeaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.WHITE
                    typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                    textSize = 8f
                }
                canvas.drawText(
                    truncate(kf.stepTitle, 32),
                    cellLeft + 6f,
                    cellTop + 13f,
                    kfHeaderPaint
                )

                val offsetPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#34D399")
                    typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                    textSize = 8.2f
                    textAlign = Paint.Align.RIGHT
                }
                canvas.drawText(
                    kf.formattedOffsetSeconds,
                    cellLeft + cellWidth - 6f,
                    cellTop + 13f,
                    offsetPaint
                )

                val imgArea = RectF(
                    cellLeft + 5f,
                    cellTop + 24f,
                    cellLeft + cellWidth - 5f,
                    cellTop + cellHeight - 36f
                )
                drawKeyframeBitmapInRect(canvas, kf.imageFilePath, imgArea)

                val footerY1 = cellTop + cellHeight - 21f
                val footerY2 = cellTop + cellHeight - 8f

                val hashLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = COLOR_TEXT_PRIMARY
                    typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                    textSize = 7.4f
                }
                canvas.drawText(
                    "${kf.labeledShortHash} (SHA-256)",
                    cellLeft + 6f,
                    footerY1,
                    hashLinePaint
                )

                val ocrInfo = kf.extractedKeyCandidate.ifBlank {
                    kf.detectedOcrText.lines().firstOrNull()?.trim().orEmpty()
                }.ifBlank { "Sensormessung: a=(%.1f, %.1f, %.1f)".format(Locale.US, kf.accelX, kf.accelY, kf.accelZ) }

                val smallMutedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = COLOR_TEXT_MUTED
                    typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
                    textSize = 7.2f
                }
                canvas.drawText(
                    "OCR/Daten: ${truncate(ocrInfo, 42)}",
                    cellLeft + 6f,
                    footerY2,
                    smallMutedPaint
                )
            } else {
                val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = COLOR_TEXT_MUTED
                    typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.ITALIC)
                    textSize = 8.5f
                    textAlign = Paint.Align.CENTER
                }
                canvas.drawText(
                    "Keyframe #${slot + 1} nicht belegt",
                    cellLeft + cellWidth / 2f,
                    cellTop + cellHeight / 2f,
                    emptyPaint
                )
            }
        }

        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_TEXT_MUTED
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
            textSize = 7.5f
        }
        canvas.drawText(
            "Protokoll-Hash (SHA-256): ${req.cryptoSeal.payloadSha256Hex} • Fortsetzung & Signatur auf Seite 2 →",
            MARGIN,
            PAGE_HEIGHT - 14f,
            footerPaint
        )
    }

    private fun drawPage2TechnicalCertificate(
        canvas: Canvas,
        req: PdfGenerationRequest,
        qrPayload: QrSealPayload
    ) {
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1f
            color = COLOR_SLATE_BORDER
        }
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textSize = 13.5f
        }
        val subHeaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#94A3B8")
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            textSize = 8.5f
        }
        val sectionTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_TEXT_PRIMARY
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textSize = 10.5f
        }

        fillPaint.color = Color.WHITE
        canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), PAGE_HEIGHT.toFloat(), fillPaint)

        // 1. Kopfbalken Seite 2
        fillPaint.color = COLOR_DARK_HEADER
        canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), 58f, fillPaint)
        canvas.drawText(
            "TECHNISCHES PRÜF-ZERTIFIKAT & SENSORKETTEN-BEWEIS",
            MARGIN,
            26f,
            titlePaint
        )
        canvas.drawText(
            "SEITE 2/2 • PROTOKOLL-ID: ${req.protocolId} • ECDSA (secp256r1) HARDWARE-SIEGEL",
            MARGIN,
            43f,
            subHeaderPaint
        )

        var y = 74f

        // 2. Lückenloses Sensor-Verlaufsdiagramm
        canvas.drawText(
            "1. LÜCKENLOSES SENSOR-VERLAUFSDIAGRAMM (BESCHLEUNIGUNG & GYROSKOP + FOTO-TRIGGER)",
            MARGIN,
            y,
            sectionTitlePaint
        )
        y += 8f

        val chartBox = RectF(MARGIN, y, PAGE_WIDTH - MARGIN, y + 235f)
        drawSensorChainChart(canvas, chartBox, req)
        y += 250f

        // 3. Sensor- & Integritäts-Kennzahlen-Leiste
        val statsRect = RectF(MARGIN, y, PAGE_WIDTH - MARGIN, y + 48f)
        fillPaint.color = COLOR_SLATE_BG
        canvas.drawRoundRect(statsRect, 5f, 5f, fillPaint)
        strokePaint.color = COLOR_SLATE_BORDER
        canvas.drawRoundRect(statsRect, 5f, 5f, strokePaint)

        val statLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_TEXT_MUTED
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textSize = 7.5f
        }
        val statValPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_TEXT_PRIMARY
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textSize = 8.4f
        }

        val sr = req.sensorReport
        val pressureStr = sr.averagePressureHpa?.let { String.format(Locale.US, "%.1f hPa", it) } ?: "n/v"
        val lightStr = sr.averageLightLux?.let { String.format(Locale.US, "%.0f Lux", it) } ?: "n/v"

        drawMetaPair(
            canvas,
            MARGIN + 10f,
            y + 15f,
            "SENSORKETTEN-SAMPLES & DAUER",
            "${sr.totalSamples} Messpunkte in ${String.format(Locale.US, "%.2fs", sr.durationSeconds)} (Max Gap: ${sr.maxGapMillis}ms)",
            statLabelPaint,
            statValPaint
        )
        drawMetaPair(
            canvas,
            PAGE_WIDTH / 2f + 10f,
            y + 15f,
            "3D-PARALLAXE / ANTI-SCREEN-SPOOFING",
            "Score: ${sr.parallaxConfidenceScore}% | Varianz: ${String.format(Locale.US, "%.3f", sr.accelVariance)} | Peak ω: ${String.format(Locale.US, "%.2f rad/s", sr.gyroPeakRadSec)}",
            statLabelPaint,
            statValPaint
        )
        drawMetaPair(
            canvas,
            MARGIN + 10f,
            y + 37f,
            "UMGEBUNGSSENSOREN (BAROMETER / LICHT)",
            "Luftdruck: $pressureStr • Beleuchtungsstärke: $lightStr • Sensorketten-SHA256: ${sr.sensorChainSha256.take(24)}…",
            statLabelPaint,
            statValPaint
        )

        y += 64f

        // 4. Kryptografischer Signaturblock (Monospace) + Verifikations-QR-Code
        canvas.drawText(
            "2. KRYPTOGRAFISCHER HARDWARE-SIGNATURBLOCK & VERIFIKATIONS-QR-SIEGEL",
            MARGIN,
            y,
            sectionTitlePaint
        )
        y += 8f

        val cryptoHeight = 335f
        val qrPanelWidth = 175f
        val sigBoxWidth = (PAGE_WIDTH - 2 * MARGIN) - qrPanelWidth - 10f

        val sigRect = RectF(MARGIN, y, MARGIN + sigBoxWidth, y + cryptoHeight)
        fillPaint.color = Color.parseColor("#0F172A")
        canvas.drawRoundRect(sigRect, 6f, 6f, fillPaint)

        val monoLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#34D399")
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textSize = 7.8f
        }
        val monoBodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E2E8F0")
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
            textSize = 7.2f
        }

        var sigY = y + 16f
        val sigLeft = MARGIN + 10f

        canvas.drawText(">> KOMBINIERTER SHA-256 PAYLOAD HASH (GESAMTPAKET):", sigLeft, sigY, monoLabelPaint)
        sigY += 11f
        sigY = drawWrappedMonospace(canvas, req.cryptoSeal.payloadSha256Hex, sigLeft, sigY, 52, 9.8f, monoBodyPaint)
        sigY += 4f

        canvas.drawText(">> VERSIEGELTE FOTO-KURZHASHES (IM QR-CODE VERANKERT):", sigLeft, sigY, monoLabelPaint)
        sigY += 11f
        val imgHashesText = qrPayload.imageHashes.joinToString(" | ")
        sigY = drawWrappedMonospace(canvas, imgHashesText, sigLeft, sigY, 52, 9.8f, monoBodyPaint)
        sigY += 4f

        canvas.drawText(
            ">> ECDSA (secp256r1 / SHA256withECDSA) HARDWARE-SIGNATUR (BASE64):",
            sigLeft,
            sigY,
            monoLabelPaint
        )
        sigY += 11f
        sigY = drawWrappedMonospace(canvas, req.cryptoSeal.signatureBase64, sigLeft, sigY, 52, 9.5f, monoBodyPaint)
        sigY += 4f

        canvas.drawText(">> PUBLIC KEY FINGERPRINT (SHA-256):", sigLeft, sigY, monoLabelPaint)
        sigY += 11f
        canvas.drawText(req.cryptoSeal.publicKeyFingerprint, sigLeft, sigY, monoBodyPaint)
        sigY += 13f

        canvas.drawText(">> X.509 PUBLIC KEY (BASE64):", sigLeft, sigY, monoLabelPaint)
        sigY += 11f
        drawWrappedMonospace(canvas, req.cryptoSeal.publicKeyBase64, sigLeft, sigY, 52, 9.5f, monoBodyPaint)

        // Rechts: Verifikations-QR-Code Panel
        val qrRect = RectF(
            MARGIN + sigBoxWidth + 10f,
            y,
            PAGE_WIDTH - MARGIN,
            y + cryptoHeight
        )
        fillPaint.color = COLOR_SLATE_BG
        canvas.drawRoundRect(qrRect, 6f, 6f, fillPaint)
        strokePaint.color = COLOR_EMERALD_LINE
        strokePaint.strokeWidth = 1.4f
        canvas.drawRoundRect(qrRect, 6f, 6f, strokePaint)

        val qrHeaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_EMERALD_DARK
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textSize = 8.8f
            textAlign = Paint.Align.CENTER
        }
        val qrCenterX = qrRect.centerX()
        canvas.drawText("OFFLINE-ECHTHEITSPRÜFUNG", qrCenterX, y + 18f, qrHeaderPaint)

        val qrBitmap = generateQrCodeBitmap(qrPayload.toCompactJson(), 340)
        val qrDestRect = RectF(
            qrRect.left + 12f,
            y + 28f,
            qrRect.right - 12f,
            y + 28f + (qrRect.width() - 24f)
        )
        canvas.drawBitmap(qrBitmap, null, qrDestRect, null)

        val qrHintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_TEXT_MUTED
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            textSize = 7.2f
            textAlign = Paint.Align.CENTER
        }
        var hintY = qrDestRect.bottom + 13f
        val hintLines = listOf(
            "Scanne diesen QR-Code oder",
            "lade das PDF direkt im",
            "ProofSeal 'Echtheits-Prüfer':",
            "• Prüft ECDSA-Signatur",
            "• Prüft Eigenes/Fremdgerät",
            "• Prüft alle Foto-SHA256",
            "• Prüft PDF-Binärstrom",
            "Chip: ${req.cryptoSeal.hardwareSecurityLevel}",
            "3D-Score: ${req.sensorReport.parallaxConfidenceScore}%"
        )
        for (line in hintLines) {
            canvas.drawText(line, qrCenterX, hintY, qrHintPaint)
            hintY += 10.2f
        }

        // 5. Rechtlicher Hinweis am Fußende
        val legalRect = RectF(MARGIN, PAGE_HEIGHT - 54f, PAGE_WIDTH - MARGIN, PAGE_HEIGHT - 18f)
        fillPaint.color = COLOR_AMBER_BG
        canvas.drawRoundRect(legalRect, 4f, 4f, fillPaint)
        strokePaint.color = COLOR_AMBER_TEXT
        strokePaint.strokeWidth = 0.8f
        canvas.drawRoundRect(legalRect, 4f, 4f, strokePaint)

        val legalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_AMBER_TEXT
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textSize = 7.8f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(
            "Technischer Anscheinsbeweis gemäß kryptografischer Hardware-Signatur.",
            PAGE_WIDTH / 2f,
            PAGE_HEIGHT - 39f,
            legalPaint
        )
        canvas.drawText(
            "Jede nachträgliche Veränderung der Daten bricht die mathematische Signatur.",
            PAGE_WIDTH / 2f,
            PAGE_HEIGHT - 26f,
            legalPaint
        )
    }

    private fun drawSensorChainChart(
        canvas: Canvas,
        box: RectF,
        req: PdfGenerationRequest
    ) {
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#0B1120")
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1f
            color = COLOR_SLATE_BORDER
        }
        canvas.drawRoundRect(box, 6f, 6f, bgPaint)
        canvas.drawRoundRect(box, 6f, 6f, borderPaint)

        val plotLeft = box.left + 38f
        val plotRight = box.right - 16f
        val plotTop = box.top + 28f
        val plotBottom = box.bottom - 26f
        val plotW = plotRight - plotLeft
        val plotH = plotBottom - plotTop

        val legendPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textSize = 7.8f
        }
        val accelLegendDot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = COLOR_EMERALD_LINE }
        val gyroLegendDot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = COLOR_CYAN_LINE }
        val trigLegendDot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#F59E0B") }

        canvas.drawCircle(plotLeft + 4f, box.top + 14f, 4f, accelLegendDot)
        canvas.drawText("Beschleunigung |a| (m/s²)", plotLeft + 12f, box.top + 17f, legendPaint)

        canvas.drawCircle(plotLeft + 145f, box.top + 14f, 4f, gyroLegendDot)
        canvas.drawText("Gyroskop-Drehrate |ω| (rad/s x4)", plotLeft + 153f, box.top + 17f, legendPaint)

        canvas.drawCircle(plotLeft + 320f, box.top + 14f, 4f, trigLegendDot)
        canvas.drawText("Foto-Auslösung (Keyframe-Zeitpunkt)", plotLeft + 328f, box.top + 17f, legendPaint)

        val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 0.6f
            color = Color.parseColor("#1E293B")
        }
        val axisLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#94A3B8")
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
            textSize = 6.8f
        }

        val maxScale = 22f
        val steps = listOf(0f, 5f, 10f, 15f, 20f)
        for (v in steps) {
            val gy = plotBottom - (v / maxScale) * plotH
            canvas.drawLine(plotLeft, gy, plotRight, gy, gridPaint)
            canvas.drawText(String.format(Locale.US, "%2.0f", v), box.left + 8f, gy + 2.5f, axisLabelPaint)
        }

        val points = req.sensorReport.graphPoints
        val totalDuration = req.sensorReport.durationSeconds.coerceAtLeast(0.5f)

        for (i in 0..4) {
            val frac = i / 4f
            val tx = plotLeft + frac * plotW
            val tVal = frac * totalDuration
            canvas.drawLine(tx, plotTop, tx, plotBottom, gridPaint)
            canvas.drawText(
                String.format(Locale.US, "+%.1fs", tVal),
                tx - 10f,
                box.bottom - 8f,
                axisLabelPaint
            )
        }

        if (points.size >= 2) {
            val accelLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 1.6f
                color = COLOR_EMERALD_LINE
            }
            val gyroLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 1.3f
                color = COLOR_CYAN_LINE
            }

            for (i in 1 until points.size) {
                val p0 = points[i - 1]
                val p1 = points[i]

                val x0 = plotLeft + (p0.offsetSeconds / totalDuration).coerceIn(0f, 1f) * plotW
                val x1 = plotLeft + (p1.offsetSeconds / totalDuration).coerceIn(0f, 1f) * plotW

                val ay0 = plotBottom - (p0.accelMagnitude.coerceIn(0f, maxScale) / maxScale) * plotH
                val ay1 = plotBottom - (p1.accelMagnitude.coerceIn(0f, maxScale) / maxScale) * plotH
                canvas.drawLine(x0, ay0, x1, ay1, accelLinePaint)

                val gy0 = plotBottom - ((p0.gyroMagnitude * 4f).coerceIn(0f, maxScale) / maxScale) * plotH
                val gy1 = plotBottom - ((p1.gyroMagnitude * 4f).coerceIn(0f, maxScale) / maxScale) * plotH
                canvas.drawLine(x0, gy0, x1, gy1, gyroLinePaint)
            }
        }

        val markerLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.3f
            color = Color.parseColor("#F59E0B")
            pathEffect = DashPathEffect(floatArrayOf(4f, 3f), 0f)
        }
        val markerTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FBBF24")
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textSize = 7.2f
        }

        req.keyframes.forEachIndexed { idx, kf ->
            val tSec = (kf.offsetMillis / 1000f).coerceIn(0f, totalDuration)
            val mx = plotLeft + (tSec / totalDuration).coerceIn(0.02f, 0.98f) * plotW
            canvas.drawLine(mx, plotTop, mx, plotBottom, markerLinePaint)
            val labelY = plotTop + 12f + (idx % 2) * 11f
            canvas.drawText(
                "KF#${kf.index} (${kf.formattedOffsetSeconds})",
                (mx + 3f).coerceAtMost(plotRight - 58f),
                labelY,
                markerTextPaint
            )
        }
    }

    private fun drawKeyframeBitmapInRect(canvas: Canvas, imagePath: String, targetRect: RectF) {
        val file = File(imagePath)
        if (!file.exists()) return

        try {
            val opts = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(file.absolutePath, opts)
            var sampleSize = 1
            while (opts.outWidth / sampleSize > 640 || opts.outHeight / sampleSize > 640) {
                sampleSize *= 2
            }
            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            val bitmap = BitmapFactory.decodeFile(file.absolutePath, decodeOpts) ?: return

            val bw = bitmap.width.toFloat()
            val bh = bitmap.height.toFloat()
            val rw = targetRect.width()
            val rh = targetRect.height()
            val scale = minOf(rw / bw, rh / bh)
            val drawW = bw * scale
            val drawH = bh * scale
            val left = targetRect.left + (rw - drawW) / 2f
            val top = targetRect.top + (rh - drawH) / 2f
            val dest = RectF(left, top, left + drawW, top + drawH)

            val bgPaint = Paint().apply { color = Color.parseColor("#0F172A") }
            canvas.drawRect(targetRect, bgPaint)
            canvas.drawBitmap(bitmap, Rect(0, 0, bitmap.width, bitmap.height), dest, null)
            bitmap.recycle()
        } catch (_: Exception) {
        }
    }

    private fun generateQrCodeBitmap(content: String, sizePx: Int): Bitmap {
        val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java).apply {
            put(EncodeHintType.CHARACTER_SET, "UTF-8")
            put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.L)
            put(EncodeHintType.MARGIN, 1)
        }
        val bitMatrix = MultiFormatWriter().encode(
            content,
            BarcodeFormat.QR_CODE,
            sizePx,
            sizePx,
            hints
        )
        val width = bitMatrix.width
        val height = bitMatrix.height
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            val offset = y * width
            for (x in 0 until width) {
                pixels[offset + x] = if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE
            }
        }
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, width, 0, 0, width, height)
        }
    }

    private fun drawMetaPair(
        canvas: Canvas,
        x: Float,
        y: Float,
        label: String,
        value: String,
        labelPaint: Paint,
        valuePaint: Paint
    ) {
        canvas.drawText(label, x, y, labelPaint)
        canvas.drawText(value, x, y + 10.5f, valuePaint)
    }

    private fun drawWrappedMonospace(
        canvas: Canvas,
        text: String,
        x: Float,
        startY: Float,
        charsPerLine: Int,
        lineHeight: Float,
        paint: Paint
    ): Float {
        var curY = startY
        var idx = 0
        while (idx < text.length) {
            val end = (idx + charsPerLine).coerceAtMost(text.length)
            canvas.drawText(text.substring(idx, end), x, curY, paint)
            curY += lineHeight
            idx = end
        }
        return curY
    }

    private fun truncate(text: String, maxLen: Int): String {
        val clean = text.replace("\n", " ").trim()
        return if (clean.length <= maxLen) clean else clean.take(maxLen - 1) + "…"
    }
}
