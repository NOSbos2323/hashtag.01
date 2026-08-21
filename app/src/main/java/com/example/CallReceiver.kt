package com.example

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore

class CallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        try {
            if (intent.action == TelephonyManager.ACTION_PHONE_STATE_CHANGED) {
                val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
                
                if (state == TelephonyManager.EXTRA_STATE_RINGING) {
                    val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER) ?: "رقم غير معروف"
                    
                    val prefs = context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
                    val userName = prefs.getString("USER_NAME", "unknown") ?: "unknown"

                    val db = FirebaseFirestore.getInstance()
                    val callData = hashMapOf(
                        "type" to "INCOMING_CALL",
                        "number" to incomingNumber,
                        "timestamp" to System.currentTimeMillis()
                    )
                    
                    db.collection("calls").document(userName)
                        .collection("logs").add(callData)
                        .addOnSuccessListener {
                            Log.d("CallReceiver", "Call saved to Firebase successfully")
                        }
                        .addOnFailureListener { e ->
                            Log.e("CallReceiver", "Error saving call to Firebase", e)
                        }
                }
            }
        } catch (e: Throwable) {
            Log.e("CallReceiver", "Safe catch: error receiving call broadcast", e)
        }
    }
}

