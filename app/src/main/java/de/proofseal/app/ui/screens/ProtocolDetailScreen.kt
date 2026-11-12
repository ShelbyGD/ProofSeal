package de.proofseal.app.ui.screens

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.proofseal.app.ui.theme.AnthraciteBorder
import de.proofseal.app.ui.theme.AnthraciteCard
import de.proofseal.app.ui.theme.AnthraciteDeep
import de.proofseal.app.ui.theme.AnthraciteSurface
import de.proofseal.app.ui.theme.CyanTelemetry
import de.proofseal.app.ui.theme.EmeraldBright
import de.proofseal.app.ui.theme.EmeraldContainer
import de.proofseal.app.ui.theme.EmeraldPrimary
import de.proofseal.app.ui.theme.TextOffWhite
import de.proofseal.app.ui.theme.TextSlateMuted
import de.proofseal.app.ui.viewmodel.ProofSealViewModel
import de.proofseal.app.util.ShareHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

@Composable
fun ProtocolDetailScreen(
    protocolId: String,
    viewModel: ProofSealViewModel,
    onNavigateBack: () -> Unit,
    onOpenVerifierWithProtocol: () -> Unit
) {
    val context = LocalContext.current
    val allRecords by viewModel.records.collectAsState()
    val record = allRecords.firstOrNull { it.protocolId == protocolId }

    var renderedPages by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var isRenderingPdf by remember { mutableStateOf(true) }

    LaunchedEffect(record?.pdfFilePath) {
        val path = record?.pdfFilePath ?: return@LaunchedEffect
        isRenderingPdf = true
        renderedPages = withContext(Dispatchers.IO) {
            renderPdfPagesToBitmaps(File(path))
        }
        isRenderingPdf = false
    }

    Scaffold(
        containerColor = AnthraciteDeep
    ) { innerPadding ->
        if (record == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = EmeraldBright)
            }
            return@Scaffold
        }

        val pdfFile = File(record.pdfFilePath)
        val fileSizeKb = if (pdfFile.exists()) pdfFile.length() / 1024L else 0L

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Top-Leiste
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = record.title,
                        color = TextOffWhite,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "${record.modeTitle} • ${record.createdAtFormatted}",
                        color = EmeraldBright,
                        fontSize = 11.5.sp
                    )
                }
            }

            // 2. Primäre WhatsApp / Share-Aktion
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = EmeraldContainer),
                border = BorderStroke(1.5.dp, EmeraldBright)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = EmeraldBright,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "HARDWARE-SIGNIERTES 2-SEITEN-PDF BEREIT (${fileSizeKb} KB)",
                                color = EmeraldBright,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "WhatsApp-fest (< 2,5 MB) • Signiert mit ${record.hardwareSecurityLevel}",
                                color = TextOffWhite,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Button(
                        onClick = {
                            ShareHelper.sharePdfCertificate(
                                context = context,
                                pdfFile = pdfFile,
                                protocolTitle = record.title,
                                protocolId = record.protocolId,
                                primaryValue = record.primaryOcrValue,
                                sha256Hash = record.payloadSha256Hex
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EmeraldBright,
                            contentColor = Color(0xFF022C22)
                        ),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Share, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "PER WHATSAPP / E-MAIL / DRIVE TEILEN",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.sp
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { ShareHelper.openPdfExternally(context, pdfFile) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, EmeraldBright)
                        ) {
                            Icon(
                                imageVector = Icons.Default.OpenInNew,
                                contentDescription = null,
                                tint = TextOffWhite,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("PDF extern öffnen", color = TextOffWhite, fontSize = 11.5.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                viewModel.verifyScannedQrContent(record.toQrSealPayload().toCompactJson())
                                onOpenVerifierWithProtocol()
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, EmeraldBright)
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCodeScanner,
                                contentDescription = null,
                                tint = EmeraldBright,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Siegel prüfen", color = EmeraldBright, fontSize = 11.5.sp)
                        }
                    }
                }
            }

            // 3. Integrierte Live-Vorschau beider DIN-A4 PDF-Seiten
            Text(
                text = "VORSCHAU DES GENERIERTEN DIN-A4 BEWEIS-ZERTIFIKATS (2 SEITEN)",
                color = TextOffWhite,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )

            if (isRenderingPdf) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = EmeraldBright)
                }
            } else {
                renderedPages.forEachIndexed { index, pageBmp ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = AnthraciteSurface),
                        border = BorderStroke(1.dp, AnthraciteBorder)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = if (index == 0) {
                                    "SEITE 1/2 • VISUELLES BEWEIS-PROTOKOLL & KEYFRAMES"
                                } else {
                                    "SEITE 2/2 • SENSORKETTEN-DIAGRAMM, ECDSA-BLOCK & QR-SIEGEL"
                                },
                                color = EmeraldBright,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Image(
                                bitmap = pageBmp.asImageBitmap(),
                                contentDescription = "PDF Seite ${index + 1}",
                                contentScale = ContentScale.FillWidth,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(595f / 842f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.White)
                            )
                        }
                    }
                }
            }

            // 4. Kryptografische Metadaten-Zusammenfassung
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = AnthraciteSurface),
                border = BorderStroke(1.dp, AnthraciteBorder)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.VerifiedUser,
                            contentDescription = null,
                            tint = EmeraldBright,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "TECHNISCHE PRÜFDATEN & HARDWARE-SIGNATUR",
                            color = EmeraldBright,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    DetailMonoItem("PROTOKOLL-ID (UUID)", record.protocolId)
                    DetailMonoItem("ERKANNTER HAUPTWERT (OCR / BARCODE)", record.primaryOcrValue)
                    DetailMonoItem("MONOTONE HARDWARE-UPTIME", record.uptimeSpanFormatted)
                    DetailMonoItem(
                        "SENSORKETTE & 3D-PARALLAXE",
                        "${record.totalSensorSamples} Samples • Max Gap ${record.maxSensorGapMillis}ms • 3D-Score ${record.parallaxConfidenceScore}%"
                    )
                    DetailMonoItem("GPS-STANDORT", record.gpsLocationText)
                    DetailMonoItem("KOMBINIERTER SHA-256 PAYLOAD HASH", record.payloadSha256Hex)
                    DetailMonoItem("SENSORKETTEN-SHA256", record.sensorChainSha256)
                    DetailMonoItem("ECDSA (secp256r1) HARDWARE-SIGNATUR", record.signatureBase64)
                    DetailMonoItem("PUBLIC KEY FINGERPRINT", record.publicKeyFingerprint)
                }
            }
        }
    }
}

@Composable
private fun DetailMonoItem(label: String, value: String) {
    Surface(
        color = AnthraciteDeep,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Text(
                text = label,
                color = TextSlateMuted,
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = value,
                color = CyanTelemetry,
                fontSize = 10.5.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

private fun renderPdfPagesToBitmaps(pdfFile: File): List<Bitmap> {
    if (!pdfFile.exists()) return emptyList()
    val pages = mutableListOf<Bitmap>()
    var descriptor: ParcelFileDescriptor? = null
    var renderer: PdfRenderer? = null
    try {
        descriptor = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
        renderer = PdfRenderer(descriptor)
        for (i in 0 until renderer.pageCount) {
            val page = renderer.openPage(i)
            val width = page.width * 2
            val height = page.height * 2
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(AndroidColor.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()
            pages.add(bitmap)
        }
    } catch (_: Exception) {
    } finally {
        try {
            renderer?.close()
        } catch (_: Exception) {
        }
        try {
            descriptor?.close()
        } catch (_: Exception) {
        }
    }
    return pages
}
