package com.example.wastesorting

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCapture.OnImageCapturedCallback
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.wastesorting.data.GarbageClassifier
import com.example.wastesorting.data.db.GarbageDatabase
import com.example.wastesorting.data.db.entity.CaptureRecordEntity
import com.example.wastesorting.ui.theme.WasteSortingTheme
import com.example.wastesorting.util.ImageUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "WasteSorting"

// ============================================================
// 页面导航
// ============================================================
sealed class Screen {
    object Main : Screen()
    object BrowseRecords : Screen()
    data class Result(val bitmap: Bitmap, val recordId: Long) : Screen()
}

// ============================================================
// MainActivity
// ============================================================
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WasteSortingTheme {
                AppRoot()
            }
        }
    }
}

@Composable
fun AppRoot() {
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Main) }

    val navTo = { screen: Screen -> currentScreen = screen }

    when (val screen = currentScreen) {
        is Screen.Main -> WasteSortingScreen(
            onNavigateToRecords = { navTo(Screen.BrowseRecords) },
            onNavigateToResult = { bitmap, recordId -> navTo(Screen.Result(bitmap, recordId)) }
        )
        is Screen.BrowseRecords -> BrowseRecordsScreen(
            onBack = { navTo(Screen.Main) }
        )
        is Screen.Result -> ResultScreen(
            bitmap = screen.bitmap,
            recordId = screen.recordId,
            onExit = { navTo(Screen.Main) }
        )
    }
}

// ============================================================
// 主界面
// ============================================================
@Composable
fun WasteSortingScreen(
    onNavigateToRecords: () -> Unit = {},
    onNavigateToResult: (Bitmap, Long) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val db = remember { GarbageDatabase.getInstance(context) }
    val scope = rememberCoroutineScope()

    var isCameraActive by remember { mutableStateOf(false) }
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val imageCaptureRef = remember { mutableStateOf<ImageCapture?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        hasCameraPermission = grants[Manifest.permission.CAMERA] == true
        if (hasCameraPermission) {
            isCameraActive = true
        } else {
            Toast.makeText(context, "需要相机权限才能使用摄像头", Toast.LENGTH_SHORT).show()
        }
    }

    val toggleCamera: () -> Unit = {
        if (isCameraActive) {
            isCameraActive = false
            imageCaptureRef.value = null
        } else {
            if (hasCameraPermission) {
                isCameraActive = true
            } else {
                permissionLauncher.launch(
                    arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
                )
            }
        }
    }

    // 拍照 → 保存 → 跳转结果页
    val takePhoto: () -> Unit = {
        if (!isCameraActive) {
            Toast.makeText(context, "请先开启摄像头", Toast.LENGTH_SHORT).show()
        } else {
            val imageCapture = imageCaptureRef.value
            if (imageCapture == null) {
                Toast.makeText(context, "相机正在初始化…", Toast.LENGTH_SHORT).show()
            } else {
                try {
                    imageCapture.takePicture(
                        ContextCompat.getMainExecutor(context),
                        object : OnImageCapturedCallback() {
                            override fun onCaptureSuccess(image: ImageProxy) {
                                val rawBitmap = ImageUtils.imageProxyToBitmap(image)
                                val rotation = image.imageInfo.rotationDegrees
                                image.close()
                                val bitmap = ImageUtils.rotateBitmap(rawBitmap, rotation)

                                // 后台保存并跳转
                                scope.launch(Dispatchers.IO) {
                                    val recordId = saveAndGetRecordId(context, db, bitmap)
                                    withContext(Dispatchers.Main) {
                                        isCameraActive = false
                                        imageCaptureRef.value = null
                                        onNavigateToResult(bitmap, recordId)
                                    }
                                }
                            }

                            override fun onError(exc: ImageCaptureException) {
                                Log.e(TAG, "拍照失败: ${exc.message}", exc)
                                Toast.makeText(context, "拍照失败", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "拍照出错: ${e.message}", e)
                    Toast.makeText(context, "拍照出错", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 背景层
        if (isCameraActive && hasCameraPermission) {
            CameraPreviewView(
                modifier = Modifier.fillMaxSize(),
                imageCaptureRef = imageCaptureRef
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF1B5E20),
                                Color(0xFF0D3010),
                                Color(0xFF000000)
                            )
                        )
                    )
            )
        }

        // 预览区域遮罩（摄像头关闭时）
        if (!isCameraActive) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(0.85f)
                    .fillMaxHeight(0.5f)
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                    .clickable { toggleCamera() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "点击开启摄像头",
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 16.sp
                )
            }
        }

        // 全屏透明层（摄像头开启时可点击关闭）
        if (isCameraActive) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { toggleCamera() }
            )
        }

        // 顶部：摄像头图标
        Icon(
            imageVector = Icons.Default.CameraAlt,
            contentDescription = "摄像头",
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 64.dp)
                .size(56.dp)
                .clickable { toggleCamera() },
            tint = if (isCameraActive) Color.White else Color.White.copy(alpha = 0.85f)
        )

        // 右上角：历史记录按钮
        Icon(
            imageVector = Icons.Default.MoreVert,
            contentDescription = "历史记录",
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 72.dp, end = 24.dp)
                .size(32.dp)
                .clickable { onNavigateToRecords() },
            tint = Color.White.copy(alpha = 0.85f)
        )

        // 底部居中：拍照按钮
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp)
                .size(72.dp)
                .clip(CircleShape)
                .border(4.dp, Color.White, CircleShape)
                .background(Color.White.copy(alpha = 0.15f))
                .clickable { takePhoto() },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color.White)
            )
        }
    }
}

// ============================================================
// 识别结果页面
// ============================================================
@Composable
fun ResultScreen(
    bitmap: Bitmap,
    recordId: Long,
    onExit: () -> Unit
) {
    val context = LocalContext.current
    val db = remember { GarbageDatabase.getInstance(context) }
    val classifier = remember { GarbageClassifier.getInstance(context) }

    var result by remember { mutableStateOf<GarbageClassifier.PredictionResult?>(null) }
    var isRecognizing by remember { mutableStateOf(true) }

    // 进入页面自动开始识别
    LaunchedEffect(recordId) {
        val r = withContext(Dispatchers.IO) {
            classifier.classify(bitmap)
        }
        db.captureRecordDao().updateRecognition(recordId, r.label, r.category, r.confidence)
        result = r
        isRecognizing = false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1B5E20),
                        Color(0xFF0D3010),
                        Color(0xFF000000)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 16.dp)
        ) {
            // 顶部退出按钮
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "退出",
                modifier = Modifier
                    .padding(top = 48.dp, start = 16.dp)
                    .size(32.dp)
                    .clickable { onExit() },
                tint = Color.White.copy(alpha = 0.85f)
            )

            Text(
                text = "识别结果",
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 12.dp),
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 拍照图片
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "拍照图片",
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.4f)
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.FillWidth
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 识别结果区域
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White.copy(alpha = 0.1f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "识别结果",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    if (isRecognizing) {
                        Text(
                            text = "正在识别…",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 16.sp
                        )
                    } else {
                        val r = result
                        if (r != null && r.classId >= 0) {
                            Text(
                                text = r.label,
                                color = Color.White,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(categoryColor(r.category))
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = r.category,
                                        color = Color.White,
                                        fontSize = 13.sp
                                    )
                                }
                                Spacer(modifier = Modifier.padding(start = 12.dp))
                                Text(
                                    text = "置信度 ${"%.1f".format(r.confidence * 100)}%",
                                    color = Color.White.copy(alpha = 0.5f),
                                    fontSize = 13.sp
                                )
                            }
                        } else {
                            Text(
                                text = "识别失败",
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 16.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // AI建议占位区域
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White.copy(alpha = 0.05f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "AI 建议",
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "智能投放建议将在此展示",
                        color = Color.White.copy(alpha = 0.25f),
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

// ============================================================
// 浏览记录页面（数据来自 Room 数据库）
// ============================================================
@Composable
fun BrowseRecordsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val db = remember { GarbageDatabase.getInstance(context) }
    val records by db.captureRecordDao().getAllFlow().collectAsState(initial = emptyList())
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1B5E20),
                        Color(0xFF0D3010),
                        Color(0xFF000000)
                    )
                )
            )
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "返回",
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 48.dp, start = 16.dp)
                .size(32.dp)
                .clickable { onBack() },
            tint = Color.White.copy(alpha = 0.85f)
        )

        Text(
            text = "浏览记录",
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 56.dp),
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )

        if (records.isEmpty()) {
            Text(
                text = "暂无拍照记录",
                modifier = Modifier.align(Alignment.Center),
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 16.sp
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 100.dp, start = 16.dp, end = 16.dp, bottom = 16.dp)
            ) {
                items(records, key = { it.id }) { record ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color.White.copy(alpha = 0.1f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            if (record.isRecognized) {
                                Text(
                                    text = "${record.itemName}  →  ${record.categoryName}",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                if (record.confidence != null) {
                                    Text(
                                        text = "置信度: ${"%.1f".format(record.confidence * 100)}%",
                                        color = Color.White.copy(alpha = 0.5f),
                                        fontSize = 13.sp
                                    )
                                }
                            } else {
                                Text(
                                    text = "未识别",
                                    color = Color.White.copy(alpha = 0.5f),
                                    fontSize = 16.sp
                                )
                            }
                            Text(
                                text = dateFormat.format(Date(record.timestamp)),
                                color = Color.White.copy(alpha = 0.35f),
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }
}

// ============================================================
// CameraX 预览
// ============================================================
@Composable
fun CameraPreviewView(
    modifier: Modifier = Modifier,
    imageCaptureRef: androidx.compose.runtime.MutableState<ImageCapture?>
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    DisposableEffect(lifecycleOwner) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                val preview = androidx.camera.core.Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()
                imageCaptureRef.value = imageCapture
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageCapture)
            } catch (e: Exception) {
                Log.e(TAG, "CameraX 绑定失败: ${e.message}", e)
                imageCaptureRef.value = null
                Toast.makeText(context, "摄像头启动失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            imageCaptureRef.value = null
            val f = ProcessCameraProvider.getInstance(context)
            f.addListener({
                try { f.get().unbindAll() } catch (_: Exception) {}
            }, ContextCompat.getMainExecutor(context))
        }
    }

    AndroidView(factory = { previewView }, modifier = modifier)
}

// ============================================================
// 静默保存 Bitmap 到 MediaStore 并写入数据库，返回 recordId
// ============================================================
private suspend fun saveAndGetRecordId(
    context: android.content.Context,
    db: GarbageDatabase,
    bitmap: Bitmap
): Long = withContext(Dispatchers.IO) {
    try {
        val name = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.getDefault())
            .format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "IMG_$name.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/WasteSorting")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val uri = context.contentResolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues
        )
        if (uri != null) {
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                context.contentResolver.update(uri, contentValues, null, null)
            }
        }

        val uriStr = uri?.toString() ?: ""
        val record = CaptureRecordEntity(
            imageUri = uriStr,
            itemName = null,
            categoryName = null,
            confidence = null,
            isRecognized = false,
            timestamp = System.currentTimeMillis()
        )
        val recordId = db.captureRecordDao().insert(record)
        Log.d(TAG, "拍照已保存: $uriStr, recordId=$recordId")
        recordId
    } catch (e: Exception) {
        Log.e(TAG, "保存照片失败: ${e.message}", e)
        -1L
    }
}

// ============================================================
// 垃圾分类颜色映射
// ============================================================
private fun categoryColor(category: String): Color {
    return when (category) {
        "可回收物" -> Color(0xFF2196F3)
        "厨余垃圾" -> Color(0xFF4CAF50)
        "有害垃圾" -> Color(0xFFF44336)
        "其他垃圾" -> Color(0xFF9E9E9E)
        else -> Color.Gray
    }
}
