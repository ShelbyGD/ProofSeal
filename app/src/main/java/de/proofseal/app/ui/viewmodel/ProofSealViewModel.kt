package de.proofseal.app.ui.viewmodel

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.OpenableColumns
import android.util.Base64
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
import de.proofseal.app.ui.theme.AppThemeMode
import de.proofseal.app.util.LocationHelper
import de.proofseal.app.util.MlKitAnalyzerHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

enum class CertificateOriginStatus {
    OWN_DEVICE,
    FOREIGN_DEVICE,
    TAMPERED_OR_INVALID
}

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
    val originStatus: CertificateOriginStatus,
    val payload: QrSealPayload?,
    val computedKeyFingerprint: String,
    val localDeviceKeyFingerprint: String,
    val verificationTimestamp: String,
    val isDirectPdfCheck: Boolean = false,
    val pdfFileName: String? = null,
    val isPdfBinaryStreamIntact: Boolean? = null,
    val expectedPdfSha256: String? = null,
    val actualPdfSha256: String? = null,
    val errorReason: String? = null
)

data class SystemPreferencesUiState(
    val useDynamicColor: Boolean = false,
    val themeMode: AppThemeMode = AppThemeMode.DARK,
    val defaultWitnessName: String = ""
)

@OptIn(ExperimentalCoroutinesApi::class)
class ProofSealViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val uiPrefs = appContext.getSharedPreferences("proofseal_ui_prefs", Context.MODE_PRIVATE)
    private val dao = ProofDatabase.getInstance(appContext).proofRecordDao()
    val cryptoManager = HardwareCryptoManager(appContext)
    private val sensorRecorder = SensorChainRecorder(appContext)

    val sensorLiveSnapshot: StateFlow<SensorLiveSnapshot> = sensorRecorder.liveSnapshot

    private val _hardwareInfo = MutableStateFlow(HardwareKeystoreInfo())
    val hardwareInfo: StateFlow<HardwareKeystoreInfo> = _hardwareInfo.asStateFlow()

    private val _uiPreferences = MutableStateFlow(
        SystemPreferencesUiState(
            useDynamicColor = uiPrefs.getBoolean("use_dynamic_color", false),
            themeMode = runCatching {
                AppThemeMode.valueOf(
                    uiPrefs.getString("theme_mode", AppThemeMode.DARK.name) ?: AppThemeMode.DARK.name
                )
            }.getOrDefault(AppThemeMode.DARK),
            defaultWitnessName = uiPrefs.getString("default_witness_name", "") ?: ""
        )
    )
    val uiPreferences: StateFlow<SystemPreferencesUiState> = _uiPreferences.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedModeFilter = MutableStateFlow<String?>(null)
    val selectedModeFilter: StateFlow<String?> = _selectedModeFilter.asStateFlow()

    private val baseRecordsFlow = _searchQuery
        .flatMapLatest { query ->
            if (query.isBlank()) {
                dao.getAllRecords()
            } else {
                dao.searchRecords(query.trim())
            }
        }

    val records: StateFlow<List<ProofRecordEntity>> = combine(
        baseRecordsFlow,
        _selectedModeFilter
    ) { list, modeFilter ->
        if (modeFilter.isNullOrBlank()) {
            list
        } else {
            list.filter { it.modeId == modeFilter }
        }
    }.stateIn(
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

    fun setUseDynamicColor(enabled: Boolean) {
        uiPrefs.edit().putBoolean("use_dynamic_color", enabled).apply()
        _uiPreferences.update { it.copy(useDynamicColor = enabled) }
    }

    fun setThemeMode(mode: AppThemeMode) {
        uiPrefs.edit().putString("theme_mode", mode.name).apply()
        _uiPreferences.update { it.copy(themeMode = mode) }
    }

    fun setDefaultWitnessName(name: String) {
        uiPrefs.edit().putString("default_witness_name", name).apply()
        _uiPreferences.update { it.copy(defaultWitnessName = name) }
    }

    fun updateSearchQuery(newQuery: String) {
        _searchQuery.value = newQuery
    }

    fun updateModeFilter(modeId: String?) {
        _selectedModeFilter.value = modeId
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
            witnessName = _uiPreferences.value.defaultWitnessName,
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

    fun finalizeAndSealProtocol(onSealedSuccess: (String) -> Unit) {
        val state = _captureState.value
        if (state.isSealingProtocol || state.capturedKeyframes.isEmpty()) return

        _captureState.update { it.copy(isSealingProtocol = true, errorMessage = null) }

        viewModelScope.launch {
            try {
                val createdRecordId = withContext(Dispatchers.IO) {
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
                        "%.2fs → %.2fs (Δ %.2fs)",
                        startSec,
                        endSec,
                        sensorReport.durationSeconds
                    )

                    val deviceModel = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}"
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
                    }.take(64)
                    val effectiveTitle = state.protocolTitle.trim().ifBlank { state.mode.title }

                    val (securityLevelLabel, _) = cryptoManager.getHardwareSecurityStatus()
                    val localKeyFingerprint = cryptoManager.getPublicKeyFingerprint()

                    // Kanonisches Beweispaket inkl. aller 16-Zeichen Foto-SHA256-Kurzwerte und Geräte-Identität
                    val canonicalBytes = cryptoManager.buildCanonicalEvidencePayload(
                        protocolId = state.protocolId,
                        modeTitle = state.mode.shortTitle,
                        createdAtUtcIso = utcIso,
                        uptimeNanos = sensorReport.endElapsedNanos,
                        deviceModel = deviceModel,
                        securityLevel = securityLevelLabel,
                        primaryOcrValue = effectivePrimaryOcr,
                        keyframes = state.capturedKeyframes,
                        keyFingerprint = localKeyFingerprint
                    )

                    val cryptoSeal = cryptoManager.signPayload(canonicalBytes)

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
                    val pdfFile = ProofPdfGenerator.generateProofPdf(
                        context = appContext,
                        request = pdfReq,
                        cryptoManager = cryptoManager
                    )

                    val kfJsonArr = JSONArray()
                    state.capturedKeyframes.forEach { kfJsonArr.put(it.toJson()) }

                    val graphJsonArr = JSONArray()
                    sensorReport.graphPoints.forEach { graphJsonArr.put(it.toJson()) }

                    val entity = ProofRecordEntity(
                        protocolId = state.protocolId,
                        title = effectiveTitle,
                        modeId = state.mode.id,
                        modeTitle = state.mode.shortTitle,
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

    /**
     * Prüft ein gescanntes QR-Code-JSON mathematisch auf:
     * 1. Kanonische Feld- & Bild-Hash-Integrität + ECDSA-Signatur
     * 2. Geräte-Herkunft ("Eigenes Gerät" vs. "Fremdgerät")
     */
    fun verifyScannedQrContent(rawQrText: String) {
        val nowFormatted = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss", Locale.GERMANY)
            .withZone(ZoneId.systemDefault())
            .format(Instant.now())
        val localFp = cryptoManager.getPublicKeyFingerprint()

        val parsedPayload = QrSealPayload.fromJsonOrNull(rawQrText)
        if (parsedPayload == null) {
            _verificationResult.value = SealVerificationResult(
                isValidSignature = false,
                originStatus = CertificateOriginStatus.TAMPERED_OR_INVALID,
                payload = null,
                computedKeyFingerprint = "-",
                localDeviceKeyFingerprint = localFp,
                verificationTimestamp = nowFormatted,
                errorReason = "Kein gültiges ProofSeal-Zertifikats-JSON im QR-Code erkannt."
            )
            return
        }

        val (isValid, tamperError) = cryptoManager.verifyQrPayloadIntegrity(parsedPayload)

        val certKeyFingerprint = try {
            val pubBytes = Base64.decode(parsedPayload.publicKeyBase64.trim(), Base64.NO_WRAP)
            HardwareCryptoManager.formatFingerprint(pubBytes)
        } catch (_: Exception) {
            parsedPayload.keyFingerprint.ifBlank { "UNBEKANNT" }
        }

        val isOwnDevice = isValid && cryptoManager.isSignedByThisDevice(
            certPublicKeyBase64 = parsedPayload.publicKeyBase64,
            certKeyFingerprint = certKeyFingerprint
        )

        val originStatus = when {
            !isValid -> CertificateOriginStatus.TAMPERED_OR_INVALID
            isOwnDevice -> CertificateOriginStatus.OWN_DEVICE
            else -> CertificateOriginStatus.FOREIGN_DEVICE
        }

        _verificationResult.value = SealVerificationResult(
            isValidSignature = isValid,
            originStatus = originStatus,
            payload = parsedPayload,
            computedKeyFingerprint = certKeyFingerprint,
            localDeviceKeyFingerprint = localFp,
            verificationTimestamp = nowFormatted,
            isDirectPdfCheck = false,
            errorReason = tamperError
        )
    }

    /**
     * Prüft eine vom Nutzer ausgewählte PDF-Datei (z. B. aus WhatsApp oder Downloads)
     * direkt auf ihren eingebetteten %%PROOFSEAL_PAYLOAD_JSON%% Block, den Binärstrom-Hash,
     * die ECDSA-Signatur und die Geräte-Herkunft.
     */
    fun verifyPdfDocumentUri(uri: Uri) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                val fileName = resolveUriFileName(uri) ?: "Dokument.pdf"
                val pdfBytes = try {
                    appContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                } catch (_: Exception) {
                    null
                }
                if (pdfBytes == null || pdfBytes.isEmpty()) {
                    return@withContext createFailedPdfVerificationResult(
                        fileName = fileName,
                        reason = "Die ausgewählte PDF-Datei konnte nicht gelesen werden."
                    )
                }
                evaluatePdfBytes(pdfBytes, fileName)
            }
            _verificationResult.value = result
        }
    }

    fun verifyLocalPdfFile(file: File, simulateBinaryTamper: Boolean = false) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                if (!file.exists()) {
                    return@withContext createFailedPdfVerificationResult(
                        fileName = file.name,
                        reason = "PDF-Datei nicht im Speicher gefunden."
                    )
                }
                val rawBytes = file.readBytes()
                val bytesToTest = if (simulateBinaryTamper && rawBytes.size > 120) {
                    rawBytes.clone().also { copy ->
                        // Manipuliere 1 Byte mitten im PDF-Dokumentenstrom (vor dem Footer)
                        copy[rawBytes.size / 2] = (copy[rawBytes.size / 2].toInt() xor 0xFF).toByte()
                    }
                } else {
                    rawBytes
                }
                evaluatePdfBytes(
                    pdfBytes = bytesToTest,
                    fileName = if (simulateBinaryTamper) "${file.name} (1 Byte manipuliert)" else file.name
                )
            }
            _verificationResult.value = result
        }
    }

    /**
     * Erzeugt zu Demonstrations- und Testzwecken ein mathematisch gültiges Zertifikat
     * eines simulierten Fremdgeräts (mit eigenem flüchtigem EC-Schlüsselpaar),
     * damit die "Fremdgerät"-Erkennung sofort getestet werden kann.
     */
    fun simulateForeignDeviceCertificate() {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val kpg = KeyPairGenerator.getInstance("EC")
                kpg.initialize(ECGenParameterSpec("secp256r1"))
                val foreignKeyPair = kpg.generateKeyPair()

                val foreignPubBytes = foreignKeyPair.public.encoded
                val foreignPubBase64 = Base64.encodeToString(foreignPubBytes, Base64.NO_WRAP)
                val foreignFp = HardwareCryptoManager.formatFingerprint(foreignPubBytes)

                val protocolId = UUID.randomUUID().toString()
                val nowUtc = DateTimeFormatter.ISO_INSTANT.format(Instant.now().atOffset(ZoneOffset.UTC))
                val uptimeNs = SystemClock.elapsedRealtimeNanos()
                val foreignDevice = "Google Pixel 9 Pro (Externes Gerät)"
                val secLevel = "StrongBox Hardware-SE"
                val modeTitle = "Paket & Verkauf"
                val ocrVal = "DHL-0034043429184721"
                val imgHashes = listOf(
                    "IMG1: 4F8A901C2B3E4D5F",
                    "IMG2: 9C1B77E204A811D3",
                    "IMG3: 3E72F8910C4B55A2",
                    "IMG4: 8A19D4E672B0C3F1"
                )

                val canonicalBytes = HardwareCryptoManager.buildCanonicalSealBytes(
                    protocolId = protocolId,
                    timestampUtc = nowUtc,
                    uptimeNanos = uptimeNs,
                    deviceModel = foreignDevice,
                    securityLevel = secLevel,
                    modeTitle = modeTitle,
                    ocrPrimaryValue = ocrVal,
                    imageHashes = imgHashes,
                    keyFingerprint = foreignFp
                )
                val payloadHash = HardwareCryptoManager.sha256Hex(canonicalBytes).lowercase(Locale.ROOT)

                val signer = Signature.getInstance("SHA256withECDSA")
                signer.initSign(foreignKeyPair.private)
                signer.update(payloadHash.toByteArray(Charsets.UTF_8))
                val foreignSigBase64 = Base64.encodeToString(signer.sign(), Base64.NO_WRAP)

                val foreignPayload = QrSealPayload(
                    version = 2,
                    protocolId = protocolId,
                    timestampUtc = nowUtc,
                    uptimeNanos = uptimeNs,
                    deviceModel = foreignDevice,
                    securityLevel = secLevel,
                    modeTitle = modeTitle,
                    primaryOcrValue = ocrVal,
                    imageHashes = imgHashes,
                    keyFingerprint = foreignFp,
                    parallaxScore = 91,
                    isContinuous = true,
                    payloadHash = payloadHash,
                    signatureBase64 = foreignSigBase64,
                    publicKeyBase64 = foreignPubBase64
                )

                withContext(Dispatchers.Main) {
                    verifyScannedQrContent(foreignPayload.toCompactJson())
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun evaluatePdfBytes(pdfBytes: ByteArray, fileName: String): SealVerificationResult {
        val nowFormatted = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss", Locale.GERMANY)
            .withZone(ZoneId.systemDefault())
            .format(Instant.now())
        val localFp = cryptoManager.getPublicKeyFingerprint()

        val pdfExtract = ProofPdfGenerator.verifyPdfFileBytes(pdfBytes, cryptoManager)
        val payload = pdfExtract.qrPayload

        if (!pdfExtract.hasProofSealFooter || !pdfExtract.isPdfBinaryStreamIntact || payload == null) {
            val certFp = payload?.keyFingerprint?.ifBlank { "-" } ?: "-"
            return SealVerificationResult(
                isValidSignature = false,
                originStatus = CertificateOriginStatus.TAMPERED_OR_INVALID,
                payload = payload,
                computedKeyFingerprint = certFp,
                localDeviceKeyFingerprint = localFp,
                verificationTimestamp = nowFormatted,
                isDirectPdfCheck = true,
                pdfFileName = fileName,
                isPdfBinaryStreamIntact = false,
                expectedPdfSha256 = pdfExtract.expectedPdfSha256,
                actualPdfSha256 = pdfExtract.actualPdfSha256,
                errorReason = pdfExtract.tamperReason ?: "PDF-Integritätsprüfung fehlgeschlagen."
            )
        }

        val (isPayloadValid, payloadError) = cryptoManager.verifyQrPayloadIntegrity(payload)
        val certKeyFingerprint = try {
            val pubBytes = Base64.decode(payload.publicKeyBase64.trim(), Base64.NO_WRAP)
            HardwareCryptoManager.formatFingerprint(pubBytes)
        } catch (_: Exception) {
            payload.keyFingerprint
        }

        val overallValid = isPayloadValid && pdfExtract.isPdfBinaryStreamIntact
        val isOwnDevice = overallValid && cryptoManager.isSignedByThisDevice(
            certPublicKeyBase64 = payload.publicKeyBase64,
            certKeyFingerprint = certKeyFingerprint
        )

        val originStatus = when {
            !overallValid -> CertificateOriginStatus.TAMPERED_OR_INVALID
            isOwnDevice -> CertificateOriginStatus.OWN_DEVICE
            else -> CertificateOriginStatus.FOREIGN_DEVICE
        }

        return SealVerificationResult(
            isValidSignature = overallValid,
            originStatus = originStatus,
            payload = payload,
            computedKeyFingerprint = certKeyFingerprint,
            localDeviceKeyFingerprint = localFp,
            verificationTimestamp = nowFormatted,
            isDirectPdfCheck = true,
            pdfFileName = fileName,
            isPdfBinaryStreamIntact = pdfExtract.isPdfBinaryStreamIntact,
            expectedPdfSha256 = pdfExtract.expectedPdfSha256,
            actualPdfSha256 = pdfExtract.actualPdfSha256,
            errorReason = payloadError
        )
    }

    private fun createFailedPdfVerificationResult(
        fileName: String,
        reason: String
    ): SealVerificationResult {
        val nowFormatted = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss", Locale.GERMANY)
            .withZone(ZoneId.systemDefault())
            .format(Instant.now())
        return SealVerificationResult(
            isValidSignature = false,
            originStatus = CertificateOriginStatus.TAMPERED_OR_INVALID,
            payload = null,
            computedKeyFingerprint = "-",
            localDeviceKeyFingerprint = cryptoManager.getPublicKeyFingerprint(),
            verificationTimestamp = nowFormatted,
            isDirectPdfCheck = true,
            pdfFileName = fileName,
            isPdfBinaryStreamIntact = false,
            errorReason = reason
        )
    }

    private fun resolveUriFileName(uri: Uri): String? {
        return try {
            appContext.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && cursor.moveToFirst()) {
                    cursor.getString(nameIndex)
                } else null
            }
        } catch (_: Exception) {
            null
        }
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
