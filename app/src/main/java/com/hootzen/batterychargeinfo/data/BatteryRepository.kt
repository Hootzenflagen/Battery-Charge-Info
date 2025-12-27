package com.hootzen.batterychargeinfo.data

import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import com.hootzen.batterychargeinfo.R
import com.hootzen.batterychargeinfo.model.BatteryHealth
import com.hootzen.batterychargeinfo.model.BatteryInfo
import com.hootzen.batterychargeinfo.model.ChargingStatus
import com.hootzen.batterychargeinfo.util.TimeToFullCalculator

class BatteryRepository(private val context: Context) {
    private val dataSource = BatteryDataSource(context)
    private val calculator = TimeToFullCalculator()
    private var maxCapacityMah = 0

    fun getBatteryInfo(intent: Intent): BatteryInfo {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val batteryPct = (level / scale.toFloat() * 100).toInt()

        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        val chargingStatus = parseChargingStatus(intent, status, isCharging)
        val voltageMv = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)
        val temperatureC = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) / 10.0
        val technology = intent.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: "Unknown"
        val health = parseHealth(intent.getIntExtra(BatteryManager.EXTRA_HEALTH, -1))

        val currentRaw = dataSource.readCurrentRaw()
        val currentMa = BatteryUnitConverter.normalizeCurrentToMa(currentRaw, voltageMv)
        val displayCurrent = BatteryUnitConverter.normalizeCurrentSign(currentMa, isCharging)

        val capacityRaw = dataSource.readChargeCounterRaw()
        val currentCapacityMah = BatteryUnitConverter.normalizeCapacityToMah(capacityRaw)

        if (maxCapacityMah == 0 && currentCapacityMah > 0 && batteryPct in 20..100) {
            maxCapacityMah = (currentCapacityMah * 100.0 / batteryPct).toInt()
        }

        val wattage = BatteryUnitConverter.calculateWattage(voltageMv, currentMa)

        val timeResult = calculator.calculate(currentMa, batteryPct, maxCapacityMah, isCharging)
        val timeToFull = formatTimeToFull(timeResult)

        return BatteryInfo(
            level = batteryPct,
            isCharging = isCharging,
            chargingStatus = chargingStatus,
            currentMa = displayCurrent,
            voltageMv = voltageMv,
            temperatureC = temperatureC,
            health = health,
            technology = technology,
            currentCapacityMah = currentCapacityMah,
            maxCapacityMah = maxCapacityMah,
            wattage = wattage,
            timeToFull = timeToFull
        )
    }

    private fun parseChargingStatus(intent: Intent, status: Int, isCharging: Boolean): ChargingStatus {
        if (status == BatteryManager.BATTERY_STATUS_FULL) return ChargingStatus.Full
        if (!isCharging) return ChargingStatus.NotCharging

        return when (intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)) {
            BatteryManager.BATTERY_PLUGGED_USB -> ChargingStatus.USB
            BatteryManager.BATTERY_PLUGGED_AC -> ChargingStatus.AC
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> ChargingStatus.Wireless
            else -> ChargingStatus.Unknown
        }
    }

    private fun parseHealth(health: Int): BatteryHealth {
        return when (health) {
            BatteryManager.BATTERY_HEALTH_GOOD -> BatteryHealth.Good
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> BatteryHealth.Overheat
            BatteryManager.BATTERY_HEALTH_DEAD -> BatteryHealth.Dead
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> BatteryHealth.OverVoltage
            BatteryManager.BATTERY_HEALTH_COLD -> BatteryHealth.Cold
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> BatteryHealth.Failure
            else -> BatteryHealth.Unknown
        }
    }

    private fun formatTimeToFull(result: TimeToFullCalculator.TimeToFullResult): String? {
        return when (result) {
            is TimeToFullCalculator.TimeToFullResult.Calculated -> {
                if (result.hours > 0) {
                    context.getString(R.string.time_hours_minutes, result.hours, result.minutes)
                } else {
                    context.getString(R.string.time_minutes, result.minutes)
                }
            }
            TimeToFullCalculator.TimeToFullResult.Full -> context.getString(R.string.status_full)
            TimeToFullCalculator.TimeToFullResult.LowCurrent -> context.getString(R.string.status_low_current)
            TimeToFullCalculator.TimeToFullResult.Calculating -> context.getString(R.string.status_calculating)
            TimeToFullCalculator.TimeToFullResult.NotCharging -> null
        }
    }

    fun reset() {
        calculator.reset()
    }
}
