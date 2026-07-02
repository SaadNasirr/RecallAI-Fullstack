package com.example.recallai.geofence

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.recallai.ui.components.PrimaryActionButton
import com.example.recallai.ui.components.RecallTopBar
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState

@Composable
fun PatientGeofenceScreen(
    onBack: () -> Unit,
    viewModel: PatientWhereAmIViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val mapHeight = (LocalConfiguration.current.screenHeightDp * 0.6f).dp

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        if (granted[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            granted[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        ) {
            viewModel.refreshLocationAfterPermission()
        }
    }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    LaunchedEffect(state.shareMessage, state.shareError) {
        if (state.shareMessage != null || state.shareError != null) {
            kotlinx.coroutines.delay(3500)
            viewModel.clearShareFeedback()
        }
    }

    val cameraState = rememberCameraPositionState()
    LaunchedEffect(state.lat, state.lng) {
        val la = state.lat
        val ln = state.lng
        if (la != null && ln != null && state.mapsAvailable) {
            val target = LatLng(la, ln)
            cameraState.position = CameraPosition.fromLatLngZoom(target, 16f)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { RecallTopBar(title = "Where am I?", onBack = onBack) }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
            ) {
                Text(
                    text = "Only a linked caregiver can add or edit safe zones: they open their RecallAI app, tap the Zones tab, then Add New Zone. This screen shows your location and any zones they created.",
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(mapHeight)
            ) {
                when {
                    state.loading -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    state.mapsAvailable && state.lat != null && state.lng != null -> {
                        val pos = LatLng(state.lat!!, state.lng!!)
                        GoogleMap(
                            modifier = Modifier.fillMaxSize(),
                            cameraPositionState = cameraState
                        ) {
                            GeofenceMapOverlays(zones = state.safeZones)
                            Marker(state = MarkerState(position = pos))
                        }
                    }
                    else -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                if (!state.mapsAvailable) {
                                    "Map needs a Google Maps API key in local.properties."
                                } else {
                                    "Waiting for location…"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            when {
                state.permissionDenied -> {
                    Text(
                        "Location is off for RecallAI.",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            val i = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            }
                            context.startActivity(i)
                        }
                    ) {
                        Text("Open Settings")
                    }
                }
                state.noNetwork && state.fullAddress.isBlank() -> {
                    Text(
                        "No connection.",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    state.lastKnownFullAddress?.let { last ->
                        Spacer(Modifier.height(8.dp))
                        Text(last, style = MaterialTheme.typography.bodyLarge)
                        state.lastKnownAtLabel?.let { age ->
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Last updated $age",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                else -> {
                    Text(
                        state.fullAddress.ifBlank { state.lastKnownFullAddress.orEmpty() },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (state.neighborhood.isNotBlank() || state.city.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            listOf(state.neighborhood, state.city).filter { it.isNotBlank() }
                                .joinToString(" · "),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (state.noNetwork) {
                        state.lastKnownAtLabel?.let { age ->
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Last updated $age",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                }
            }

            state.shareMessage?.let { m ->
                Spacer(Modifier.height(8.dp))
                Text(m, color = MaterialTheme.colorScheme.primary)
            }
            state.shareError?.let { e ->
                Spacer(Modifier.height(8.dp))
                Text(e, color = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.height(16.dp))
            PrimaryActionButton(
                text = "Share location",
                onClick = { viewModel.shareWithCaregiver() },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
