package com.example

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.util.Base64
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import kotlin.concurrent.thread

class CameraService : Service(), LifecycleOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry

    private val executor = Executors.newSingleThreadExecutor()
    private var lastFrameTime = 0L
    private var settingsListener: ListenerRegistration? = null
    private var cameraProvider: ProcessCameraProvider? = null

    private var audioRecord: AudioRecord? = null
    private var isRecordingAudio = false
    private var latestAudioBase64: String = ""

    override fun onCreate() {
        super.onCreate()
        try {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
            createNotificationChannel()
        } catch (e: Throwable) {
            Log.e("CameraService", "Error during service onCreate", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            try {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } catch (e: Exception) {
                Log.e("CameraService", "Error stopping foreground", e)
            }
            stopSelf()
            return START_NOT_STICKY
        }
        
        val userName = intent?.getStringExtra("USER_NAME") ?: "unknown"
        
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, "CameraServiceChannel")
            .setContentTitle("بث الكاميرا نشط")
            .setContentText("يتم إرسال البث في الخلفية...")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val serviceType = ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                startForeground(1, notification, serviceType)
            } else {
                startForeground(1, notification)
            }
        } catch (e: Throwable) {
            Log.e("CameraService", "Fallback: Starting foreground service without specific type", e)
            try {
                startForeground(1, notification)
            } catch (inner: Throwable) {
                Log.e("CameraService", "Critical: Could not start foreground service", inner)
            }
        }
        
        startAudioRecording()
        startStreaming(userName)

        return START_STICKY
    }

    private var currentCamera: androidx.camera.core.Camera? = null
    private var isTorchOn = false
    private var currentFacing = "back"
    private var compressionQuality = 35
    private var targetFpsMs = 400L

    private fun startStreaming(userName: String) {
        val db = FirebaseHelper.getFirestore(this)
        
        // Listen to controls and remote control commands directly on devices/{userName}
        settingsListener?.remove()
        settingsListener = db.collection("devices").document(userName)
            .addSnapshotListener { snapshot, e ->
                if (e != null || snapshot == null) return@addSnapshotListener
                
                val controls = snapshot.get("controls") as? Map<*, *>
                
                // 1. Camera facing command
                val facing = (controls?.get("cameraFacing") as? String)
                    ?: snapshot.getString("cameraFacing")
                    ?: "back"
                if (facing != currentFacing) {
                    currentFacing = facing
                    val cameraSelector = if (facing == "front") {
                        CameraSelector.DEFAULT_FRONT_CAMERA
                    } else {
                        CameraSelector.DEFAULT_BACK_CAMERA
                    }
                    bindCamera(cameraSelector, userName)
                }

                // 2. Torch command
                val torchRequested = (controls?.get("torch") as? Boolean)
                    ?: snapshot.getBoolean("torch")
                    ?: false
                if (torchRequested != isTorchOn) {
                    isTorchOn = torchRequested
                    try {
                        currentCamera?.cameraControl?.enableTorch(isTorchOn)
                    } catch (t: Throwable) {
                        Log.e("CameraService", "Torch error", t)
                    }
                }

                // 3. Quality & FPS commands
                val q = ((controls?.get("quality") as? Number)?.toInt())
                    ?: snapshot.getLong("quality")?.toInt()
                    ?: 35
                compressionQuality = q.coerceIn(10, 90)
                val fps = ((controls?.get("fps") as? Number)?.toLong())
                    ?: snapshot.getLong("fps")?.toLong()
                    ?: 2L
                targetFpsMs = (1000L / fps.coerceIn(1L, 10L)).coerceAtLeast(100L)

                // 4. Remote Snapshot command
                val takeSnapshot = (controls?.get("take_snapshot") as? Boolean)
                    ?: snapshot.getBoolean("take_snapshot")
                    ?: false
                if (takeSnapshot) {
                    db.collection("devices").document(userName).update(
                        "controls.take_snapshot", false,
                        "take_snapshot", false
                    )
                }
            }

        // Periodically report complete device telemetry (Heartbeat, WiFi, Battery, RAM, GPS)
        thread(start = true) {
            while (cameraProvider != null) {
                try {
                    val fullInfo = DeviceInfoCollector.collectFullDeviceInfo(this)
                    fullInfo["cameraFacing"] = currentFacing
                    fullInfo["isStreaming"] = true
                    fullInfo["torchOn"] = isTorchOn
                    fullInfo["lastSeen"] = System.currentTimeMillis()

                    db.collection("devices").document(userName).set(fullInfo)
                } catch (t: Throwable) {
                    Log.e("CameraService", "Error sending device telemetry", t)
                }
                try {
                    Thread.sleep(5000)
                } catch (_: InterruptedException) {
                    break
                }
            }
        }

        // Initial bind
        val initialSelector = if (currentFacing == "front") CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
        bindCamera(initialSelector, userName)
    }

    private fun bindCamera(cameraSelector: CameraSelector, userName: String) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                
                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    
                imageAnalysis.setAnalyzer(executor) { imageProxy ->
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastFrameTime > targetFpsMs) {
                        lastFrameTime = currentTime
                        try {
                            val bitmap = imageProxy.toBitmap()
                            val stream = ByteArrayOutputStream()
                            bitmap.compress(Bitmap.CompressFormat.JPEG, compressionQuality, stream)
                            val bytes = stream.toByteArray()
                            val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                            
                            FirebaseHelper.getFirestore(this).collection("devices").document(userName)
                                .set(
                                    hashMapOf(
                                        "stream" to hashMapOf(
                                            "frame" to "data:image/jpeg;base64,$base64",
                                            "audio" to latestAudioBase64,
                                            "facing" to currentFacing,
                                            "timestamp" to currentTime
                                        )
                                    ),
                                    com.google.firebase.firestore.SetOptions.merge()
                                )
                        } catch (e: Throwable) {
                            Log.e("CameraService", "Error encoding/sending image", e)
                        }
                    }
                    imageProxy.close()
                }

                cameraProvider?.unbindAll()
                currentCamera = cameraProvider?.bindToLifecycle(
                    this,
                    cameraSelector,
                    imageAnalysis
                )
                
                if (isTorchOn) {
                    currentCamera?.cameraControl?.enableTorch(true)
                }
            } catch (e: Throwable) {
                Log.e("CameraService", "Safe catch: Use case binding failed", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun startAudioRecording() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        val sampleRate = 16000
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val minBufSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        val bufferSize = if (minBufSize != AudioRecord.ERROR_BAD_VALUE && minBufSize != AudioRecord.ERROR) {
            minBufSize.coerceAtLeast(sampleRate / 2 * 2) // At least 500ms
        } else {
            sampleRate / 2 * 2
        }

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )
            
            audioRecord?.startRecording()
            isRecordingAudio = true

            thread(start = true) {
                val buffer = ByteArray(bufferSize)
                while (isRecordingAudio) {
                    try {
                        val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                        if (read > 0) {
                            latestAudioBase64 = Base64.encodeToString(buffer, 0, read, Base64.DEFAULT)
                        }
                    } catch (e: Throwable) {
                        Log.e("CameraService", "Audio reading thread error", e)
                    }
                    Thread.sleep(50) // Short sleep to prevent tight looping when not reading
                }
            }
        } catch (e: Throwable) {
            Log.e("CameraService", "Audio record init error", e)
        }
    }

    override fun onDestroy() {
        try {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
            settingsListener?.remove()
            cameraProvider?.unbindAll()
            
            isRecordingAudio = false
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
            
            executor.shutdown()
        } catch (e: Throwable) {
            Log.e("CameraService", "Error during service destroy", e)
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                "CameraServiceChannel",
                "Camera Streaming Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }
}

