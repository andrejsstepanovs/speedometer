package com.example.gpsspeedometer.domain.util

object SpeedConverter {
    fun metersPerSecondToKmh(mps: Float): Float = mps * 3.6f
    fun kmhToMetersPerSecond(kmh: Float): Float = kmh / 3.6f
}