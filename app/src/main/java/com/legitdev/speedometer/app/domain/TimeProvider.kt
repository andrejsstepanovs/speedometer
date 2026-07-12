package com.legitdev.speedometer.app.domain

interface TimeProvider {
    fun currentTimeMillis(): Long
}