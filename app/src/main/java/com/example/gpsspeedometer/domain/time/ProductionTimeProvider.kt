package com.example.gpsspeedometer.domain.time

import android.os.SystemClock
import com.example.gpsspeedometer.domain.TimeProvider

class ProductionTimeProvider : TimeProvider {
    override fun currentTimeMillis(): Long = SystemClock.elapsedRealtime()
}