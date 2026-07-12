package com.legitdev.speedometer.app.domain.time

import android.os.SystemClock
import com.legitdev.speedometer.app.domain.TimeProvider

class ProductionTimeProvider : TimeProvider {
    override fun currentTimeMillis(): Long = SystemClock.elapsedRealtime()
}