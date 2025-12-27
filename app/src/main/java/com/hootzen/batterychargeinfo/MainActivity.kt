package com.hootzen.batterychargeinfo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.hootzen.batterychargeinfo.databinding.ActivityMainBinding
import com.hootzen.batterychargeinfo.model.BatteryHealth
import com.hootzen.batterychargeinfo.model.BatteryInfo
import com.hootzen.batterychargeinfo.model.ChargingStatus
import com.hootzen.batterychargeinfo.ui.BatteryViewModel
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val viewModel: BatteryViewModel by viewModels()
    private val handler = Handler(Looper.getMainLooper())
    private val updateInterval = 1000L

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.let { viewModel.updateBatteryInfo(it) }
        }
    }

    private val updateRunnable = object : Runnable {
        override fun run() {
            val intent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            intent?.let { viewModel.updateBatteryInfo(it) }
            handler.postDelayed(this, updateInterval)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWindowInsets()
        setupDeviceInfo()
        setupButtons()
        setupBatteryReceiver()
        observeBatteryInfo()

        handler.post(updateRunnable)
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = insets.top, bottom = insets.bottom)
            WindowInsetsCompat.CONSUMED
        }
    }

    private fun setupDeviceInfo() {
        val deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}"
        val androidVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
        binding.tvDeviceModel.text = getString(R.string.device_model_format, deviceModel, androidVersion)
    }

    private fun setupButtons() {
        binding.btnBatterySettings.setOnClickListener { openBatterySettings() }
        binding.btnReset.setOnClickListener {
            viewModel.reset()
            binding.tvTimeToFull.text = getString(R.string.reset)
        }
    }

    private fun setupBatteryReceiver() {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(batteryReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(batteryReceiver, filter)
        }
    }

    private fun observeBatteryInfo() {
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.batteryInfo.collect { info ->
                    info?.let { bindBatteryInfo(it) }
                }
            }
        }
    }

    private fun bindBatteryInfo(info: BatteryInfo) {
        binding.tvBatteryLevel.text = getString(R.string.battery_level_format, info.level)
        binding.chipChargingStatus.text = getStatusText(info)
        updateChipColor(info.isCharging)

        val voltageV = info.voltageMv / 1000.0
        binding.tvVoltage.text = getString(R.string.voltage_format, voltageV)
        binding.tvCurrent.text = getString(R.string.current_format, info.currentMa)
        binding.tvWattage.text = getString(R.string.wattage_format, info.wattage)

        binding.tvCurrentCapacity.text = getString(R.string.capacity_format, info.currentCapacityMah)
        binding.tvTotalCapacity.text = if (info.maxCapacityMah > 0) {
            getString(R.string.capacity_format, info.maxCapacityMah)
        } else {
            getString(R.string.status_calculating)
        }

        binding.tvTemperature.text = getString(R.string.temperature_format, info.temperatureC)
        binding.tvTechnology.text = info.technology
        binding.tvHealth.text = getHealthText(info.health)
        binding.tvTimeToFull.text = info.timeToFull ?: getString(R.string.status_dash)
    }

    private fun getStatusText(info: BatteryInfo): String {
        return when (info.chargingStatus) {
            ChargingStatus.Full -> getString(R.string.status_full)
            ChargingStatus.Wireless -> getString(R.string.status_wireless)
            ChargingStatus.AC -> getString(R.string.status_ac_charging)
            ChargingStatus.USB -> getString(R.string.status_usb_charging)
            ChargingStatus.Unknown -> getString(R.string.status_charging)
            ChargingStatus.NotCharging -> getString(R.string.status_not_charging)
        }
    }

    private fun getHealthText(health: BatteryHealth): String {
        return when (health) {
            BatteryHealth.Good -> getString(R.string.health_good)
            BatteryHealth.Overheat -> getString(R.string.health_overheat)
            BatteryHealth.Dead -> getString(R.string.health_dead)
            BatteryHealth.OverVoltage -> getString(R.string.health_over_voltage)
            BatteryHealth.Failure -> getString(R.string.health_failure)
            BatteryHealth.Cold -> getString(R.string.health_cold)
            BatteryHealth.Unknown -> getString(R.string.health_unknown)
        }
    }

    private fun updateChipColor(isCharging: Boolean) {
        val chipBgColor = if (isCharging) {
            ContextCompat.getColorStateList(this, R.color.charging_chip_bg)
        } else null
        chipBgColor?.let { binding.chipChargingStatus.chipBackgroundColor = it }
    }

    private fun openBatterySettings() {
        try {
            startActivity(Intent(Intent.ACTION_POWER_USAGE_SUMMARY))
        } catch (_: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS))
            } catch (_: Exception) {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            }
        }
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(updateRunnable)
    }

    override fun onResume() {
        super.onResume()
        handler.post(updateRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(updateRunnable)
        unregisterReceiver(batteryReceiver)
    }
}
