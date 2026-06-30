package com.example.ui

import android.Manifest
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview as CameraPreview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FlashlightOff
import androidx.compose.material.icons.outlined.FlashlightOn
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.NavigationLog
import com.example.ui.theme.*
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import androidx.compose.material3.ExperimentalMaterial3Api
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: NavigationViewModel,
    modifier: Modifier = Modifier
) {
    val cameraPermissionState = rememberPermissionState(permission = Manifest.permission.CAMERA)

    val isPrototypeMode by viewModel.isPrototypeMode.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier.fillMaxSize().background(SightBlack),
        topBar = {
            CenterAlignedTopAppBar(
                title = { 
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Visibility,
                            contentDescription = null,
                            tint = SightYellow,
                            modifier = Modifier.padding(end = 8.dp).size(26.dp)
                        )
                        Text(
                            text = "SIGHTLOOP",
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.5.sp,
                            color = SightYellow,
                            fontSize = 20.sp
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = SightSurface
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(SightBlack)
        ) {
            if (isPrototypeMode || cameraPermissionState.status.isGranted) {
                CameraActiveLayout(viewModel = viewModel)
            } else {
                CameraPermissionExplanationLayout(
                    onRequestPermission = { cameraPermissionState.launchPermissionRequest() }
                )
            }
        }
    }
}

@Composable
fun CameraPermissionExplanationLayout(onRequestPermission: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .semantics {
                paneTitle = "Camera Permission Screen"
            },
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .background(SightSurface, shape = CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.PhotoCamera,
                contentDescription = null,
                tint = SightYellow,
                modifier = Modifier.size(48.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "Camera Access Required",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = SightWhite,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Text(
            text = "SightLoop requires camera credentials to snap surroundings and generate real-time auditive scene feedback with tactile indicators.",
            fontSize = 16.sp,
            color = SightGray,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        
        Spacer(modifier = Modifier.height(36.dp))
        
        Button(
            onClick = onRequestPermission,
            colors = ButtonDefaults.buttonColors(containerColor = SightYellow, contentColor = SightBlack),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .testTag("request_permission_button")
                .semantics {
                    contentDescription = "Grant camera permission"
                }
        ) {
            Text(
                text = "GRANT PERMISSION",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                letterSpacing = 1.sp
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraActiveLayout(viewModel: NavigationViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val isPrototypeMode by viewModel.isPrototypeMode.collectAsStateWithLifecycle()
    val isProcessing by viewModel.isProcessing.collectAsStateWithLifecycle()
    val isContinuousMode by viewModel.isContinuousMode.collectAsStateWithLifecycle()
    val activeDescription by viewModel.activeDescription.collectAsStateWithLifecycle()
    val activeGuidance by viewModel.activeGuidance.collectAsStateWithLifecycle()
    val activeVibrationLevel by viewModel.activeVibrationLevel.collectAsStateWithLifecycle()
    val hazardsList by viewModel.hazardsList.collectAsStateWithLifecycle()
    val cameraStatus by viewModel.cameraStatus.collectAsStateWithLifecycle()
    val isFlashEnabled by viewModel.isFlashEnabled.collectAsStateWithLifecycle()
    val logsHistory by viewModel.navigationHistory.collectAsStateWithLifecycle(initialValue = emptyList())
    val triggerTime by viewModel.triggerContinuousCapture.collectAsStateWithLifecycle()
    val speechSpeed by viewModel.speechSpeed.collectAsStateWithLifecycle()

    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }

    // Reactively trigger capture in Continuous Scan Mode
    LaunchedEffect(triggerTime) {
        if (triggerTime > 0 && isContinuousMode) {
            viewModel.captureAndAnalyze(imageCapture)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .semantics {
                paneTitle = "Navigation Assistant Dashboard"
            },
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        // Section 1: Camera Viewport with overlay indicators
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .background(SightSurface)
            ) {
                AndroidView(
                    factory = { ctx ->
                        val previewView = PreviewView(ctx).apply {
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                        }
                        val executor = ContextCompat.getMainExecutor(ctx)
                        cameraProviderFuture.addListener({
                            val cameraProvider = cameraProviderFuture.get()
                            val preview = CameraPreview.Builder().build().also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }
                            
                            val builtImageCapture = ImageCapture.Builder()
                                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                                .build()
                            
                            imageCapture = builtImageCapture
                            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                            try {
                                cameraProvider.unbindAll()
                                cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    cameraSelector,
                                    preview,
                                    builtImageCapture
                                )
                            } catch (e: Exception) {
                                Log.e("CameraActiveLayout", "Camera binding failed", e)
                            }
                        }, executor)
                        previewView
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Dark contrast overlay for visual accessibility gradients
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, SightBlack.copy(alpha = 0.85f)),
                                startY = 120f
                            )
                        )
                )

                // Top Overlay Badges: Continuous modes & Audit status
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: Continuous status indicator
                    Row(
                        modifier = Modifier
                            .background(
                                if (isContinuousMode) SightYellow.copy(alpha = 0.9f) else SightBorder,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(
                                    if (isContinuousMode) SightBlack else SightGray,
                                    shape = CircleShape
                                )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isContinuousMode) "LOOP ACTIVE" else "LOOP INACTIVE",
                            color = if (isContinuousMode) SightBlack else SightWhite,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }

                    // Right: Flashlight toggle button
                    IconButton(
                        onClick = { viewModel.toggleCameraFlash() },
                        modifier = Modifier
                            .size(44.dp)
                            .background(SightSurface.copy(alpha = 0.8f), shape = CircleShape)
                            .border(BorderStroke(1.dp, SightYellow), CircleShape),
                    ) {
                        Icon(
                            imageVector = if (isFlashEnabled) Icons.Outlined.FlashlightOn else Icons.Outlined.FlashlightOff,
                            contentDescription = if (isFlashEnabled) "Disable Torchlight" else "Enable Torchlight",
                            tint = SightYellow
                        )
                    }
                }

                // Center Ring pulse during room scanning processing
                if (isProcessing) {
                    Box(
                        modifier = Modifier.align(Alignment.Center)
                    ) {
                        ScanningPulseAnimation()
                    }
                }

                // Bottom Status display
                Text(
                    text = cameraStatus.uppercase(),
                    color = if (isProcessing) SightYellow else SightGreen,
                    fontWeight = FontWeight.Black,
                    fontSize = 13.sp,
                    letterSpacing = 1.sp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 12.dp)
                        .background(SightBlack.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }

        // Section: Prototype Control Switch Card
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isPrototypeMode) SightYellow.copy(alpha = 0.08f) else SightSurface
                ),
                border = BorderStroke(
                    1.dp,
                    if (isPrototypeMode) SightYellow else SightBorder
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { viewModel.togglePrototypeMode() }
                    .testTag("toggle_prototype_card")
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(
                                    if (isPrototypeMode) SightYellow else SightBorder,
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isPrototypeMode) Icons.Default.Build else Icons.Default.PhotoCamera,
                                contentDescription = null,
                                tint = if (isPrototypeMode) SightBlack else SightGray,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = "FUNCTION PROTOTYPE MODE",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isPrototypeMode) SightYellow else SightWhite,
                                letterSpacing = 0.5.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (isPrototypeMode) 
                                    "Simulating high-contrast haptics & TTS descriptions without camera hardware dependencies." 
                                    else "Using real back camera & Gemini API credentials.",
                                fontSize = 11.sp,
                                color = SightGray,
                                lineHeight = 15.sp
                            )
                        }
                    }
                    Switch(
                        checked = isPrototypeMode,
                        onCheckedChange = { viewModel.togglePrototypeMode() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = SightBlack,
                            checkedTrackColor = SightYellow,
                            uncheckedThumbColor = SightGray,
                            uncheckedTrackColor = SightBorder
                        ),
                        modifier = Modifier.testTag("toggle_prototype_switch")
                    )
                }
            }
        }

        // Section 2: Massive Double-Tap Screen-size trigger panel
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .background(
                        if (isProcessing) SightBorder else SightYellow,
                        shape = RoundedCornerShape(16.dp)
                    )
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { viewModel.performImmediateScan(imageCapture) }
                    .testTag("tap_to_describe")
                    .padding(vertical = 32.dp, horizontal = 24.dp)
                    .semantics {
                        role = Role.Button
                        contentDescription = "Double tap anywhere on this yellow panel to analyze surroundings and listen to scene layout details."
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = if (isProcessing) Icons.Default.HourglassEmpty else Icons.Default.Radar,
                        contentDescription = null,
                        tint = if (isProcessing) SightYellow else SightBlack,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (isProcessing) "SCANNED AND ANALYZING..." else "TAP TO AUDIT ENVIRONMENT",
                        color = if (isProcessing) SightWhite else SightBlack,
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp,
                        textAlign = TextAlign.Center,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Vibrates and reads details aloud with custom voice instructions.",
                        color = if (isProcessing) SightGray else SightBlack.copy(alpha = 0.7f),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // Section 3: Surrounds description log
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SightSurface),
                border = BorderStroke(1.dp, SightBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .semantics(mergeDescendants = true) {
                        contentDescription = "Scene description: $activeDescription"
                    }
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "ENVIRONMENT AUDIT",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = SightYellow,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = activeDescription,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = SightWhite,
                        lineHeight = 24.sp
                    )
                }
            }
        }

        // Section 4: Safe Navigation Guidelines direction indicator
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = when (activeVibrationLevel.uppercase()) {
                        "HIGH" -> SightRed.copy(alpha = 0.15f)
                        "MEDIUM" -> SightYellow.copy(alpha = 0.1f)
                        else -> SightSurface
                    }
                ),
                border = BorderStroke(
                    1.dp, 
                    when (activeVibrationLevel.uppercase()) {
                        "HIGH" -> SightRed
                        "MEDIUM" -> SightYellow
                        else -> SightBorder
                    }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .semantics(mergeDescendants = true) {
                        contentDescription = "Tactile Guidance direction alert: $activeGuidance"
                    }
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(
                                color = when (activeVibrationLevel.uppercase()) {
                                    "HIGH" -> SightRed
                                    "MEDIUM" -> SightYellow
                                    else -> SightGreen
                                },
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (activeVibrationLevel.uppercase()) {
                                "HIGH" -> Icons.Default.PlayForWork
                                "MEDIUM" -> Icons.Default.Warning
                                else -> Icons.Default.ArrowUpward
                            },
                            contentDescription = null,
                            tint = if (activeVibrationLevel.uppercase() == "HIGH" || activeVibrationLevel.uppercase() == "NONE") SightWhite else SightBlack,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = when (activeVibrationLevel.uppercase()) {
                                "HIGH" -> "TACTILE ALERT: HIGH RISK"
                                "MEDIUM" -> "TACTILE ALERT: MODERATE"
                                else -> "ROUTE STABILIZED"
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            color = when (activeVibrationLevel.uppercase()) {
                                "HIGH" -> SightRed
                                "MEDIUM" -> SightYellow
                                else -> SightGreen
                            },
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = activeGuidance,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = SightWhite,
                            lineHeight = 22.sp
                        )
                    }
                }
            }
        }

        // Section 5: Identified Tactics & Speed Controllers
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    text = "ASSISTANT ADJUSTMENTS",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = SightYellow,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // Row: Continuous Switch & Speed Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Continuous mode layout
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SightSurface),
                        border = BorderStroke(1.dp, SightBorder),
                        modifier = Modifier
                            .weight(1f)
                            .height(130.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { viewModel.toggleContinuousScan() }
                            .testTag("toggle_continuous")
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.SpaceBetween,
                            horizontalAlignment = Alignment.Start
                        ) {
                            Icon(
                                imageVector = if (isContinuousMode) Icons.Default.Sync else Icons.Default.SyncDisabled,
                                contentDescription = null,
                                tint = if (isContinuousMode) SightYellow else SightGray,
                                modifier = Modifier.size(28.dp)
                            )
                            Column {
                                Text(
                                    text = "Continuous Scan",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = SightWhite
                                )
                                Text(
                                    text = if (isContinuousMode) "Every 7 seconds" else "Manual snaps only",
                                    fontSize = 11.sp,
                                    color = SightGray
                                )
                            }
                        }
                    }

                    // Speed controls cycle
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SightSurface),
                        border = BorderStroke(1.dp, SightBorder),
                        modifier = Modifier
                            .weight(1f)
                            .height(130.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                val nextSpeed = when (speechSpeed) {
                                    0.8f -> 1.0f
                                    1.0f -> 1.4f
                                    1.4f -> 1.8f
                                    else -> 0.8f
                                }
                                viewModel.setSpeechRate(nextSpeed)
                            }
                            .testTag("toggle_speed")
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.SpaceBetween,
                            horizontalAlignment = Alignment.Start
                        ) {
                            Icon(
                                imageVector = Icons.Default.VolumeUp,
                                contentDescription = null,
                                tint = SightYellow,
                                modifier = Modifier.size(28.dp)
                            )
                            Column {
                                Text(
                                    text = "Speech Speed",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = SightWhite
                                )
                                Text(
                                    text = when (speechSpeed) {
                                        0.8f -> "0.8x (Slow)"
                                        1.0f -> "1.0x (Normal)"
                                        1.4f -> "1.4x (Fast)"
                                        else -> "1.8x (Very Fast)"
                                    },
                                    fontSize = 11.sp,
                                    color = SightYellow,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        // Section 6: Specific Hazards detailing List (if any hazards are found)
        if (hazardsList.isNotEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "TACTILE HAZARD BREAKDOWN",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = SightRed,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    hazardsList.forEach { hazard ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = SightSurface),
                            border = BorderStroke(1.dp, if (hazard.severity == "HIGH") SightRed else SightBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .background(
                                                color = when (hazard.severity.uppercase()) {
                                                    "HIGH" -> SightRed
                                                    "MEDIUM" -> SightYellow
                                                    else -> SightGreen
                                                },
                                                shape = CircleShape
                                            )
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = hazard.name.uppercase(),
                                        fontWeight = FontWeight.Bold,
                                        color = SightWhite,
                                        fontSize = 13.sp
                                    )
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    // Severity Badge
                                    Text(
                                        text = hazard.severity,
                                        color = when (hazard.severity.uppercase()) {
                                            "HIGH" -> SightRed
                                            "MEDIUM" -> SightYellow
                                            else -> SightGreen
                                        },
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Black,
                                        modifier = Modifier
                                            .background(SightBlack, RoundedCornerShape(4.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                    // Distance info
                                    Text(
                                        text = hazard.distance.uppercase(),
                                        color = SightGray,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier
                                            .background(SightBorder, RoundedCornerShape(4.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Section 7: Travel History log records list (SQLite history persistent state)
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.History,
                        contentDescription = null,
                        tint = SightYellow,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "NAVIGATION LOGS",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = SightYellow,
                        letterSpacing = 1.sp
                    )
                }

                if (logsHistory.isNotEmpty()) {
                    IconButton(
                        onClick = { viewModel.clearLogHistory() },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = "Clear logs history",
                            tint = SightRed,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        if (logsHistory.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SightSurface),
                    border = BorderStroke(1.dp, SightBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "History logs empty. Your travel layout audits are recorded here securely.",
                        color = SightGray,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp)
                    )
                }
            }
        } else {
            items(logsHistory) { log ->
                HistoryItemRow(log = log)
            }
        }
    }
}

@Composable
fun HistoryItemRow(log: NavigationLog) {
    val sdf = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
    val formattedTime = remember(log.timestamp) { sdf.format(Date(log.timestamp)) }

    Card(
        colors = CardDefaults.cardColors(containerColor = SightSurface),
        border = BorderStroke(1.dp, SightBorder),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = "Travel Log at $formattedTime, ${log.description}. Guidance: ${log.directions}"
            }
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formattedTime,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = SightYellow
                )

                // Danger severity indicator
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                color = when (log.dangerLevel.uppercase()) {
                                    "HIGH" -> SightRed
                                    "MEDIUM" -> SightYellow
                                    else -> SightGreen
                                },
                                shape = CircleShape
                            )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = when (log.dangerLevel.uppercase()) {
                            "HIGH" -> "DANGER HIGH"
                            "MEDIUM" -> "DANGER MODERATE"
                            else -> "CLEAR ROUTE"
                        },
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        color = when (log.dangerLevel.uppercase()) {
                            "HIGH" -> SightRed
                            "MEDIUM" -> SightYellow
                            else -> SightGreen
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = log.description,
                fontSize = 14.sp,
                color = SightWhite,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Guideline: " + log.directions,
                fontSize = 13.sp,
                color = SightGray,
                fontWeight = FontWeight.Medium,
                lineHeight = 17.sp
            )
        }
    }
}

@Composable
fun ScanningPulseAnimation() {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 0.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Box(
        modifier = Modifier
            .size(110.dp)
            .background(SightYellow.copy(alpha = alpha), CircleShape)
            .padding(12.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(SightYellow.copy(alpha = 0.2f), CircleShape)
                .border(BorderStroke(3.dp, SightYellow), CircleShape)
        )
    }
}
