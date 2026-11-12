package de.proofseal.app.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.os.Build
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.proofseal.app.crypto.HardwareCryptoManager
import de.proofseal.app.data.ProofDatabase
import de.proofseal.app.data.ProofRecordEntity
import de.proofseal.app.model.CapturedKeyframe
import de.proofseal.app.model.ProofMode
import de.proofseal.app.model.QrSealPayload
import de.proofseal.app.pdf.PdfGenerationRequest
import de.proofseal.app.pdf.ProofPdfGenerator
import de.proofseal.app.sensor.SensorChainRecorder
import de.proofseal.app.sensor.SensorLiveSnapshot
import de.proofseal.app.util.LocationHelper
import de.proofseal.app.util.MlKitAnalyzerHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

data class CaptureSessionUiState(
    val protocolId: String = UUID.randomUUID().toString(),
    val mode: ProofMode = ProofMode.PACKAGE_SHIPPING,
    val currentStepIndex: Int = 0,
    val capturedKeyframes: List<CapturedKeyframe> = emptyList(),
    val protocolTitle: String = "",
    val primaryOcrValue: String = "",
    val witnessName: String = "",
    val userNotes: String = "",
    val gpsLocationText: String = "GPS wird ermittelt…",
    val liveOcrPreviewHint: String = "",
    val liveBarcodePreviewHint: String = "",
    val isCapturingKeyframe: Boolean = false,
    val isSealingProtocol: Boolean = false,
    val isReadyToSealSheetOpen: Boolean = false,
    val errorMessage: String? = null
)

data class HardwareKeystoreInfo(
    val securityLevelLabel: String = "Hardware Keystore wird geprüft…",
    val isStrongBoxBacked: Boolean = false,
    val publicKeyFingerprint: String = "…"
)

data class SealVerificationResult(
    val isValidSignature: Boolean,
    val payload: QrSealPayload?,
    val computedKeyFingerprint: String,
    val verificationTimestamp: String,
    val errorReason: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class ProofSealViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val dao = ProofDatabase.getInstance(appContext).proofRecordDao()
    val cryptoManager = HardwareCryptoManager(appContext)
    private val sensorRecorder = SensorChainRecorder(appContext)

    val sensorLiveSnapshot: StateFlow<SensorLiveSnapshot> = sensorRecorder.liveSnapshot

    private val _hardwareInfo = MutableStateFlow(HardwareKeystoreInfo())
    val hardwareInfo: StateFlow<HardwareKeystoreInfo> = _hardwareInfo.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val records: StateFlow<List<ProofRecordEntity>> = _searchQuery
        .flatMapLatest { query ->
            if (query.isBlank()) {
                dao.getAllRecords()
            } else {
                dao.searchRecords(query.trim())
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _captureState = MutableStateFlow(CaptureSessionUiState())
    val captureState: StateFlow<CaptureSessionUiState> = _captureState.asStateFlow()

    private val _verificationResult = MutableStateFlow<SealVerificationResult?>(null)
    val verificationResult: StateFlow<SealVerificationResult?> = _verificationResult.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val (level, strongBox) = cryptoManager.getHardwareSecurityStatus()
            val fp = cryptoManager.getPublicKeyFingerprint()
            _hardwareInfo.value = HardwareKeystoreInfo(
                securityLevelLabel = level,
                isStrongBoxBacked = strongBox,
                publicKeyFingerprint = fp
            )
        }
    }

    fun updateSearchQuery(newQuery: String) {
        _searchQuery.value = newQuery
    }

    fun startNewCaptureSession(mode: ProofMode) {
        val defaultTitle = when (mode) {
            ProofMode.PACKAGE_SHIPPING -> "Paket-Versand & Verkauf"
            ProofMode.METER_HANDOVER -> "Zähler- & Objekt-Übergabe"
            ProofMode.INCIDENT_FAST -> "Vorfall & Zustands-Beweis"
        }
        val gps = LocationHelper.resolveCurrentLocationString(appContext)
        _captureState.value = CaptureSessionUiState(
            protocolId = UUID.randomUUID().toString(),
            mode = mode,
            currentStepIndex = 0,
            capturedKeyframes = emptyList(),
            protocolTitle = defaultTitle,
            primaryOcrValue = "",
            witnessName = "",
            userNotes = "",
            gpsLocationText = gps,
            liveOcrPreviewHint = "",
            liveBarcodePreviewHint = "",
            isCapturingKeyframe = false,
            isSealingProtocol = false,
            isReadyToSealSheetOpen = false,
            errorMessage = null
        )
        sensorRecorder.startRecording()
    }

    fun refreshGpsLocation() {
        val gps = LocationHelper.resolveCurrentLocationString(appContext)
        _captureState.update { it.copy(gpsLocationText = gps) }
    }

    fun onLiveAnalyzerHint(ocrHint: String, barcodeHint: String) {
        _captureState.update { state ->
            state.copy(
                liveOcrPreviewHint = ocrHint.ifBlank { state.liveOcrPreviewHint },
                liveBarcodePreviewHint = barcodeHint.ifBlank { state.liveBarcodePreviewHint }
            )
        }
    }

    fun updateProtocolTitle(title: String) {
        _captureState.update { it.copy(protocolTitle = title) }
    }

    fun updatePrimaryOcrValue(value: String) {
        _captureState.update { it.copy(primaryOcrValue = value) }
    }

    fun updateWitnessName(witness: String) {
        _captureState.update { it.copy(witnessName = witness) }
    }

    fun updateUserNotes(notes: String) {
        _captureState.update { it.copy(userNotes = notes) }
    }

    fun setReadyToSealSheetOpen(open: Boolean) {
        _captureState.update { it.copy(isReadyToSealSheetOpen = open) }
    }

    fun clearCaptureError() {
        _captureState.update { it.copy(errorMessage = null) }
    }

    fun abortCaptureSession() {
        sensorRecorder.stopRecording()
    }

    /**
     * Verarbeitet das aufgenommene Keyframe-Bitmap:
     * 1. Speichert komprimiertes Bild im internen App-Speicher und berechnet den individuellen Bild-SHA256.
     * 2. Markiert den exakten Millisekunden-Offset in der laufenden Sensorkette.
     * 3. Führt On-Device ML Kit OCR & Barcode-Scanning aus.
     * 4. Schaltet automatisch zum nächsten Beweisschritt weiter.
     */
    fun processCapturedKeyframeBitmap(bitmap: Bitmap) {
        val currentState = _captureState.value
        if (currentState.isCapturingKeyframe || currentState.isSealingProtocol) return
        if (currentState.capturedKeyframes.size >= currentState.mode.maxKeyframes) return

        _captureState.update { it.copy(isCapturingKeyframe = true, errorMessage = null) }

        viewModelScope.launch {
            try {
                val stepIndex = currentState.capturedKeyframes.size
                val stepSpec = currentState.mode.steps.getOrElse(stepIndex) {
                    currentState.mode.steps.last()
                }

                val offsetMillis = sensorRecorder.markKeyframeCaptured()
                val elapsedNanos = SystemClock.elapsedRealtimeNanos()
                val motionVec = sensorRecorder.getCurrentMotionVector()

                val newKeyframe = withContext(Dispatchers.IO) {
                    // 1. JPEG-Komprimierung & SHA-256 Berechnung
                    val baos = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 84, baos)
                    val jpegBytes = baos.toByteArray()
                    val imageSha256 = HardwareCryptoManager.sha256Hex(jpegBytes)

                    val imgDir = File(appContext.filesDir, "images").apply { mkdirs() }
                    val imgFile = File(
                        imgDir,
                        "kf_${currentState.protocolId.take(8)}_${stepIndex + 1}_${System.currentTimeMillis()}.jpg"
                    )
                    FileOutputStream(imgFile).use { it.write(jpegBytes) }

                    // 2. ML Kit On-Device OCR & Barcode-Analyse
                    val mlResult = MlKitAnalyzerHelper.analyzeCapturedBitmap(bitmap, currentState.mode)

                    CapturedKeyframe(
                        index = stepIndex + 1,
                        stepTitle = stepSpec.title,
                        imageFilePath = imgFile.absolutePath,
                        imageSha256 = imageSha256,
                        offsetMillis = offsetMillis,
                        elapsedRealtimeNanos = elapsedNanos,
                        detectedOcrText = mlResult.fullOcrText,
                        detectedBarcodes = mlResult.detectedBarcodes,
                        extractedKeyCandidate = mlResult.primaryCandidate,
                        accelX = motionVec[0],
                        accelY = motionVec[1],
                        accelZ = motionVec[2],
                        gyroX = motionVec[3],
                        gyroY = motionVec[4],
                        gyroZ = motionVec[5]
                    )
                }

                _captureState.update { state ->
                    val updatedList = state.capturedKeyframes + newKeyframe
                    val nextStepIdx = updatedList.size.coerceAtMost(state.mode.maxKeyframes - 1)

                    // Übernehme neu erkannten Barcode oder OCR-Hauptwert automatisch,
                    // insbesondere im letzten Schritt (Versandlabel) oder wenn noch leer
                    val newPrimaryValue = when {
                        newKeyframe.detectedBarcodes.isNotEmpty() ->
                            newKeyframe.detectedBarcodes.first()
                        state.primaryOcrValue.isBlank() && newKeyframe.extractedKeyCandidate.isNotBlank() ->
                            newKeyframe.extractedKeyCandidate
                        state.mode == ProofMode.PACKAGE_SHIPPING &&
                            updatedList.size == 4 &&
                            newKeyframe.extractedKeyCandidate.isNotBlank() ->
                            newKeyframe.extractedKeyCandidate
                        else -> state.primaryOcrValue
                    }

                    val autoOpenSealDialog = updatedList.size >= state.mode.maxKeyframes

                    state.copy(
                        capturedKeyframes = updatedList,
                        currentStepIndex = nextStepIdx,
                        primaryOcrValue = newPrimaryValue,
                        isCapturingKeyframe = false,
                        isReadyToSealSheetOpen = autoOpenSealDialog
                    )
                }
            } catch (e: Exception) {
                _captureState.update {
                    it.copy(
                        isCapturingKeyframe = false,
                        errorMessage = "Fehler bei Keyframe-Verarbeitung: ${e.localizedMessage}"
                    )
                }
            }
        }
    }

    /**
     * Beendet die lückenlose Sensorkette, prüft die 3D-Parallaxe & Kontinuität,
     * signiert das Gesamtpaket mit dem Android Hardware-Keystore (StrongBox/TEE),
     * generiert das 2-seitige WhatsApp-feste DIN-A4 PDF und speichert alles in Room.
     */
    fun finalizeAndSealProtocol(onSealedSuccess: (String) -> Unit) {
        val state = _captureState.value
        if (state.isSealingProtocol || state.capturedKeyframes.isEmpty()) return

        _captureState.update { it.copy(isSealingProtocol = true, errorMessage = null) }

        viewModelScope.launch {
            try {
                val createdRecordId = withContext(Dispatchers.IO) {
                    // 1. Sensorkette beenden & Integritätsbericht berechnen
                    val sensorReport = sensorRecorder.stopRecording()

                    val nowInstant = Instant.now()
                    val utcIso = DateTimeFormatter.ISO_INSTANT.format(nowInstant.atOffset(ZoneOffset.UTC))
                    val localFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss", Locale.GERMANY)
                        .withZone(ZoneId.systemDefault())
                    val localFormatted = localFormatter.format(nowInstant)

                    val startSec = sensorReport.startElapsedNanos / 1_000_000_000.0
                    val endSec = sensorReport.endElapsedNanos / 1_000_000_000.0
                    val uptimeSpanFormatted = String.format(
                        Locale.US,
                        "%.2fs -> %.2fs (Δ %.2fs)",
                        startSec,
                        endSec,
                        sensorReport.durationSeconds
                    )

                    val deviceModel = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL} (Android ${Build.VERSION.RELEASE})"
                    val gpsText = LocationHelper.resolveCurrentLocationString(appContext)

                    val allBarcodes = state.capturedKeyframes
                        .flatMap { it.detectedBarcodes }
                        .distinct()
                    val fullOcrSummary = state.capturedKeyframes
                        .mapNotNull { kf ->
                            kf.detectedOcrText.takeIf { it.isNotBlank() }?.let { "[#${kf.index}] $it" }
                        }
                        .joinToString(" | ")
                    val effectivePrimaryOcr = state.primaryOcrValue.trim().ifBlank {
                        allBarcodes.firstOrNull()
                            ?: state.capturedKeyframes.firstNotNullOfOrNull { it.extractedKeyCandidate.takeIf { c -> c.isNotBlank() } }
                            ?: "Kein Code erkannt"
                    }
                    val effectiveTitle = state.protocolTitle.trim().ifBlank { state.mode.title }

                    // 2. Kanonisches Beweispaket bauen & im Hardware-Keystore signieren
                    val canonicalBytes = cryptoManager.buildCanonicalEvidencePayload(
                        protocolId = state.protocolId,
                        modeId = state.mode.id,
                        createdAtUtcIso = utcIso,
                        deviceModel = deviceModel,
                        gpsCoordinates = gpsText,
                        primaryOcrValue = effectivePrimaryOcr,
                        witnessName = state.witnessName,
                        userNotes = state.userNotes,
                        keyframes = state.capturedKeyframes,
                        sensorReport = sensorReport
                    )

                    val cryptoSeal = cryptoManager.signPayload(canonicalBytes)

                    // 3. 2-Seiten DIN-A4 Beweis-PDF generieren
                    val pdfReq = PdfGenerationRequest(
                        protocolId = state.protocolId,
                        title = effectiveTitle,
                        mode = state.mode,
                        createdAtLocalFormatted = localFormatted,
                        createdAtUtcIso = utcIso,
                        uptimeSpanFormatted = uptimeSpanFormatted,
                        deviceModel = deviceModel,
                        gpsLocationText = gpsText,
                        primaryOcrValue = effectivePrimaryOcr,
                        fullOcrSummary = fullOcrSummary,
                        detectedBarcodes = allBarcodes,
                        witnessName = state.witnessName.trim(),
                        userNotes = state.userNotes.trim(),
                        keyframes = state.capturedKeyframes,
                        sensorReport = sensorReport,
                        cryptoSeal = cryptoSeal
                    )
                    val pdfFile = ProofPdfGenerator.generateProofPdf(appContext, pdfReq)

                    // 4. JSON-Serialisierung der Keyframes & Sensorpunkte für Room
                    val kfJsonArr = JSONArray()
                    state.capturedKeyframes.forEach { kfJsonArr.put(it.toJson()) }

                    val graphJsonArr = JSONArray()
                    sensorReport.graphPoints.forEach { graphJsonArr.put(it.toJson()) }

                    val entity = ProofRecordEntity(
                        protocolId = state.protocolId,
                        title = effectiveTitle,
                        modeId = state.mode.id,
                        modeTitle = state.mode.title,
                        createdAtEpochMillis = System.currentTimeMillis(),
                        createdAtUtcIso = utcIso,
                        createdAtFormatted = localFormatted,
                        uptimeSpanFormatted = uptimeSpanFormatted,
                        startElapsedNanos = sensorReport.startElapsedNanos,
                        endElapsedNanos = sensorReport.endElapsedNanos,
                        durationSeconds = sensorReport.durationSeconds,
                        deviceModel = deviceModel,
                        hardwareSecurityLevel = cryptoSeal.hardwareSecurityLevel,
                        isStrongBoxBacked = cryptoSeal.isStrongBoxBacked,
                        gpsLocationText = gpsText,
                        parallaxConfidenceScore = sensorReport.parallaxConfidenceScore,
                        isSensorChainContinuous = sensorReport.isContinuous,
                        maxSensorGapMillis = sensorReport.maxGapMillis,
                        totalSensorSamples = sensorReport.totalSamples,
                        sensorChainSha256 = sensorReport.sensorChainSha256,
                        primaryOcrValue = effectivePrimaryOcr,
                        fullOcrSummary = fullOcrSummary,
                        detectedBarcodesJoined = allBarcodes.joinToString(", "),
                        witnessName = state.witnessName.trim(),
                        userNotes = state.userNotes.trim(),
                        payloadSha256Hex = cryptoSeal.payloadSha256Hex,
                        signatureBase64 = cryptoSeal.signatureBase64,
                        publicKeyBase64 = cryptoSeal.publicKeyBase64,
                        publicKeyFingerprint = cryptoSeal.publicKeyFingerprint,
                        pdfFilePath = pdfFile.absolutePath,
                        previewImagePath = state.capturedKeyframes.firstOrNull()?.imageFilePath.orEmpty(),
                        keyframesJson = kfJsonArr.toString(),
                        sensorGraphJson = graphJsonArr.toString()
                    )

                    dao.insertRecord(entity)
                    entity.protocolId
                }

                _captureState.update {
                    it.copy(
                        isSealingProtocol = false,
                        isReadyToSealSheetOpen = false
                    )
                }
                onSealedSuccess(createdRecordId)
            } catch (e: Exception) {
                _captureState.update {
                    it.copy(
                        isSealingProtocol = false,
                        errorMessage = "Fehler bei der Hardware-Versiegelung: ${e.localizedMessage}"
                    )
                }
            }
        }
    }

    fun verifyScannedQrContent(rawQrText: String) {
        val nowFormatted = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss", Locale.GERMANY)
            .withZone(ZoneId.systemDefault())
            .format(Instant.now())

        val parsedPayload = QrSealPayload.fromJsonOrNull(rawQrText)
        if (parsedPayload == null) {
            _verificationResult.value = SealVerificationResult(
                isValidSignature = false,
                payload = null,
                computedKeyFingerprint = "-",
                verificationTimestamp = nowFormatted,
                errorReason = "Kein gültiges ProofSeal-Zertifikats-JSON im QR-Code erkannt."
            )
            return
        }

        val isValid = cryptoManager.verifySeal(
            payloadHash = parsedPayload.payloadHash,
            signatureBase64 = parsedPayload.signatureBase64,
            publicKeyBase64 = parsedPayload.publicKeyBase64
        )

        val keyFingerprint = try {
            val pubBytes = android.util.Base64.decode(
                parsedPayload.publicKeyBase64.trim(),
                android.util.Base64.NO_WRAP
            )
            HardwareCryptoManager.formatFingerprint(pubBytes)
        } catch (_: Exception) {
            "UNBEKANNT"
        }

        _verificationResult.value = SealVerificationResult(
            isValidSignature = isValid,
            payload = parsedPayload,
            computedKeyFingerprint = keyFingerprint,
            verificationTimestamp = nowFormatted,
            errorReason = if (isValid) {
                null
            } else {
                "Kryptografischer Signaturbruch! Hash oder ECDSA-Signatur stimmt nicht mit dem Public Key überein."
            }
        )
    }

    fun clearVerificationResult() {
        _verificationResult.value = null
    }

    fun deleteRecord(record: ProofRecordEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                File(record.pdfFilePath).takeIf { it.exists() }?.delete()
                record.parseKeyframes().forEach { kf ->
                    File(kf.imageFilePath).takeIf { it.exists() }?.delete()
                }
            } catch (_: Exception) {
            }
            dao.deleteRecord(record)
        }
    }
}
