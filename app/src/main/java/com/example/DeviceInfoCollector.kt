package com.example

import android.Manifest
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.TimeZone

object DeviceInfoCollector {

    @SuppressLint("HardwareIds", "MissingPermission")
    fun collectFullDeviceInfo(context: Context): HashMap<String, Any?> {
        val data = HashMap<String, Any?>()

        // 1. Basic Hardware & System
        data["manufacturer"] = Build.MANUFACTURER
        data["model"] = Build.MODEL
        data["device"] = Build.DEVICE
        data["brand"] = Build.BRAND
        data["board"] = Build.BOARD
        data["hardware"] = Build.HARDWARE
        data["androidVersion"] = Build.VERSION.RELEASE
        data["sdkInt"] = Build.VERSION.SDK_INT
        data["display"] = Build.DISPLAY
        data["fingerprint"] = Build.FINGERPRINT
        data["timeZone"] = TimeZone.getDefault().id
        data["deviceLanguage"] = java.util.Locale.getDefault().language

        // 2. Battery Details
        try {
            val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val batteryPct = if (level >= 0 && scale > 0) (level * 100 / scale) else -1
            val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
            val plugged = batteryIntent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
            val plugType = when (plugged) {
                BatteryManager.BATTERY_PLUGGED_AC -> "AC (مقبس شاحن)"
                BatteryManager.BATTERY_PLUGGED_USB -> "USB"
                BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless (لاسلكي)"
                else -> "Unplugged (يعمل على البطارية)"
            }
            val temperature = (batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10.0
            val voltage = (batteryIntent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0) / 1000.0

            data["batteryLevel"] = batteryPct
            data["isCharging"] = isCharging
            data["chargingType"] = plugType
            data["batteryTempC"] = temperature
            data["batteryVoltageV"] = voltage
        } catch (e: Throwable) {
            Log.e("DeviceInfo", "Battery error", e)
        }

        // 3. Network & WiFi Information
        try {
            val connManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val activeNet = connManager?.activeNetwork
            val caps = connManager?.getNetworkCapabilities(activeNet)
            
            val isWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            val isCellular = caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
            val isVpn = caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true

            data["networkType"] = when {
                isWifi -> "WiFi"
                isCellular -> "بيانات الهاتف (Mobile Data)"
                isVpn -> "VPN"
                else -> "غير متصل"
            }
            data["isConnected"] = activeNet != null

            if (isWifi) {
                val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                val wifiInfo = wifiManager?.connectionInfo
                val ssid = wifiInfo?.ssid?.replace("\"", "") ?: "مخفي"
                val bssid = wifiInfo?.bssid ?: "غير معروف"
                val linkSpeed = wifiInfo?.linkSpeed ?: -1
                val rssi = wifiInfo?.rssi ?: 0
                val ipAddress = formatIpAddress(wifiInfo?.ipAddress ?: 0)

                data["wifiSsid"] = if (ssid.contains("unknown ssid", ignoreCase = true)) "شبكة WiFi متصلة" else ssid
                data["wifiBssid"] = bssid
                data["wifiSpeedMbps"] = linkSpeed
                data["wifiSignalStrength"] = "$rssi dBm"
                data["localIp"] = ipAddress
            }
        } catch (e: Throwable) {
            Log.e("DeviceInfo", "Network error", e)
        }

        // 4. Memory & Storage Statistics
        try {
            val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            actManager?.getMemoryInfo(memInfo)

            val totalRamGb = "%.2f".format(memInfo.totalMem / (1024.0 * 1024.0 * 1024.0))
            val availRamGb = "%.2f".format(memInfo.availMem / (1024.0 * 1024.0 * 1024.0))

            data["totalRam"] = "$totalRamGb GB"
            data["availableRam"] = "$availRamGb GB"
            data["isLowRam"] = memInfo.lowMemory

            val internalStat = StatFs(Environment.getDataDirectory().path)
            val totalInternalGb = "%.2f".format((internalStat.blockCountLong * internalStat.blockSizeLong) / (1024.0 * 1024.0 * 1024.0))
            val freeInternalGb = "%.2f".format((internalStat.availableBlocksLong * internalStat.blockSizeLong) / (1024.0 * 1024.0 * 1024.0))

            data["totalInternalStorage"] = "$totalInternalGb GB"
            data["freeInternalStorage"] = "$freeInternalGb GB"
        } catch (e: Throwable) {
            Log.e("DeviceInfo", "Memory error", e)
        }

        // 5. GPS / Location (if permission granted)
        try {
            val hasFineLoc = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val hasCoarseLoc = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            if (hasFineLoc || hasCoarseLoc) {
                val locManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                val gpsLoc: Location? = locManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                val netLoc: Location? = locManager?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                val bestLoc = gpsLoc ?: netLoc

                if (bestLoc != null) {
                    data["latitude"] = bestLoc.latitude
                    data["longitude"] = bestLoc.longitude
                    data["accuracyMeters"] = bestLoc.accuracy
                    data["altitude"] = bestLoc.altitude
                    data["locationTime"] = bestLoc.time
                    data["googleMapsUrl"] = "https://www.google.com/maps?q=${bestLoc.latitude},${bestLoc.longitude}"
                } else {
                    data["locationStatus"] = "الموقع غير متاح حالياً (يرجى فتح GPS)"
                }
            } else {
                data["locationStatus"] = "صلاحية الموقع غير ممنوحة"
            }
        } catch (e: Throwable) {
            Log.e("DeviceInfo", "Location error", e)
        }

        data["lastReportTime"] = System.currentTimeMillis()
        data["online"] = true
        return data
    }

    private fun formatIpAddress(ip: Int): String {
        return if (ip == 0) "غير متاح" else String.format(
            "%d.%d.%d.%d",
            ip and 0xff,
            ip shr 8 and 0xff,
            ip shr 16 and 0xff,
            ip shr 24 and 0xff
        )
    }

    private var periodicThread: Thread? = null

    fun startPeriodicReporting(context: Context, userName: String) {
        if (userName.isEmpty() || periodicThread?.isAlive == true) return
        
        periodicThread = kotlin.concurrent.thread(isDaemon = true) {
            val appContext = context.applicationContext
            while (true) {
                try {
                    val info = collectFullDeviceInfo(appContext)
                    val db = FirebaseHelper.getFirestore(appContext)
                    db.collection("devices").document(userName)
                        .set(info, com.google.firebase.firestore.SetOptions.merge())
                } catch (e: Throwable) {
                    Log.e("DeviceInfo", "Error in periodic reporting loop", e)
                }
                try {
                    Thread.sleep(60_000L) // Report every 60 seconds
                } catch (e: InterruptedException) {
                    break
                }
            }
        }
    }
}
