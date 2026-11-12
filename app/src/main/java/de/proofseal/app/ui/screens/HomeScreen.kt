package de.proofseal.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.GppGood
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.proofseal.app.data.ProofRecordEntity
import de.proofseal.app.model.ProofMode
import de.proofseal.app.ui.theme.AmberWarning
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
import de.proofseal.app.ui.viewmodel.HardwareKeystoreInfo
import de.proofseal.app.ui.viewmodel.ProofSealViewModel
import de.proofseal.app.util.ShareHelper
import java.io.File

@Composable
fun HomeScreen(
    viewModel: ProofSealViewModel,
    onStartCaptureMode: (ProofMode) -> Unit,
    onOpenVerifier: () -> Unit,
    onOpenProtocolDetail: (String) -> Unit
) {
    val context = LocalContext.current
    val hardwareInfo by viewModel.hardwareInfo.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val records by viewModel.records.collectAsState()

    Scaffold(
        containerColor = AnthraciteDeep
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Header & Hardware-Keystore Sicherheitsstatus
            item {
                HardwareSecurityHeaderCard(
                    hardwareInfo = hardwareInfo,
                    onOpenVerifier = onOpenVerifier
                )
            }

            // 2. Sektion: Die 3 geführten Beweis-Modi
            item {
                Text(
                    text = "NEUES BEWEIS-PROTOKOLL STARTEN",
                    color = EmeraldBright,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.1.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Text(
                    text = "Lückenlose Sensorkette + 3D-Anti-Fake-Prüfung + On-Device OCR & Hardware-Signatur",
                    color = TextSlateMuted,
                    fontSize = 12.sp
                )
            }

            item {
                GuidedModeSelectionCard(
                    mode = ProofMode.PACKAGE_SHIPPING,
                    icon = Icons.Default.Inventory2,
                    accentColor = EmeraldPrimary,
                    badgeText = "3 PFLICHT-SCHRITTE • 4 KEYFRAMES",
                    onClick = { onStartCaptureMode(ProofMode.PACKAGE_SHIPPING) }
                )
            }

            item {
                GuidedModeSelectionCard(
                    mode = ProofMode.METER_HANDOVER,
                    icon = Icons.Default.Speed,
                    accentColor = CyanTelemetry,
                    badgeText = "3D-PARALLAXE • LIVE-OCR PRÜFFELD",
                    onClick = { onStartCaptureMode(ProofMode.METER_HANDOVER) }
                )
            }

            item {
                GuidedModeSelectionCard(
                    mode = ProofMode.INCIDENT_FAST,
                    icon = Icons.Default.ReportProblem,
                    accentColor = AmberWarning,
                    badgeText = "SCHNELL-PROTOKOLL • GPS & ZEUGENFELD",
                    onClick = { onStartCaptureMode(ProofMode.INCIDENT_FAST) }
                )
            }

            // 3. Lokales Beweis-Archiv & Suche (Room Database)
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "VERSIEGELTE PROTOKOLLE (${records.size})",
                        color = TextOffWhite,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                    Text(
                        text = "Lokal & Offline gesichert",
                        color = EmeraldBright,
                        fontSize = 11.sp
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = viewModel::updateSearchQuery,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = {
                        Text(
                            "Suche nach Titel, Sendungsnr., OCR-Text, Datum…",
                            color = TextSlateMuted,
                            fontSize = 13.sp
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Suchen",
                            tint = EmeraldBright
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Suche löschen",
                                    tint = TextSlateMuted
                                )
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = EmeraldPrimary,
                        unfocusedBorderColor = AnthraciteBorder,
                        focusedContainerColor = AnthraciteSurface,
                        unfocusedContainerColor = AnthraciteSurface,
                        focusedTextColor = TextOffWhite,
                        unfocusedTextColor = TextOffWhite
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            if (records.isEmpty()) {
                item {
                    EmptyArchiveCard(searchQuery = searchQuery)
                }
            } else {
                items(records, key = { it.protocolId }) { record ->
                    ProofRecordArchiveItemCard(
                        record = record,
                        onOpenDetail = { onOpenProtocolDetail(record.protocolId) },
                        onSharePdf = {
                            ShareHelper.sharePdfCertificate(
                                context = context,
                                pdfFile = File(record.pdfFilePath),
                                protocolTitle = record.title,
                                protocolId = record.protocolId,
                                primaryValue = record.primaryOcrValue,
                                sha256Hash = record.payloadSha256Hex
                            )
                        },
                        onDelete = { viewModel.deleteRecord(record) }
                    )
                }
            }
        }
    }
}

@Composable
private fun HardwareSecurityHeaderCard(
    hardwareInfo: HardwareKeystoreInfo,
    onOpenVerifier: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = AnthraciteSurface),
        border = BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.45f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(EmeraldContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.GppGood,
                            contentDescription = null,
                            tint = EmeraldBright,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "PROOFSEAL",
                            color = TextOffWhite,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.2.sp
                        )
                        Text(
                            text = "Digitaler Zeuge & Hardware-Beweisgenerator",
                            color = TextSlateMuted,
                            fontSize = 12.sp
                        )
                    }
                }

                Surface(
                    color = EmeraldContainer,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, EmeraldPrimary)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.VerifiedUser,
                            contentDescription = null,
                            tint = EmeraldBright,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (hardwareInfo.isStrongBoxBacked) "STRONGBOX SE" else "TEE KEYSTORE",
                            color = EmeraldBright,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Surface(
                color = AnthraciteDeep,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(
                        text = "KRYPTO-MODUL: ${hardwareInfo.securityLevelLabel} (ECDSA secp256r1 / SHA-256)",
                        color = EmeraldBright,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Key Fingerprint: ${hardwareInfo.publicKeyFingerprint}",
                        color = TextSlateMuted,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onOpenVerifier,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AnthraciteCard,
                    contentColor = EmeraldBright
                ),
                border = BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.6f))
            ) {
                Icon(
                    imageVector = Icons.Default.QrCodeScanner,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "EINGEBAUTER ECHTHEITS-PRÜFER (QR-SIEGEL SCANNEN)",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun GuidedModeSelectionCard(
    mode: ProofMode,
    icon: ImageVector,
    accentColor: Color,
    badgeText: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = AnthraciteCard),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.45f))
    ) {
        Column(modifier = Modifier.padding(15.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    color = accentColor.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = badgeText,
                        color = accentColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                Text(
                    text = "Sensorkette + PDF ->",
                    color = accentColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(accentColor.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(25.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = mode.title,
                        color = TextOffWhite,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = mode.description,
                        color = TextSlateMuted,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun ProofRecordArchiveItemCard(
    record: ProofRecordEntity,
    onOpenDetail: () -> Unit,
    onSharePdf: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenDetail),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = AnthraciteSurface),
        border = BorderStroke(1.dp, AnthraciteBorder)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = EmeraldBright,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = record.modeTitle.uppercase(),
                        color = EmeraldBright,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = record.createdAtFormatted,
                    color = TextSlateMuted,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = record.title,
                color = TextOffWhite,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Erkannter Wert (OCR/Code): ${record.primaryOcrValue}",
                color = CyanTelemetry,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BadgeChip(
                    text = "3D-Score: ${record.parallaxConfidenceScore}%",
                    color = if (record.parallaxConfidenceScore >= 35) EmeraldBright else AmberWarning
                )
                BadgeChip(
                    text = if (record.isSensorChainContinuous) "Sensorkette lückenlos" else "Gap ${record.maxSensorGapMillis}ms",
                    color = if (record.isSensorChainContinuous) EmeraldBright else AmberWarning
                )
                BadgeChip(
                    text = record.hardwareSecurityLevel,
                    color = TextSlateMuted
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "SHA-256: ${record.payloadSha256Hex.take(20)}…${record.payloadSha256Hex.takeLast(12)}",
                color = TextSlateMuted,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onOpenDetail,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, AnthraciteBorder),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        tint = TextOffWhite,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("PDF & Zertifikat", color = TextOffWhite, fontSize = 12.sp)
                }

                Button(
                    onClick = onSharePdf,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EmeraldPrimary,
                        contentColor = Color(0xFF022C22)
                    ),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "WhatsApp / Teilen",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Löschen",
                        tint = TextSlateMuted
                    )
                }
            }
        }
    }
}

@Composable
private fun BadgeChip(text: String, color: Color) {
    Surface(
        color = AnthraciteDeep,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(0.8.dp, color.copy(alpha = 0.4f))
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun EmptyArchiveCard(searchQuery: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = AnthraciteSurface),
        border = BorderStroke(1.dp, AnthraciteBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = null,
                tint = TextSlateMuted,
                modifier = Modifier.size(36.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (searchQuery.isBlank()) {
                    "Noch keine versiegelten Beweis-Protokolle vorhanden"
                } else {
                    "Keine Protokolle für \"$searchQuery\" gefunden"
                },
                color = TextOffWhite,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Wähle oben einen der 3 geführten Beweis-Modi, um eine lückenlose Sensorkette aufzuzeichnen und ein Hardware-signiertes PDF zu generieren.",
                color = TextSlateMuted,
                fontSize = 12.sp
            )
        }
    }
}
