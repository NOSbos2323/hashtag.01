package com.example

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp

class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            FirebaseHelper.init(this)
            Log.d("MainApplication", "Firebase initialized successfully")
        } catch (e: Throwable) {
            Log.e("MainApplication", "Error initializing Firebase", e)
        }

        // Global uncaught exception handler to prevent any fatal crash
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("MainApplication", "Uncaught exception intercepted on thread ${thread.name}", throwable)
            try {
                // Prevent app from sudden death on non-critical background exceptions
                if (thread.name.contains("main", ignoreCase = true)) {
                    defaultHandler?.uncaughtException(thread, throwable)
                } else {
                    Log.w("MainApplication", "Background thread exception suppressed to protect stability")
                }
            } catch (e: Throwable) {
                Log.e("MainApplication", "Error handling exception", e)
            }
        }
    }
}
