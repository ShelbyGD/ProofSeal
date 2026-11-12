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
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import de.proofseal.app.ui.theme.AmberContainer
import de.proofseal.app.ui.theme.AmberWarning
import de.proofseal.app.ui.theme.AnthraciteBorder
import de.proofseal.app.ui.theme.AnthraciteCard
import de.proofseal.app.ui.theme.AnthraciteDeep
import de.proofseal.app.ui.theme.AnthraciteSurface
import de.proofseal.app.ui.theme.CrimsonAlert
import de.proofseal.app.ui.theme.CrimsonContainer
import de.proofseal.app.ui.theme.CyanTelemetry
import de.proofseal.app.ui.theme.EmeraldBright
import de.proofseal.app.ui.theme.EmeraldContainer
import de.proofseal.app.ui.theme.EmeraldPrimary
import de.proofseal.app.ui.theme.TextOffWhite
import de.proofseal.app.ui.theme.TextSlateMuted
import de.proofseal.app.ui.viewmodel.ProofSealViewModel
import de.proofseal.app.ui.viewmodel.SealVerificationResult
import de.proofseal.app.util.MlKitAnalyzerHelper
import java.util.concurrent.Executors

@Composable
fun SealVerifierScreen(
    viewModel: ProofSealViewModel,
    onNavigateBack: () -> Unit
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
        viewModel.clearVerificationResult()
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

    Scaffold(
        containerColor = AnthraciteDeep
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Top-Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Zurück",
                        tint = TextOffWhite
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Column {
                    Text(
                        text = "ECHTHEITS-PRÜFER (QR & ECDSA)",
                        color = TextOffWhite,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "Mathematische Signaturprüfung nach SHA256withECDSA (secp256r1)",
                        color = EmeraldBright,
                        fontSize = 11.5.sp
                    )
                }
            }

            // 2. Live-QR-Scanner-Kamera (wenn noch kein Scan-Ergebnis vorliegt)
            if (verificationResult == null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = AnthraciteSurface),
                    border = BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.55f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.QrCodeScanner,
                                contentDescription = null,
                                tint = EmeraldBright,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "LIVE-KAMERA AUF PROOFSEAL-PDF QR-CODE RICHTEN",
                                color = EmeraldBright,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(230.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .border(1.dp, AnthraciteBorder, RoundedCornerShape(12.dp))
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

                                // Grüner QR-Scan-Rahmen
                                Box(
                                    modifier = Modifier
                                        .size(150.dp)
                                        .border(2.dp, EmeraldBright, RoundedCornerShape(14.dp))
                                )
                            } else {
                                Button(
                                    onClick = { camPermissionLauncher.launch(Manifest.permission.CAMERA) },
                                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                                ) {
                                    Text("Kamera für QR-Scanner freigeben", color = Color(0xFF022C22))
                                }
                            }
                        }
                    }
                }
            } else {
                // 3. Zertifikats-Prüfergebnis-Karte
                VerificationResultDetailCard(
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

            // 4. Direktprüfung eines lokal gespeicherten Protokolls oder manueller JSON-Import
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = AnthraciteSurface),
                border = BorderStroke(1.dp, AnthraciteBorder)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "DIREKT-PRÜFUNG & SELBSTTEST (AUCH AUF EINZELGERÄT)",
                        color = TextOffWhite,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold
                    )

                    if (savedRecords.isNotEmpty()) {
                        val latest = savedRecords.first()
                        OutlinedButton(
                            onClick = {
                                val qrJson = latest.toQrSealPayload().toCompactJson()
                                manualJsonInput = qrJson
                                viewModel.verifyScannedQrContent(qrJson)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.6f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.VerifiedUser,
                                contentDescription = null,
                                tint = EmeraldBright,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Letztes eigenes Protokoll kryptografisch prüfen",
                                color = EmeraldBright,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    OutlinedTextField(
                        value = manualJsonInput,
                        onValueChange = { manualJsonInput = it },
                        label = { Text("QR-Siegel JSON-Payload (optional manuell einfügen)") },
                        placeholder = { Text("{\"app\":\"ProofSeal\",\"v\":1,\"hash\":\"…\",\"sig\":\"…\",\"pub\":\"…\"}") },
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

                    Button(
                        onClick = { viewModel.verifyScannedQrContent(manualJsonInput) },
                        enabled = manualJsonInput.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EmeraldPrimary,
                            contentColor = Color(0xFF022C22)
                        )
                    ) {
                        Text(
                            text = "ECDSA-SIGNATUR MATHEMATISCH PRÜFEN",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VerificationResultDetailCard(
    result: SealVerificationResult,
    onResetScanner: () -> Unit,
    onSimulateTamper: () -> Unit
) {
    val isValid = result.isValidSignature
    val containerCol = if (isValid) EmeraldContainer else CrimsonContainer
    val accentCol = if (isValid) EmeraldBright else CrimsonAlert

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = AnthraciteSurface),
        border = BorderStroke(2.dp, accentCol)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                color = containerCol,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isValid) Icons.Default.CheckCircle else Icons.Default.Error,
                        contentDescription = null,
                        tint = accentCol,
                        modifier = Modifier.size(30.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = if (isValid) {
                                "KRYPTOGRAFISCHE SIGNATUR VERIFIZIERT"
                            } else {
                                "SIGNATURBRUCH / MANIPULATION ERKANNT!"
                            },
                            color = accentCol,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = if (isValid) {
                                "ECDSA (secp256r1) Signatur passt mathematisch exakt zum SHA-256 Hash und Public Key."
                            } else {
                                result.errorReason ?: "Die Signatur stimmt nicht mit den Zertifikatsdaten überein."
                            },
                            color = TextOffWhite,
                            fontSize = 11.5.sp
                        )
                    }
                }
            }

            val p = result.payload
            if (p != null) {
                VerifiedFieldRow("Protokoll-ID (UUID)", p.protocolId, mono = true)
                VerifiedFieldRow("Zertifizierter Zeitstempel (UTC)", p.timestampUtc, mono = true)
                VerifiedFieldRow("Aufnahmegerät (Hardware)", p.deviceModel)
                VerifiedFieldRow("Hardware-Keystore-Level", p.securityLevel)
                VerifiedFieldRow("Beweis-Modus", p.modeTitle)
                VerifiedFieldRow("Zertifizierter OCR-/Barcode-Wert", p.primaryOcrValue, highlight = true)
                VerifiedFieldRow(
                    "3D-Parallaxe & Sensorkette",
                    "${p.parallaxScore}% 3D-Score • ${if (p.isContinuous) "Lückenlose Aufzeichnung" else "Lücke gemeldet"}"
                )
                VerifiedFieldRow("SHA-256 Payload Hash", p.payloadHash, mono = true)
                VerifiedFieldRow("Public Key Fingerprint", result.computedKeyFingerprint, mono = true)
                VerifiedFieldRow("ECDSA Hardware-Signatur (Base64)", p.signatureBase64, mono = true)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isValid && result.payload != null) {
                    OutlinedButton(
                        onClick = onSimulateTamper,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, AmberWarning)
                    ) {
                        Icon(
                            imageVector = Icons.Default.BugReport,
                            contentDescription = null,
                            tint = AmberWarning,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Manipulation testen", color = AmberWarning, fontSize = 11.5.sp)
                    }
                }

                Button(
                    onClick = onResetScanner,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AnthraciteCard,
                        contentColor = TextOffWhite
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Neuen QR-Code scannen", fontSize = 11.5.sp)
                }
            }
        }
    }
}

@Composable
private fun VerifiedFieldRow(
    label: String,
    value: String,
    mono: Boolean = false,
    highlight: Boolean = false
) {
    Surface(
        color = AnthraciteDeep,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Text(
                text = label.uppercase(),
                color = TextSlateMuted,
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = value,
                color = if (highlight) CyanTelemetry else TextOffWhite,
                fontSize = if (mono) 10.5.sp else 12.sp,
                fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
                fontWeight = if (highlight) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}
