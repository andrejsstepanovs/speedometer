package com.legitdev.speedometer.app.domain.repository

import com.legitdev.speedometer.app.domain.model.SpeedUnit

interface SettingsRepository {
    fun getSpeedUnit(): SpeedUnit
    fun setSpeedUnit(unit: SpeedUnit)
}
