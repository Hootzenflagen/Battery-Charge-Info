package com.example.amped

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
import com.example.amped.databinding.ActivityMainBinding
import kotlin.math.abs

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val handler = Handler(Looper.getMainLooper())
    private val updateInterval = 1000L
    private val currentHistory = mutableListOf<Int>()
    private var lastTimeToFullUpdate = 0L
    private var maxBatteryCapacity = 0

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
        binding.tvDeviceModel.text = "$deviceModel • $androidVersion"

        // Battery settings button
        binding.btnBatterySettings.setOnClickListener {
            openBatterySettings()
        }

        // Reset button
        binding.btnReset.setOnClickListener {
            currentHistory.clear()
            lastTimeToFullUpdate = 0L
            binding.tvTimeToFull.text = "Reset"
        }

        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(batteryReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
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
        } catch (e: Exception) {
            try {
                // Fallback to battery saver settings
                val intent = Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)
                startActivity(intent)
            } catch (e2: Exception) {
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
        binding.tvBatteryLevel.text = "$batteryPct"

        val statusText = when {
            status == BatteryManager.BATTERY_STATUS_FULL -> "Full"
            isCharging -> when {
                wirelessCharge -> "⚡ Wireless"
                acCharge -> "⚡ AC Charging"
                usbCharge -> "⚡ USB Charging"
                else -> "⚡ Charging"
            }
            else -> "Not Charging"
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
        binding.tvVoltage.text = String.format("%.2f V", voltageV)

        // Show signed current
        binding.tvCurrent.text = "$displayCurrent mA"

        val wattage = voltageV * abs(currentMa) / 1000.0
        binding.tvWattage.text = String.format("%.1f W", wattage)

        binding.tvCurrentCapacity.text = "$currentCapacity mAh"
        binding.tvTotalCapacity.text = if (maxBatteryCapacity > 0) {
            "$maxBatteryCapacity mAh"
        } else {
            "Calculating..."
        }

        binding.tvTemperature.text = String.format("%.1f °C", temperature)
        binding.tvTechnology.text = technology

        val healthText = when (health) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over Voltage"
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Failure"
            BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
            else -> "Unknown"
        }
        binding.tvHealth.text = healthText

        // Time to full calculation
        if (isCharging) {
            val currentTime = System.currentTimeMillis()
            val absCurrent = abs(currentMa)

            currentHistory.add(absCurrent)
            if (currentHistory.size > 10) {
                currentHistory.removeAt(0)
            }

            if (batteryPct >= 100) {
                binding.tvTimeToFull.text = "Full"
            } else if (currentTime - lastTimeToFullUpdate >= 5000 || lastTimeToFullUpdate == 0L) {
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
                    binding.tvTimeToFull.text = if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
                    lastTimeToFullUpdate = currentTime
                } else {
                    binding.tvTimeToFull.text = "Low current"
                }
            }
        } else {
            binding.tvTimeToFull.text = "--"
            currentHistory.clear()
            lastTimeToFullUpdate = 0L
        }
    }
}