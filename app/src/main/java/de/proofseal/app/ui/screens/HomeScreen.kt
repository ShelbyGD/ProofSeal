package de.proofseal.app.ui.screens

import android.graphics.BitmapFactory
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddModerator
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.GppGood
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.proofseal.app.data.ProofRecordEntity
import de.proofseal.app.model.ProofMode
import de.proofseal.app.ui.theme.AppThemeMode
import de.proofseal.app.ui.viewmodel.HardwareKeystoreInfo
import de.proofseal.app.ui.viewmodel.ProofSealViewModel
import de.proofseal.app.util.ShareHelper
import kotlinx.coroutines.launch
import java.io.File

private enum class MainNavTab(
    val index: Int,
    val drawerTitle: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    CREATE(0, "Neues Beweis-Protokoll", Icons.Filled.Shield, Icons.Outlined.Shield),
    ARCHIVE(1, "Versiegelte Protokolle (Archiv)", Icons.Filled.FolderSpecial, Icons.Outlined.Folder),
    VERIFIER(2, "Echtheits-Prüfer (QR-Scanner)", Icons.Filled.QrCodeScanner, Icons.Outlined.QrCodeScanner),
    SYSTEM(3, "System, Design & Info", Icons.Filled.Tune, Icons.Outlined.Settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: ProofSealViewModel,
    onStartCaptureMode: (ProofMode) -> Unit,
    onOpenVerifier: () -> Unit,
    onOpenProtocolDetail: (String) -> Unit
) {
    val hardwareInfo by viewModel.hardwareInfo.collectAsState()
    val allRecords by viewModel.records.collectAsState()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()

    var selectedTabIndex by rememberSaveable { mutableIntStateOf(MainNavTab.CREATE.index) }
    var isStrongBoxSheetOpen by rememberSaveable { mutableStateOf(false) }

    // Zurück-Geste schließt zuerst den offenen Drawer oder springt von Unter-Bereichen zurück auf "Neues Beweis-Protokoll"
    BackHandler(enabled = drawerState.isOpen || selectedTabIndex != MainNavTab.CREATE.index) {
        if (drawerState.isOpen) {
            coroutineScope.launch { drawerState.close() }
        } else {
            selectedTabIndex = MainNavTab.CREATE.index
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                drawerShape = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp)
            ) {
                // 1. Edler Drawer-Header oben
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            modifier = Modifier.size(48.dp),
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Shield,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = "ProofSeal",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Digitaler Zeuge & Hardware-Beweis",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Surface(
                        onClick = {
                            coroutineScope.launch { drawerState.close() }
                            isStrongBoxSheetOpen = true
                        },
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.GppGood,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (hardwareInfo.isStrongBoxBacked) {
                                    "StrongBox SE aktiv"
                                } else {
                                    "StrongBox / TEE aktiv"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 2. Die 4 Hauptbereiche als NavigationDrawerItem mit Pill-Markierung
                MainNavTab.entries.forEach { tab ->
                    val isSelected = selectedTabIndex == tab.index
                    NavigationDrawerItem(
                        label = {
                            Text(
                                text = tab.drawerTitle,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        selected = isSelected,
                        onClick = {
                            selectedTabIndex = tab.index
                            coroutineScope.launch { drawerState.close() }
                        },
                        icon = {
                            Icon(
                                imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                contentDescription = tab.drawerTitle
                            )
                        },
                        badge = if (tab == MainNavTab.ARCHIVE) {
                            {
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerHighest
                                    }
                                ) {
                                    Text(
                                        text = allRecords.size.toString(),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) {
                                            MaterialTheme.colorScheme.onPrimary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        } else null,
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    navigationIcon = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 4.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    coroutineScope.launch {
                                        if (drawerState.isClosed) drawerState.open() else drawerState.close()
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Menu,
                                    contentDescription = "Navigationsmenü öffnen",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Spacer(modifier = Modifier.width(2.dp))
                            Surface(
                                modifier = Modifier.size(34.dp),
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Shield,
                                        contentDescription = "ProofSeal",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(9.dp))
                            Text(
                                text = "ProofSeal",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    },
                    title = {},
                    actions = {
                        // Rechts oben NUR der grüne, anklickbare StrongBox-Status-Chip
                        AssistChip(
                            onClick = { isStrongBoxSheetOpen = true },
                            label = {
                                Text(
                                    text = "StrongBox",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.GppGood,
                                    contentDescription = "Krypto-Hardware",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(17.dp)
                                )
                            },
                            shape = RoundedCornerShape(50),
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                                labelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            border = null,
                            modifier = Modifier.padding(end = 12.dp)
                        )
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { innerPadding ->
            AnimatedContent(
                targetState = selectedTabIndex,
                transitionSpec = {
                    fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(180))
                },
                label = "MainDrawerScreenTransition",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) { targetTab ->
                when (targetTab) {
                    MainNavTab.CREATE.index -> {
                        CreateTabContent(
                            hardwareInfo = hardwareInfo,
                            onStartCaptureMode = onStartCaptureMode,
                            onOpenHardwareSheet = { isStrongBoxSheetOpen = true }
                        )
                    }

                    MainNavTab.ARCHIVE.index -> {
                        ArchiveTabContent(
                            viewModel = viewModel,
                            onOpenProtocolDetail = onOpenProtocolDetail,
                            onSwitchToCreateTab = { selectedTabIndex = MainNavTab.CREATE.index }
                        )
                    }

                    MainNavTab.VERIFIER.index -> {
                        SealVerifierTabContent(
                            viewModel = viewModel
                        )
                    }

                    MainNavTab.SYSTEM.index -> {
                        SystemInfoTabContent(
                            viewModel = viewModel,
                            hardwareInfo = hardwareInfo,
                            onOpenHardwareSheet = { isStrongBoxSheetOpen = true }
                        )
                    }
                }
            }

            // ModalBottomSheet für detaillierte Krypto-Hardware-Daten (StrongBox / TEE)
            if (isStrongBoxSheetOpen) {
                ModalBottomSheet(
                    onDismissRequest = { isStrongBoxSheetOpen = false },
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
                ) {
                    StrongBoxCryptoDetailsBottomSheet(
                        hardwareInfo = hardwareInfo,
                        onDismiss = { isStrongBoxSheetOpen = false }
                    )
                }
            }
        }
    }
}

// ============================================================================
// TAB 1: "ERSTELLEN" (Willkommens-Header + 3 edle Material 3 Hero-Cards)
// ============================================================================
@Composable
private fun CreateTabContent(
    hardwareInfo: HardwareKeystoreInfo,
    onStartCaptureMode: (ProofMode) -> Unit,
    onOpenHardwareSheet: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Luftige Material 3 Willkommens-Header-Card
        item {
            Card(
                onClick = onOpenHardwareSheet,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.size(54.dp),
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.primary
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.VerifiedUser,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Bereit für neue Beweissicherung",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Hardware-Siegel aktiv (${hardwareInfo.securityLevelLabel}) • Lückenlose 3D-Sensorkette & On-Device OCR bereit.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item {
            Column(modifier = Modifier.padding(horizontal = 4.dp)) {
                Text(
                    text = "Geführte Beweis-Modi",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Wähle ein Szenario für dein signiertes 2-Seiten-Beweis-PDF",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 2. Hero-Card 1: Paket-Versand & Verkauf
        item {
            GuidedModeMaterial3HeroCard(
                mode = ProofMode.PACKAGE_SHIPPING,
                squircleIcon = Icons.Default.Inventory2,
                squircleContainerColor = MaterialTheme.colorScheme.primaryContainer,
                squircleIconTint = MaterialTheme.colorScheme.primary,
                chipLabel = "4 Keyframes",
                featureBadges = listOf(
                    Icons.Default.Sensors to "Sensorkette",
                    Icons.Default.DocumentScanner to "Barcode & OCR",
                    Icons.Default.PictureAsPdf to "Signiertes PDF"
                ),
                onClick = { onStartCaptureMode(ProofMode.PACKAGE_SHIPPING) }
            )
        }

        // 3. Hero-Card 2: Zählerstand, Kfz & Übergabe
        item {
            GuidedModeMaterial3HeroCard(
                mode = ProofMode.METER_HANDOVER,
                squircleIcon = Icons.Default.Speed,
                squircleContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                squircleIconTint = MaterialTheme.colorScheme.secondary,
                chipLabel = "3D-Parallaxe",
                featureBadges = listOf(
                    Icons.Default.Sensors to "Anti-Spoofing",
                    Icons.Default.DocumentScanner to "Live-OCR",
                    Icons.Default.PictureAsPdf to "Übergabe-Siegel"
                ),
                onClick = { onStartCaptureMode(ProofMode.METER_HANDOVER) }
            )
        }

        // 4. Hero-Card 3: Vorfall & Zustands-Beweis
        item {
            GuidedModeMaterial3HeroCard(
                mode = ProofMode.INCIDENT_FAST,
                squircleIcon = Icons.Default.ReportProblem,
                squircleContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                squircleIconTint = MaterialTheme.colorScheme.tertiary,
                chipLabel = "Schnell-Siegel",
                featureBadges = listOf(
                    Icons.Default.GpsFixed to "GPS-Ortsbeweis",
                    Icons.Default.VerifiedUser to "Zeugenfeld",
                    Icons.Default.PictureAsPdf to "Sofort-PDF"
                ),
                onClick = { onStartCaptureMode(ProofMode.INCIDENT_FAST) }
            )
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun GuidedModeMaterial3HeroCard(
    mode: ProofMode,
    squircleIcon: ImageVector,
    squircleContainerColor: Color,
    squircleIconTint: Color,
    chipLabel: String,
    featureBadges: List<Pair<ImageVector, String>>,
    onClick: () -> Unit
) {
    ElevatedCard(
        onClick = onClick,
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
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Obere Zeile: 52.dp Squircle-Icon links + SuggestionChip rechts
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Surface(
                    modifier = Modifier.size(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = squircleContainerColor
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = squircleIcon,
                            contentDescription = mode.title,
                            tint = squircleIconTint,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                SuggestionChip(
                    onClick = onClick,
                    label = {
                        Text(
                            text = chipLabel,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    shape = RoundedCornerShape(50),
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    border = null
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Mittlere Zeile: Großer Titel + 2-zeilige Beschreibung + runder Tonal-Chevron-Container rechts
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = mode.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = mode.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "Modus starten",
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Untere Zeile: Dezente Feature-Badges mit Mini-Icons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                featureBadges.forEach { (badgeIcon, badgeText) ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLowest
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = badgeIcon,
                                contentDescription = null,
                                tint = squircleIconTint,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = badgeText,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================
// TAB 2: "ARCHIV" (Pill-Search, Filter-Chips, Protokoll-Karten & Empty-State)
// ============================================================================
@Composable
private fun ArchiveTabContent(
    viewModel: ProofSealViewModel,
    onOpenProtocolDetail: (String) -> Unit,
    onSwitchToCreateTab: () -> Unit
) {
    val context = LocalContext.current
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedFilter by viewModel.selectedModeFilter.collectAsState()
    val records by viewModel.records.collectAsState()

    val filterOptions = listOf(
        null to "Alle",
        ProofMode.PACKAGE_SHIPPING.id to "Paket",
        ProofMode.METER_HANDOVER.id to "Zähler/Kfz",
        ProofMode.INCIDENT_FAST.id to "Vorfall"
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Pill-Shape Suchleiste (RoundedCornerShape(50))
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = viewModel::updateSearchQuery,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(50),
                placeholder = {
                    Text("Protokolle, Sendungsnr., OCR-Werte durchsuchen…")
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Suchen",
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Suche löschen"
                            )
                        }
                    }
                }
            )
        }

        // 2. Filter-Chips ("Alle", "Paket", "Zähler/Kfz", "Vorfall")
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                filterOptions.forEach { (modeId, label) ->
                    val selected = selectedFilter == modeId
                    FilterChip(
                        selected = selected,
                        onClick = { viewModel.updateModeFilter(modeId) },
                        label = { Text(label) },
                        leadingIcon = if (selected) {
                            {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        } else null,
                        shape = RoundedCornerShape(50)
                    )
                }
            }
        }

        // 3. Protokoll-Liste oder edler Empty-State
        if (records.isEmpty()) {
            item {
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            modifier = Modifier.size(64.dp),
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Outlined.Folder,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = if (searchQuery.isBlank() && selectedFilter == null) {
                                "Noch keine Protokolle im Archiv"
                            } else {
                                "Keine passenden Beweis-Protokolle gefunden"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Erstelle ein neues Beweis-Protokoll mit lückenloser Sensorkette und Hardware-Keystore-Signatur.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        FilledTonalButton(
                            onClick = onSwitchToCreateTab,
                            shape = RoundedCornerShape(50)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AddModerator,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Jetzt erstes Protokoll erstellen")
                        }
                    }
                }
            }
        } else {
            items(records, key = { it.protocolId }) { record ->
                ArchiveProtocolMaterial3Card(
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

@Composable
private fun ArchiveProtocolMaterial3Card(
    record: ProofRecordEntity,
    onOpenDetail: () -> Unit,
    onSharePdf: () -> Unit,
    onDelete: () -> Unit
) {
    val previewBitmap = remember(record.previewImagePath) {
        try {
            val file = File(record.previewImagePath)
            if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
        } catch (_: Exception) {
            null
        }
    }

    ElevatedCard(
        onClick = onOpenDetail,
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 3.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Vorschaubild oder Squircle-Fallback
                if (previewBitmap != null) {
                    Image(
                        bitmap = previewBitmap.asImageBitmap(),
                        contentDescription = record.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(16.dp))
                    )
                } else {
                    Surface(
                        modifier = Modifier.size(64.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = record.modeTitle,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = record.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = record.createdAtFormatted,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    modifier = Modifier.size(36.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "Details öffnen",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // OCR-Wert & Status-Pills
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "OCR / Code: ${record.primaryOcrValue}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "3D: ${record.parallaxConfidenceScore}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Direkt-Buttons für "PDF öffnen" und "PDF teilen"
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalButton(
                    onClick = onOpenDetail,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(50)
                ) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("PDF öffnen")
                }

                Button(
                    onClick = onSharePdf,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(50)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("PDF teilen", fontWeight = FontWeight.Bold)
                }

                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Protokoll löschen",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ============================================================================
// TAB 4: "SYSTEM & INFO" (Dynamic Color, Theme-Modus, Standard-Zeuge, Krypto-Info)
// ============================================================================
@Composable
private fun SystemInfoTabContent(
    viewModel: ProofSealViewModel,
    hardwareInfo: HardwareKeystoreInfo,
    onOpenHardwareSheet: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val uiPrefs by viewModel.uiPreferences.collectAsState()
    val supportsDynamic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Erscheinungsbild & Material You Dynamic Color
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            )
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Design & Material You (Material 3)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Dynamic Color (Systemfarben)",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (supportsDynamic) {
                                "Passt das Farbschema automatisch an dein Android-12+-Hintergrundbild an"
                            } else {
                                "Erfordert Android 12+ (nutzt ProofSeal Slate & Mint Palette)"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Switch(
                        checked = uiPrefs.useDynamicColor,
                        onCheckedChange = { viewModel.setUseDynamicColor(it) },
                        enabled = supportsDynamic
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Text(
                    text = "Farbmodus (Dark / Light Mode)",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val modes = listOf(
                        AppThemeMode.DARK to "Dunkel (Slate)",
                        AppThemeMode.LIGHT to "Hell",
                        AppThemeMode.SYSTEM to "System"
                    )
                    modes.forEach { (mode, label) ->
                        val selected = uiPrefs.themeMode == mode
                        FilterChip(
                            selected = selected,
                            onClick = { viewModel.setThemeMode(mode) },
                            label = { Text(label) },
                            leadingIcon = if (selected) {
                                {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            } else null,
                            shape = RoundedCornerShape(50)
                        )
                    }
                }
            }
        }

        // 2. Protokoll-Voreinstellungen (Standard-Zeugenname)
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
                Text(
                    text = "Standard-Voreinstellungen für PDFs",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Optionaler Standard-Zeugenname, der bei neuen Beweis-Protokollen automatisch vorbefüllt wird.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = uiPrefs.defaultWitnessName,
                    onValueChange = viewModel::setDefaultWitnessName,
                    label = { Text("Standard-Zeugenname (Optional)") },
                    placeholder = { Text("z. B. Max Mustermann / Versandabteilung") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // 3. Detaillierter Hardware-Keystore-Status
        ElevatedCard(
            onClick = onOpenHardwareSheet,
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.GppGood,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Krypto-Hardware & Schlüssel-Status",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Sicherheitslevel: ${hardwareInfo.securityLevelLabel}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Algorithmus: ECDSA (Kurve secp256r1) • Digest: SHA-256",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Fingerprint: ${hardwareInfo.publicKeyFingerprint}",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                FilledTonalButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(hardwareInfo.publicKeyFingerprint))
                        Toast.makeText(
                            context,
                            "Public Key Fingerprint in Zwischenablage kopiert",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Public Key Fingerprint kopieren")
                }
            }
        }

        // 4. Rechtlicher Hinweis zum technischen Anscheinsbeweis
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f)
            )
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Rechtlicher Hinweis & Technischer Anscheinsbeweis",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Text(
                    text = "Technischer Anscheinsbeweis gemäß kryptografischer Hardware-Signatur. Alle Aufnahmen werden mit monotoner Hardware-Uptime, 3D-Sensorkette und SHA256withECDSA-Signatur im Android Hardware-Keystore versiegelt. Jede nachträgliche Veränderung der Daten bricht die mathematische Signatur.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
        }
    }
}

// ============================================================================
// MODAL BOTTOM SHEET FÜR STRONGBOX / HARDWARE-KEYSTORE DETAILS
// ============================================================================
@Composable
private fun StrongBoxCryptoDetailsBottomSheet(
    hardwareInfo: HardwareKeystoreInfo,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.GppGood,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(
                    text = "Hardware-Keystore Sicherheitszertifikat",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = hardwareInfo.securityLevelLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CryptoSheetRow("Algorithmus & Kurve", "ECDSA (secp256r1 / NIST P-256)")
                CryptoSheetRow("Signatur-Verfahren", "SHA256withECDSA (AndroidKeyStore)")
                CryptoSheetRow(
                    "Hardware-Backing",
                    if (hardwareInfo.isStrongBoxBacked) {
                        "Echtes StrongBox Hardware-SE (Dedizierter Sicherheitschip)"
                    } else {
                        "Standard TEE (Trusted Execution Environment mit Auto-Fallback)"
                    }
                )
                CryptoSheetRow(
                    "Public Key Fingerprint (SHA-256)",
                    hardwareInfo.publicKeyFingerprint,
                    isMono = true
                )
            }
        }

        Text(
            text = "Der private ECDSA-Signaturschlüssel verlässt niemals den geschützten Hardware-Sicherheitsbereich deines Smartphones. Selbst bei Root-Zugriff kann der Schlüssel nicht extrahiert oder zum Nachsignieren manipulierter PDFs missbraucht werden.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FilledTonalButton(
                onClick = {
                    clipboardManager.setText(AnnotatedString(hardwareInfo.publicKeyFingerprint))
                    Toast.makeText(
                        context,
                        "Fingerprint kopiert!",
                        Toast.LENGTH_SHORT
                    ).show()
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(50)
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(17.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Fingerprint kopieren")
            }

            Button(
                onClick = onDismiss,
                modifier = Modifier.weight(0.8f),
                shape = RoundedCornerShape(50)
            ) {
                Text("Schließen")
            }
        }

        Spacer(modifier = Modifier.height(18.dp))
    }
}

@Composable
private fun CryptoSheetRow(
    label: String,
    value: String,
    isMono: Boolean = false
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            fontFamily = if (isMono) FontFamily.Monospace else FontFamily.Default
        )
    }
}
