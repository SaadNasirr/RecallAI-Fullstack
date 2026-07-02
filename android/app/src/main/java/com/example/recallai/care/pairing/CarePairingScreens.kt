package com.example.recallai.care.pairing

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PendingActions
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Dialpad
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import com.example.recallai.R
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.recallai.data.remote.CarePermissionsDto
import com.example.recallai.data.remote.resolveUser
import com.example.recallai.ui.components.AppBackdrop
import com.example.recallai.ui.components.GlassCard
import com.example.recallai.ui.components.PrimaryActionButton
import com.example.recallai.ui.components.RecallTopBar
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.roundToInt

private fun looksLikeCareInvitePayload(raw: String): Boolean {
    val s = raw.trim()
    if (s.length < 20) return false
    if (s.startsWith("{") && (s.contains("\"t\"") || s.contains("\"token\""))) return true
    return s.none { it.isWhitespace() } && s.length in 32..256
}

/**
 * Renders a QR bitmap at [pixelSize]×[pixelSize] (not dp). Use at least ~512px so the code stays
 * sharp when shown at ~280dp on high-density phones; a tiny bitmap upscaled on screen is often
 * unscannable by another device's camera.
 */
private fun encodeQrBitmap(content: String, pixelSize: Int): Bitmap? {
    if (content.isBlank() || pixelSize < 64) return null
    return try {
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 2
        )
        val bits = QRCodeWriter().encode(
            content,
            BarcodeFormat.QR_CODE,
            pixelSize,
            pixelSize,
            hints
        )
        val bmp = Bitmap.createBitmap(pixelSize, pixelSize, Bitmap.Config.ARGB_8888)
        for (x in 0 until pixelSize) {
            for (y in 0 until pixelSize) {
                bmp.setPixel(x, y, if (bits[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        bmp
    } catch (_: Exception) {
        null
    }
}

@Composable
fun PatientConnectCaregiverScreen(
    onBack: () -> Unit,
    onShowQr: () -> Unit,
    onPending: () -> Unit,
    onLinked: () -> Unit,
    onBackupCode: () -> Unit,
    viewModel: PatientCarePairingViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val connectBanner by viewModel.careConnectionBanner.collectAsState()
    LaunchedEffect(Unit) { viewModel.refreshAll() }
    var pendingUnlinkRelId by remember { mutableStateOf<String?>(null) }

    Scaffold(
        containerColor = CarePairingTheme.PageBackground,
        topBar = { RecallTopBar(title = "Link caregiver", onBack = onBack) }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            ShareAccessHeroCard(
                title = "Share Access",
                subtitle = "Show your QR code so your caregiver can connect securely to your care circle.",
                buttonLabel = "Show QR",
                onPrimaryClick = {
                    viewModel.refreshQr()
                    onShowQr()
                }
            )

            val pendingN = state.pending.size
            val linkedN = state.linked.size

            PairingTintNavigationTile(
                title = "Backup",
                subtitle = "Share a short verbal backup code when QR isn't practical.",
                icon = Icons.Outlined.CloudUpload,
                surfaceColor = CarePairingTheme.MintSurface,
                inkColor = CarePairingTheme.MintInk,
                onClick = onBackupCode
            )
            PairingTintNavigationTile(
                title = "Pending",
                subtitle = when (pendingN) {
                    0 -> "No caregiver requests waiting"
                    1 -> "1 request awaiting approval"
                    else -> "$pendingN requests awaiting approval"
                },
                icon = Icons.Filled.PendingActions,
                surfaceColor = CarePairingTheme.RoseSurface,
                inkColor = CarePairingTheme.RoseInk,
                onClick = onPending
            )
            PairingTintNavigationTile(
                title = "Linked",
                subtitle = when (linkedN) {
                    0 -> "No caregivers linked yet"
                    1 -> "1 active caregiver"
                    else -> "$linkedN linked caregivers"
                },
                icon = Icons.Filled.People,
                surfaceColor = CarePairingTheme.LavenderSurface,
                inkColor = CarePairingTheme.LavenderInk,
                onClick = onLinked
            )

            if (state.linked.isNotEmpty()) {
                PairingInlineLinkedSection(
                    title = "Your care circle",
                    subtitle = "These caregivers can view memories, tasks, and alerts you allow.",
                    entries = state.linked.map { row ->
                        PairingLinkedEntry(
                            id = row._id,
                            name = row.caregiverId.resolveUser()?.name ?: "Caregiver"
                        )
                    },
                    onRemove = { pendingUnlinkRelId = it }
                )
            }

            if (state.pending.isNotEmpty()) {
                PairingScreenIntro(
                    title = "Needs your approval",
                    subtitle = "Review these requests here or open Pending for the full list.",
                    badge = state.pending.size.toString()
                )
                state.pending.take(2).forEach { row ->
                    val name = row.caregiverId.resolveUser()?.name ?: "Caregiver"
                    PairingPendingCaregiverCard(
                        name = name,
                        onApprove = { viewModel.approve(row._id) },
                        onReject = { viewModel.reject(row._id) },
                        busy = state.busy
                    )
                }
                if (state.pending.size > 2) {
                    TextButton(
                        onClick = onPending,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("View all ${state.pending.size} requests")
                    }
                }
            }

            if (!state.message.isNullOrBlank()) {
                PairingFeedbackBanner(message = state.message ?: "")
            }

            connectBanner?.let { msg ->
                PairingCelebrationDialog(
                    title = "You're connected",
                    message = msg,
                    onDismiss = { viewModel.clearCareConnectionBanner() }
                )
            }

            pendingUnlinkRelId?.let { relId ->
                val who = state.linked.find { it._id == relId }?.caregiverId.resolveUser()?.name ?: "this caregiver"
                PairingConfirmDialog(
                    title = "Remove caregiver?",
                    message = "$who will lose access to your care circle, tasks, and alerts for this link.",
                    confirmLabel = "Remove",
                    onConfirm = {
                        pendingUnlinkRelId = null
                        viewModel.removeLinkedCaregiver(relId)
                    },
                    onDismiss = { pendingUnlinkRelId = null }
                )
            }

            CollaborativeCareBanner(
                illustrationRes = R.drawable.ill_patient_caregiver,
                title = "Collaborative Care",
                subtitle = "Stay connected with those who support your wellbeing."
            )
        }
    }
}

@Composable
fun PatientQrDisplayScreen(
    onBack: () -> Unit,
    viewModel: PatientCarePairingViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) {
        if (state.qrPayload == null) viewModel.refreshQr()
    }
    val density = LocalDensity.current
    val qrPixelSize = remember(density.density) {
        (280f * density.density).roundToInt().coerceIn(512, 1024)
    }
    val bmp = remember(state.qrPayload?.token, qrPixelSize) {
        state.qrPayload?.token?.let { token ->
            // Raw token only (smaller symbol than JSON). CareQrCodec.decodeInviteToken accepts plain strings.
            encodeQrBitmap(token.trim(), qrPixelSize)
        }
    }
    Scaffold(
        containerColor = CarePairingTheme.PageBackground,
        topBar = { RecallTopBar(title = "Your QR code", onBack = onBack) }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            PairingScreenIntro(
                title = "Show this code",
                subtitle = "Ask your caregiver to scan with Add patient in their Recall app. Keep the screen bright and hold steady."
            )
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    bmp?.let {
                        Image(
                            bitmap = it.asImageBitmap(),
                            contentDescription = "Patient invite QR code",
                            modifier = Modifier.size(260.dp)
                        )
                    } ?: Text(
                        "Generating QR…",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Valid for a limited time — tap back and Show QR again if it expires.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}

@Composable
fun PatientPendingApprovalScreen(
    onBack: () -> Unit,
    viewModel: PatientCarePairingViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val connectBanner by viewModel.careConnectionBanner.collectAsState()
    LaunchedEffect(Unit) { viewModel.refreshAll() }
    Scaffold(
        containerColor = CarePairingTheme.PageBackground,
        topBar = { RecallTopBar(title = "Pending requests", onBack = onBack) }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            PairingScreenIntro(
                title = "Approve caregivers",
                subtitle = "Only approve people you trust. They'll see what you allow in permissions.",
                badge = state.pending.size.takeIf { it > 0 }?.toString()
            )
            if (state.pending.isEmpty()) {
                PairingEmptyState(
                    icon = Icons.Filled.PendingActions,
                    title = "No pending requests",
                    body = "When a caregiver scans your QR or enters your backup code, their request will appear here.",
                    surfaceColor = CarePairingTheme.RoseSurface,
                    inkColor = CarePairingTheme.RoseInk
                )
            } else {
                state.pending.forEach { row ->
                    val name = row.caregiverId.resolveUser()?.name ?: "Caregiver"
                    PairingPendingCaregiverCard(
                        name = name,
                        onApprove = { viewModel.approve(row._id) },
                        onReject = { viewModel.reject(row._id) },
                        busy = state.busy
                    )
                }
            }
            if (!state.message.isNullOrBlank()) {
                PairingFeedbackBanner(message = state.message ?: "")
            }
            connectBanner?.let { msg ->
                PairingCelebrationDialog(
                    title = "You're connected",
                    message = msg,
                    onDismiss = { viewModel.clearCareConnectionBanner() }
                )
            }
        }
    }
}

@Composable
fun PatientLinkedCaregiversScreen(
    onBack: () -> Unit,
    onOpenPermissions: (String) -> Unit,
    viewModel: PatientCarePairingViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.refreshAll() }
    Scaffold(
        containerColor = CarePairingTheme.PageBackground,
        topBar = { RecallTopBar(title = "Linked caregivers", onBack = onBack) }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            PairingScreenIntro(
                title = "Your caregivers",
                subtitle = "Manage what each person can see and do in your care circle.",
                badge = state.linked.size.takeIf { it > 0 }?.toString()
            )
            if (state.linked.isEmpty()) {
                PairingEmptyState(
                    icon = Icons.Filled.People,
                    title = "No caregivers linked",
                    body = "Share your QR from Link caregiver so family or clinicians can connect with you.",
                    surfaceColor = CarePairingTheme.LavenderSurface,
                    inkColor = CarePairingTheme.LavenderInk
                )
            } else {
                state.linked.forEach { row ->
                    val cg = row.caregiverId.resolveUser()?.name ?: "Caregiver"
                    PairingLinkedCaregiverCard(
                        name = cg,
                        onPermissions = { onOpenPermissions(row._id) }
                    )
                }
            }
            if (!state.message.isNullOrBlank()) {
                PairingFeedbackBanner(message = state.message ?: "")
            }
        }
    }
}

@Composable
fun PatientCaregiverPermissionsScreen(
    relationshipId: String,
    onBack: () -> Unit,
    viewModel: PatientCarePairingViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.refreshAll() }
    val rel = state.linked.find { it._id == relationshipId }
    var p by remember(rel) {
        mutableStateOf(
            rel?.permissions ?: CarePermissionsDto(
                viewMemories = true,
                manageReminders = true,
                receiveAlerts = true,
                viewLocation = true,
                emergencyAccess = true
            )
        )
    }
    val caregiverName = rel?.caregiverId.resolveUser()?.name ?: "Caregiver"
    Scaffold(
        containerColor = CarePairingTheme.PageBackground,
        topBar = { RecallTopBar(title = "Access control", onBack = onBack) }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            PairingScreenIntro(
                title = caregiverName,
                subtitle = "Choose what this caregiver can see and manage in your care circle."
            )
            PermissionToggle("Memories", p.viewMemories) {
                p = p.copy(viewMemories = it)
            }
            PermissionToggle("Reminders", p.manageReminders) {
                p = p.copy(manageReminders = it)
            }
            PermissionToggle("Alerts", p.receiveAlerts) {
                p = p.copy(receiveAlerts = it)
            }
            PermissionToggle("Location", p.viewLocation) {
                p = p.copy(viewLocation = it)
            }
            PermissionToggle("Emergency", p.emergencyAccess) {
                p = p.copy(emergencyAccess = it)
            }
            PrimaryActionButton(
                text = "Save permissions",
                onClick = { viewModel.savePermissions(relationshipId, p) },
                modifier = Modifier.fillMaxWidth()
            )
            if (!state.message.isNullOrBlank()) {
                PairingFeedbackBanner(message = state.message ?: "")
            }
        }
    }
}

@Composable
private fun PermissionToggle(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    GlassCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, fontSize = 20.sp, fontWeight = FontWeight.Medium)
            Switch(checked = checked, onCheckedChange = onChecked)
        }
    }
}

@Composable
fun PatientBackupCodeScreen(
    onBack: () -> Unit,
    viewModel: PatientCarePairingViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.refreshBackupCode() }
    Scaffold(
        containerColor = CarePairingTheme.PageBackground,
        topBar = { RecallTopBar(title = "Backup code", onBack = onBack) }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            PairingScreenIntro(
                title = "Verbal invite",
                subtitle = "Read this code to your caregiver when they can't scan your QR."
            )
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp, horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val code = state.backupInvite?.shortCode ?: "…"
                    Text(
                        code,
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Bold,
                        color = CarePairingTheme.MintInk,
                        letterSpacing = 4.sp
                    )
                    Text(
                        "Letters are not case-sensitive",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            PrimaryActionButton(
                text = "Generate new code",
                onClick = { viewModel.refreshBackupCode() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.busy
            )
            if (!state.message.isNullOrBlank()) {
                PairingFeedbackBanner(message = state.message ?: "")
            }
        }
    }
}

@Composable
fun CaregiverAddPatientScreen(
    onBack: () -> Unit,
    onScan: () -> Unit,
    onCode: () -> Unit,
    onPatients: () -> Unit,
    viewModel: CaregiverCarePairingViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val connectBanner by viewModel.careConnectionBanner.collectAsState()
    var pendingUnlinkRelId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { viewModel.refreshPatients() }
    Scaffold(
        containerColor = CarePairingTheme.PageBackground,
        topBar = { RecallTopBar(title = "Add patient", onBack = onBack) }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CaregiverAddPatientHeroHeader(
                illustrationRes = R.drawable.img_tool_caregiver,
                headline = "Scan to connect",
                body = "Connect with your patient instantly by scanning their Recall QR or entering their invite code."
            )
            if (state.patients.isNotEmpty()) {
                PairingInlineLinkedSection(
                    title = "Linked patients",
                    subtitle = "You have ${state.patients.size} patient(s) on your watchlist. Remove a link or add another with QR/code.",
                    entries = state.patients.map { row ->
                        PairingLinkedEntry(
                            id = row._id,
                            name = row.patientId.resolveUser()?.name ?: "Patient"
                        )
                    },
                    onRemove = { pendingUnlinkRelId = it }
                )
            }
            CaregiverScanHeroCard(onClick = onScan)
            CaregiverSecondaryRouteCard(
                title = "Enter Code",
                subtitle = "Type a unique patient invite code manually",
                icon = Icons.Outlined.Dialpad,
                surfaceColor = CarePairingTheme.MintSurface,
                inkColor = CarePairingTheme.MintInk,
                onClick = onCode
            )
            CaregiverSecondaryRouteCard(
                title = "My Patients",
                subtitle = "View profiles you've already linked",
                icon = Icons.Filled.People,
                surfaceColor = CarePairingTheme.LavenderSurface,
                inkColor = CarePairingTheme.LavenderInk,
                onClick = onPatients
            )
            if (!state.message.isNullOrBlank()) {
                PairingFeedbackBanner(message = state.message ?: "")
            }
            connectBanner?.let { msg ->
                PairingCelebrationDialog(
                    title = "You're connected",
                    message = msg,
                    onDismiss = { viewModel.clearCareConnectionBanner() }
                )
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Need help finding your code?",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = { /* Support flow can be wired later */ }) {
                    Text(
                        "Contact healthcare support",
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            pendingUnlinkRelId?.let { relId ->
                val who = state.patients.find { it._id == relId }?.patientId.resolveUser()?.name ?: "this patient"
                PairingConfirmDialog(
                    title = "Remove patient?",
                    message = "$who will be unlinked from your caregiver account for this connection.",
                    confirmLabel = "Remove",
                    onConfirm = {
                        pendingUnlinkRelId = null
                        viewModel.removePatientRelationship(relId)
                    },
                    onDismiss = { pendingUnlinkRelId = null }
                )
            }
        }
    }
}

@Composable
fun CaregiverQrScannerScreen(
    onBack: () -> Unit,
    onDone: () -> Unit,
    viewModel: CaregiverCarePairingViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val camExecutor = remember { Executors.newSingleThreadExecutor() }
    val barcodeScanner = remember { BarcodeScanning.getClient() }
    val mlKitBusy = remember { AtomicBoolean(false) }
    val zxingFrame = remember { AtomicInteger(0) }
    DisposableEffect(barcodeScanner) {
        onDispose { barcodeScanner.close() }
    }
    DisposableEffect(Unit) {
        onDispose { camExecutor.shutdown() }
    }
    var permissionOk by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permissionOk = it
    }
    LaunchedEffect(Unit) {
        if (!permissionOk) launcher.launch(Manifest.permission.CAMERA)
    }
    AppBackdrop {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.12f),
            topBar = { RecallTopBar(title = "Scan QR", onBack = onBack) }
        ) { padding ->
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                Text(
                    "Fill frame.",
                    Modifier.padding(16.dp),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium
                )
                if (permissionOk) {
                    AndroidView(
                        factory = { ctx ->
                            val pv = PreviewView(ctx).apply {
                                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                            }
                            val camProviderFuture = ProcessCameraProvider.getInstance(ctx)
                            camProviderFuture.addListener({
                                val camProvider = camProviderFuture.get()
                                val preview = Preview.Builder().build().also {
                                    it.setSurfaceProvider(pv.surfaceProvider)
                                }
                                val analysisResolution = ResolutionSelector.Builder()
                                    .setResolutionStrategy(
                                        ResolutionStrategy(
                                            Size(1920, 1080),
                                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                                        )
                                    )
                                    .build()
                                val analysis = ImageAnalysis.Builder()
                                    .setResolutionSelector(analysisResolution)
                                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                    .build()
                                analysis.setAnalyzer(camExecutor) { imageProxy ->
                                    if (!mlKitBusy.compareAndSet(false, true)) {
                                        imageProxy.close()
                                        return@setAnalyzer
                                    }
                                    if (zxingFrame.getAndIncrement() % 2 == 0) {
                                        val zxingText = CareQrZxingDecoder.tryDecodeQr(imageProxy)
                                        if (!zxingText.isNullOrBlank() &&
                                            looksLikeCareInvitePayload(zxingText)
                                        ) {
                                            android.util.Log.d(
                                                "CarePairingQR",
                                                "ZXing decoded invite length=${zxingText.length}"
                                            )
                                            mlKitBusy.set(false)
                                            imageProxy.close()
                                            viewModel.submitScannedToken(zxingText, onDone)
                                            return@setAnalyzer
                                        }
                                    }
                                    val mediaImg = imageProxy.image
                                    if (mediaImg == null) {
                                        mlKitBusy.set(false)
                                        imageProxy.close()
                                        return@setAnalyzer
                                    }
                                    val input = InputImage.fromMediaImage(
                                        mediaImg,
                                        imageProxy.imageInfo.rotationDegrees
                                    )
                                    barcodeScanner.process(input)
                                        .addOnSuccessListener { codes: MutableList<Barcode> ->
                                            val raw = codes.mapNotNull { it.rawValue?.trim() }
                                                .firstOrNull { looksLikeCareInvitePayload(it) }
                                            if (!raw.isNullOrBlank()) {
                                                android.util.Log.d(
                                                    "CarePairingQR",
                                                    "ML Kit raw invite length=${raw.length}"
                                                )
                                                viewModel.submitScannedToken(raw, onDone)
                                            }
                                        }
                                        .addOnFailureListener { }
                                        .addOnCompleteListener {
                                            mlKitBusy.set(false)
                                            imageProxy.close()
                                        }
                                }
                                try {
                                    camProvider.unbindAll()
                                    camProvider.bindToLifecycle(
                                        lifecycleOwner,
                                        CameraSelector.DEFAULT_BACK_CAMERA,
                                        preview,
                                        analysis
                                    )
                                } catch (_: Exception) {
                                }
                            }, ContextCompat.getMainExecutor(ctx))
                            pv
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                } else {
                    Text("Camera needed.", Modifier.padding(16.dp))
                }
            }
        }
    }
}

@Composable
fun CaregiverInviteCodeScreen(
    onBack: () -> Unit,
    onDone: () -> Unit,
    viewModel: CaregiverCarePairingViewModel = hiltViewModel()
) {
    var code by remember { mutableStateOf("") }
    val state by viewModel.state.collectAsState()
    Scaffold(
        containerColor = CarePairingTheme.PageBackground,
        topBar = { RecallTopBar(title = "Enter invite code", onBack = onBack) }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            PairingScreenIntro(
                title = "Patient backup code",
                subtitle = "Ask your patient to read the short code from Link caregiver → Backup when QR isn't possible."
            )
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    androidx.compose.material3.OutlinedTextField(
                        value = code,
                        onValueChange = { code = it.uppercase().take(12) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Invite code") },
                        placeholder = { Text("e.g. A1B2C3") },
                        textStyle = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp
                        )
                    )
                    PrimaryActionButton(
                        text = "Send request",
                        onClick = { viewModel.submitCode(code.trim(), onDone) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = code.trim().length >= 4 && !state.busy
                    )
                }
            }
            if (!state.message.isNullOrBlank()) {
                PairingFeedbackBanner(message = state.message ?: "")
            }
        }
    }
}

@Composable
fun CaregiverLinkedPatientsScreen(
    onBack: () -> Unit,
    onPatientChosen: () -> Unit,
    onAddPatient: () -> Unit = {},
    viewModel: CaregiverCarePairingViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val selectedId by viewModel.selectedPatientId.collectAsState()
    LaunchedEffect(Unit) { viewModel.refreshPatients() }
    Scaffold(
        containerColor = CarePairingTheme.PageBackground,
        topBar = { RecallTopBar(title = "My patients", onBack = onBack) }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            PairingScreenIntro(
                title = "Your watchlist",
                subtitle = "Choose a patient to open their dashboard, tasks, zones, and timeline.",
                badge = state.patients.size.takeIf { it > 0 }?.toString()
            )
            if (state.patients.isEmpty()) {
                PairingEmptyState(
                    icon = Icons.Outlined.Person,
                    title = "No patients linked yet",
                    body = "Scan a patient's Recall QR or enter their invite code from Add patient.",
                    surfaceColor = CarePairingTheme.MintSurface,
                    inkColor = CarePairingTheme.MintInk,
                    actionLabel = "Add a patient",
                    onAction = onAddPatient
                )
            } else {
                state.patients.forEach { row ->
                    val pid = row.patientId.resolveUser()?._id ?: return@forEach
                    val name = row.patientId.resolveUser()?.name ?: "Patient"
                    PairingLinkedPatientCard(
                        name = name,
                        isSelected = selectedId == pid,
                        onOpen = {
                            viewModel.selectPatientForCare(pid)
                            onPatientChosen()
                        }
                    )
                }
            }
            if (!state.message.isNullOrBlank()) {
                PairingFeedbackBanner(message = state.message ?: "")
            }
        }
    }
}
