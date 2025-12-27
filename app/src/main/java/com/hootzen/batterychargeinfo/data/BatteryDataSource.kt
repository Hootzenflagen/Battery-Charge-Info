package com.hootzen.batterychargeinfo.data

import android.content.Context
import android.os.BatteryManager
import java.io.File

class BatteryDataSource(private val context: Context) {
    private val batteryManager: BatteryManager? by lazy {
        context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
    }

    companion object {
        private val CURRENT_SYSFS_PATHS = listOf(
            "/sys/class/power_supply/battery/current_now",
            "/sys/class/power_supply/battery/batt_current_ua_avg"
        )
        private const val CHARGE_COUNTER_SYSFS_PATH = "/sys/class/power_supply/battery/charge_counter"
    }

    fun readCurrentRaw(): Int {
        val bmValue = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) ?: 0
        return if (bmValue == 0 || bmValue == Int.MIN_VALUE) {
            readFromSysfs(CURRENT_SYSFS_PATHS) ?: 0
        } else bmValue
    }

    fun readChargeCounterRaw(): Int {
        val bmValue = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER) ?: 0
        return if (bmValue == 0 || bmValue == Int.MIN_VALUE) {
            readFromSysfs(CHARGE_COUNTER_SYSFS_PATH) ?: 0
        } else bmValue
    }

    private fun readFromSysfs(paths: List<String>): Int? {
        for (path in paths) {
            readFromSysfs(path)?.let { return it }
        }
        return null
    }

    private fun readFromSysfs(path: String): Int? {
        return try {
            val value = File(path).readText().trim().toIntOrNull()
            if (value != null && value != 0) value else null
        } catch (_: Exception) {
            null
        }
    }
}
