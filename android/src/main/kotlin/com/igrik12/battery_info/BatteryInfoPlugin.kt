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
        // ---- Helpers ----
        fun BatteryManager.intPropOrNull(id: Int): Int? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                getIntProperty(id).takeUnless { it == Int.MIN_VALUE }
            } else null

        fun BatteryManager.longPropOrNull(id: Int): Long? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                getLongProperty(id).takeUnless { it == Long.MIN_VALUE }
            } else null

        fun normalizeCurrentToMilliAmps(raw: Int?, capMah: Int?): Int? {
            raw ?: return null
            val absRaw = kotlin.math.abs(raw)
            val looksLikeMicroAmps = absRaw > 10_000 || (capMah != null && absRaw > capMah * 10)
            val asMilli = if (looksLikeMicroAmps) raw / 1000.0 else raw.toDouble()
            return kotlin.math.round(asMilli).toInt()
        }

        // ---- Basic intent/extras ----
        val chargingStatus = getChargingStatus(intent)
        val voltageMilliV = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)
        val health = getBatteryHealth(intent)
        val pluggedStatus = getBatteryPluggedStatus(intent)
        val present = intent.getBooleanExtra(BatteryManager.EXTRA_PRESENT, true)
        val tech = intent.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY)
        val maximumBatteryLevel = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val temperatureC = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10.0

        // ---- Properties (SDK >= 21) ----
        val pctFromProp: Int? = batteryManager.intPropOrNull(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            ?.takeIf { it in 0..100 }

        val chargeCounterMicroAh: Int? =
            batteryManager.intPropOrNull(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)

        val currentAverageRaw: Int? =
            batteryManager.intPropOrNull(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE)

        val currentNowRaw: Int? =
            batteryManager.intPropOrNull(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)

        val remainingEnergyNWh: Long? =
            batteryManager.longPropOrNull(BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER)

        val pctFromIntent: Int? =
            if (level >= 0 && maximumBatteryLevel > 0) kotlin.math.round(level * 100.0 / maximumBatteryLevel).toInt() else null

        val batteryLevelPercentage: Int = (pctFromProp ?: pctFromIntent) ?: -1

        // ---- Normalize units ----
        // Capacity: µAh -> mAh (Int, rounded)
        val remainingbatteryCapacityMilliAh: Int? = chargeCounterMicroAh?.let { kotlin.math.round(it / 1000.0).toInt() }

        // Currents to mA (negative = purkaus, positive = lataus)
        val currentNowMilliA: Int? = normalizeCurrentToMilliAmps(currentNowRaw, remainingbatteryCapacityMilliAh)
        val currentAverageMilliA: Int? = normalizeCurrentToMilliAmps(currentAverageRaw, remainingbatteryCapacityMilliAh)

        // Charge time remaining (ms) or -1 jos ei saatavilla
        val chargeTimeRemainingMs: Long? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                batteryManager.computeChargeTimeRemaining()
            } else null

        return mapOf(
            "batteryLevelPercentage" to batteryLevelPercentage,              // %
            "remainingbatteryCapacityMilliAh" to remainingbatteryCapacityMilliAh,               // mAh (Int?)
            "chargeTimeRemainingMs" to chargeTimeRemainingMs, // ms (Long?)
            "chargingStatus" to chargingStatus,
            "currentAverageMilliA" to currentAverageMilliA,       // mA (Int?)
            "currentNowMilliA" to currentNowMilliA,               // mA (Int?)
            "health" to health,
            "present" to present,
            "pluggedStatus" to pluggedStatus,
            "remainingEnergyNWh" to remainingEnergyNWh,        // nWh (Long?)
            "maximumBatteryLevel" to maximumBatteryLevel,
            "technology" to tech,
            "temperatureC" to temperatureC,                  // °C (Double)
            "voltageMilliV" to voltageMilliV                          // mV (Int)
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
