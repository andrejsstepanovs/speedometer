package com.legitdev.speedometer.app.data.repository

import com.legitdev.speedometer.app.domain.model.GpsReading

interface LocationRepository {
    suspend fun startLocationUpdates(
        onReadingUpdate: (GpsReading) -> Unit,
        onGpsError: (String?) -> Unit
    )
    suspend fun stopLocationUpdates()
}