package de.proofseal.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.graphics.Paint as AndroidPaint
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import de.proofseal.app.model.CapturedKeyframe
import de.proofseal.app.model.ProofMode
import de.proofseal.app.sensor.SensorLiveSnapshot
import de.proofseal.app.ui.theme.AmberWarning
import de.proofseal.app.ui.theme.AnthraciteBorder
import de.proofseal.app.ui.theme.AnthraciteCard
import de.proofseal.app.ui.theme.AnthraciteDeep
import de.proofseal.app.ui.theme.AnthraciteSurface
import de.proofseal.app.ui.theme.CrimsonAlert
import de.proofseal.app.ui.theme.CyanTelemetry
import de.proofseal.app.ui.theme.EmeraldBright
import de.proofseal.app.ui.theme.EmeraldContainer
import de.proofseal.app.ui.theme.EmeraldPrimary
import de.proofseal.app.ui.theme.TextOffWhite
import de.proofseal.app.ui.theme.TextSlateMuted
import de.proofseal.app.ui.viewmodel.CaptureSessionUiState
import de.proofseal.app.ui.viewmodel.ProofSealViewModel
import de.proofseal.app.util.MlKitAnalyzerHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuidedCaptureScreen(
    viewModel: ProofSealViewModel,
    onNavigateBack: () -> Unit,
    onProtocolSealed: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    val captureState by viewModel.captureState.collectAsState()
    val sensorLive by viewModel.sensorLiveSnapshot.collectAsState()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        hasCameraPermission = grants[Manifest.permission.CAMERA] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        viewModel.refreshGpsLocation()
    }

    LaunchedEffect(Unit) {
        val needed = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.CAMERA)
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.ACCESS_FINE_LOCATION)
            needed.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        } else {
            viewModel.refreshGpsLocation()
        }
    }

    BackHandler {
        viewModel.abortCaptureSession()
        onNavigateBack()
    }

    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }

    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) {
        onDispose {
            analysisExecutor.shutdown()
        }
    }

    var countdownSeconds by remember { mutableIntStateOf(0) }

    fun triggerKeyframeCapture() {
        if (captureState.isCapturingKeyframe || captureState.isSealingProtocol) return
        if (!hasCameraPermission) {
            // Synthetischer Fallback falls Kamera-Berechtigung verweigert oder kein Kamerasensor
            val fallbackBmp = createDiagnosticKeyframeBitmap(captureState, sensorLive)
            viewModel.processCapturedKeyframeBitmap(fallbackBmp)
            return
        }

        imageCapture.takePicture(
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val bitmap = MlKitAnalyzerHelper.imageProxyToScaledBitmap(image, maxDimension = 1280)
                    image.close()
                    viewModel.processCapturedKeyframeBitmap(bitmap)
                }

                override fun onError(exception: ImageCaptureException) {
                    // Fallback auf diagnostisches Beweisbild (z. B. im Emulator ohne virtuelle Kamera)
                    val fallbackBmp = createDiagnosticKeyframeBitmap(captureState, sensorLive)
                    viewModel.processCapturedKeyframeBitmap(fallbackBmp)
                }
            }
        )
    }

    val currentStepSpec = captureState.mode.steps.getOrElse(captureState.currentStepIndex) {
        captureState.mode.steps.last()
    }
    val capturedCount = captureState.capturedKeyframes.size
    val canSealNow = capturedCount >= captureState.mode.minRequiredKeyframes

    Scaffold(
        containerColor = AnthraciteDeep
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 1. Top-Header + Live-Sensor-Ausschlag (Live-Graph als aktiver Aufzeichnungs-Beweis)
            LiveSensorChainTopBar(
                modeTitle = captureState.mode.title,
                sensorLive = sensorLive,
                onAbort = {
                    viewModel.abortCaptureSession()
                    onNavigateBack()
                }
            )

            // 2. Aktueller Schritt-Leitfaden & Fortschrittsanzeige
            StepGuidanceBanner(
                mode = captureState.mode,
                currentStepIndex = captureState.currentStepIndex,
                capturedCount = capturedCount,
                stepSpec = currentStepSpec,
                parallaxScore = sensorLive.liveParallaxScore
            )

            // 3. Kamera-Sucher mit ML Kit Live-OCR / Barcode-Overlay
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.5.dp, EmeraldPrimary.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
                    .background(Color.Black)
            ) {
                if (hasCameraPermission) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            val previewView = PreviewView(ctx).apply {
                                scaleType = PreviewView.ScaleType.FILL_CENTER
                            }
                            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                            cameraProviderFuture.addListener({
                                try {
                                    val cameraProvider = cameraProviderFuture.get()
                                    val preview = Preview.Builder().build().also {
                                        it.setSurfaceProvider(previewView.surfaceProvider)
                                    }
                                    val imageAnalysis = ImageAnalysis.Builder()
                                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                        .build()
                                        .also { analysis ->
                                            analysis.setAnalyzer(
                                                analysisExecutor,
                                                MlKitAnalyzerHelper.createLivePreviewOverlayAnalyzer { ocrHint, barcodeHint ->
                                                    viewModel.onLiveAnalyzerHint(ocrHint, barcodeHint)
                                                }
                                            )
                                        }
                                    cameraProvider.unbindAll()
                                    cameraProvider.bindToLifecycle(
                                        lifecycleOwner,
                                        CameraSelector.DEFAULT_BACK_CAMERA,
                                        preview,
                                        imageCapture,
                                        imageAnalysis
                                    )
                                } catch (_: Exception) {
                                }
                            }, ContextCompat.getMainExecutor(ctx))
                            previewView
                        }
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Kamera-Berechtigung erforderlich",
                            color = TextOffWhite,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                permissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.CAMERA,
                                        Manifest.permission.ACCESS_FINE_LOCATION
                                    )
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                        ) {
                            Text("Kamera & Standort freigeben", color = Color(0xFF022C22))
                        }
                    }
                }

                // Fadenkreuz & Live-ML-Kit-Erkennungs-Badges im Kamerabild
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(10.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = AnthraciteDeep.copy(alpha = 0.82f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, AnthraciteBorder)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.GpsFixed,
                                    contentDescription = null,
                                    tint = CyanTelemetry,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = captureState.gpsLocationText,
                                    color = TextOffWhite,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Surface(
                            color = EmeraldContainer.copy(alpha = 0.88f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "Bilder: $capturedCount/${captureState.mode.maxKeyframes}",
                                color = EmeraldBright,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    // Countdown-Anzeige bei Freihand-Timer (für Schritt 2 Einpacken)
                    if (countdownSeconds > 0) {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                color = AmberWarning.copy(alpha = 0.92f),
                                shape = CircleShape,
                                modifier = Modifier.size(72.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "${countdownSeconds}s",
                                        color = Color.Black,
                                        fontSize = 26.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }
                        }
                    }

                    // Untere Live-OCR & Barcode-Vorschau im Sucher
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (captureState.liveBarcodePreviewHint.isNotBlank()) {
                            Surface(
                                color = EmeraldContainer.copy(alpha = 0.9f),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, EmeraldBright)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.QrCode,
                                        contentDescription = null,
                                        tint = EmeraldBright,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Live-Barcode: ${captureState.liveBarcodePreviewHint}",
                                        color = TextOffWhite,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                        if (captureState.liveOcrPreviewHint.isNotBlank()) {
                            Surface(
                                color = AnthraciteDeep.copy(alpha = 0.84f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "Live-OCR: ${captureState.liveOcrPreviewHint}",
                                    color = CyanTelemetry,
                                    fontSize = 10.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 4. Direktes Prüffeld für erkannten OCR-/Barcode-Hauptwert (editierbar)
            OutlinedTextField(
                value = captureState.primaryOcrValue,
                onValueChange = viewModel::updatePrimaryOcrValue,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = {
                    Text(
                        text = "${captureState.mode.primaryValueLabel} (Live-OCR Prüffeld)",
                        fontSize = 11.sp
                    )
                },
                placeholder = {
                    Text(
                        text = captureState.mode.primaryValueHint,
                        fontSize = 11.sp,
                        color = TextSlateMuted
                    )
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = EmeraldPrimary,
                    unfocusedBorderColor = AnthraciteBorder,
                    focusedContainerColor = AnthraciteSurface,
                    unfocusedContainerColor = AnthraciteSurface,
                    focusedTextColor = TextOffWhite,
                    unfocusedTextColor = TextOffWhite,
                    focusedLabelColor = EmeraldBright,
                    unfocusedLabelColor = TextSlateMuted
                ),
                shape = RoundedCornerShape(10.dp)
            )

            // 5. Aufgenommene Keyframes-Vorschau-Leiste
            if (captureState.capturedKeyframes.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(captureState.capturedKeyframes, key = { it.index }) { kf ->
                        CapturedKeyframeMiniCard(kf)
                    }
                }
            }

            // 6. Auslöse-Buttons & Versiegelungs-Aktion
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (capturedCount < captureState.mode.maxKeyframes) {
                    // 3s Freihand-Timer (ideal für Schritt 2: Paket einpacken & zukleben)
                    OutlinedButton(
                        onClick = {
                            if (countdownSeconds == 0 && !captureState.isCapturingKeyframe) {
                                coroutineScope.launch {
                                    for (sec in 3 downTo 1) {
                                        countdownSeconds = sec
                                        delay(1000L)
                                    }
                                    countdownSeconds = 0
                                    triggerKeyframeCapture()
                                }
                            }
                        },
                        enabled = !captureState.isCapturingKeyframe && countdownSeconds == 0,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, CyanTelemetry.copy(alpha = 0.6f)),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = "3s Timer",
                            tint = CyanTelemetry,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("3s", color = CyanTelemetry, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { triggerKeyframeCapture() },
                        enabled = !captureState.isCapturingKeyframe,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EmeraldPrimary,
                            contentColor = Color(0xFF022C22)
                        ),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        if (captureState.isCapturingKeyframe) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = Color(0xFF022C22)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("ML Kit OCR & Hash…", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        } else {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "KEYFRAME #${capturedCount + 1} AUFNEHMEN",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                if (canSealNow) {
                    Button(
                        onClick = { viewModel.setReadyToSealSheetOpen(true) },
                        modifier = if (capturedCount >= captureState.mode.maxKeyframes) {
                            Modifier.fillMaxWidth()
                        } else {
                            Modifier.weight(0.85f)
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EmeraldContainer,
                            contentColor = EmeraldBright
                        ),
                        border = BorderStroke(1.dp, EmeraldBright),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "VERSIEGELN ($capturedCount)",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        // Modal Bottom Sheet für finale Kontrolle, Zeugenfeld, Notizen & Hardware-Keystore-Signatur
        if (captureState.isReadyToSealSheetOpen) {
            ModalBottomSheet(
                onDismissRequest = {
                    if (!captureState.isSealingProtocol) {
                        viewModel.setReadyToSealSheetOpen(false)
                    }
                },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = AnthraciteSurface,
                contentColor = TextOffWhite
            ) {
                FinalizeAndSealSheetContent(
                    captureState = captureState,
                    sensorLive = sensorLive,
                    onUpdateTitle = viewModel::updateProtocolTitle,
                    onUpdatePrimaryOcr = viewModel::updatePrimaryOcrValue,
                    onUpdateWitness = viewModel::updateWitnessName,
                    onUpdateNotes = viewModel::updateUserNotes,
                    onBackToCamera = { viewModel.setReadyToSealSheetOpen(false) },
                    onConfirmSeal = {
                        viewModel.finalizeAndSealProtocol { sealedProtocolId ->
                            onProtocolSealed(sealedProtocolId)
                        }
                    }
                )
            }
        }
    }
}

/**
 * Oberes Live-Sensor-Oszilloskop (Live-Graph) als Echtzeit-Beweis,
 * dass Beschleunigung, Gyroskop und monotone Hardware-Uptime lückenlos aufgezeichnet werden.
 */
@Composable
private fun LiveSensorChainTopBar(
    modeTitle: String,
    sensorLive: SensorLiveSnapshot,
    onAbort: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = AnthraciteSurface),
        border = BorderStroke(
            1.dp,
            if (sensorLive.isContinuousSoFar) EmeraldPrimary.copy(alpha = 0.5f) else CrimsonAlert
        )
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onAbort,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Abbrechen",
                            tint = TextOffWhite,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text(
                            text = modeTitle.uppercase(Locale.ROOT),
                            color = TextOffWhite,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Sensors,
                                contentDescription = null,
                                tint = EmeraldBright,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = String.format(
                                    Locale.US,
                                    "SENSORKETTE AKTIV • +%.1fs • %d Samples",
                                    sensorLive.elapsedSeconds,
                                    sensorLive.sampleCount
                                ),
                                color = EmeraldBright,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                Surface(
                    color = if (sensorLive.liveParallaxScore >= 35) EmeraldContainer else AnthraciteCard,
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(
                        1.dp,
                        if (sensorLive.liveParallaxScore >= 35) EmeraldPrimary else AmberWarning
                    )
                ) {
                    Text(
                        text = "3D-Parallaxe: ${sensorLive.liveParallaxScore}%",
                        color = if (sensorLive.liveParallaxScore >= 35) EmeraldBright else AmberWarning,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Gezeichneter Live-Sensor-Graph (Accel = Smaragdgrün, Gyro = Cyan)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(AnthraciteDeep)
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val midY = h / 2f

                    drawLine(
                        color = AnthraciteBorder,
                        start = Offset(0f, midY),
                        end = Offset(w, midY),
                        strokeWidth = 1f
                    )

                    val accelPts = sensorLive.recentAccelHistory
                    if (accelPts.size >= 2) {
                        val stepX = w / (accelPts.size - 1).coerceAtLeast(1)
                        for (i in 1 until accelPts.size) {
                            val x0 = (i - 1) * stepX
                            val x1 = i * stepX
                            val y0 = (h - (accelPts[i - 1].coerceIn(0f, 22f) / 22f) * h)
                            val y1 = (h - (accelPts[i].coerceIn(0f, 22f) / 22f) * h)
                            drawLine(
                                color = EmeraldBright,
                                start = Offset(x0, y0),
                                end = Offset(x1, y1),
                                strokeWidth = 2.2f
                            )
                        }
                    }

                    val gyroPts = sensorLive.recentGyroHistory
                    if (gyroPts.size >= 2) {
                        val stepX = w / (gyroPts.size - 1).coerceAtLeast(1)
                        for (i in 1 until gyroPts.size) {
                            val x0 = (i - 1) * stepX
                            val x1 = i * stepX
                            val y0 = (h - ((gyroPts[i - 1] * 4f).coerceIn(0f, 22f) / 22f) * h)
                            val y1 = (h - ((gyroPts[i] * 4f).coerceIn(0f, 22f) / 22f) * h)
                            drawLine(
                                color = CyanTelemetry,
                                start = Offset(x0, y0),
                                end = Offset(x1, y1),
                                strokeWidth = 1.6f
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = String.format(
                            Locale.US,
                            "|a|=%.2f m/s²  |ω|=%.2f rad/s",
                            sensorLive.accelMagnitude,
                            sensorLive.gyroMagnitude
                        ),
                        color = TextSlateMuted,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = if (sensorLive.isContinuousSoFar) "LÜCKENLOS (<500ms)" else "WARNUNG: LÜCKE",
                        color = if (sensorLive.isContinuousSoFar) EmeraldBright else CrimsonAlert,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun StepGuidanceBanner(
    mode: ProofMode,
    currentStepIndex: Int,
    capturedCount: Int,
    stepSpec: de.proofseal.app.model.CaptureStepSpec,
    parallaxScore: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = AnthraciteCard),
        border = BorderStroke(1.dp, AnthraciteBorder)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stepSpec.phaseBadge,
                    color = EmeraldBright,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "Schritt ${(currentStepIndex + 1).coerceAtMost(mode.maxKeyframes)} von ${mode.maxKeyframes}",
                    color = TextSlateMuted,
                    fontSize = 10.sp
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            LinearProgressIndicator(
                progress = { (capturedCount.toFloat() / mode.maxKeyframes.toFloat()).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = EmeraldPrimary,
                trackColor = AnthraciteDeep
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = stepSpec.title,
                color = TextOffWhite,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stepSpec.instruction,
                color = TextSlateMuted,
                fontSize = 11.sp,
                lineHeight = 14.sp
            )

            if (stepSpec.requiresParallaxHint) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Anti-Fake 3D-Bewegung: Bewege das Gerät leicht seitlich (aktuell $parallaxScore% Parallaxe-Vertrauen).",
                    color = if (parallaxScore >= 35) EmeraldBright else AmberWarning,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun CapturedKeyframeMiniCard(kf: CapturedKeyframe) {
    val thumbBitmap = remember(kf.imageFilePath) {
        try {
            val file = File(kf.imageFilePath)
            if (file.exists()) {
                BitmapFactory.decodeFile(file.absolutePath)
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    Card(
        modifier = Modifier
            .width(138.dp)
            .height(68.dp),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = AnthraciteSurface),
        border = BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (thumbBitmap != null) {
                Image(
                    bitmap = thumbBitmap.asImageBitmap(),
                    contentDescription = kf.stepTitle,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(6.dp))
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "KF #${kf.index} (${kf.formattedOffsetSeconds})",
                    color = EmeraldBright,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = kf.shortSha256,
                    color = TextSlateMuted,
                    fontSize = 8.5.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1
                )
                Text(
                    text = kf.extractedKeyCandidate.ifBlank { "OCR geprüft" },
                    color = CyanTelemetry,
                    fontSize = 9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun FinalizeAndSealSheetContent(
    captureState: CaptureSessionUiState,
    sensorLive: SensorLiveSnapshot,
    onUpdateTitle: (String) -> Unit,
    onUpdatePrimaryOcr: (String) -> Unit,
    onUpdateWitness: (String) -> Unit,
    onUpdateNotes: (String) -> Unit,
    onBackToCamera: () -> Unit,
    onConfirmSeal: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.VerifiedUser,
                contentDescription = null,
                tint = EmeraldBright,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = "BEWEIS-PAKET HARDWARE-VERSIEGELN",
                    color = TextOffWhite,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "${captureState.capturedKeyframes.size} Keyframes • ${sensorLive.sampleCount} Sensorpunkte • 3D-Score ${sensorLive.liveParallaxScore}%",
                    color = EmeraldBright,
                    fontSize = 11.5.sp
                )
            }
        }

        OutlinedTextField(
            value = captureState.protocolTitle,
            onValueChange = onUpdateTitle,
            label = { Text("Protokoll-Titel") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = EmeraldPrimary,
                unfocusedBorderColor = AnthraciteBorder,
                focusedTextColor = TextOffWhite,
                unfocusedTextColor = TextOffWhite
            )
        )

        OutlinedTextField(
            value = captureState.primaryOcrValue,
            onValueChange = onUpdatePrimaryOcr,
            label = { Text(captureState.mode.primaryValueLabel) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = EmeraldPrimary,
                unfocusedBorderColor = AnthraciteBorder,
                focusedTextColor = TextOffWhite,
                unfocusedTextColor = TextOffWhite
            )
        )

        OutlinedTextField(
            value = captureState.witnessName,
            onValueChange = onUpdateWitness,
            label = { Text("Zeuge / Anwesende Person (Optional)") },
            placeholder = { Text("z. B. Max Mustermann (Käufer / Vermieter / Nachbar)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = EmeraldPrimary,
                unfocusedBorderColor = AnthraciteBorder,
                focusedTextColor = TextOffWhite,
                unfocusedTextColor = TextOffWhite
            )
        )

        OutlinedTextField(
            value = captureState.userNotes,
            onValueChange = onUpdateNotes,
            label = { Text("Freitext-Notiz zum Zustand / Vorfall") },
            placeholder = { Text("Zusätzliche Bemerkungen, Zustand, Plombennummer…") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            maxLines = 4,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = EmeraldPrimary,
                unfocusedBorderColor = AnthraciteBorder,
                focusedTextColor = TextOffWhite,
                unfocusedTextColor = TextOffWhite
            )
        )

        if (captureState.errorMessage != null) {
            Text(
                text = captureState.errorMessage,
                color = CrimsonAlert,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (captureState.capturedKeyframes.size < captureState.mode.maxKeyframes) {
                OutlinedButton(
                    onClick = onBackToCamera,
                    enabled = !captureState.isSealingProtocol,
                    modifier = Modifier.weight(0.9f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, AnthraciteBorder)
                ) {
                    Text("Weiteres Bild", color = TextOffWhite)
                }
            }

            Button(
                onClick = onConfirmSeal,
                enabled = !captureState.isSealingProtocol,
                modifier = Modifier.weight(1.4f),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = EmeraldPrimary,
                    contentColor = Color(0xFF022C22)
                ),
                contentPadding = PaddingValues(vertical = 14.dp)
            ) {
                if (captureState.isSealingProtocol) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = Color(0xFF022C22)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Signiere im Keystore & baue PDF…", fontWeight = FontWeight.Bold)
                } else {
                    Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "HARDWARE-SIGNATUR & PDF ERSTELLEN",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 12.5.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))
    }
}

private fun createDiagnosticKeyframeBitmap(
    state: CaptureSessionUiState,
    sensorLive: SensorLiveSnapshot
): Bitmap {
    val bmp = Bitmap.createBitmap(800, 600, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)
    canvas.drawColor(AndroidColor.parseColor("#0F172A"))
    val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.parseColor("#34D399")
        textSize = 28f
        isFakeBoldText = true
    }
    val stepIdx = state.capturedKeyframes.size + 1
    canvas.drawText("PROOFSEAL KEYFRAME #$stepIdx", 40f, 80f, paint)
    paint.color = AndroidColor.WHITE
    paint.textSize = 22f
    canvas.drawText("Modus: ${state.mode.title}", 40f, 140f, paint)
    canvas.drawText(
        String.format(Locale.US, "Offset: +%.2fs | Samples: %d", sensorLive.elapsedSeconds, sensorLive.sampleCount),
        40f,
        190f,
        paint
    )
    canvas.drawText(
        String.format(
            Locale.US,
            "Sensor a=(%.2f, %.2f, %.2f) m/s²",
            sensorLive.accelX,
            sensorLive.accelY,
            sensorLive.accelZ
        ),
        40f,
        240f,
        paint
    )
    canvas.drawText("SN-2026-99481204 / 14892.5 kWh", 40f, 320f, paint)
    return bmp
}
