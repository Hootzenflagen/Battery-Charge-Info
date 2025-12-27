package com.hootzen.batterychargeinfo.ui

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import com.hootzen.batterychargeinfo.data.BatteryRepository
import com.hootzen.batterychargeinfo.model.BatteryInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class BatteryViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = BatteryRepository(application)

    private val _batteryInfo = MutableStateFlow<BatteryInfo?>(null)
    val batteryInfo: StateFlow<BatteryInfo?> = _batteryInfo.asStateFlow()

    fun updateBatteryInfo(intent: Intent) {
        _batteryInfo.value = repository.getBatteryInfo(intent)
    }

    fun reset() {
        repository.reset()
        _batteryInfo.value = _batteryInfo.value?.copy(timeToFull = null)
    }
}
