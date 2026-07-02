package com.example.recallai.geofence

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.recallai.ui.components.PrimaryActionButton
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

private val PRESET_COLORS = listOf(
    "#1E88E5",
    "#43A047",
    "#FB8C00",
    "#E53935",
    "#8E24AA"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateGeofenceScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: CreateGeofenceViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var center by remember { mutableStateOf<LatLng?>(null) }
    var radiusM by remember { mutableDoubleStateOf(200.0) }
    var zoneName by remember { mutableStateOf("") }
    var colorHex by remember { mutableStateOf(PRESET_COLORS.first()) }

    val fused = remember { LocationServices.getFusedLocationProviderClient(context) }
    fun hasLocPerm(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    var myLocationEnabled by remember { mutableStateOf(hasLocPerm()) }

    val locPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        val ok = granted[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            granted[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        myLocationEnabled = ok
        if (!ok) {
            scope.launch { snackbar.showSnackbar("Location permission is needed for current location.") }
        }
    }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(37.4, -122.1), 11f)
    }

    LaunchedEffect(center?.latitude, center?.longitude) {
        val c = center ?: return@LaunchedEffect
        cameraPositionState.position = CameraPosition.fromLatLngZoom(c, 15f)
    }

    LaunchedEffect(state.done) {
        if (state.done) {
            snackbar.showSnackbar("Zone saved")
            viewModel.clearDone()
            onSaved()
        }
    }

    LaunchedEffect(state.error) {
        val err = state.error
        if (err != null) {
            snackbar.showSnackbar(err)
            viewModel.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Draw zone", fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                properties = MapProperties(
                    isMyLocationEnabled = myLocationEnabled
                ),
                uiSettings = MapUiSettings(
                    zoomControlsEnabled = true,
                    compassEnabled = true,
                    myLocationButtonEnabled = myLocationEnabled
                ),
                onMapClick = { latLng ->
                    center = latLng
                    if (radiusM < 50.0) radiusM = 200.0
                }
            ) {
                center?.let { c ->
                    val fill = runCatching {
                        Color(android.graphics.Color.parseColor(colorHex)).copy(alpha = 0.28f)
                    }.getOrElse { Color(0x4D1E88E5) }
                    val stroke = runCatching {
                        Color(android.graphics.Color.parseColor(colorHex))
                    }.getOrElse { Color(0xFF1E88E5) }
                    if (radiusM >= 50.0) {
                        Circle(
                            center = c,
                            radius = radiusM,
                            fillColor = fill,
                            strokeColor = stroke,
                            strokeWidth = 2f
                        )
                    }
                }
            }

            if (center != null && radiusM >= 50.0) {
                Card(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 12.dp, start = 12.dp, end = 12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
                    )
                ) {
                    Text(
                        text = "${radiusM.toInt()} m",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.97f))
                    .navigationBarsPadding()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    "Tap the map for the zone center, or use this phone’s GPS. Adjust radius with the slider.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = {
                        if (!hasLocPerm()) {
                            locPermLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                            return@OutlinedButton
                        }
                        scope.launch {
                            val loc = fetchFreshLocation(fused)
                            if (loc != null) {
                                center = LatLng(loc.latitude, loc.longitude)
                                if (radiusM < 50.0) radiusM = 200.0
                            } else {
                                snackbar.showSnackbar("GPS not ready—tap the map to set the center.")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Use this phone’s GPS")
                }
                Spacer(Modifier.height(12.dp))
                if (center != null) {
                    Text("Radius", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Slider(
                        value = radiusM.toFloat().coerceIn(50f, 5000f),
                        onValueChange = { radiusM = it.toDouble() },
                        valueRange = 50f..5000f,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                OutlinedTextField(
                    value = zoneName,
                    onValueChange = { zoneName = it },
                    label = { Text("Zone name", fontSize = 18.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp)
                )
                Spacer(Modifier.height(10.dp))
                Text("Color", fontSize = 18.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    PRESET_COLORS.forEach { hex ->
                        val selected = colorHex == hex
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 56.dp)
                                .background(
                                    runCatching { Color(android.graphics.Color.parseColor(hex)) }
                                        .getOrElse { Color.Gray },
                                    shape = CircleShape
                                )
                                .padding(if (selected) 4.dp else 0.dp)
                                .then(
                                    if (selected) Modifier.background(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                                        CircleShape
                                    ) else Modifier
                                )
                                .clickable { colorHex = hex },
                            contentAlignment = Alignment.Center
                        ) {}
                    }
                }
                Spacer(Modifier.height(12.dp))
                PrimaryActionButton(
                    text = if (state.saving) "Saving…" else "Save Zone",
                    onClick = {
                        val c = center
                        if (c == null || radiusM < 50.0) {
                            scope.launch {
                                snackbar.showSnackbar("Set a center (tap map or current location) and radius.")
                            }
                            return@PrimaryActionButton
                        }
                        if (zoneName.isBlank()) {
                            scope.launch { snackbar.showSnackbar("Enter a zone name") }
                            return@PrimaryActionButton
                        }
                        viewModel.saveZone(zoneName, c.latitude, c.longitude, radiusM, colorHex)
                    },
                    enabled = !state.saving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                )
                TextButton(
                    onClick = {
                        center = null
                        radiusM = 200.0
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                ) {
                    Text("Clear zone", fontSize = 18.sp)
                }
            }
        }
    }
}

private suspend fun fetchFreshLocation(
    fused: com.google.android.gms.location.FusedLocationProviderClient
): android.location.Location? {
    val current = suspendCancellableCoroutine<android.location.Location?> { cont ->
        val cts = CancellationTokenSource()
        cont.invokeOnCancellation { cts.cancel() }
        fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
            .addOnCompleteListener { task ->
                if (cont.isActive) cont.resume(task.result)
            }
    }
    if (current != null) return current
    return suspendCancellableCoroutine { cont ->
        fused.lastLocation.addOnCompleteListener { t ->
            if (cont.isActive) cont.resume(t.result)
        }
    }
}
