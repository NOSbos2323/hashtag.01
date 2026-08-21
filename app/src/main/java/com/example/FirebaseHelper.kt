package com.example

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.firestore.FirebaseFirestore

object FirebaseHelper {
    private const val APP_ID = "1:191966236404:android:911e3608b3df4938f8d8a1"
    private const val API_KEY = "AIzaSyDXtfuLHnCnddZJ-9udKJU3dgsGqp_8sTo"
    private const val PROJECT_ID = "mommo-7b717"
    private const val STORAGE_BUCKET = "mommo-7b717.firebasestorage.app"

    fun init(context: Context) {
        try {
            if (FirebaseApp.getApps(context).isEmpty()) {
                val options = FirebaseOptions.Builder()
                    .setApplicationId(APP_ID)
                    .setApiKey(API_KEY)
                    .setProjectId(PROJECT_ID)
                    .setStorageBucket(STORAGE_BUCKET)
                    .build()
                FirebaseApp.initializeApp(context.applicationContext, options)
                Log.d("FirebaseHelper", "Firebase initialized with explicit options")
            }
        } catch (e: Throwable) {
            Log.e("FirebaseHelper", "Error initializing Firebase", e)
        }
    }

    fun getFirestore(context: Context): FirebaseFirestore {
        init(context)
        return FirebaseFirestore.getInstance()
    }
}
