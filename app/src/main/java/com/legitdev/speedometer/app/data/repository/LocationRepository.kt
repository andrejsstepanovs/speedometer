package com.legitdev.speedometer.app.data.repository

import com.legitdev.speedometer.app.domain.model.GpsReading

interface LocationRepository {
    suspend fun startLocationUpdates(
        onLocationFix: (GpsReading) -> Unit,
        onSatelliteCount: (Int) -> Unit,
        onGpsError: (String?) -> Unit
    )
    suspend fun stopLocationUpdates()
}