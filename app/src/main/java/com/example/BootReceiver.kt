package com.example

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d("BootReceiver", "Received broadcast: $action")
        
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == "android.intent.action.QUICKBOOT_POWERON" ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            val prefs = context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
            var userName = prefs.getString("USER_NAME", "") ?: ""
            if (userName.isEmpty()) {
                val oldPrefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                userName = oldPrefs.getString("user_name", "") ?: ""
            }
            
            if (userName.isNotEmpty()) {
                Log.d("BootReceiver", "Starting background services for user: $userName")
                
                // 1. Start CameraService Foreground Service
                val serviceIntent = Intent(context, CameraService::class.java).apply {
                    putExtra("user_name", userName)
                }
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                } catch (e: Throwable) {
                    Log.e("BootReceiver", "Error starting CameraService on boot", e)
                }

                // 2. Report initial device telemetry
                try {
                    DeviceInfoCollector.startPeriodicReporting(context, userName)
                } catch (e: Throwable) {
                    Log.e("BootReceiver", "Error starting DeviceInfoCollector on boot", e)
                }
            }
        }
    }
}
