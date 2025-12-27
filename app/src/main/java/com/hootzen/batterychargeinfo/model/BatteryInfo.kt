package com.hootzen.batterychargeinfo.model

data class BatteryInfo(
    val level: Int,
    val isCharging: Boolean,
    val chargingStatus: ChargingStatus,
    val currentMa: Int,
    val voltageMv: Int,
    val temperatureC: Double,
    val health: BatteryHealth,
    val technology: String,
    val currentCapacityMah: Int,
    val maxCapacityMah: Int,
    val wattage: Double,
    val timeToFull: String?
)
