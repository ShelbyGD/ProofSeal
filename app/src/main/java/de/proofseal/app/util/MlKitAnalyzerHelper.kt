package de.proofseal.app.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import de.proofseal.app.model.ProofMode
import kotlinx.coroutines.tasks.await
import java.nio.ByteBuffer

data class MlKitScanResult(
    val fullOcrText: String,
    val detectedBarcodes: List<String>,
    val primaryCandidate: String
)

object MlKitAnalyzerHelper {

    private val textRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    private val barcodeScanner by lazy {
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
            .build()
        BarcodeScanning.getClient(options)
    }

    private val qrOnlyScanner by lazy {
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
        BarcodeScanning.getClient(options)
    }

    /**
     * Führt auf einem aufgenommenen Bitmap-Keyframe synchronisiert On-Device ML Kit OCR
     * und Barcode-Scanning aus und extrahiert intelligent Seriennummern, Sendungsnummern,
     * Zählerstände oder Kfz-Kennzeichen.
     */
    suspend fun analyzeCapturedBitmap(bitmap: Bitmap, mode: ProofMode): MlKitScanResult {
        val inputImage = InputImage.fromBitmap(bitmap, 0)

        val ocrText = try {
            val visionText = textRecognizer.process(inputImage).await()
            visionText.text.trim()
        } catch (e: Exception) {
            ""
        }

        val barcodes = try {
            val results = barcodeScanner.process(inputImage).await()
            results.mapNotNull { it.rawValue?.trim() }.filter { it.isNotEmpty() }.distinct()
        } catch (e: Exception) {
            emptyList()
        }

        val candidate = extractBestCandidate(ocrText, barcodes, mode)
        return MlKitScanResult(
            fullOcrText = ocrText,
            detectedBarcodes = barcodes,
            primaryCandidate = candidate
        )
    }

    /**
     * Extrahiert aus dem OCR-Text und den gescannten Barcodes den wichtigsten Beweiswert
     * passend zum gewählten Modus (Sendungsnummer, Seriennummer, Zählerstand, Kennzeichen).
     */
    fun extractBestCandidate(
        ocrText: String,
        barcodes: List<String>,
        mode: ProofMode
    ): String {
        // 1. Wenn ein Barcode gefunden wurde (und kein ProofSeal-JSON selbst ist), hat er höchste Priorität
        val cleanBarcode = barcodes.firstOrNull { !it.startsWith("{") && it.length >= 4 }
        if (cleanBarcode != null && mode == ProofMode.PACKAGE_SHIPPING) {
            return "BARCODE: $cleanBarcode"
        }

        val lines = ocrText.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        if (lines.isEmpty()) {
            return cleanBarcode ?: ""
        }

        when (mode) {
            ProofMode.PACKAGE_SHIPPING -> {
                // Suche nach expliziten S/N, IMEI, Sendungsnummer-Mustern
                val serialRegex = Regex(
                    """(?i)(?:S/N|SN|Seriennr\.?|IMEI|Sendungsnr\.?|Tracking|Paket-Nr\.?)[:\s#-]*([A-Z0-9-]{5,28})"""
                )
                for (line in lines) {
                    val m = serialRegex.find(line)
                    if (m != null) {
                        return m.groupValues[1].trim()
                    }
                }
                // Suche nach typischer 10-22 stelliger Tracking-/Seriennummer
                val codeRegex = Regex("""\b[A-Z0-9]{8,24}\b""")
                for (line in lines) {
                    val normalized = line.replace(" ", "")
                    val m = codeRegex.find(normalized)
                    if (m != null && m.value.any { it.isDigit() }) {
                        return m.value
                    }
                }
            }

            ProofMode.METER_HANDOVER -> {
                // Suche nach Zählerständen (z. B. 12458,7 kWh / m3) oder Kfz-Kennzeichen
                val meterWithUnitRegex = Regex(
                    """(?i)\b(\d{3,9}(?:[.,]\d{1,3})?)\s*(kWh|MWh|m3|m³|km|hPa)\b"""
                )
                for (line in lines) {
                    val m = meterWithUnitRegex.find(line)
                    if (m != null) {
                        return "${m.groupValues[1]} ${m.groupValues[2]}"
                    }
                }
                // Mehrstellige Zählerzahl (5-8 Ziffern)
                val digitsRegex = Regex("""\b\d{4,8}(?:[.,]\d{1,2})?\b""")
                for (line in lines) {
                    val m = digitsRegex.find(line)
                    if (m != null) {
                        return m.value
                    }
                }
            }

            ProofMode.INCIDENT_FAST -> {
                if (cleanBarcode != null) return cleanBarcode
            }
        }

        if (cleanBarcode != null) return cleanBarcode
        return lines.first().take(48)
    }

    /**
     * Konvertiert ein CameraX ImageProxy zuverlässig in ein gedrehtes und für die
     * PDF-Generierung optimal skaliertes Bitmap (maxDimension = 1280px -> PDF < 2,5 MB).
     */
    fun imageProxyToScaledBitmap(imageProxy: ImageProxy, maxDimension: Int = 1280): Bitmap {
        val rawBitmap = try {
            imageProxy.toBitmap()
        } catch (e: Exception) {
            val buffer: ByteBuffer = imageProxy.planes[0].buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888)
        }

        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        val rotatedBitmap = if (rotationDegrees != 0) {
            val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
            Bitmap.createBitmap(
                rawBitmap,
                0,
                0,
                rawBitmap.width,
                rawBitmap.height,
                matrix,
                true
            )
        } else {
            rawBitmap
        }

        val width = rotatedBitmap.width
        val height = rotatedBitmap.height
        val longestSide = maxOf(width, height)
        if (longestSide <= maxDimension) {
            return rotatedBitmap
        }

        val scale = maxDimension.toFloat() / longestSide.toFloat()
        val targetW = (width * scale).toInt().coerceAtLeast(1)
        val targetH = (height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(rotatedBitmap, targetW, targetH, true)
    }

    /**
     * Live-ImageAnalysis.Analyzer für den Echtheits-Prüfer (SealVerifierScreen),
     * der QR-Codes in Echtzeit erkennt.
     */
    fun createLiveQrScannerAnalyzer(onQrCodeDetected: (String) -> Unit): ImageAnalysis.Analyzer {
        return object : ImageAnalysis.Analyzer {
            private var lastProcessedMs = 0L

            @OptIn(ExperimentalGetImage::class)
            override fun analyze(imageProxy: ImageProxy) {
                val now = System.currentTimeMillis()
                if (now - lastProcessedMs < 250L) {
                    imageProxy.close()
                    return
                }
                val mediaImage = imageProxy.image
                if (mediaImage == null) {
                    imageProxy.close()
                    return
                }
                lastProcessedMs = now
                val inputImage = InputImage.fromMediaImage(
                    mediaImage,
                    imageProxy.imageInfo.rotationDegrees
                )
                qrOnlyScanner.process(inputImage)
                    .addOnSuccessListener { barcodes ->
                        val rawQr = barcodes.firstOrNull { !it.rawValue.isNullOrBlank() }?.rawValue
                        if (!rawQr.isNullOrBlank()) {
                            onQrCodeDetected(rawQr)
                        }
                    }
                    .addOnCompleteListener {
                        imageProxy.close()
                    }
            }
        }
    }

    /**
     * Live-Vorschau-Analyzer für die GuidedCaptureScreen-Kamera (zeigt erkannte Barcodes
     * und OCR-Snippets bereits im Sucher an).
     */
    fun createLivePreviewOverlayAnalyzer(
        onLiveDetection: (ocrHint: String, barcodeHint: String) -> Unit
    ): ImageAnalysis.Analyzer {
        return object : ImageAnalysis.Analyzer {
            private var lastAnalysedTimestamp = 0L

            @OptIn(ExperimentalGetImage::class)
            override fun analyze(imageProxy: ImageProxy) {
                val now = System.currentTimeMillis()
                if (now - lastAnalysedTimestamp < 650L) {
                    imageProxy.close()
                    return
                }
                val mediaImage = imageProxy.image
                if (mediaImage == null) {
                    imageProxy.close()
                    return
                }
                lastAnalysedTimestamp = now
                val inputImage = InputImage.fromMediaImage(
                    mediaImage,
                    imageProxy.imageInfo.rotationDegrees
                )

                barcodeScanner.process(inputImage)
                    .addOnSuccessListener { codes ->
                        val firstCode = codes.firstOrNull { !it.rawValue.isNullOrBlank() }?.rawValue ?: ""
                        textRecognizer.process(inputImage)
                            .addOnSuccessListener { textResult ->
                                val firstLine = textResult.text.lines()
                                    .map { it.trim() }
                                    .firstOrNull { it.length >= 3 }
                                    ?.take(42) ?: ""
                                if (firstLine.isNotEmpty() || firstCode.isNotEmpty()) {
                                    onLiveDetection(firstLine, firstCode)
                                }
                            }
                            .addOnCompleteListener {
                                imageProxy.close()
                            }
                    }
                    .addOnFailureListener {
                        imageProxy.close()
                    }
            }
        }
    }
}
