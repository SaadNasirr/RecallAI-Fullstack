package com.example.recallai.geofence

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.recallai.BuildConfig
import com.example.recallai.data.remote.GeofenceResponse
import com.example.recallai.ui.components.PrimaryActionButton
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeofenceZonesScreen(
    onBack: () -> Unit,
    onNavigateCreate: (patientId: String) -> Unit,
    onNavigateAlerts: (patientId: String) -> Unit,
    viewModel: GeofenceZonesViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    val sheetPeek = (LocalConfiguration.current.screenHeightDp.dp * 0.26f).coerceIn(200.dp, 300.dp)
    var patientMenuOpen by remember { mutableStateOf(false) }
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(37.421999, -122.084057), 11f)
    }

    val focusPatient = state.selectedPatientId
    val anchorZoneId = state.zones.firstOrNull()?._id

    LaunchedEffect(Unit) {
        viewModel.load()
    }

    // Move camera only when switching patient or when the first listed zone identity changes (not on every toggle).
    LaunchedEffect(focusPatient, anchorZoneId) {
        val z = state.zones.firstOrNull() ?: return@LaunchedEffect
        cameraPositionState.position =
            CameraPosition.fromLatLngZoom(LatLng(z.centerLat, z.centerLng), 14f)
    }

    val scaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = rememberStandardBottomSheetState(
            initialValue = SheetValue.PartiallyExpanded,
            skipHiddenState = false
        )
    )

    var deleteTarget by remember { mutableStateOf<GeofenceResponse?>(null) }

    Box(Modifier.fillMaxSize()) {
        BottomSheetScaffold(
            scaffoldState = scaffoldState,
            sheetPeekHeight = sheetPeek,
            sheetDragHandle = null,
            sheetContent = {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = state.selectedPatientName.ifBlank { "Patient" },
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        if (state.patientOptions.size > 1) {
                            Box {
                                TextButton(onClick = { patientMenuOpen = true }) {
                                    Text("Switch", fontSize = 16.sp)
                                }
                                DropdownMenu(
                                    expanded = patientMenuOpen,
                                    onDismissRequest = { patientMenuOpen = false }
                                ) {
                                    state.patientOptions.forEach { (id, name) ->
                                        DropdownMenuItem(
                                            text = { Text(name, fontSize = 18.sp) },
                                            onClick = {
                                                patientMenuOpen = false
                                                viewModel.selectPatient(id)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    PrimaryActionButton(
                        text = "Add New Zone",
                        onClick = {
                            state.selectedPatientId?.let { onNavigateCreate(it) }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp),
                        enabled = state.selectedPatientId != null
                    )
                    TextButton(
                        onClick = {
                            state.selectedPatientId?.let { onNavigateAlerts(it) }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = state.selectedPatientId != null
                    ) {
                        Text("View alert history", fontSize = 18.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))
                    when {
                        state.loading -> Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator()
                        }
                        state.error != null -> {
                            Text(
                                text = state.error ?: "",
                                fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(Modifier.height(8.dp))
                            TextButton(onClick = { viewModel.load() }) {
                                Text("Retry", fontSize = 18.sp)
                            }
                        }
                        !state.hasLinkedPatients ->
                            Text(
                                text = "Link an approved patient from the Watch tab first. Safe zones are created for a specific patient.",
                                fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.error
                            )
                        state.zones.isEmpty() -> Text(
                            text = "No zones yet. Use Add New Zone above, tap the map to set the center, then Save.",
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        else -> LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.heightIn(max = 280.dp)
                        ) {
                            items(state.zones, key = { it._id }) { z ->
                                ZoneRow(
                                    zone = z,
                                    onZoom = {
                                        scope.launch {
                                            cameraPositionState.position =
                                                CameraPosition.fromLatLngZoom(
                                                    LatLng(z.centerLat, z.centerLng),
                                                    15f
                                                )
                                        }
                                    },
                                    onToggle = { viewModel.toggleZone(z._id) },
                                    onDelete = { deleteTarget = z }
                                )
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = cameraPositionState,
                    properties = MapProperties(isMyLocationEnabled = false),
                    uiSettings = MapUiSettings(
                        zoomControlsEnabled = true,
                        compassEnabled = false,
                        mapToolbarEnabled = false
                    )
                ) {
                    GeofenceMapOverlays(zones = state.zones)
                }

                SafeZonesTopChrome(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .zIndex(4f),
                    onBack = onBack,
                    showMapsKeyHint = !BuildConfig.HAS_GOOGLE_MAPS_KEY
                )
            }
        }

        deleteTarget?.let { z ->
            AlertDialog(
                onDismissRequest = { deleteTarget = null },
                title = { Text("Delete zone?", fontSize = 18.sp) },
                text = { Text(z.name, fontSize = 18.sp) },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.deleteZone(z._id)
                        deleteTarget = null
                    }) { Text("Delete", fontSize = 18.sp) }
                },
                dismissButton = {
                    TextButton(onClick = { deleteTarget = null }) {
                        Text("Cancel", fontSize = 18.sp)
                    }
                }
            )
        }
    }
}

@Composable
private fun SafeZonesTopChrome(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    showMapsKeyHint: Boolean
) {
    Column(
        modifier = modifier.statusBarsPadding()
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            tonalElevation = 2.dp,
            shadowElevation = 6.dp,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(52.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back"
                    )
                }
                Text(
                    text = "Safe zones",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        if (showMapsKeyHint) {
            Text(
                text = "Set GOOGLE_MAPS_API_KEY in gradle.properties so the map can load tiles.",
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .background(
                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.96f),
                        shape = RoundedCornerShape(10.dp)
                    )
                    .padding(12.dp),
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

@Composable
internal fun GeofenceMapOverlays(zones: List<GeofenceResponse>) {
    zones.forEach { z ->
        val fill = zoneColor(z, z.isActive).copy(alpha = 0.3f)
        val stroke = if (z.isActive) zoneColor(z, true) else Color(0xFF9E9E9E)
        val center = remember(z._id, z.centerLat, z.centerLng) {
            LatLng(z.centerLat, z.centerLng)
        }
        val markerState = rememberMarkerState(position = center)
        LaunchedEffect(z.centerLat, z.centerLng) {
            markerState.position = LatLng(z.centerLat, z.centerLng)
        }
        Circle(
            center = center,
            radius = z.radiusMeters,
            fillColor = fill,
            strokeColor = stroke,
            strokeWidth = 2f
        )
        Marker(
            state = markerState,
            title = z.name
        )
    }
}

@Composable
private fun ZoneRow(
    zone: GeofenceResponse,
    onZoom: () -> Unit,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onZoom)
            .heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .background(zoneColor(zone, zone.isActive), CircleShape)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(zone.name, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text("${zone.radiusMeters.toInt()} m", fontSize = 18.sp)
        }
        Switch(checked = zone.isActive, onCheckedChange = { onToggle() })
        TextButton(onClick = onDelete) {
            Text("Delete", fontSize = 18.sp)
        }
    }
}

internal fun zoneColor(zone: GeofenceResponse, active: Boolean): Color {
    if (!active) return Color(0xFFBDBDBD)
    return runCatching {
        Color(AndroidColor.parseColor(zone.color))
    }.getOrElse { Color(0xFF1E88E5) }
}
