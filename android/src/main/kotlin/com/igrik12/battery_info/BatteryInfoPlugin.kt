package com.igrik12.battery_info

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import androidx.annotation.NonNull
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.EventChannel.EventSink
import io.flutter.plugin.common.EventChannel.StreamHandler
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result

/** BatteryInfoPlugin */
public class BatteryInfoPlugin : FlutterPlugin, MethodCallHandler, StreamHandler {
    private var applicationContext: Context? = null
    private var channel: MethodChannel? = null
    private var streamChannel: EventChannel? = null
    private lateinit var filter: IntentFilter
    private lateinit var batteryManager: BatteryManager
    private var chargingStateChangeReceiver: BroadcastReceiver? = null

    override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        onAttachedToEngine(flutterPluginBinding.applicationContext, flutterPluginBinding.binaryMessenger)
    }

    private fun onAttachedToEngine(applicationContext: Context, messenger: BinaryMessenger) {
        this.applicationContext = applicationContext
        channel = MethodChannel(messenger, "com.igrik12.battery_info/channel")
        streamChannel = EventChannel(messenger, "com.igrik12.battery_info/stream")
        channel?.setMethodCallHandler(this)
        streamChannel?.setStreamHandler(this)
        filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        batteryManager = applicationContext?.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    }

    override fun onMethodCall(@NonNull call: MethodCall, @NonNull result: Result) {
        when (call.method) {
            "getBatteryInfo" -> result.success(getBatteryCall())
            else -> {
                result.notImplemented()
            }
        }
    }

    override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
        channel?.setMethodCallHandler(null)
        streamChannel?.setStreamHandler(null)
        channel = null;
        streamChannel = null;
        if(chargingStateChangeReceiver != null) {
            applicationContext?.unregisterReceiver(chargingStateChangeReceiver);
            applicationContext = null;
            chargingStateChangeReceiver = null;
        }
    }

    /** Gets battery information*/
    private fun getBatteryInfo(intent: Intent): Map<String, Any?> {
        val chargingStatus = getChargingStatus(intent)
        val voltageMv = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)
        val health = getBatteryHealth(intent)
        val pluggedStatus = getBatteryPluggedStatus(intent)

        var batteryLevelPct = -1
        var capacityMicroAh = -1
        var currentAverageRaw = Int.MIN_VALUE
        var currentNowRaw = Int.MIN_VALUE
        val present = intent.extras?.getBoolean(BatteryManager.EXTRA_PRESENT)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 0)
        val technology = intent.extras?.getString(BatteryManager.EXTRA_TECHNOLOGY)
        var remainingEnergyNWh: Long? = null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            batteryLevelPct = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            capacityMicroAh = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
            currentAverageRaw = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE)
            currentNowRaw = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)

            val e = batteryManager.getLongProperty(BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER)
            // Some devices return Long.MIN_VALUE when unsupported
            if (e != java.lang.Long.MIN_VALUE) remainingEnergyNWh = e
        }

        // ---- Normalize units ----
        // Capacity: µAh -> mAh (Int, rounded)
        val capacityMah: Int? = if (capacityMicroAh >= 0) {
            kotlin.math.round(capacityMicroAh / 1000.0).toInt()
        } else null

        // Heuristic to decide if current is µA or mA, then return mA (Int, rounded)
        fun normalizeCurrentToMilliAmps(raw: Int, capMah: Int?): Int? {
            if (raw == Int.MIN_VALUE) return null
            val absRaw = kotlin.math.abs(raw)
            val looksLikeMicroAmps = absRaw > 10_000 || (capMah != null && absRaw > capMah * 10)
            val asMilli = if (looksLikeMicroAmps) raw / 1000.0 else raw.toDouble()
            return kotlin.math.round(asMilli).toInt()
        }

        val currentNowMilliA = normalizeCurrentToMilliAmps(currentNowRaw, capacityMah)
        val currentAverageMilliA = normalizeCurrentToMilliAmps(currentAverageRaw, capacityMah)

        val chargeTimeRemainingMs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            batteryManager.computeChargeTimeRemaining()
        } else {
            -1
        }

        // Temperature: tenths of °C -> °C (Double)
        val temperatureC = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10.0

        return mapOf(
            "batteryLevel" to batteryLevelPct,                // %
            "batteryCapacity" to capacityMah,                 // mAh (Int?)
            "chargeTimeRemaining" to chargeTimeRemainingMs,   // ms
            "chargingStatus" to chargingStatus,
            "currentAverage" to currentAverageMilliA,         // mA (Int?)
            "currentNow" to currentNowMilliA,                 // mA (Int?)
            "currentUnit" to "mA",                            // hint for clients
            "health" to health,
            "present" to present,
            "pluggedStatus" to pluggedStatus,
            "remainingEnergy" to remainingEnergyNWh,          // nWh (Long?)
            "scale" to scale,
            "technology" to technology,
            "temperature" to temperatureC,                    // °C (Double)
            "voltage" to voltageMv                            // mV (Int)
        )
    }

    /** Gets the current charging state of the device */
    private fun getChargingStatus(intent: Intent): String {
        return when (intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "charging"
            BatteryManager.BATTERY_STATUS_FULL -> "full"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "discharging"
            else -> {
                "unknown"
            }
        }
    }

    /** Gets the battery health */
    private fun getBatteryHealth(intent: Intent): String {
        return when (intent.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "health_good"
            BatteryManager.BATTERY_HEALTH_DEAD -> "dead"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "over_heat"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "over_voltage"
            BatteryManager.BATTERY_HEALTH_COLD -> "cold"
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "unspecified_failure"
            else -> {
                "unknown"
            }
        }
    }

    /** Gets the battery plugged status */
    private fun getBatteryPluggedStatus(intent: Intent): String {
        return when (intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)) {
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_AC -> "AC"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "wireless"
            else -> {
                "unknown"
            }
        }
    }

    /**This call acts as a MethodChannel handler to retrieve battery information*/
    private fun getBatteryCall(): Map<String, Any?> {
        val intent: Intent? = applicationContext?.registerReceiver(null, filter)
        return intent?.let { getBatteryInfo(it) }!!;
    }

    override fun onListen(arguments: Any?, events: EventSink?) {
        chargingStateChangeReceiver = createChargingStateChangeReceiver(events);
        applicationContext?.registerReceiver(
                chargingStateChangeReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED));
    }

    override fun onCancel(arguments: Any?) {
        applicationContext!!.unregisterReceiver(chargingStateChangeReceiver);
        chargingStateChangeReceiver = null;
    }

    /** Creates broadcast receiver object that provides battery information upon subscription to the stream */
    private fun createChargingStateChangeReceiver(events: EventSink?): BroadcastReceiver {
        return object : BroadcastReceiver() {
            override fun onReceive(contxt: Context?, intent: Intent?) {
                events?.success(intent?.let { getBatteryInfo(it) })
            }
        }
    }
}
