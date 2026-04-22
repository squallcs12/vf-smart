package com.daotranbang.vfsmart.navigation

data class GpsState(
    val isActive: Boolean = false,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val speedMs: Float = 0f,
    val bearing: Float = 0f
) {
    val speedKmh: Float get() = speedMs * 3.6f

    fun toLocation(): android.location.Location? {
        if (!isActive) return null
        return android.location.Location("gatt").also {
            it.latitude  = latitude
            it.longitude = longitude
            it.speed     = speedMs
            it.bearing   = bearing
        }
    }
}
