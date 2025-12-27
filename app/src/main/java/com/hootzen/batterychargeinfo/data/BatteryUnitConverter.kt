package com.hootzen.batterychargeinfo.data

import kotlin.math.abs

object BatteryUnitConverter {
    private const val MAX_REALISTIC_WATTAGE = 100.0
    private const val FALLBACK_CURRENT_THRESHOLD = 15000
    private const val CAPACITY_THRESHOLD = 10000

    /**
     * Normalizes raw current value to milliamps.
     * Android standard is µA, but some OEMs (Samsung) report mA.
     * Uses wattage validation: if >100W, value must be in µA.
     */
    fun normalizeCurrentToMa(rawValue: Int, voltageMv: Int): Int {
        if (rawValue == 0) return 0
        val voltageV = voltageMv / 1000.0
        return if (voltageV > 0) {
            val testWattage = voltageV * abs(rawValue) / 1000.0
            if (testWattage > MAX_REALISTIC_WATTAGE) rawValue / 1000 else rawValue
        } else {
            if (abs(rawValue) > FALLBACK_CURRENT_THRESHOLD) rawValue / 1000 else rawValue
        }
    }

    /**
     * Normalizes raw capacity value to milliamp-hours.
     * Values > 10000 are likely in µAh.
     */
    fun normalizeCapacityToMah(rawValue: Int): Int {
        return if (abs(rawValue) > CAPACITY_THRESHOLD) rawValue / 1000 else rawValue
    }

    /**
     * Calculates wattage from voltage and current.
     */
    fun calculateWattage(voltageMv: Int, currentMa: Int): Double {
        return (voltageMv / 1000.0) * abs(currentMa) / 1000.0
    }

    /**
     * Normalizes current sign: positive when charging, negative when discharging.
     */
    fun normalizeCurrentSign(currentMa: Int, isCharging: Boolean): Int {
        return if (isCharging) abs(currentMa) else -abs(currentMa)
    }
}
