package de.proofseal.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import de.proofseal.app.ui.viewmodel.ProofSealViewModel
import de.proofseal.app.ui.viewmodel.SealVerificationResult
import de.proofseal.app.util.MlKitAnalyzerHelper
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SealVerifierScreen(
    viewModel: ProofSealViewModel,
    onNavigateBack: () -> Unit
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Echtheits-Prüfer",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Zurück"
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        SealVerifierTabContent(
            viewModel = viewModel,
            modifier = Modifier.padding(innerPadding)
        )
    }
}

@Composable
fun SealVerifierTabContent(
    viewModel: ProofSealViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val verificationResult by viewModel.verificationResult.collectAsState()
    val savedRecords by viewModel.records.collectAsState()

    var manualJsonInput by remember { mutableStateOf("") }
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val camPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            camPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val qrExecutor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) {
        onDispose {
            qrExecutor.shutdown()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Header-Info-Card im Material 3 Tonal-Stil
        FilledTonalCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.filledTonalCardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Kryptografischer Siegel-Prüfer",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Prüft QR-Siegel von ProofSeal-PDFs mathematisch via SHA256withECDSA (secp256r1).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 2. Live-Kamera-Sucher oder Verifikations-Ergebnis
        if (verificationResult == null) {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Kamera-Scanner",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        AssistChip(
                            onClick = {},
                            label = { Text("On-Device ML Kit") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.VerifiedUser,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            shape = RoundedCornerShape(50)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(250.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.Black),
                        contentAlignment = Alignment.Center
                    ) {
                        if (hasCameraPermission) {
                            AndroidView(
                                modifier = Modifier.fillMaxSize(),
                                factory = { ctx ->
                                    val previewView = PreviewView(ctx).apply {
                                        scaleType = PreviewView.ScaleType.FILL_CENTER
                                    }
                                    val providerFuture = ProcessCameraProvider.getInstance(ctx)
                                    providerFuture.addListener({
                                        try {
                                            val cameraProvider = providerFuture.get()
                                            val preview = Preview.Builder().build().also {
                                                it.setSurfaceProvider(previewView.surfaceProvider)
                                            }
                                            val analysis = ImageAnalysis.Builder()
                                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                                .build()
                                                .also { imgAnalysis ->
                                                    imgAnalysis.setAnalyzer(
                                                        qrExecutor,
                                                        MlKitAnalyzerHelper.createLiveQrScannerAnalyzer { rawQr ->
                                                            manualJsonInput = rawQr
                                                            viewModel.verifyScannedQrContent(rawQr)
                                                        }
                                                    )
                                                }
                                            cameraProvider.unbindAll()
                                            cameraProvider.bindToLifecycle(
                                                lifecycleOwner,
                                                CameraSelector.DEFAULT_BACK_CAMERA,
                                                preview,
                                                analysis
                                            )
                                        } catch (_: Exception) {
                                        }
                                    }, ContextCompat.getMainExecutor(ctx))
                                    previewView
                                }
                            )

                            // Moderner abgerundeter Zielrahmen im Kamera-Overlay
                            Box(
                                modifier = Modifier
                                    .size(168.dp)
                                    .border(
                                        width = 3.dp,
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                                        shape = RoundedCornerShape(24.dp)
                                    )
                            )

                            Surface(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 12.dp),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                shape = RoundedCornerShape(50)
                            ) {
                                Text(
                                    text = "QR-Siegel von Seite 2 des PDFs im Rahmen zentrieren",
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                )
                            }
                        } else {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.padding(20.dp)
                            ) {
                                Text(
                                    text = "Kamera-Zugriff für QR-Scan benötigt",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White
                                )
                                Button(
                                    onClick = { camPermissionLauncher.launch(Manifest.permission.CAMERA) },
                                    shape = RoundedCornerShape(50)
                                ) {
                                    Text("Kamera aktivieren")
                                }
                            }
                        }
                    }
                }
            }
        } else {
            VerificationResultMaterial3Card(
                result = verificationResult!!,
                onResetScanner = {
                    viewModel.clearVerificationResult()
                },
                onSimulateTamper = {
                    val orig = verificationResult?.payload
                    if (orig != null) {
                        val tamperedHash = if (orig.payloadHash.startsWith("a")) {
                            "b" + orig.payloadHash.drop(1)
                        } else {
                            "a" + orig.payloadHash.drop(1)
                        }
                        val tamperedPayload = orig.copy(payloadHash = tamperedHash)
                        val tamperedJson = tamperedPayload.toCompactJson()
                        manualJsonInput = tamperedJson
                        viewModel.verifyScannedQrContent(tamperedJson)
                    }
                }
            )
        }

        // 3. Direkt-Prüfung & Selbsttest Card
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Direkt-Prüfung & Einzelgerät-Selbsttest",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Teste ein lokal gespeichertes Protokoll oder füge den JSON-Inhalt eines QR-Siegels manuell ein.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (savedRecords.isNotEmpty()) {
                    val latest = savedRecords.first()
                    FilledTonalButton(
                        onClick = {
                            val qrJson = latest.toQrSealPayload().toCompactJson()
                            manualJsonInput = qrJson
                            viewModel.verifyScannedQrContent(qrJson)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.VerifiedUser,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Letztes eigenes Protokoll (${latest.title}) prüfen",
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                OutlinedTextField(
                    value = manualJsonInput,
                    onValueChange = { manualJsonInput = it },
                    label = { Text("QR-Siegel JSON-Payload") },
                    placeholder = { Text("{\"app\":\"ProofSeal\",\"v\":1,\"hash\":\"…\"}") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4,
                    shape = RoundedCornerShape(16.dp)
                )

                Button(
                    onClick = { viewModel.verifyScannedQrContent(manualJsonInput) },
                    enabled = manualJsonInput.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(50),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    Text(
                        text = "ECDSA-Signatur mathematisch verifizieren",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun VerificationResultMaterial3Card(
    result: SealVerificationResult,
    onResetScanner: () -> Unit,
    onSimulateTamper: () -> Unit
) {
    val isValid = result.isValidSignature
    val bannerContainer = if (isValid) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.errorContainer
    }
    val bannerContent = if (isValid) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onErrorContainer
    }

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                color = bannerContainer,
                contentColor = bannerContent,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = bannerContent.copy(alpha = 0.15f),
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isValid) Icons.Default.CheckCircle else Icons.Default.Error,
                                contentDescription = null,
                                tint = bannerContent,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = if (isValid) {
                                "Signatur mathematisch gültig"
                            } else {
                                "Signaturbruch erkannt!"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = if (isValid) {
                                "ECDSA (secp256r1) Signatur bestätigt Originalität und Unversehrtheit."
                            } else {
                                result.errorReason ?: "Daten oder Signatur wurden verändert."
                            },
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            val p = result.payload
            if (p != null) {
                M3VerifiedItem("Zertifizierter OCR-/Barcode-Wert", p.primaryOcrValue, highlight = true)
                M3VerifiedItem("Protokoll-ID (UUID)", p.protocolId, mono = true)
                M3VerifiedItem("Zeitstempel (UTC)", p.timestampUtc, mono = true)
                M3VerifiedItem("Aufnahmegerät", p.deviceModel)
                M3VerifiedItem("Hardware-Sicherheitslevel", p.securityLevel)
                M3VerifiedItem(
                    "Sensorkette & 3D-Parallaxe",
                    "${p.parallaxScore}% 3D-Score • ${if (p.isContinuous) "Lückenlos" else "Lücke gemeldet"}"
                )
                M3VerifiedItem("SHA-256 Payload Hash", p.payloadHash, mono = true)
                M3VerifiedItem("Public Key Fingerprint", result.computedKeyFingerprint, mono = true)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (isValid && result.payload != null) {
                    OutlinedButton(
                        onClick = onSimulateTamper,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(50)
                    ) {
                        Icon(
                            imageVector = Icons.Default.BugReport,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Manipulation testen")
                    }
                }

                FilledTonalButton(
                    onClick = onResetScanner,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(50)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Neuer Scan")
                }
            }
        }
    }
}

@Composable
private fun M3VerifiedItem(
    label: String,
    value: String,
    mono: Boolean = false,
    highlight: Boolean = false
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = if (highlight) {
                    MaterialTheme.typography.titleSmall
                } else {
                    MaterialTheme.typography.bodySmall
                },
                color = if (highlight) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
                fontWeight = if (highlight) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}
