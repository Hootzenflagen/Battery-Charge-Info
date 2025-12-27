package com.hootzen.batterychargeinfo.model

sealed class ChargingStatus {
    data object NotCharging : ChargingStatus()
    data object USB : ChargingStatus()
    data object AC : ChargingStatus()
    data object Wireless : ChargingStatus()
    data object Full : ChargingStatus()
    data object Unknown : ChargingStatus()
}

enum class BatteryHealth {
    Good, Overheat, Dead, OverVoltage, Cold, Failure, Unknown
}
