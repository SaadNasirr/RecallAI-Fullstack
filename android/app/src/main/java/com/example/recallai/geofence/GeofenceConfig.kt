package com.example.recallai.geofence

data class SafeZone(
    val id: String,
    val name: String,
    val lat: Double,
    val lng: Double,
    val radiusMeters: Float
)

object GeofenceConfig {
    val zones: List<SafeZone> = listOf(
        SafeZone(id = "home_zone", name = "Home Zone", lat = 31.5204, lng = 74.3587, radiusMeters = 250f),
        SafeZone(id = "clinic_zone", name = "Clinic Zone", lat = 31.4697, lng = 74.2728, radiusMeters = 250f),
        SafeZone(id = "family_zone", name = "Family Zone", lat = 31.5820, lng = 74.3294, radiusMeters = 250f)
    )
}
