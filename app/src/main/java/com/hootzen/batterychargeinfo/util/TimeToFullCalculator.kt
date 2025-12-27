package com.hootzen.batterychargeinfo.util

import kotlin.math.abs

class TimeToFullCalculator {
    private val currentHistory = mutableListOf<Int>()
    private var chargingStartTime = 0L
    private var lastUpdateTime = 0L
    private var cachedResult: TimeToFullResult? = null

    companion object {
        private const val HISTORY_SIZE = 10
        private const val UPDATE_INTERVAL_MS = 5000L
        private const val STABILIZATION_DELAY_MS = 5000L
        private const val MIN_CURRENT_THRESHOLD = 10
        private const val DEFAULT_CAPACITY_MAH = 4000
    }

    sealed class TimeToFullResult {
        data class Calculated(val hours: Int, val minutes: Int) : TimeToFullResult()
        data object Full : TimeToFullResult()
        data object LowCurrent : TimeToFullResult()
        data object Calculating : TimeToFullResult()
        data object NotCharging : TimeToFullResult()
    }

    fun calculate(
        currentMa: Int,
        batteryPct: Int,
        maxCapacityMah: Int,
        isCharging: Boolean
    ): TimeToFullResult {
        if (!isCharging) {
            reset()
            return TimeToFullResult.NotCharging
        }

        val currentTime = System.currentTimeMillis()
        val absCurrent = abs(currentMa)

        if (chargingStartTime == 0L) {
            chargingStartTime = currentTime
        }

        val timeSinceStart = currentTime - chargingStartTime

        currentHistory.add(absCurrent)
        if (currentHistory.size > HISTORY_SIZE) {
            currentHistory.removeAt(0)
        }

        if (batteryPct >= 100) {
            cachedResult = null
            return TimeToFullResult.Full
        }

        if (currentTime - lastUpdateTime >= UPDATE_INTERVAL_MS || lastUpdateTime == 0L) {
            cachedResult = calculateTimeToFull(batteryPct, maxCapacityMah, absCurrent)
            lastUpdateTime = currentTime
        }

        return when {
            timeSinceStart < STABILIZATION_DELAY_MS -> TimeToFullResult.Calculating
            cachedResult != null -> cachedResult!!
            else -> TimeToFullResult.Calculating
        }
    }

    private fun calculateTimeToFull(
        batteryPct: Int,
        maxCapacityMah: Int,
        absCurrent: Int
    ): TimeToFullResult {
        val remainingPercent = 100 - batteryPct
        val capacity = if (maxCapacityMah > 0) maxCapacityMah else DEFAULT_CAPACITY_MAH
        val remainingCapacity = remainingPercent * capacity / 100.0

        val avgCurrent = if (currentHistory.isNotEmpty()) {
            currentHistory.average().toInt()
        } else {
            absCurrent
        }

        return if (avgCurrent > MIN_CURRENT_THRESHOLD) {
            val timeToFullHours = remainingCapacity / avgCurrent
            val hours = timeToFullHours.toInt()
            val minutes = ((timeToFullHours - hours) * 60).toInt()
            TimeToFullResult.Calculated(hours, minutes)
        } else {
            TimeToFullResult.LowCurrent
        }
    }

    fun reset() {
        currentHistory.clear()
        chargingStartTime = 0L
        lastUpdateTime = 0L
        cachedResult = null
    }
}
