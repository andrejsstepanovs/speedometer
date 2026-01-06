package com.example.gpsspeedometer.data.repository

import com.example.gpsspeedometer.domain.model.GpsReading

interface LocationRepository {
    suspend fun startLocationUpdates(
        onReadingUpdate: (GpsReading) -> Unit,
        onGpsError: (String?) -> Unit
    )
    suspend fun stopLocationUpdates()
}