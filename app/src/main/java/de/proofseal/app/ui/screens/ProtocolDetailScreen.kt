package de.proofseal.app.ui.screens

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.proofseal.app.ui.viewmodel.ProofSealViewModel
import de.proofseal.app.util.ShareHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
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
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = record?.title ?: "Beweis-Protokoll",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
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
        if (record == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
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
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Tonal Hero-Card für WhatsApp / Share & Schnellaktionen
            FilledTonalCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.filledTonalCardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            modifier = Modifier.size(46.dp),
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = "Signiertes 2-Seiten-PDF bereit ($fileSizeKb KB)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "WhatsApp-fest (< 2,5 MB) • ${record.hardwareSecurityLevel}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
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
                        shape = RoundedCornerShape(50),
                        contentPadding = PaddingValues(vertical = 13.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Share, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Per WhatsApp / E-Mail / Drive teilen",
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FilledTonalButton(
                            onClick = { ShareHelper.openPdfExternally(context, pdfFile) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(50)
                        ) {
                            Icon(
                                imageVector = Icons.Default.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Extern öffnen")
                        }

                        FilledTonalButton(
                            onClick = {
                                viewModel.verifyScannedQrContent(record.toQrSealPayload().toCompactJson())
                                onOpenVerifierWithProtocol()
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(50)
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCodeScanner,
                                contentDescription = null,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Siegel prüfen")
                        }
                    }
                }
            }

            // 2. Vorschau der beiden DIN-A4 PDF-Seiten
            Text(
                text = "Vorschau des DIN-A4 Beweis-Zertifikats",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            if (isRenderingPdf) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                renderedPages.forEachIndexed { index, pageBmp ->
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 3.dp),
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = if (index == 0) {
                                    "Seite 1/2 • Visuelles Beweis-Protokoll & Keyframes"
                                } else {
                                    "Seite 2/2 • Sensorketten-Diagramm, ECDSA-Block & QR-Siegel"
                                },
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Image(
                                bitmap = pageBmp.asImageBitmap(),
                                contentDescription = "PDF Seite ${index + 1}",
                                contentScale = ContentScale.FillWidth,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(595f / 842f)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color.White)
                            )
                        }
                    }
                }
            }

            // 3. Kryptografische Metadaten-Zusammenfassung
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.VerifiedUser,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Technische Prüfdaten & Hardware-Signatur",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    DetailMonoItem("Protokoll-ID (UUID)", record.protocolId)
                    DetailMonoItem("Erkannter Hauptwert (OCR / Barcode)", record.primaryOcrValue)
                    DetailMonoItem("Monotone Hardware-Uptime", record.uptimeSpanFormatted)
                    DetailMonoItem(
                        "Sensorkette & 3D-Parallaxe",
                        "${record.totalSensorSamples} Samples • Max Gap ${record.maxSensorGapMillis}ms • 3D-Score ${record.parallaxConfidenceScore}%"
                    )
                    DetailMonoItem("GPS-Standort", record.gpsLocationText)
                    DetailMonoItem("Kombinierter SHA-256 Payload Hash", record.payloadSha256Hex)
                    DetailMonoItem("Sensorketten-SHA256", record.sensorChainSha256)
                    DetailMonoItem("ECDSA (secp256r1) Hardware-Signatur", record.signatureBase64)
                    DetailMonoItem("Public Key Fingerprint", record.publicKeyFingerprint)
                }
            }
        }
    }
}

@Composable
private fun DetailMonoItem(label: String, value: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
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
