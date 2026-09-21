package com.example.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import com.example.calendar.LunarCalendarHelper
import com.example.calendar.LunarDate
import com.example.data.BackgroundStyleOption
import com.example.data.BadgeSizeOption
import com.example.data.DisplayModeOption
import com.example.data.FloatingSettings
import com.example.data.TextFormatOption
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.RedPrimary
import com.example.util.InstalledAppInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsState()
    val hasPermission by viewModel.hasOverlayPermission.collectAsState()
    val hasUsageStatsPermission by viewModel.hasUsageStatsPermission.collectAsState()
    val selectedLunarDate by viewModel.selectedLunarDate.collectAsState()
    val installedApps by viewModel.installedApps.collectAsState()
    val isLoadingApps by viewModel.isLoadingApps.collectAsState()
    val liveForegroundPackage by viewModel.liveForegroundPackage.collectAsState()
    val isLiveHomeDetected by viewModel.isLiveHomeDetected.collectAsState()
    val updateState by viewModel.updateState.collectAsState()

    var showAppPickerDialog by remember { mutableStateOf(false) }

    // Re-check permission when resuming screen
    DisposableEffect(Unit) {
        viewModel.checkPermission()
        onDispose { }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(RedPrimary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.NightsStay,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Lịch Âm Nổi",
                                fontWeight = FontWeight.Bold,
                                fontSize = 19.sp,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "Hiển thị ngày Âm nổi trên màn hình",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // 1. Service Status & Main Toggle Card
            item {
                ServiceControlCard(
                    settings = settings,
                    hasPermission = hasPermission,
                    onToggleService = { viewModel.toggleService(context) },
                    onRequestPermission = {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        )
                        context.startActivity(intent)
                    }
                )
            }

            // 2. Smart Display Mode & Auto-Hide Rules Card
            item {
                SmartDisplayModeCard(
                    settings = settings,
                    hasUsageStatsPermission = hasUsageStatsPermission,
                    installedApps = installedApps,
                    liveForegroundPackage = liveForegroundPackage,
                    isLiveHomeDetected = isLiveHomeDetected,
                    onAddPackageToHome = { viewModel.addPackageToHome(it) },
                    onSelectDisplayMode = { viewModel.setDisplayMode(it) },
                    onCustomPackagesChanged = { viewModel.setCustomHomePackages(it) },
                    onOpenAppPicker = {
                        viewModel.loadInstalledApps()
                        showAppPickerDialog = true
                    },
                    onToggleAppPackage = { viewModel.toggleCustomHomePackage(it) },
                    onToggleSupportPip = { viewModel.setSupportPipMode(it) },
                    onPipPackagesChanged = { viewModel.setPipAllowedPackages(it) },
                    onRequestUsageAccess = {
                        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                        context.startActivity(intent)
                    }
                )
            }

            // 3. Live Wallpaper Preview Card
            item {
                LiveBadgePreviewCard(
                    settings = settings,
                    lunarDate = selectedLunarDate
                )
            }

            // 4. Customizer Controls
            item {
                BadgeCustomizerCard(
                    settings = settings,
                    onColorSelected = { viewModel.setTextColor(it) },
                    onFormatSelected = { viewModel.setTextFormat(it) },
                    onSizeSelected = { viewModel.setSizeOption(it) },
                    onFontSizeChanged = { viewModel.setFontSizeSp(it) },
                    onBgStyleSelected = { viewModel.setBgStyle(it) },
                    onLockToggle = { viewModel.setLockPosition(it) }
                )
            }

            // 5. Full Today Lunar Calendar Info
            item {
                DetailedLunarCard(
                    lunarDate = selectedLunarDate,
                    onPrevDay = { viewModel.navigateDate(-1) },
                    onNextDay = { viewModel.navigateDate(1) },
                    onResetToday = { viewModel.resetToToday() }
                )
            }

            // 6. Automatic App Update Card
            item {
                AppUpdateCard(
                    currentVersionName = viewModel.currentVersionName,
                    currentVersionCode = viewModel.currentVersionCode,
                    autoCheckUpdate = settings.autoCheckUpdate,
                    customUpdateUrl = settings.customUpdateUrl,
                    updateState = updateState,
                    onToggleAutoCheck = { viewModel.setAutoCheckUpdate(it) },
                    onCheckUpdateNow = { viewModel.checkForUpdate(manual = true) },
                    onCustomUrlChange = { viewModel.setCustomUpdateUrl(it) },
                    onSimulateUpdate = { viewModel.simulateUpdateForTesting() }
                )
            }
        }

        if (showAppPickerDialog) {
            LauncherAppPickerDialog(
                installedApps = installedApps,
                isLoading = isLoadingApps,
                customHomePackages = settings.customHomePackages,
                onToggleAppPackage = { viewModel.toggleCustomHomePackage(it) },
                onRefresh = { viewModel.loadInstalledApps() },
                onDismiss = { showAppPickerDialog = false }
            )
        }

        AppUpdateDialog(
            updateState = updateState,
            onDismiss = { viewModel.dismissUpdateDialog() },
            onDownloadAndInstall = { viewModel.downloadAndInstallUpdate(it) },
            onInstallDownloaded = { viewModel.installDownloadedApk() }
        )
    }
}

@Composable
fun ServiceControlCard(
    settings: FloatingSettings,
    hasPermission: Boolean,
    onToggleService: () -> Unit,
    onRequestPermission: () -> Unit
) {
    ElevatedCard(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 3.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("service_control_card")
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Permission Banner if missing
            if (!hasPermission) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0x33FF1744),
                    border = CardDefaults.outlinedCardBorder().copy(brush = Brush.horizontalGradient(listOf(RedPrimary, RedPrimary))),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = RedPrimary
                            )
                            Text(
                                text = "Yêu cầu quyền hiển thị đè màn hình",
                                fontWeight = FontWeight.Bold,
                                color = RedPrimary,
                                fontSize = 14.sp
                            )
                        }
                        Text(
                            text = "Để hiển thị widget ngày âm nổi trên các ứng dụng khác, ứng dụng cần được cấp quyền System Alert Window.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        )
                        Button(
                            onClick = onRequestPermission,
                            colors = ButtonDefaults.buttonColors(containerColor = RedPrimary),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("grant_permission_button")
                        ) {
                            Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Cấp Quyền Ngay", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Main Status Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(if (settings.isEnabled && hasPermission) Color(0xFF00E676) else Color.Gray)
                    )
                    Column {
                        Text(
                            text = if (settings.isEnabled && hasPermission) "Lịch Âm Nổi: ĐANG BẬT" else "Lịch Âm Nổi: ĐANG TẮT",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (settings.isEnabled) "Dịch vụ nổi đang chạy trên màn hình" else "Bật công tắc để hiển thị widget nổi 20x50px",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }

                Switch(
                    checked = settings.isEnabled && hasPermission,
                    onCheckedChange = { onToggleService() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = RedPrimary
                    ),
                    modifier = Modifier.testTag("toggle_floating_switch")
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.TouchApp,
                    contentDescription = null,
                    tint = RedPrimary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "Hướng dẫn: Chạm giữ & vuốt widget nổi trên màn hình để di chuyển đến vị trí mong muốn.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
fun LiveBadgePreviewCard(
    settings: FloatingSettings,
    lunarDate: LunarDate
) {
    val displayBadgeText = when (settings.textFormat) {
        TextFormatOption.SHORT -> lunarDate.toShortString(showAmLabel = false, padZero = false)
        TextFormatOption.PAD_ZERO -> lunarDate.toShortString(showAmLabel = false, padZero = true)
        TextFormatOption.SHORT_AM -> lunarDate.toShortString(showAmLabel = true, padZero = false)
        TextFormatOption.FULL_TEXT -> "Ngày ${lunarDate.day} Thg ${lunarDate.month}"
    }

    val badgeColor = try {
        Color(android.graphics.Color.parseColor(settings.textColorHex))
    } catch (e: Exception) {
        RedPrimary
    }

    ElevatedCard(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 3.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("badge_preview_card")
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Visibility,
                        contentDescription = null,
                        tint = RedPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Xem Trước Widget Nổi",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = RedPrimary.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "Kích thước: ${settings.sizeOption.displayName}",
                        fontSize = 11.sp,
                        color = RedPrimary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            // Mockup Screen Container
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF1F1C2C),
                                Color(0xFF928DAB),
                                Color(0xFF180B17)
                            )
                        )
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
            ) {
                // Mock phone status bar at top
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("09:41", color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.Wifi, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                        Icon(Icons.Default.BatteryFull, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                    }
                }

                // App icons on background mockup
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        repeat(4) { idx ->
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color.White.copy(alpha = 0.2f))
                            )
                        }
                    }
                }

                // Interactive Floating Badge Widget Centered/Draggable Simulation
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = when (settings.bgStyle) {
                        BackgroundStyleOption.TRANSLUCENT_DARK -> Color(0xE6121214)
                        BackgroundStyleOption.GLASS_BORDER -> Color(0xAA1A0F15)
                        BackgroundStyleOption.PURE_DARK -> Color.Black
                        BackgroundStyleOption.TEXT_ONLY -> Color.Transparent
                    },
                    border = if (settings.bgStyle == BackgroundStyleOption.GLASS_BORDER) {
                        BorderStroke(1.dp, RedPrimary.copy(alpha = 0.6f))
                    } else if (settings.bgStyle == BackgroundStyleOption.TRANSLUCENT_DARK) {
                        BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                    } else null,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .shadow(if (settings.bgStyle != BackgroundStyleOption.TEXT_ONLY) 6.dp else 0.dp, RoundedCornerShape(16.dp))
                        .testTag("preview_floating_badge")
                ) {
                    Row(
                        modifier = Modifier.padding(
                            horizontal = when (settings.sizeOption) {
                                BadgeSizeOption.MINI_20X50 -> 10.dp
                                BadgeSizeOption.MEDIUM_STANDARD -> 14.dp
                                BadgeSizeOption.LARGE_ACCESSIBLE -> 18.dp
                            },
                            vertical = when (settings.sizeOption) {
                                BadgeSizeOption.MINI_20X50 -> 4.dp
                                BadgeSizeOption.MEDIUM_STANDARD -> 6.dp
                                BadgeSizeOption.LARGE_ACCESSIBLE -> 8.dp
                            }
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = displayBadgeText,
                            color = badgeColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = settings.fontSizeSp.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BadgeCustomizerCard(
    settings: FloatingSettings,
    onColorSelected: (String) -> Unit,
    onFormatSelected: (TextFormatOption) -> Unit,
    onSizeSelected: (BadgeSizeOption) -> Unit,
    onFontSizeChanged: (Int) -> Unit,
    onBgStyleSelected: (BackgroundStyleOption) -> Unit,
    onLockToggle: (Boolean) -> Unit
) {
    val presetColors = listOf(
        "#FF1744" to "Đỏ Tươi",
        "#FFD700" to "Vàng Hoàng Gia",
        "#00E676" to "Xanh Lục",
        "#2979FF" to "Xanh Lam",
        "#D0BCFF" to "Tím Thủy Tinh",
        "#FFFFFF" to "Trắng Sáng",
        "#E040FB" to "Hồng Neon"
    )

    var showCustomColorPanel by remember { mutableStateOf(false) }

    // Parse current color to RGB
    val currentColor = try {
        android.graphics.Color.parseColor(settings.textColorHex)
    } catch (e: Exception) {
        android.graphics.Color.parseColor("#FF1744")
    }
    val currentR = android.graphics.Color.red(currentColor)
    val currentG = android.graphics.Color.green(currentColor)
    val currentB = android.graphics.Color.blue(currentColor)

    ElevatedCard(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 3.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("badge_customizer_card")
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Palette,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Tùy Chỉnh Giao Diện Cửa Sổ",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // 1. Color Picker
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "1. Màu chữ hiển thị (Bất kỳ):",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                // Color circles
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    presetColors.forEach { (hex, label) ->
                        val color = Color(android.graphics.Color.parseColor(hex))
                        val isSelected = settings.textColorHex.equals(hex, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) Color.White else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable { onColorSelected(hex) },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = label,
                                    tint = if (hex == "#FFFFFF") Color.Black else Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                // Custom Color expander
                Button(
                    onClick = { showCustomColorPanel = !showCustomColorPanel },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (showCustomColorPanel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = if (showCustomColorPanel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = if (showCustomColorPanel) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (showCustomColorPanel) "Đóng cài đặt màu sắc tự do" else "Tự phối màu bất kỳ (RGB)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                AnimatedVisibility(visible = showCustomColorPanel) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Xem trước màu sắc: ", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp, 20.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Color(currentColor))
                                            .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                                    )
                                    Text(settings.textColorHex, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                                }
                            }

                            // R Slider
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Đỏ (R): $currentR", fontSize = 11.sp)
                                }
                                Slider(
                                    value = currentR.toFloat(),
                                    onValueChange = { newVal ->
                                        val hex = String.format("#%02X%02X%02X", newVal.toInt(), currentG, currentB)
                                        onColorSelected(hex)
                                    },
                                    valueRange = 0f..255f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color.Red,
                                        activeTrackColor = Color.Red.copy(alpha = 0.5f)
                                    )
                                )
                            }

                            // G Slider
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Lục (G): $currentG", fontSize = 11.sp)
                                }
                                Slider(
                                    value = currentG.toFloat(),
                                    onValueChange = { newVal ->
                                        val hex = String.format("#%02X%02X%02X", currentR, newVal.toInt(), currentB)
                                        onColorSelected(hex)
                                    },
                                    valueRange = 0f..255f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color.Green,
                                        activeTrackColor = Color.Green.copy(alpha = 0.5f)
                                    )
                                )
                            }

                            // B Slider
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Lam (B): $currentB", fontSize = 11.sp)
                                }
                                Slider(
                                    value = currentB.toFloat(),
                                    onValueChange = { newVal ->
                                        val hex = String.format("#%02X%02X%02X", currentR, currentG, newVal.toInt())
                                        onColorSelected(hex)
                                    },
                                    valueRange = 0f..255f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color.Blue,
                                        activeTrackColor = Color.Blue.copy(alpha = 0.5f)
                                    )
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

            // 2. Format Selector
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "2. Định dạng ngày tháng:",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(TextFormatOption.entries) { option ->
                        FilterChip(
                            selected = settings.textFormat == option,
                            onClick = { onFormatSelected(option) },
                            label = { Text(option.displayName, fontSize = 12.sp) },
                            leadingIcon = if (settings.textFormat == option) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                            } else null,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

            // 3. Size Option & Slider
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "3. Kích thước Widget (Bất kỳ):",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Quick buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    BadgeSizeOption.entries.forEach { sizeOpt ->
                        val isSel = settings.sizeOption == sizeOpt && settings.fontSizeSp == sizeOpt.fontSp
                        OutlinedButton(
                            onClick = { onSizeSelected(sizeOpt) },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (isSel) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent,
                                contentColor = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            ),
                            border = BorderStroke(1.dp, if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(sizeOpt.displayName, fontSize = 11.sp, textAlign = TextAlign.Center, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }

                // Custom font size slider
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Kích thước chữ tùy ý: ${settings.fontSizeSp} SP",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        )
                    }

                    Slider(
                        value = settings.fontSizeSp.toFloat(),
                        onValueChange = { onFontSizeChanged(it.toInt()) },
                        valueRange = 8f..36f,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

            // 4. Background Style
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "4. Kiểu khung nền:",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(BackgroundStyleOption.entries) { bgOpt ->
                        FilterChip(
                            selected = settings.bgStyle == bgOpt,
                            onClick = { onBgStyleSelected(bgOpt) },
                            label = { Text(bgOpt.displayName, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

            // 5. Lock Position Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Cố định vị trí widget",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Khóa di chuyển chống trượt tay nhầm",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                Switch(
                    checked = settings.lockPosition,
                    onCheckedChange = { onLockToggle(it) }
                )
            }
        }
    }
}

@Composable
fun DetailedLunarCard(
    lunarDate: LunarDate,
    onPrevDay: () -> Unit,
    onNextDay: () -> Unit,
    onResetToday: () -> Unit
) {
    ElevatedCard(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 3.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("lunar_detail_card")
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CalendarMonth,
                        contentDescription = null,
                        tint = GoldAccent,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Thông Tin Âm Lịch Chi Tiết",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                TextButton(onClick = onResetToday) {
                    Icon(Icons.Default.Today, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Hôm nay", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Date Navigation Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onPrevDay) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Ngày trước")
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${lunarDate.day}/${lunarDate.month} ÂM LỊCH",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = RedPrimary
                    )
                    Text(
                        text = "Năm ${lunarDate.yearCanChi}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = GoldAccent
                    )
                }

                IconButton(onClick = onNextDay) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Ngày sau")
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

            // Can Chi Grid Details
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                InfoColumn(label = "Can Chi Ngày", value = lunarDate.dayCanChi)
                InfoColumn(label = "Can Chi Tháng", value = lunarDate.monthCanChi)
                InfoColumn(label = "Năm Âm", value = lunarDate.yearCanChi)
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.Star, contentDescription = null, tint = GoldAccent, modifier = Modifier.size(16.dp))
                        Text(
                            text = "Giờ Hoàng Đạo Trong Ngày:",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = lunarDate.zodiacHours,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

@Composable
fun InfoColumn(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun SmartDisplayModeCard(
    settings: FloatingSettings,
    hasUsageStatsPermission: Boolean,
    installedApps: List<InstalledAppInfo>,
    liveForegroundPackage: String?,
    isLiveHomeDetected: Boolean,
    onAddPackageToHome: (String) -> Unit,
    onSelectDisplayMode: (DisplayModeOption) -> Unit,
    onCustomPackagesChanged: (String) -> Unit,
    onOpenAppPicker: () -> Unit,
    onToggleAppPackage: (String) -> Unit,
    onToggleSupportPip: (Boolean) -> Unit,
    onPipPackagesChanged: (String) -> Unit,
    onRequestUsageAccess: () -> Unit
) {
    ElevatedCard(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 3.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("smart_display_mode_card")
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(GoldAccent.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        tint = GoldAccent,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column {
                    Text(
                        text = "Quy Tắc Ẩn / Hiện Widget",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Tối ưu hiển thị cho Màn hình ô tô & Đa nhiệm chia đôi PIP",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

            // Option 1: HOME OR NAVBAR COMBINED (Recommended for Car HU)
            val isHomeOrNavbar = settings.displayMode == DisplayModeOption.HOME_OR_NAVBAR
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (isHomeOrNavbar) RedPrimary.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                border = BorderStroke(1.5.dp, if (isHomeOrNavbar) RedPrimary else Color.Transparent),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectDisplayMode(DisplayModeOption.HOME_OR_NAVBAR) }
                    .testTag("mode_home_or_navbar")
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        RadioButton(
                            selected = isHomeOrNavbar,
                            onClick = { onSelectDisplayMode(DisplayModeOption.HOME_OR_NAVBAR) },
                            colors = RadioButtonDefaults.colors(selectedColor = RedPrimary)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "Màn Hình Ô Tô & Đa Nhiệm PIP",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = if (isHomeOrNavbar) RedPrimary else MaterialTheme.colorScheme.onSurface
                                )
                                Surface(
                                    color = Color(0xFF00C853),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "KHUYÊN DÙNG Ô TÔ",
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = "Luôn hiện ở Màn hình chính ô tô (kể cả khi chạy 2 ứng dụng chia đôi PIP YouTube + Bản đồ). Tự động ẩn khi vào ứng dụng khác hoặc xem toàn màn hình.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    }

                    if (isHomeOrNavbar) {
                        LauncherPickerInlineSection(
                            settings = settings,
                            installedApps = installedApps,
                            liveForegroundPackage = liveForegroundPackage,
                            isLiveHomeDetected = isLiveHomeDetected,
                            onAddPackageToHome = onAddPackageToHome,
                            onOpenAppPicker = onOpenAppPicker,
                            onToggleAppPackage = onToggleAppPackage,
                            onCustomPackagesChanged = onCustomPackagesChanged,
                            onToggleSupportPip = onToggleSupportPip,
                            onPipPackagesChanged = onPipPackagesChanged,
                            hasUsageStatsPermission = hasUsageStatsPermission,
                            onRequestUsageAccess = onRequestUsageAccess
                        )
                    }
                }
            }

            // Option 2: NAVBAR VISIBILITY
            val isNavBar = settings.displayMode == DisplayModeOption.NAVBAR_VISIBILITY
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (isNavBar) RedPrimary.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                border = BorderStroke(1.5.dp, if (isNavBar) RedPrimary else Color.Transparent),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectDisplayMode(DisplayModeOption.NAVBAR_VISIBILITY) }
                    .testTag("mode_navbar_visibility")
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        RadioButton(
                            selected = isNavBar,
                            onClick = { onSelectDisplayMode(DisplayModeOption.NAVBAR_VISIBILITY) },
                            colors = RadioButtonDefaults.colors(selectedColor = RedPrimary)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Theo Thanh Điều Hướng (Navbar)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = if (isNavBar) RedPrimary else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Hiện khi có thanh điều hướng xuất hiện. Tự động ẩn khi vào ứng dụng toàn màn hình không có thanh đáy.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }

            // Option 3: HOME ONLY
            val isHomeOnly = settings.displayMode == DisplayModeOption.HOME_ONLY
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (isHomeOnly) RedPrimary.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                border = BorderStroke(1.5.dp, if (isHomeOnly) RedPrimary else Color.Transparent),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectDisplayMode(DisplayModeOption.HOME_ONLY) }
                    .testTag("mode_home_only")
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        RadioButton(
                            selected = isHomeOnly,
                            onClick = { onSelectDisplayMode(DisplayModeOption.HOME_ONLY) },
                            colors = RadioButtonDefaults.colors(selectedColor = RedPrimary)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Chỉ hiện ở Màn hình chính (Home Launcher / PIP)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = if (isHomeOnly) RedPrimary else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Nhận diện theo Launcher & Dashboard PIP ô tô. Tự động ẩn khi mở ứng dụng khác (Bản đồ full màn hình, YouTube full, Settings...).",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    }

                    if (isHomeOnly) {
                        LauncherPickerInlineSection(
                            settings = settings,
                            installedApps = installedApps,
                            liveForegroundPackage = liveForegroundPackage,
                            isLiveHomeDetected = isLiveHomeDetected,
                            onAddPackageToHome = onAddPackageToHome,
                            onOpenAppPicker = onOpenAppPicker,
                            onToggleAppPackage = onToggleAppPackage,
                            onCustomPackagesChanged = onCustomPackagesChanged,
                            onToggleSupportPip = onToggleSupportPip,
                            onPipPackagesChanged = onPipPackagesChanged,
                            hasUsageStatsPermission = hasUsageStatsPermission,
                            onRequestUsageAccess = onRequestUsageAccess
                        )
                    }
                }
            }

            // Option 4: ALWAYS SHOW
            val isAlwaysShow = settings.displayMode == DisplayModeOption.ALWAYS_SHOW
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (isAlwaysShow) RedPrimary.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                border = BorderStroke(1.5.dp, if (isAlwaysShow) RedPrimary else Color.Transparent),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectDisplayMode(DisplayModeOption.ALWAYS_SHOW) }
                    .testTag("mode_always_show")
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RadioButton(
                        selected = isAlwaysShow,
                        onClick = { onSelectDisplayMode(DisplayModeOption.ALWAYS_SHOW) },
                        colors = RadioButtonDefaults.colors(selectedColor = RedPrimary)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Luôn hiển thị liên tục",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = if (isAlwaysShow) RedPrimary else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Widget luôn luôn nổi trên mọi ứng dụng và màn hình",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LauncherPickerInlineSection(
    settings: FloatingSettings,
    installedApps: List<InstalledAppInfo>,
    liveForegroundPackage: String?,
    isLiveHomeDetected: Boolean,
    onAddPackageToHome: (String) -> Unit,
    onOpenAppPicker: () -> Unit,
    onToggleAppPackage: (String) -> Unit,
    onCustomPackagesChanged: (String) -> Unit,
    onToggleSupportPip: (Boolean) -> Unit,
    onPipPackagesChanged: (String) -> Unit,
    hasUsageStatsPermission: Boolean,
    onRequestUsageAccess: () -> Unit
) {
    val selectedPackages = remember(settings.customHomePackages) {
        settings.customHomePackages
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
    ) {
        // 1. Live Diagnostic Card (Chẩn đoán trực tiếp gói màn hình chính)
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = if (isLiveHomeDetected) Color(0x1500C853) else Color(0x15FF9800),
            border = BorderStroke(1.dp, if (isLiveHomeDetected) Color(0xFF00C853) else Color(0xFFFF9800)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (isLiveHomeDetected) Color(0xFF00C853) else Color(0xFFFF9800))
                    )
                    Text(
                        text = if (isLiveHomeDetected) "Đang nhận diện: ĐANG Ở MÀN HÌNH CHÍNH" else "Đang nhận diện: ĐANG TRONG ỨNG DỤNG KHÁC",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = if (isLiveHomeDetected) Color(0xFF00C853) else Color(0xFFFF9800)
                    )
                }

                val currentAppLabel = installedApps.firstOrNull { it.packageName == liveForegroundPackage }?.appName
                Text(
                    text = "Gói đang mở: ${liveForegroundPackage ?: "Đang quét..."}${if (!currentAppLabel.isNullOrBlank()) " ($currentAppLabel)" else ""}",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                )

                // Quick 1-touch button to add detected app to Home list if it's not recognized as home yet
                if (!liveForegroundPackage.isNullOrEmpty() &&
                    !selectedPackages.contains(liveForegroundPackage) &&
                    !isLiveHomeDetected
                ) {
                    Button(
                        onClick = { onAddPackageToHome(liveForegroundPackage) },
                        colors = ButtonDefaults.buttonColors(containerColor = RedPrimary),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(vertical = 6.dp, horizontal = 12.dp),
                        modifier = Modifier.fillMaxWidth().testTag("add_current_as_home_btn")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Đặt '${currentAppLabel ?: liveForegroundPackage}' làm Màn hình chính",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // PIP Dashboard Mode Toggle
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = if (settings.supportPipMode) Color(0x2200C853) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            border = BorderStroke(1.dp, if (settings.supportPipMode) Color(0xFF00C853) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Hỗ trợ 2 App PIP trên Màn hình chính",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (settings.supportPipMode) Color(0xFF00C853) else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            text = "Giữ widget hiện khi ở màn hình chính đang chạy 2 ứng dụng chia đôi PIP (YouTube, Bản đồ...). Tự động ẩn khi vào ứng dụng toàn màn hình.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                        )
                    }
                    Switch(
                        checked = settings.supportPipMode,
                        onCheckedChange = { onToggleSupportPip(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF00C853)
                        )
                    )
                }
            }
        }

        if (!hasUsageStatsPermission) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0x33FF9800),
                border = BorderStroke(1.dp, Color(0xFFFF9800)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFFFF9800), modifier = Modifier.size(16.dp))
                        Text(
                            text = "Cần quyền 'Truy cập dữ liệu sử dụng'",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = Color(0xFFFF9800)
                        )
                    }
                    Text(
                        text = "Để ứng dụng nhận diện được gói Launcher chính của máy khi đang chạy.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                    )
                    Button(
                        onClick = onRequestUsageAccess,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().testTag("grant_usage_access_btn")
                    ) {
                        Text("Cấp Quyền Truy Cập Sử Dụng", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }

        // Action Button: Pick from installed apps
        Button(
            onClick = onOpenAppPicker,
            colors = ButtonDefaults.buttonColors(
                containerColor = RedPrimary
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("pick_launcher_app_btn"),
            contentPadding = PaddingValues(vertical = 10.dp, horizontal = 14.dp)
        ) {
            Icon(
                imageVector = Icons.Default.TouchApp,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (selectedPackages.isEmpty()) "Chọn Ứng Dụng Launcher Từ Máy" else "Chọn Launcher Từ Máy (${selectedPackages.size} đã chọn)",
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
        }

        // Selected package chips
        if (selectedPackages.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Gói Launcher Đã Chọn (${selectedPackages.size}):",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )

                selectedPackages.forEach { pkg ->
                    val matchingApp = installedApps.firstOrNull { it.packageName == pkg }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        border = BorderStroke(1.dp, RedPrimary.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AppIconImage(
                                drawable = matchingApp?.icon,
                                modifier = Modifier.size(24.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = matchingApp?.appName ?: pkg,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1
                                )
                                if (matchingApp != null) {
                                    Text(
                                        text = pkg,
                                        fontSize = 9.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                        maxLines = 1
                                    )
                                }
                            }
                            IconButton(
                                onClick = { onToggleAppPackage(pkg) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Bỏ chọn",
                                    tint = RedPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Manual text field backup (optional)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Hoặc nhập tên gói thủ công (phân cách dấu phẩy):",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            OutlinedTextField(
                value = settings.customHomePackages,
                onValueChange = { onCustomPackagesChanged(it) },
                placeholder = { Text("VD: com.carwebguru, com.syu.ms, com.ts.launcher...") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(fontSize = 11.sp)
            )
        }
    }
}

@Composable
fun AppIconImage(
    drawable: android.graphics.drawable.Drawable?,
    modifier: Modifier = Modifier
) {
    if (drawable != null) {
        val bitmap = remember(drawable) {
            try {
                drawable.toBitmap(width = 96, height = 96)
            } catch (e: Exception) {
                null
            }
        }
        if (bitmap != null) {
            androidx.compose.foundation.Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = modifier.clip(RoundedCornerShape(6.dp))
            )
            return
        }
    }
    Icon(
        imageVector = Icons.Default.Apps,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LauncherAppPickerDialog(
    installedApps: List<InstalledAppInfo>,
    isLoading: Boolean,
    customHomePackages: String,
    onToggleAppPackage: (String) -> Unit,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var filterMode by remember { mutableStateOf("ALL") } // ALL, LAUNCHER_ONLY, SELECTED_ONLY

    val selectedList = remember(customHomePackages) {
        customHomePackages.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    }

    val filteredApps = remember(installedApps, searchQuery, filterMode, selectedList) {
        installedApps.filter { app ->
            val matchesSearch = searchQuery.isBlank() ||
                    app.appName.contains(searchQuery, ignoreCase = true) ||
                    app.packageName.contains(searchQuery, ignoreCase = true)

            val matchesFilter = when (filterMode) {
                "LAUNCHER_ONLY" -> app.isHomeApp
                "SELECTED_ONLY" -> selectedList.contains(app.packageName)
                else -> true
            }

            matchesSearch && matchesFilter
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp)
            .testTag("launcher_app_picker_dialog")
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(RedPrimary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Apps,
                                contentDescription = null,
                                tint = RedPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Chọn Giao Diện Launcher",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${selectedList.size} ứng dụng được chọn",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                    IconButton(
                        onClick = onRefresh,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Làm mới danh sách",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Tìm kiếm tên app hoặc tên gói...", fontSize = 12.sp) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Xóa tìm kiếm", modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                // Quick Filter Chips
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item {
                        FilterChip(
                            selected = filterMode == "ALL",
                            onClick = { filterMode = "ALL" },
                            label = { Text("Tất cả (${installedApps.size})", fontSize = 11.sp) },
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                    item {
                        FilterChip(
                            selected = filterMode == "LAUNCHER_ONLY",
                            onClick = { filterMode = "LAUNCHER_ONLY" },
                            label = {
                                val launcherCount = installedApps.count { it.isHomeApp }
                                Text("Gợi ý Launcher ($launcherCount)", fontSize = 11.sp)
                            },
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                    item {
                        FilterChip(
                            selected = filterMode == "SELECTED_ONLY",
                            onClick = { filterMode = "SELECTED_ONLY" },
                            label = { Text("Đã chọn (${selectedList.size})", fontSize = 11.sp) },
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                // List of Apps
                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(32.dp), color = RedPrimary)
                            Text("Đang quét các ứng dụng trên thiết bị...", fontSize = 12.sp)
                        }
                    }
                } else if (filteredApps.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Không tìm thấy ứng dụng phù hợp",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(filteredApps, key = { it.packageName }) { app ->
                            val isSelected = selectedList.contains(app.packageName)
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) RedPrimary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                border = BorderStroke(1.dp, if (isSelected) RedPrimary else Color.Transparent),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onToggleAppPackage(app.packageName) }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    AppIconImage(
                                        drawable = app.icon,
                                        modifier = Modifier.size(34.dp)
                                    )

                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = app.appName,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                                color = if (isSelected) RedPrimary else MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1
                                            )
                                            if (app.isHomeApp) {
                                                Surface(
                                                    color = Color(0xFF00C853),
                                                    shape = RoundedCornerShape(4.dp)
                                                ) {
                                                    Text(
                                                        text = "LAUNCHER",
                                                        color = Color.White,
                                                        fontSize = 8.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                    )
                                                }
                                            }
                                        }
                                        Text(
                                            text = app.packageName,
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                            maxLines = 1
                                        )
                                    }

                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = { onToggleAppPackage(app.packageName) },
                                        colors = CheckboxDefaults.colors(
                                            checkedColor = RedPrimary,
                                            checkmarkColor = Color.White
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                // Bottom Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = RedPrimary),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("close_app_picker_dialog")
                    ) {
                        Text("Xong", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

