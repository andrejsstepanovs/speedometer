package com.example.gpsspeedometer.domain

interface TimeProvider {
    fun currentTimeMillis(): Long
}