package com.hootzen.batterychargeinfo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.hootzen.batterychargeinfo.databinding.ActivityMainBinding
import java.util.Locale
import kotlin.math.abs

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val handler = Handler(Looper.getMainLooper())
    private val updateInterval = 1000L
    private val currentHistory = mutableListOf<Int>()
    private var lastTimeToFullUpdate = 0L
    private var maxBatteryCapacity = 0
    private var chargingStartTime = 0L
    private var calculatedTimeToFull: String? = null

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.let { updateBatteryInfo(it) }
        }
    }

    private val updateRunnable = object : Runnable {
        override fun run() {
            val intent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            intent?.let { updateBatteryInfo(it) }
            handler.postDelayed(this, updateInterval)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = insets.top, bottom = insets.bottom)
            WindowInsetsCompat.CONSUMED
        }

        val deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}"
        val androidVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
        binding.tvDeviceModel.text = getString(R.string.device_model_format, deviceModel, androidVersion)

        // Battery settings button
        binding.btnBatterySettings.setOnClickListener {
            openBatterySettings()
        }

        // Reset button
        binding.btnReset.setOnClickListener {
            currentHistory.clear()
            lastTimeToFullUpdate = 0L
            chargingStartTime = 0L
            calculatedTimeToFull = null
            binding.tvTimeToFull.text = getString(R.string.reset)
        }

        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(batteryReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(batteryReceiver, filter)
        }

        handler.post(updateRunnable)
    }

    private fun openBatterySettings() {
        try {
            // Try the battery usage summary first (most common)
            val intent = Intent(Intent.ACTION_POWER_USAGE_SUMMARY)
            startActivity(intent)
        } catch (_: Exception) {
            try {
                // Fallback to battery saver settings
                val intent = Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)
                startActivity(intent)
            } catch (_: Exception) {
                // Final fallback to general settings
                val intent = Intent(Settings.ACTION_SETTINGS)
                startActivity(intent)
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

    private fun updateBatteryInfo(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val batteryPct = (level / scale.toFloat() * 100).toInt()

        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        val chargePlug = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
        val usbCharge = chargePlug == BatteryManager.BATTERY_PLUGGED_USB
        val acCharge = chargePlug == BatteryManager.BATTERY_PLUGGED_AC
        val wirelessCharge = chargePlug == BatteryManager.BATTERY_PLUGGED_WIRELESS

        val voltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)
        val temperature = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) / 10.0
        val technology = intent.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: "Unknown"
        val health = intent.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)

        val bm = getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val currentMicroAmps = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) ?: 0

        // Show actual sign: positive when charging, negative when discharging
        val currentMa = currentMicroAmps / 1000
        val displayCurrent = if (isCharging) abs(currentMa) else -abs(currentMa)

        val currentCapacityMicroAh = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER) ?: 0
        val currentCapacity = currentCapacityMicroAh / 1000

        if (maxBatteryCapacity == 0 && currentCapacity > 0 && batteryPct in 20..100) {
            maxBatteryCapacity = (currentCapacity * 100.0 / batteryPct).toInt()
        }

        // Update hero section
        binding.tvBatteryLevel.text = getString(R.string.battery_level_format, batteryPct)

        val statusText = when {
            status == BatteryManager.BATTERY_STATUS_FULL -> getString(R.string.status_full)
            isCharging -> when {
                wirelessCharge -> getString(R.string.status_wireless)
                acCharge -> getString(R.string.status_ac_charging)
                usbCharge -> getString(R.string.status_usb_charging)
                else -> getString(R.string.status_charging)
            }
            else -> getString(R.string.status_not_charging)
        }
        binding.chipChargingStatus.text = statusText

        // Update chip color based on charging state
        val chipBgColor = if (isCharging) {
            ContextCompat.getColorStateList(this, R.color.charging_chip_bg)
        } else {
            null
        }
        chipBgColor?.let { binding.chipChargingStatus.chipBackgroundColor = it }

        val voltageV = voltage / 1000.0
        binding.tvVoltage.text = getString(R.string.voltage_format, voltageV)

        // Show signed current
        binding.tvCurrent.text = getString(R.string.current_format, displayCurrent)

        val wattage = voltageV * abs(currentMa) / 1000.0
        binding.tvWattage.text = getString(R.string.wattage_format, wattage)

        binding.tvCurrentCapacity.text = getString(R.string.capacity_format, currentCapacity)
        binding.tvTotalCapacity.text = if (maxBatteryCapacity > 0) {
            getString(R.string.capacity_format, maxBatteryCapacity)
        } else {
            getString(R.string.status_calculating)
        }

        binding.tvTemperature.text = getString(R.string.temperature_format, temperature)
        binding.tvTechnology.text = technology

        val healthText = when (health) {
            BatteryManager.BATTERY_HEALTH_GOOD -> getString(R.string.health_good)
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> getString(R.string.health_overheat)
            BatteryManager.BATTERY_HEALTH_DEAD -> getString(R.string.health_dead)
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> getString(R.string.health_over_voltage)
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> getString(R.string.health_failure)
            BatteryManager.BATTERY_HEALTH_COLD -> getString(R.string.health_cold)
            else -> getString(R.string.health_unknown)
        }
        binding.tvHealth.text = healthText

        // Time to full calculation
        if (isCharging) {
            val currentTime = System.currentTimeMillis()
            val absCurrent = abs(currentMa)

            // Initialize charging start time
            if (chargingStartTime == 0L) {
                chargingStartTime = currentTime
            }

            val timeSinceChargingStart = currentTime - chargingStartTime

            currentHistory.add(absCurrent)
            if (currentHistory.size > 10) {
                currentHistory.removeAt(0)
            }

            if (batteryPct >= 100) {
                binding.tvTimeToFull.text = getString(R.string.status_full)
                calculatedTimeToFull = null
            } else {
                // Calculate the time to full value
                if (currentTime - lastTimeToFullUpdate >= 5000 || lastTimeToFullUpdate == 0L) {
                    val remainingPercent = 100 - batteryPct
                    val remainingCapacity = if (maxBatteryCapacity > 0) {
                        (remainingPercent * maxBatteryCapacity / 100.0)
                    } else {
                        (remainingPercent * 4000 / 100.0)
                    }

                    val avgCurrent = if (currentHistory.isNotEmpty()) {
                        currentHistory.average().toInt()
                    } else {
                        absCurrent
                    }

                    if (avgCurrent > 10) {
                        val timeToFullHours = remainingCapacity / avgCurrent
                        val hours = timeToFullHours.toInt()
                        val minutes = ((timeToFullHours - hours) * 60).toInt()
                        calculatedTimeToFull = if (hours > 0) {
                            getString(R.string.time_hours_minutes, hours, minutes)
                        } else {
                            getString(R.string.time_minutes, minutes)
                        }
                        lastTimeToFullUpdate = currentTime
                    } else {
                        calculatedTimeToFull = getString(R.string.status_low_current)
                    }
                }

                // Display logic with minimum 5 second delay for stable readings
                binding.tvTimeToFull.text = when {
                    // Show "Calculating..." for at least 5 seconds to get stable reading
                    timeSinceChargingStart < 5000 -> getString(R.string.status_calculating)
                    // After 5 seconds, show calculated value if available
                    calculatedTimeToFull != null -> calculatedTimeToFull
                    // If still no value after 5 seconds, keep showing calculating
                    else -> getString(R.string.status_calculating)
                }
            }
        } else {
            binding.tvTimeToFull.text = getString(R.string.status_dash)
            currentHistory.clear()
            lastTimeToFullUpdate = 0L
            chargingStartTime = 0L
            calculatedTimeToFull = null
        }
    }
}