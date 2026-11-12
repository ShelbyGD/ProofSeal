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
import androidx.compose.material.icons.filled.DevicesOther
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
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
import de.proofseal.app.ui.viewmodel.CertificateOriginStatus
import de.proofseal.app.ui.viewmodel.ProofSealViewModel
import de.proofseal.app.ui.viewmodel.SealVerificationResult
import de.proofseal.app.util.MlKitAnalyzerHelper
import java.io.File
import java.util.Locale
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
    val hardwareInfo by viewModel.hardwareInfo.collectAsState()

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

    // SAF-Dateiwähler für direkte PDF-Datei-Integritätsprüfung (WhatsApp / Downloads)
    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.verifyPdfDocumentUri(uri)
        }
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
        // 1. Header-Card + Prominenter "PDF-Datei direkt prüfen (WhatsApp / Downloads)" Button
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
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
                            text = "Kryptografischer Siegel- & PDF-Prüfer",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Prüft QR-Siegel & PDF-Dateien auf ECDSA-Hardware-Signatur, Bild-Hashes und Geräte-Herkunft.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Button(
                    onClick = { pdfPickerLauncher.launch(arrayOf("application/pdf")) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(50),
                    contentPadding = PaddingValues(vertical = 13.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.PictureAsPdf,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "PDF-Datei direkt prüfen (WhatsApp / Downloads)",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.ExtraBold
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
                            text = "Live QR-Siegel-Scanner",
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
                        // Simuliere einen manipulierten Foto-Hash im QR-Code (z. B. ausgetauschtes Bild)
                        val tamperedImages = if (orig.imageHashes.isNotEmpty()) {
                            listOf("IMG1: FFFF0000DEAD9999") + orig.imageHashes.drop(1)
                        } else {
                            listOf("IMG1: FFFF0000DEAD9999")
                        }
                        val tamperedPayload = orig.copy(imageHashes = tamperedImages)
                        val tamperedJson = tamperedPayload.toCompactJson()
                        manualJsonInput = tamperedJson
                        viewModel.verifyScannedQrContent(tamperedJson)
                    }
                },
                onSimulateForeignDevice = {
                    viewModel.simulateForeignDeviceCertificate()
                }
            )
        }

        // 3. Direkt-Prüfung, Manipulations-Simulation & Einzelgerät-Selbsttest Card
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
                    text = "Direkt-Prüfung & Sicherheits-Selbsttest",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Eigener Hardware-Key dieses Geräts: ${hardwareInfo.publicKeyFingerprint}",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary
                )

                if (savedRecords.isNotEmpty()) {
                    val latest = savedRecords.first()
                    val latestPdfFile = File(latest.pdfFilePath)

                    FilledTonalButton(
                        onClick = {
                            if (latestPdfFile.exists()) {
                                viewModel.verifyLocalPdfFile(latestPdfFile, simulateBinaryTamper = false)
                            } else {
                                val qrJson = latest.toQrSealPayload().toCompactJson()
                                manualJsonInput = qrJson
                                viewModel.verifyScannedQrContent(qrJson)
                            }
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
                            text = "Letzte eigene PDF (${latest.title}) komplett prüfen",
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                if (latestPdfFile.exists()) {
                                    viewModel.verifyLocalPdfFile(latestPdfFile, simulateBinaryTamper = true)
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.BugReport,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "PDF-Manipulation",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }

                        OutlinedButton(
                            onClick = { viewModel.simulateForeignDeviceCertificate() },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DevicesOther,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Fremdgerät-Test",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                } else {
                    OutlinedButton(
                        onClick = { viewModel.simulateForeignDeviceCertificate() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DevicesOther,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Fremdgerät-Signatur simulieren & prüfen")
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

                OutlinedTextField(
                    value = manualJsonInput,
                    onValueChange = { manualJsonInput = it },
                    label = { Text("QR-Siegel JSON-Payload manuell prüfen") },
                    placeholder = { Text("{\"app\":\"ProofSeal\",\"v\":2,\"hash\":\"…\"}") },
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
                        text = "ECDSA-Signatur & Bild-Hashes verifizieren",
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
    onSimulateTamper: () -> Unit,
    onSimulateForeignDevice: () -> Unit
) {
    // Farben & Texte exakt nach den 3 Sicherheits-Zuständen:
    // 1. OWN_DEVICE (Smaragdgrün)
    // 2. FOREIGN_DEVICE (Blau/Bernstein Info-Warnung)
    // 3. TAMPERED_OR_INVALID (Rotes Alarm-Badge)
    val badgeBgColor: Color
    val badgeBorderColor: Color
    val badgeTextColor: Color
    val badgeTitle: String
    val badgeSubtitle: String
    val badgeIcon = when (result.originStatus) {
        CertificateOriginStatus.OWN_DEVICE -> {
            badgeBgColor = Color(0xFF064E3B)
            badgeBorderColor = Color(0xFF10B981)
            badgeTextColor = Color(0xFFECFDF5)
            badgeTitle = "✅ SIGNIERT VON DIESEM GERÄT (Eigenes Original-Zertifikat)"
            badgeSubtitle = "Der öffentliche ECDSA-Schlüssel im Zertifikat stimmt zu 100 % mit dem lokalen Hardware-Keystore dieses Smartphones überein. Alle Daten & Bild-Hashes sind unversehrt."
            Icons.Default.CheckCircle
        }
        CertificateOriginStatus.FOREIGN_DEVICE -> {
            badgeBgColor = Color(0xFF1E3A8A)
            badgeBorderColor = Color(0xFFF59E0B)
            badgeTextColor = Color(0xFFFEF3C7)
            badgeTitle = "ℹ️ GÜLTIGE SIGNATUR EINES FREMDGERÄTS"
            badgeSubtitle = "Die ECDSA-Signatur und alle Bild-Hashes sind mathematisch unversehrt, wurden jedoch von einem ANDEREN Smartphone erzeugt. Vergleiche unten den fremden Key-Fingerprint!"
            Icons.Default.WarningAmber
        }
        CertificateOriginStatus.TAMPERED_OR_INVALID -> {
            badgeBgColor = Color(0xFF7F1D1D)
            badgeBorderColor = Color(0xFFEF4444)
            badgeTextColor = Color(0xFFFEE2E2)
            badgeTitle = "🚨 WARNUNG: SIGNATUR GEBROCHEN – DATEN WURDEN MANIPULIERT!"
            badgeSubtitle = result.errorReason
                ?: "Kryptografischer Signatur- oder Hash-Bruch! Mindestens ein Feld, ein Foto-Hash oder die PDF-Datei wurde nachträglich verändert."
            Icons.Default.Error
        }
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
            // Großes Top-Badge (Eigenes Gerät vs. Fremdgerät vs. Manipuliert)
            Surface(
                color = badgeBgColor,
                contentColor = badgeTextColor,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(2.dp, badgeBorderColor),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = badgeBorderColor.copy(alpha = 0.25f),
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = badgeIcon,
                                contentDescription = null,
                                tint = badgeBorderColor,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = badgeTitle,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = badgeTextColor
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = badgeSubtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = badgeTextColor.copy(alpha = 0.92f)
                        )
                    }
                }
            }

            // Falls Direkt-Prüfung einer PDF-Datei: Binärstrom-Status anzeigen
            if (result.isDirectPdfCheck) {
                val pdfOk = result.isPdfBinaryStreamIntact == true
                Surface(
                    color = if (pdfOk) {
                        Color(0xFF065F46).copy(alpha = 0.25f)
                    } else {
                        Color(0xFF7F1D1D).copy(alpha = 0.3f)
                    },
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(
                        1.dp,
                        if (pdfOk) Color(0xFF10B981) else Color(0xFFEF4444)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.PictureAsPdf,
                                contentDescription = null,
                                tint = if (pdfOk) Color(0xFF10B981) else Color(0xFFEF4444),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (pdfOk) {
                                    "PDF-Binärstrom 100 % original (${result.pdfFileName ?: "PDF"})"
                                } else {
                                    "PDF-Datei binär verändert oder unversiegelt (${result.pdfFileName ?: "PDF"})"
                                },
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (!result.expectedPdfSha256.isNullOrBlank()) {
                            Text(
                                text = "Signierter PDF-Hash: ${result.expectedPdfSha256.take(32)}…",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        if (!result.actualPdfSha256.isNullOrBlank()) {
                            Text(
                                text = "Aktueller PDF-Hash:  ${result.actualPdfSha256.take(32)}…",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }

            // Hervorhebung bei FREMDGERÄT: Direkter Vergleich Fremd-Key vs. Eigenes Gerät
            if (result.originStatus == CertificateOriginStatus.FOREIGN_DEVICE && result.payload != null) {
                Surface(
                    color = Color(0xFF78350F).copy(alpha = 0.28f),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.5.dp, Color(0xFFF59E0B)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Herkunfts-Abgleich: Fremdgerät erkannt",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFFFBBF24)
                        )
                        Text(
                            text = "Gerätemodell laut Zertifikat: ${result.payload.deviceModel}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Fremder Key-Fingerprint:\n${result.computedKeyFingerprint}",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFFFDE68A)
                        )
                        Text(
                            text = "Eigener Key-Fingerprint (dieses Gerät):\n${result.localDeviceKeyFingerprint}",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            val p = result.payload
            if (p != null) {
                M3VerifiedItem(
                    label = "Zertifizierter OCR-/Barcode-Hauptwert (${p.modeTitle})",
                    value = p.primaryOcrValue,
                    highlight = true
                )

                // Versiegelte Foto-Prüfsummen (SHA-256 Kurz-Hashes) prominent auflisten
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.PhotoLibrary,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Versiegelte Foto-Prüfsummen (${p.imageHashes.size} Bilder verankert)",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(
                            text = "Jedes Beweisfoto auf Seite 1 des PDFs ist mit seinem SHA-256 Kurzwert in der ECDSA-Signatur verankert:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (p.imageHashes.isEmpty()) {
                            Text(
                                text = "Keine individuellen Bild-Hashes im älteren v1-Siegel enthalten",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace
                            )
                        } else {
                            p.imageHashes.forEachIndexed { idx, imgHash ->
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 7.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Foto #${idx + 1} SHA-256",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = imgHash,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                val uptimeSec = String.format(Locale.US, "%.2f s", p.uptimeNanos / 1_000_000_000.0)
                M3VerifiedItem(
                    label = "Zeitstempel (UTC) & Monotone Uptime",
                    value = "${p.timestampUtc} • Uptime $uptimeSec",
                    mono = true
                )
                M3VerifiedItem(
                    label = "Aufnahmegerät (Hardware-gebunden)",
                    value = p.deviceModel
                )
                M3VerifiedItem(
                    label = "Sicherheits-Chip-Level",
                    value = p.securityLevel
                )
                M3VerifiedItem(
                    label = "Sensorkette & 3D-Parallaxe",
                    value = "${p.parallaxScore}% 3D-Score • ${if (p.isContinuous) "Lückenlose Kette" else "Lücke gemeldet"}"
                )
                M3VerifiedItem(
                    label = "Protokoll-ID (UUID)",
                    value = p.protocolId,
                    mono = true
                )
                M3VerifiedItem(
                    label = "Unterzeichnender Key-Fingerprint",
                    value = result.computedKeyFingerprint,
                    mono = true
                )
                M3VerifiedItem(
                    label = "SHA-256 Kanonischer Payload-Hash",
                    value = p.payloadHash,
                    mono = true
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (result.isValidSignature && result.payload != null) {
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
                        Text("Bild-Hash brechen", style = MaterialTheme.typography.labelMedium)
                    }
                } else {
                    OutlinedButton(
                        onClick = onSimulateForeignDevice,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(50)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DevicesOther,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Fremdgerät testen", style = MaterialTheme.typography.labelMedium)
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
                    Text("Neuer Scan", style = MaterialTheme.typography.labelMedium)
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
