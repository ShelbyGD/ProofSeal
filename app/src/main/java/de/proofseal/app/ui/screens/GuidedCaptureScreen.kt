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
import androidx.compose.animation.animateContentSize
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.StrokeCap
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
import de.proofseal.app.sensor.SensorLiveSnapshot
import de.proofseal.app.ui.theme.CyanTelemetry
import de.proofseal.app.ui.theme.EmeraldBright
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
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // 1. Kamera-Viewfinder mit halbtransparentem Floating-Status-Pill-Overlay oben
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
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
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Kamera-Zugriff für Beweisaufnahme",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = {
                                permissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.CAMERA,
                                        Manifest.permission.ACCESS_FINE_LOCATION
                                    )
                                )
                            },
                            shape = RoundedCornerShape(50)
                        ) {
                            Text("Kamera & Standort freigeben")
                        }
                    }
                }

                // Oben: Halbtransparentes Floating-Status-Pill-Overlay (Glassmorphism)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FloatingSensorStatusPillOverlay(
                        stepTitle = currentStepSpec.title,
                        sensorLive = sensorLive,
                        onAbort = {
                            viewModel.abortCaptureSession()
                            onNavigateBack()
                        }
                    )

                    // Kompaktes GPS- & 3D-Parallaxe-Pill-Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
                            shape = RoundedCornerShape(50)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.GpsFixed,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = captureState.gpsLocationText,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.88f),
                            shape = RoundedCornerShape(50)
                        ) {
                            Text(
                                text = "3D-Parallaxe: ${sensorLive.liveParallaxScore}%",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                            )
                        }
                    }
                }

                // Mittig: 3-Sekunden-Freihand-Countdown
                if (countdownSeconds > 0) {
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.92f),
                        shape = CircleShape,
                        modifier = Modifier
                            .size(84.dp)
                            .align(Alignment.Center)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "${countdownSeconds}s",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }

                // Unten im Kamerabild: Live-OCR & Barcode-Erkennungs-Pills
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomStart)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (captureState.liveBarcodePreviewHint.isNotBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
                            shape = RoundedCornerShape(50)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.QrCode,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Barcode: ${captureState.liveBarcodePreviewHint}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    if (captureState.liveOcrPreviewHint.isNotBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                            shape = RoundedCornerShape(50)
                        ) {
                            Text(
                                text = "Live-OCR: ${captureState.liveOcrPreviewHint}",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.secondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }

            // 2. Aufgeräumtes Material-3-Kamera-Steuerfeld unten
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Abgerundete Fortschrittsanzeige + Schritt-Status
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = currentStepSpec.phaseBadge,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "$capturedCount / ${captureState.mode.maxKeyframes} Keyframes",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    LinearProgressIndicator(
                        progress = {
                            (capturedCount.toFloat() / captureState.mode.maxKeyframes.toFloat())
                                .coerceIn(0.05f, 1f)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(50)),
                        strokeCap = StrokeCap.Round
                    )

                    Text(
                        text = currentStepSpec.instruction,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Editierbares Live-OCR Prüffeld im Pill-Stil
                    OutlinedTextField(
                        value = captureState.primaryOcrValue,
                        onValueChange = viewModel::updatePrimaryOcrValue,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp),
                        label = {
                            Text("${captureState.mode.primaryValueLabel} (OCR-Prüffeld)")
                        },
                        placeholder = {
                            Text(captureState.mode.primaryValueHint)
                        }
                    )

                    // Aufgenommene Keyframes als abgerundete Mini-Cards
                    if (captureState.capturedKeyframes.isNotEmpty()) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(captureState.capturedKeyframes, key = { it.index }) { kf ->
                                CapturedKeyframeMaterial3Chip(kf)
                            }
                        }
                    }

                    // Kamera-Shutter-Deck: Links 3s-Timer, Mitte großer runder Shutter-FAB, Rechts Versiegeln
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Links: 3s Freihand-Timer
                        FilledTonalButton(
                            onClick = {
                                if (countdownSeconds == 0 && !captureState.isCapturingKeyframe &&
                                    capturedCount < captureState.mode.maxKeyframes
                                ) {
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
                            enabled = !captureState.isCapturingKeyframe &&
                                countdownSeconds == 0 &&
                                capturedCount < captureState.mode.maxKeyframes,
                            shape = RoundedCornerShape(50)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Timer,
                                contentDescription = "3s Timer",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("3s Timer")
                        }

                        // Mitte: Großer runder Material 3 Kamera-Auslöser (Custom Shutter / FAB)
                        Box(
                            modifier = Modifier
                                .size(76.dp)
                                .border(
                                    width = 3.dp,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f),
                                    shape = CircleShape
                                )
                                .padding(5.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            FloatingActionButton(
                                onClick = {
                                    if (capturedCount < captureState.mode.maxKeyframes) {
                                        triggerKeyframeCapture()
                                    } else {
                                        viewModel.setReadyToSealSheetOpen(true)
                                    }
                                },
                                shape = CircleShape,
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                if (captureState.isCapturingKeyframe) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(26.dp),
                                        strokeWidth = 2.5.dp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                } else {
                                    Icon(
                                        imageVector = if (capturedCount < captureState.mode.maxKeyframes) {
                                            Icons.Default.CameraAlt
                                        } else {
                                            Icons.Default.Lock
                                        },
                                        contentDescription = "Keyframe auslösen",
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                        }

                        // Rechts: Versiegeln-Button
                        Button(
                            onClick = { viewModel.setReadyToSealSheetOpen(true) },
                            enabled = canSealNow,
                            shape = RoundedCornerShape(50)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Siegel ($capturedCount)",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // Modal Bottom Sheet für finale Versiegelung
        if (captureState.isReadyToSealSheetOpen) {
            ModalBottomSheet(
                onDismissRequest = {
                    if (!captureState.isSealingProtocol) {
                        viewModel.setReadyToSealSheetOpen(false)
                    }
                },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
            ) {
                FinalizeAndSealMaterial3Sheet(
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
 * Halbtransparentes Floating-Status-Pill-Overlay (Glassmorphism mit alpha = 0.85f
 * und RoundedCornerShape(50)) am oberen Bildschirmrand mit Live-Sensor-Graphen
 * und aktuellem Schritt.
 */
@Composable
private fun FloatingSensorStatusPillOverlay(
    stepTitle: String,
    sensorLive: SensorLiveSnapshot,
    onAbort: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        shape = RoundedCornerShape(50),
        tonalElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onAbort,
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Abbrechen",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stepTitle,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Sensors,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = String.format(
                            Locale.US,
                            "+%.1fs • %d Samples • %s",
                            sensorLive.elapsedSeconds,
                            sensorLive.sampleCount,
                            if (sensorLive.isContinuousSoFar) "Lückenlos" else "Gap!"
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Kompakter Live-Sensor-Graph in einer Pill
            Surface(
                modifier = Modifier
                    .width(96.dp)
                    .height(34.dp),
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.9f)
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                ) {
                    val w = size.width
                    val h = size.height

                    val accelPts = sensorLive.recentAccelHistory
                    if (accelPts.size >= 2) {
                        val stepX = w / (accelPts.size - 1).coerceAtLeast(1)
                        for (i in 1 until accelPts.size) {
                            val x0 = (i - 1) * stepX
                            val x1 = i * stepX
                            val y0 = h - (accelPts[i - 1].coerceIn(0f, 22f) / 22f) * h
                            val y1 = h - (accelPts[i].coerceIn(0f, 22f) / 22f) * h
                            drawLine(
                                color = EmeraldBright,
                                start = Offset(x0, y0),
                                end = Offset(x1, y1),
                                strokeWidth = 2f
                            )
                        }
                    }

                    val gyroPts = sensorLive.recentGyroHistory
                    if (gyroPts.size >= 2) {
                        val stepX = w / (gyroPts.size - 1).coerceAtLeast(1)
                        for (i in 1 until gyroPts.size) {
                            val x0 = (i - 1) * stepX
                            val x1 = i * stepX
                            val y0 = h - ((gyroPts[i - 1] * 4f).coerceIn(0f, 22f) / 22f) * h
                            val y1 = h - ((gyroPts[i] * 4f).coerceIn(0f, 22f) / 22f) * h
                            drawLine(
                                color = CyanTelemetry,
                                start = Offset(x0, y0),
                                end = Offset(x1, y1),
                                strokeWidth = 1.5f
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CapturedKeyframeMaterial3Chip(kf: CapturedKeyframe) {
    val thumbBitmap = remember(kf.imageFilePath) {
        try {
            val file = File(kf.imageFilePath)
            if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
        } catch (_: Exception) {
            null
        }
    }

    ElevatedCard(
        modifier = Modifier
            .width(142.dp)
            .height(60.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
        )
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
                        .size(46.dp)
                        .clip(RoundedCornerShape(10.dp))
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "KF #${kf.index} (${kf.formattedOffsetSeconds})",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = kf.shortSha256,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun FinalizeAndSealMaterial3Sheet(
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
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.VerifiedUser,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "Beweis-Paket Hardware-Versiegeln",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "${captureState.capturedKeyframes.size} Keyframes • ${sensorLive.sampleCount} Sensorpunkte • 3D-Score ${sensorLive.liveParallaxScore}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        OutlinedTextField(
            value = captureState.protocolTitle,
            onValueChange = onUpdateTitle,
            label = { Text("Protokoll-Titel") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(16.dp)
        )

        OutlinedTextField(
            value = captureState.primaryOcrValue,
            onValueChange = onUpdatePrimaryOcr,
            label = { Text(captureState.mode.primaryValueLabel) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(16.dp)
        )

        OutlinedTextField(
            value = captureState.witnessName,
            onValueChange = onUpdateWitness,
            label = { Text("Zeuge / Anwesende Person (Optional)") },
            placeholder = { Text("z. B. Max Mustermann") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(16.dp)
        )

        OutlinedTextField(
            value = captureState.userNotes,
            onValueChange = onUpdateNotes,
            label = { Text("Freitext-Notiz zum Zustand / Vorfall") },
            placeholder = { Text("Zusätzliche Bemerkungen, Zustand, Plombennummer…") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            maxLines = 4,
            shape = RoundedCornerShape(16.dp)
        )

        if (captureState.errorMessage != null) {
            Text(
                text = captureState.errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
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
                    shape = RoundedCornerShape(50)
                ) {
                    Text("Weiteres Bild")
                }
            }

            Button(
                onClick = onConfirmSeal,
                enabled = !captureState.isSealingProtocol,
                modifier = Modifier.weight(1.4f),
                shape = RoundedCornerShape(50),
                contentPadding = PaddingValues(vertical = 14.dp)
            ) {
                if (captureState.isSealingProtocol) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Signiere & baue PDF…", fontWeight = FontWeight.Bold)
                } else {
                    Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Hardware-Signatur & PDF erstellen",
                        fontWeight = FontWeight.ExtraBold
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
