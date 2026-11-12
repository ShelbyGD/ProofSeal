package de.proofseal.app.model

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

enum class ProofMode(
    val id: String,
    val title: String,
    val shortTitle: String,
    val description: String,
    val primaryValueLabel: String,
    val primaryValueHint: String,
    val minRequiredKeyframes: Int,
    val maxKeyframes: Int,
    val steps: List<CaptureStepSpec>
) {
    PACKAGE_SHIPPING(
        id = "PACKAGE_SHIPPING",
        title = "Paket-Versand & Verkauf",
        shortTitle = "Paket & Verkauf",
        description = "3 Pflicht-Schritte ohne Unterbrechung der Sensorkette: Ware & Seriennummer → Einpacken & Verschließen → Versandetikett & Barcode.",
        primaryValueLabel = "Sendungsnummer / Seriennummer",
        primaryValueHint = "Wird per ML Kit Barcode & OCR automatisch erkannt",
        minRequiredKeyframes = 4,
        maxKeyframes = 4,
        steps = listOf(
            CaptureStepSpec(
                stepNumber = 1,
                phaseBadge = "SCHRITT 1/3 • WARE & SERIENNUMMER",
                title = "Keyframe 1: Ware & Seriennummer",
                instruction = "Zeige den Artikel und das Typenschild/Seriennummer. ML Kit OCR liest sichtbare Nummern automatisch aus.",
                requiresBarcodeBoost = false,
                requiresParallaxHint = false
            ),
            CaptureStepSpec(
                stepNumber = 2,
                phaseBadge = "SCHRITT 2/3 • EINPACKEN (SENSORKETTE LÄUFT)",
                title = "Keyframe 2: Offener Karton mit Ware",
                instruction = "Sensorkette läuft lückenlos weiter! Lege die Ware inkl. Polsterung in den offenen Karton und löse Keyframe 2 aus.",
                requiresBarcodeBoost = false,
                requiresParallaxHint = true
            ),
            CaptureStepSpec(
                stepNumber = 3,
                phaseBadge = "SCHRITT 2/3 • VERSCHLIESSEN",
                title = "Keyframe 3: Verschlossener Versandkarton",
                instruction = "Klebe den Karton ohne Unterbrechung der Aufnahme zu und dokumentiere das verschlossene Paket.",
                requiresBarcodeBoost = false,
                requiresParallaxHint = true
            ),
            CaptureStepSpec(
                stepNumber = 4,
                phaseBadge = "SCHRITT 3/3 • VERSANDETIKETT & BARCODE",
                title = "Keyframe 4: Versandlabel & Tracking-Code",
                instruction = "Halte die Kamera auf das aufgeklebte Versandetikett. Barcode-Scanner & OCR extrahieren Sendungsnummer und Empfänger.",
                requiresBarcodeBoost = true,
                requiresParallaxHint = false
            )
        )
    ),

    METER_HANDOVER(
        id = "METER_HANDOVER",
        title = "Zählerstand, Kfz & Übergabe",
        shortTitle = "Zähler & Übergabe",
        description = "Wohnung, Mietwagen, Strom-/Gas-/Wasserzähler mit 3D-Parallaxen-Prüfung (Anti-Screen-Spoofing) und Live-OCR.",
        primaryValueLabel = "Zählerstand / KM-Stand / Kennzeichen",
        primaryValueHint = "z. B. 14892.6 kWh oder M-AB 1234 (via OCR)",
        minRequiredKeyframes = 3,
        maxKeyframes = 4,
        steps = listOf(
            CaptureStepSpec(
                stepNumber = 1,
                phaseBadge = "AUFNAHME 1/4 • FRONTALE HAUPTANSICHT + OCR",
                title = "Keyframe 1: Zählerstand / Kennzeichen frontal",
                instruction = "Erfasse Zählerwerk, Display oder Kennzeichen scharf. OCR übernimmt den erkannten Wert direkt in das Prüffeld.",
                requiresBarcodeBoost = true,
                requiresParallaxHint = false
            ),
            CaptureStepSpec(
                stepNumber = 2,
                phaseBadge = "AUFNAHME 2/4 • 3D-PARALLAXE LINKS",
                title = "Keyframe 2: Seitliche 3D-Bewegung (Links)",
                instruction = "Bewege das Smartphone um ca. 15–30 cm seitlich im 3D-Raum, um ein echtes 3D-Objekt zu beweisen (Anti-Monitor-Check).",
                requiresBarcodeBoost = false,
                requiresParallaxHint = true
            ),
            CaptureStepSpec(
                stepNumber = 3,
                phaseBadge = "AUFNAHME 3/4 • 3D-PARALLAXE RECHTS",
                title = "Keyframe 3: Seitliche 3D-Bewegung (Rechts/Schräg)",
                instruction = "Schwenke leicht in die Gegenrichtung und löse Aufnahme 3 aus (mindestens 3 Bilder für Übergabe-Siegel).",
                requiresBarcodeBoost = false,
                requiresParallaxHint = true
            ),
            CaptureStepSpec(
                stepNumber = 4,
                phaseBadge = "AUFNAHME 4/4 • PLOMBE & GESAMTZUSTAND (OPTIONAL)",
                title = "Keyframe 4: Zählernummer / Umfeld / Zustand",
                instruction = "Optionales 4. Bild für Gehäuseplombe, Seriennummer oder Gesamtzustand vor der Versiegelung.",
                requiresBarcodeBoost = true,
                requiresParallaxHint = true
            )
        )
    ),

    INCIDENT_FAST(
        id = "INCIDENT_FAST",
        title = "Vorfall & Zustands-Beweis",
        shortTitle = "Vorfall-Schnellbeweis",
        description = "Sofort-Protokoll für Schäden, Liefermängel oder Sicherheitsvorfälle mit bis zu 4 Bildern, GPS, Zeugenfeld & Hardware-Siegel.",
        primaryValueLabel = "Aktenzeichen / Objekt-ID / Hauptmerkmal",
        primaryValueHint = "Erkannte Nummer, Ort oder Vorfallskennung",
        minRequiredKeyframes = 1,
        maxKeyframes = 4,
        steps = listOf(
            CaptureStepSpec(
                stepNumber = 1,
                phaseBadge = "BEWEISBILD 1/4 • HAUPTVORFALL & GPS",
                title = "Keyframe 1: Primäre Beweisaufnahme",
                instruction = "Nimm den Zustand oder Schaden auf. Sensorkette, Hardware-Uptime und GPS-Standort werden sofort verankert.",
                requiresBarcodeBoost = true,
                requiresParallaxHint = false
            ),
            CaptureStepSpec(
                stepNumber = 2,
                phaseBadge = "BEWEISBILD 2/4 • DETAIL & SPUREN",
                title = "Keyframe 2: Nahaufnahme / Beschädigung",
                instruction = "Dokumentiere Details, Seriennummern oder Siegelbrüche aus der Nähe.",
                requiresBarcodeBoost = true,
                requiresParallaxHint = true
            ),
            CaptureStepSpec(
                stepNumber = 3,
                phaseBadge = "BEWEISBILD 3/4 • 3D-RAUMPERSPEKTIVE",
                title = "Keyframe 3: Zweiter Blickwinkel",
                instruction = "Nimm das Objekt aus versetzter 3D-Perspektive auf, um den Parallaxen-Score zu maximieren.",
                requiresBarcodeBoost = false,
                requiresParallaxHint = true
            ),
            CaptureStepSpec(
                stepNumber = 4,
                phaseBadge = "BEWEISBILD 4/4 • KONTEXT & UMGEBUNG",
                title = "Keyframe 4: Abschließende Übersicht",
                instruction = "Letztes Übersichtsbild vor der sofortigen kryptografischen Hardware-Versiegelung.",
                requiresBarcodeBoost = false,
                requiresParallaxHint = true
            )
        )
    );

    companion object {
        fun fromId(id: String): ProofMode {
            return entries.firstOrNull { it.id == id } ?: PACKAGE_SHIPPING
        }
    }
}

data class CaptureStepSpec(
    val stepNumber: Int,
    val phaseBadge: String,
    val title: String,
    val instruction: String,
    val requiresBarcodeBoost: Boolean,
    val requiresParallaxHint: Boolean
)

data class CapturedKeyframe(
    val index: Int,
    val stepTitle: String,
    val imageFilePath: String,
    val imageSha256: String,
    val offsetMillis: Long,
    val elapsedRealtimeNanos: Long,
    val detectedOcrText: String,
    val detectedBarcodes: List<String>,
    val extractedKeyCandidate: String,
    val accelX: Float,
    val accelY: Float,
    val accelZ: Float,
    val gyroX: Float,
    val gyroY: Float,
    val gyroZ: Float
) {
    val formattedOffsetSeconds: String
        get() = String.format(Locale.US, "+%.2fs", offsetMillis / 1000.0)

    val shortSha256: String
        get() = if (imageSha256.length >= 16) {
            "${imageSha256.take(8)}…${imageSha256.takeLast(8)}"
        } else {
            imageSha256
        }

    fun toJson(): JSONObject = JSONObject().apply {
        put("index", index)
        put("stepTitle", stepTitle)
        put("imageFilePath", imageFilePath)
        put("imageSha256", imageSha256)
        put("offsetMillis", offsetMillis)
        put("elapsedRealtimeNanos", elapsedRealtimeNanos)
        put("detectedOcrText", detectedOcrText)
        put("detectedBarcodes", JSONArray(detectedBarcodes))
        put("extractedKeyCandidate", extractedKeyCandidate)
        put("accelX", accelX.toDouble())
        put("accelY", accelY.toDouble())
        put("accelZ", accelZ.toDouble())
        put("gyroX", gyroX.toDouble())
        put("gyroY", gyroY.toDouble())
        put("gyroZ", gyroZ.toDouble())
    }

    companion object {
        fun fromJson(obj: JSONObject): CapturedKeyframe {
            val barcodesArr = obj.optJSONArray("detectedBarcodes") ?: JSONArray()
            val barcodes = mutableListOf<String>()
            for (i in 0 until barcodesArr.length()) {
                barcodes.add(barcodesArr.optString(i))
            }
            return CapturedKeyframe(
                index = obj.optInt("index", 1),
                stepTitle = obj.optString("stepTitle", ""),
                imageFilePath = obj.optString("imageFilePath", ""),
                imageSha256 = obj.optString("imageSha256", ""),
                offsetMillis = obj.optLong("offsetMillis", 0L),
                elapsedRealtimeNanos = obj.optLong("elapsedRealtimeNanos", 0L),
                detectedOcrText = obj.optString("detectedOcrText", ""),
                detectedBarcodes = barcodes,
                extractedKeyCandidate = obj.optString("extractedKeyCandidate", ""),
                accelX = obj.optDouble("accelX", 0.0).toFloat(),
                accelY = obj.optDouble("accelY", 0.0).toFloat(),
                accelZ = obj.optDouble("accelZ", 0.0).toFloat(),
                gyroX = obj.optDouble("gyroX", 0.0).toFloat(),
                gyroY = obj.optDouble("gyroY", 0.0).toFloat(),
                gyroZ = obj.optDouble("gyroZ", 0.0).toFloat()
            )
        }
    }
}

data class SensorGraphPoint(
    val offsetSeconds: Float,
    val accelMagnitude: Float,
    val gyroMagnitude: Float,
    val accelX: Float,
    val accelY: Float,
    val accelZ: Float,
    val gyroX: Float,
    val gyroY: Float,
    val gyroZ: Float,
    val pressureHpa: Float,
    val lightLux: Float
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("t", offsetSeconds.toDouble())
        put("a", accelMagnitude.toDouble())
        put("g", gyroMagnitude.toDouble())
        put("ax", accelX.toDouble())
        put("ay", accelY.toDouble())
        put("az", accelZ.toDouble())
        put("gx", gyroX.toDouble())
        put("gy", gyroY.toDouble())
        put("gz", gyroZ.toDouble())
        put("p", pressureHpa.toDouble())
        put("l", lightLux.toDouble())
    }

    companion object {
        fun fromJson(obj: JSONObject): SensorGraphPoint = SensorGraphPoint(
            offsetSeconds = obj.optDouble("t", 0.0).toFloat(),
            accelMagnitude = obj.optDouble("a", 9.81).toFloat(),
            gyroMagnitude = obj.optDouble("g", 0.0).toFloat(),
            accelX = obj.optDouble("ax", 0.0).toFloat(),
            accelY = obj.optDouble("ay", 0.0).toFloat(),
            accelZ = obj.optDouble("az", 9.81).toFloat(),
            gyroX = obj.optDouble("gx", 0.0).toFloat(),
            gyroY = obj.optDouble("gy", 0.0).toFloat(),
            gyroZ = obj.optDouble("gz", 0.0).toFloat(),
            pressureHpa = obj.optDouble("p", 0.0).toFloat(),
            lightLux = obj.optDouble("l", 0.0).toFloat()
        )
    }
}

data class SensorIntegrityReport(
    val isContinuous: Boolean,
    val maxGapMillis: Long,
    val totalSamples: Int,
    val durationSeconds: Float,
    val startElapsedNanos: Long,
    val endElapsedNanos: Long,
    val parallaxConfidenceScore: Int,
    val accelVariance: Float,
    val gyroPeakRadSec: Float,
    val averagePressureHpa: Float?,
    val averageLightLux: Float?,
    val sensorChainSha256: String,
    val graphPoints: List<SensorGraphPoint>,
    val keyframeTriggerSeconds: List<Float>
)

data class QrSealPayload(
    val version: Int = 1,
    val protocolId: String,
    val timestampUtc: String,
    val deviceModel: String,
    val securityLevel: String,
    val modeTitle: String,
    val primaryOcrValue: String,
    val parallaxScore: Int,
    val isContinuous: Boolean,
    val payloadHash: String,
    val signatureBase64: String,
    val publicKeyBase64: String
) {
    fun toCompactJson(): String {
        return JSONObject().apply {
            put("app", "ProofSeal")
            put("v", version)
            put("id", protocolId)
            put("ts", timestampUtc)
            put("dev", deviceModel)
            put("sec", securityLevel)
            put("mode", modeTitle)
            put("ocr", primaryOcrValue)
            put("par", parallaxScore)
            put("cont", isContinuous)
            put("hash", payloadHash)
            put("sig", signatureBase64)
            put("pub", publicKeyBase64)
        }.toString()
    }

    companion object {
        fun fromJsonOrNull(rawJson: String): QrSealPayload? {
            return try {
                val obj = JSONObject(rawJson.trim())
                if (!obj.has("hash") || !obj.has("sig") || !obj.has("pub")) {
                    return null
                }
                QrSealPayload(
                    version = obj.optInt("v", 1),
                    protocolId = obj.optString("id", "UNBEKANNT"),
                    timestampUtc = obj.optString("ts", ""),
                    deviceModel = obj.optString("dev", "Unbekanntes Gerät"),
                    securityLevel = obj.optString("sec", "Android Keystore"),
                    modeTitle = obj.optString("mode", "Beweis-Protokoll"),
                    primaryOcrValue = obj.optString("ocr", "-"),
                    parallaxScore = obj.optInt("par", 0),
                    isContinuous = obj.optBoolean("cont", true),
                    payloadHash = obj.getString("hash"),
                    signatureBase64 = obj.getString("sig"),
                    publicKeyBase64 = obj.getString("pub")
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}
