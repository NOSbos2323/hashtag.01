package com.example

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings

object FirebaseHelper {
    private const val APP_ID = "1:191966236404:android:911e3608b3df4938f8d8a1"
    private const val API_KEY = "AIzaSyDXtfuLHnCnddZJ-9udKJU3dgsGqp_8sTo"
    private const val PROJECT_ID = "mommo-7b717"
    private const val STORAGE_BUCKET = "mommo-7b717.firebasestorage.app"

    @Volatile
    private var isInitialized = false

    fun init(context: Context) {
        if (isInitialized) return
        synchronized(this) {
            if (isInitialized) return
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

                // Enable Firestore local offline persistence
                val db = FirebaseFirestore.getInstance()
                val settings = FirebaseFirestoreSettings.Builder()
                    .setLocalCacheSettings(PersistentCacheSettings.newBuilder().build())
                    .build()
                db.firestoreSettings = settings
                
                isInitialized = true
                Log.d("FirebaseHelper", "Firestore offline cache enabled")
            } catch (e: Throwable) {
                Log.e("FirebaseHelper", "Error initializing Firebase/Firestore", e)
            }
        }
    }

    fun getFirestore(context: Context): FirebaseFirestore {
        init(context)
        return FirebaseFirestore.getInstance()
    }
}

