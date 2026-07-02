package com.example.recallai.geofence

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.recallai.BuildConfig
import com.example.recallai.data.AuthManager
import com.example.recallai.data.CareRepository
import com.example.recallai.data.GeofenceRepository
import com.example.recallai.data.remote.GeofenceResponse
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

data class PatientWhereAmIUiState(
    val loading: Boolean = true,
    val permissionDenied: Boolean = false,
    val noNetwork: Boolean = false,
    val lat: Double? = null,
    val lng: Double? = null,
    val fullAddress: String = "",
    val neighborhood: String = "",
    val city: String = "",
    val lastKnownFullAddress: String? = null,
    val lastKnownAtLabel: String? = null,
    val shareMessage: String? = null,
    val shareError: String? = null,
    val mapsAvailable: Boolean = BuildConfig.HAS_GOOGLE_MAPS_KEY,
    /** Caregiver-defined safe zones (shown on map; OS registration uses same source). */
    val safeZones: List<GeofenceResponse> = emptyList()
)

@HiltViewModel
class PatientWhereAmIViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val careRepository: CareRepository,
    private val geofenceRepository: GeofenceRepository
) : ViewModel() {

    private val prefs = appContext.getSharedPreferences("recallai_where_am_i", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(PatientWhereAmIUiState())
    val uiState = _state.asStateFlow()

    fun refreshLocationAfterPermission() {
        viewModelScope.launch { resolveLocation() }
    }

    fun clearShareFeedback() {
        _state.value = _state.value.copy(shareMessage = null, shareError = null)
    }

    fun shareWithCaregiver() {
        val s = _state.value
        val line = s.fullAddress.ifBlank { s.lastKnownFullAddress.orEmpty() }.trim()
        if (line.isBlank()) {
            _state.value = s.copy(shareError = "No address to share yet.")
            return
        }
        if (AuthManager.userRole != "patient") return
        viewModelScope.launch {
            _state.value = _state.value.copy(shareMessage = null, shareError = null)
            val result = runCatching {
                careRepository.sharePatientAddress(line, s.lat, s.lng)
            }
            result.onFailure { e ->
                _state.value = _state.value.copy(shareError = e.message ?: "Could not share.")
            }
            result.onSuccess {
                _state.value = _state.value.copy(shareMessage = "Sent.")
            }
        }
    }

    init {
        viewModelScope.launch { resolveLocation() }
    }

    private fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    private fun isOnline(): Boolean {
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val nw = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(nw) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private suspend fun fetchBestLocation(): android.location.Location? {
        val client = LocationServices.getFusedLocationProviderClient(appContext)
        val current = suspendCancellableCoroutine<android.location.Location?> { cont ->
            val cts = CancellationTokenSource()
            cont.invokeOnCancellation { cts.cancel() }
            client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
                .addOnCompleteListener { task ->
                    if (cont.isActive) cont.resume(task.result)
                }
        }
        if (current != null) return current
        return suspendCancellableCoroutine { cont ->
            client.lastLocation.addOnCompleteListener { t ->
                if (cont.isActive) cont.resume(t.result)
            }
        }
    }

    private suspend fun resolveLocation() {
        if (!hasLocationPermission()) {
            _state.value = PatientWhereAmIUiState(
                loading = false,
                permissionDenied = true,
                safeZones = emptyList()
            )
            return
        }
        val online = isOnline()
        val loc = fetchBestLocation()

        if (loc == null) {
            loadCached(online)
            return
        }

        if (!online) {
            val cachedAddr = prefs.getString("addr", "").orEmpty()
            val cachedAt = prefs.getLong("at", 0L)
            val persistedZones = GeofenceRegistrationHelper.loadPersistedZones(appContext)
            _state.value = PatientWhereAmIUiState(
                loading = false,
                noNetwork = true,
                lat = loc.latitude,
                lng = loc.longitude,
                lastKnownFullAddress = cachedAddr.takeIf { it.isNotBlank() },
                lastKnownAtLabel = formatAge(cachedAt),
                mapsAvailable = BuildConfig.HAS_GOOGLE_MAPS_KEY,
                safeZones = persistedZones
            )
            return
        }

        val geo = Geocoder(appContext, Locale.getDefault())
        val addresses = withContext(Dispatchers.IO) {
            runCatching { geo.getFromLocation(loc.latitude, loc.longitude, 1) }.getOrNull()
        }
        val a = addresses?.firstOrNull()
        val full = buildString {
            (0..2).forEach { i ->
                val line = a?.getAddressLine(i)?.trim().orEmpty()
                if (line.isNotBlank()) {
                    if (isNotEmpty()) append(", ")
                    append(line)
                }
            }
        }.ifBlank { "${loc.latitude.format6()}, ${loc.longitude.format6()}" }

        val neighborhood = a?.subLocality?.trim().orEmpty()
            .ifBlank { a?.thoroughfare?.trim().orEmpty() }
        val city = a?.locality?.trim().orEmpty()
            .ifBlank { a?.subAdminArea?.trim().orEmpty() }

        prefs.edit()
            .putString("addr", full)
            .putLong("at", System.currentTimeMillis())
            .putFloat("lat", loc.latitude.toFloat())
            .putFloat("lng", loc.longitude.toFloat())
            .apply()

        val zonesResult =
            if (AuthManager.userId.isNullOrBlank().not()) {
                geofenceRepository.listZones(null)
            } else {
                Result.success(emptyList())
            }
        zonesResult.onSuccess { zones ->
            GeofenceRegistrationHelper.registerZonesFromResponses(appContext, zones)
        }
        val zonesForMap = zonesResult.getOrElse { GeofenceRegistrationHelper.loadPersistedZones(appContext) }

        _state.value = PatientWhereAmIUiState(
            loading = false,
            lat = loc.latitude,
            lng = loc.longitude,
            fullAddress = full,
            neighborhood = neighborhood,
            city = city,
            mapsAvailable = BuildConfig.HAS_GOOGLE_MAPS_KEY,
            safeZones = zonesForMap
        )
    }

    private fun loadCached(online: Boolean) {
        val addr = prefs.getString("addr", "").orEmpty()
        val at = prefs.getLong("at", 0L)
        val lat = prefs.getFloat("lat", Float.NaN).takeUnless { it.isNaN() }?.toDouble()
        val lng = prefs.getFloat("lng", Float.NaN).takeUnless { it.isNaN() }?.toDouble()
        val persistedZones = GeofenceRegistrationHelper.loadPersistedZones(appContext)
        _state.value = PatientWhereAmIUiState(
            loading = false,
            noNetwork = !online,
            lat = lat,
            lng = lng,
            fullAddress = if (online) "" else addr,
            lastKnownFullAddress = addr.takeIf { it.isNotBlank() },
            lastKnownAtLabel = formatAge(at),
            mapsAvailable = BuildConfig.HAS_GOOGLE_MAPS_KEY,
            safeZones = persistedZones
        )
    }

    private fun formatAge(atMs: Long): String? {
        if (atMs <= 0L) return null
        val mins = (System.currentTimeMillis() - atMs) / 60_000L
        return when {
            mins < 1L -> "just now"
            mins < 60L -> "${mins}m ago"
            mins < 1440L -> "${mins / 60L}h ago"
            else -> "${mins / 1440L}d ago"
        }
    }

    private fun Double.format6(): String = "%.5f".format(this)
}
