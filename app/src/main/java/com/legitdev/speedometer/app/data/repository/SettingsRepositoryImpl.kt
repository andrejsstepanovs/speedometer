package com.legitdev.speedometer.app.data.repository

import android.content.Context
import com.legitdev.speedometer.app.domain.model.SpeedUnit
import com.legitdev.speedometer.app.domain.repository.SettingsRepository

class SettingsRepositoryImpl(context: Context) : SettingsRepository {
    private val prefs = context.getSharedPreferences("speedometer_settings", Context.MODE_PRIVATE)

    override fun getSpeedUnit(): SpeedUnit {
        val saved = prefs.getString("speed_unit", SpeedUnit.KMH.name) ?: SpeedUnit.KMH.name
        return try {
            SpeedUnit.valueOf(saved)
        } catch (e: Exception) {
            SpeedUnit.KMH
        }
    }

    override fun setSpeedUnit(unit: SpeedUnit) {
        prefs.edit().putString("speed_unit", unit.name).apply()
    }
}
