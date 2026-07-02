package com.example.recallai.emergency

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.recallai.data.AuthManager
import com.example.recallai.data.CareRepository
import com.example.recallai.data.MemoryRepository
import com.example.recallai.data.remote.CareRelationshipDto
import com.example.recallai.data.remote.resolveUser
import com.example.recallai.notifications.RecallNotifications
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

data class EmergencyUiState(
    val primaryCaregiverName: String? = null,
    val hasLinkedCaregiver: Boolean = false,
    val isSending: Boolean = false,
    val statusMessage: String? = null,
    val lastConfirmation: String? = null,
    val showPulseCheck: Boolean = false
)

@HiltViewModel
class EmergencyAssistViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val memoryRepository: MemoryRepository,
    private val careRepository: CareRepository,
    private val memoryOpenCoordinator: com.example.recallai.memories.MemoryOpenCoordinator
) : ViewModel() {
    private val _uiState = MutableStateFlow(EmergencyUiState())
    val uiState = _uiState.asStateFlow()

    fun applyPendingMemoryOpen() {
        memoryOpenCoordinator.consume()?.let { payload ->
            _uiState.value = _uiState.value.copy(
                statusMessage = "Memory: ${payload.contextLabel()}"
            )
        }
    }

    fun refreshCaregiverLink() {
        if (AuthManager.userRole != "patient") {
            _uiState.value = EmergencyUiState(hasLinkedCaregiver = false)
            return
        }
        viewModelScope.launch {
            runCatching { careRepository.myCaregivers() }
                .onSuccess { rows ->
                    val approved = rows.filter { it.status?.equals("approved", true) == true }
                    val name = pickCaregiverName(approved)
                    _uiState.value = _uiState.value.copy(
                        primaryCaregiverName = name,
                        hasLinkedCaregiver = approved.isNotEmpty()
                    )
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(hasLinkedCaregiver = false)
                }
        }
    }

    private fun pickCaregiverName(rows: List<CareRelationshipDto>): String? {
        if (rows.isEmpty()) return null
        val primary = rows.firstOrNull {
            it.relationshipType?.equals("primary", true) == true
        } ?: rows.first()
        val cg = primary.caregiverId.resolveUser()
        return cg?.name?.trim()?.takeIf { it.isNotEmpty() }
            ?: cg?.email?.substringBefore("@")?.trim()?.takeIf { it.isNotEmpty() }
    }

    fun clearTransient() {
        _uiState.value = _uiState.value.copy(statusMessage = null, lastConfirmation = null, showPulseCheck = false)
    }

    fun triggerOption(type: String, label: String) {
        viewModelScope.launch { dispatch(type, label) }
    }

    fun triggerSos() {
        viewModelScope.launch { dispatch("sos", "SOS") }
    }

    private suspend fun dispatch(type: String, label: String) {
        val s = _uiState.value
        if (AuthManager.userRole != "patient") return
        if (s.isSending) return
        if (!s.hasLinkedCaregiver) {
            _uiState.value = s.copy(statusMessage = "No caregiver connected — link one first")
            return
        }
        _uiState.value = s.copy(
            isSending = true,
            statusMessage = null,
            lastConfirmation = null,
            showPulseCheck = false
        )
        val (lat, lng) = fetchLastLocation()
        val patientName = AuthManager.userName?.trim()?.takeIf { it.isNotBlank() } ?: "Patient"
        val message = "$patientName · $label"
        memoryRepository.saveTextMemory(
            text = message,
            title = "Emergency",
            type = "EMERGENCY_EVENT",
            tags = listOf("emergency", type)
        )
        val result = runCatching {
            careRepository.triggerEmergency(message = message, type = type, lat = lat, lng = lng)
        }
        result.onFailure { e ->
            _uiState.value = _uiState.value.copy(
                isSending = false,
                statusMessage = e.message ?: "Could not send alert. Check connection and try again."
            )
        }
        result.onSuccess { out ->
            val cgName = s.primaryCaregiverName ?: "Caregiver"
            val ok = out.caregiverIds.isNotEmpty()
            if (ok) {
                RecallNotifications.show(
                    context = appContext,
                    kind = RecallNotifications.Kind.Emergency,
                    title = "Emergency sent",
                    body = "Your caregiver ($cgName) was notified.",
                    ignoreActivityToggle = true,
                    screenRoute = "alert_center"
                )
            }
            _uiState.value = _uiState.value.copy(
                isSending = false,
                lastConfirmation = if (ok) "Alert sent to $cgName" else "No caregiver connected — link one first",
                showPulseCheck = ok,
                statusMessage = if (ok) null else "No caregiver connected — link one first"
            )
        }
    }

    private suspend fun fetchLastLocation(): Pair<Double?, Double?> {
        val fine = ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) return null to null
        val client = LocationServices.getFusedLocationProviderClient(appContext)
        val loc = suspendCancellableCoroutine<android.location.Location?> { cont ->
            val cts = CancellationTokenSource()
            cont.invokeOnCancellation { cts.cancel() }
            client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cts.token)
                .addOnCompleteListener { task ->
                    if (cont.isActive) cont.resume(task.result)
                }
        } ?: suspendCancellableCoroutine { cont ->
            client.lastLocation.addOnCompleteListener { t ->
                if (cont.isActive) cont.resume(t.result)
            }
        }
        return if (loc != null) loc.latitude to loc.longitude else null to null
    }
}

private val EmergencyHeaderBlue = Color(0xFF1976D2)
private val EmergencySosRed = Color(0xFFE53935)
private val EmergencyIconTint = Color(0xFF263238)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmergencyAssistScreen(
    onBack: () -> Unit,
    onNavigateHome: () -> Unit = {},
    onNavigateChat: () -> Unit = {},
    onNavigateFace: () -> Unit = {},
    onNavigateMemories: () -> Unit = {},
    onNavigateRecall: () -> Unit = {},
    viewModel: EmergencyAssistViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    val actionsEnabled = state.hasLinkedCaregiver && !state.isSending

    LaunchedEffect(Unit) {
        viewModel.applyPendingMemoryOpen()
        viewModel.refreshCaregiverLink()
    }
    LaunchedEffect(state.lastConfirmation, state.statusMessage) {
        if (state.lastConfirmation != null || state.statusMessage != null) {
            delay(5000)
            viewModel.clearTransient()
        }
    }

    Scaffold(
        containerColor = Color.White,
        topBar = {
            Surface(color = EmergencyHeaderBlue, shadowElevation = 4.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                    Text(
                        text = "Emergency",
                        modifier = Modifier.weight(1f),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            if (!state.hasLinkedCaregiver) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Link an approved caregiver first so alerts can be delivered.",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            } else {
                state.primaryCaregiverName?.let { name ->
                    Text(
                        text = "Alerts go to $name",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF546E7A)
                    )
                }
            }

            val sosModifier = Modifier
                .fillMaxWidth()
                .height(112.dp)
                .shadow(8.dp, RoundedCornerShape(16.dp), clip = false)
                .clip(RoundedCornerShape(16.dp))
                .pointerInput(actionsEnabled) {
                    detectTapGestures(
                        onPress = {
                            if (!actionsEnabled) return@detectTapGestures
                            val job = scope.launch {
                                delay(3000)
                                viewModel.triggerSos()
                            }
                            tryAwaitRelease()
                            job.cancel()
                        }
                    )
                }

            Surface(
                modifier = sosModifier,
                shape = RoundedCornerShape(16.dp),
                color = if (actionsEnabled) EmergencySosRed else EmergencySosRed.copy(alpha = 0.45f)
            ) {
                Column(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "SOS",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 36.sp,
                        letterSpacing = 2.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Press and hold 3 seconds",
                        color = Color.White.copy(alpha = 0.92f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            val gridOptions = listOf(
                EmergencyGridOption(
                    icon = Icons.Outlined.Warning,
                    label = "Fall / injury",
                    type = "fall",
                    dispatchLabel = "Fall / injury"
                ),
                EmergencyGridOption(
                    icon = Icons.Outlined.FavoriteBorder,
                    label = "Chest pain / breathing",
                    type = "medical",
                    dispatchLabel = "Medical"
                ),
                EmergencyGridOption(
                    icon = Icons.Outlined.LocationOn,
                    label = "Lost / unsafe place",
                    type = "lost",
                    dispatchLabel = "Lost"
                ),
                EmergencyGridOption(
                    icon = Icons.Outlined.Phone,
                    label = "Need caregiver call",
                    type = "callback",
                    dispatchLabel = "Callback"
                )
            )

            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                gridOptions.chunked(2).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        row.forEach { option ->
                            EmergencyGridCard(
                                option = option,
                                enabled = actionsEnabled,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    viewModel.triggerOption(option.type, option.dispatchLabel)
                                }
                            )
                        }
                        if (row.size == 1) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }

            if (state.isSending) {
                Text(
                    text = "Sending alert…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = EmergencyHeaderBlue
                )
            }

            if (state.showPulseCheck) {
                val pulse = rememberInfiniteTransition(label = "sosPulse")
                val scale by pulse.animateFloat(
                    initialValue = 0.92f,
                    targetValue = 1.08f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(700, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "scale"
                )
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFF2E7D32),
                    modifier = Modifier
                        .size(48.dp)
                        .scale(scale)
                )
            }

            state.lastConfirmation?.let { line ->
                Text(
                    line,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF2E7D32),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            state.statusMessage?.let { line ->
                Text(
                    line,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

private data class EmergencyGridOption(
    val icon: ImageVector,
    val label: String,
    val type: String,
    val dispatchLabel: String
)

@Composable
private fun EmergencyGridCard(
    option: EmergencyGridOption,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .aspectRatio(1f)
            .shadow(6.dp, RoundedCornerShape(14.dp), clip = false),
        shape = RoundedCornerShape(14.dp),
        color = Color.White,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = option.icon,
                contentDescription = null,
                modifier = Modifier.size(44.dp),
                tint = if (enabled) EmergencyIconTint else EmergencyIconTint.copy(alpha = 0.4f)
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = option.label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                color = if (enabled) EmergencyIconTint else EmergencyIconTint.copy(alpha = 0.45f),
                lineHeight = 18.sp
            )
        }
    }
}
