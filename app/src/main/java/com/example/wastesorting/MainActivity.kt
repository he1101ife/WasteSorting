package com.example.wastesorting

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.net.Uri
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
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.wastesorting.ui.theme.WasteSortingTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "WasteSorting"
private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"

// ============================================================
// 页面导航
// ============================================================
sealed class Screen {
    object Main : Screen()
    object BrowseRecords : Screen()
}

// ============================================================
// 拍照记录
// ============================================================
data class CaptureRecord(
    val id: Int,
    val imageUri: String,
    val caption: String,
    val timestamp: Long
)

object CaptureRecordStore {
    private val records = mutableListOf<CaptureRecord>()
    private var nextId = 1

    fun addRecord(imageUri: String, caption: String = "未识别") {
        records.add(0, CaptureRecord(nextId++, imageUri, caption, System.currentTimeMillis()))
    }

    fun getAllRecords(): List<CaptureRecord> = records.toList()
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

    when (val screen = currentScreen) {
        is Screen.Main -> WasteSortingScreen(
            onNavigateToRecords = { currentScreen = Screen.BrowseRecords }
        )
        is Screen.BrowseRecords -> BrowseRecordsScreen(
            onBack = { currentScreen = Screen.Main }
        )
    }
}

// ============================================================
// 主界面
// ============================================================
@Composable
fun WasteSortingScreen(onNavigateToRecords: () -> Unit = {}) {
    val context = LocalContext.current
    var isCameraActive by remember { mutableStateOf(false) }
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    // 持有 ImageCapture 实例引用
    val imageCaptureRef = remember { mutableStateOf<ImageCapture?>(null) }

    // 设置菜单状态
    var showSettingsMenu by remember { mutableStateOf(false) }

    // 多权限请求
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        hasCameraPermission = grants[Manifest.permission.CAMERA] == true
        if (hasCameraPermission) {
            isCameraActive = true
            Log.d(TAG, "权限已授予")
        } else {
            Toast.makeText(context, "需要相机权限才能使用摄像头", Toast.LENGTH_SHORT).show()
        }
    }

    val toggleCamera: () -> Unit = {
        if (isCameraActive) {
            isCameraActive = false
            imageCaptureRef.value = null
            Log.d(TAG, "摄像头已关闭")
        } else {
            if (hasCameraPermission) {
                isCameraActive = true
                Log.d(TAG, "摄像头已开启")
            } else {
                permissionLauncher.launch(
                    arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
                )
            }
        }
    }

    // 拍照函数
    val takePhoto: () -> Unit = {
        if (!isCameraActive) {
            Toast.makeText(context, "请先开启摄像头", Toast.LENGTH_SHORT).show()
        } else {
            val imageCapture = imageCaptureRef.value
            if (imageCapture == null) {
                Toast.makeText(context, "相机正在初始化…", Toast.LENGTH_SHORT).show()
            } else {
                performCapture(context, imageCapture)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 背景层：摄像头开启时显示预览，关闭时显示渐变背景
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

        // 预览区域遮罩层：摄像头关闭时显示占位区域，点击可开启
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

        // 全屏透明层：摄像头开启时点击预览区域可关闭
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

        // 顶部：摄像头图标（点击切换）
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

        // 右上角：设置按钮
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 72.dp, end = 24.dp)
        ) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "设置",
                modifier = Modifier
                    .size(32.dp)
                    .clickable { showSettingsMenu = true },
                tint = Color.White.copy(alpha = 0.85f)
            )

            DropdownMenu(
                expanded = showSettingsMenu,
                onDismissRequest = { showSettingsMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("浏览记录") },
                    onClick = {
                        showSettingsMenu = false
                        onNavigateToRecords()
                    }
                )
            }
        }

        // 底部按钮区域
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = 36.dp, end = 36.dp, bottom = 48.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左下：识别按钮
            Button(
                onClick = { /* TODO: 识别功能 */ },
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isCameraActive)
                        Color.Black.copy(alpha = 0.4f)
                    else
                        Color.White.copy(alpha = 0.2f)
                )
            ) {
                Text(
                    text = "识别",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )
            }

            // 右下：拍摄按钮（相机快门样式）
            Box(
                modifier = Modifier
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
}

// ============================================================
// 浏览记录页面
// ============================================================
@Composable
fun BrowseRecordsScreen(onBack: () -> Unit) {
    val records = remember { CaptureRecordStore.getAllRecords() }

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
        // 左上角：返回图标
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

        // 顶部标题
        Text(
            text = "浏览记录",
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 56.dp),
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )

        // 记录列表
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
                items(records) { record ->
                    RecordCard(record)
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }
}

@Composable
fun RecordCard(record: CaptureRecord) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.1f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "物品: ${record.caption}",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = dateFormat.format(Date(record.timestamp)),
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = record.imageUri,
                    color = Color.White.copy(alpha = 0.3f),
                    fontSize = 11.sp,
                    maxLines = 1
                )
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
                cameraProvider.bindToLifecycle(
                    lifecycleOwner, cameraSelector, preview, imageCapture
                )
                Log.d(TAG, "CameraX 绑定成功（Preview + ImageCapture）")
            } catch (e: Exception) {
                Log.e(TAG, "CameraX 绑定失败: ${e.message}", e)
                imageCaptureRef.value = null
                Toast.makeText(context, "摄像头启动失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            imageCaptureRef.value = null
            val cameraProviderFuture2 = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture2.addListener({
                try {
                    cameraProviderFuture2.get().unbindAll()
                    Log.d(TAG, "CameraX 已解绑")
                } catch (_: Exception) {}
            }, ContextCompat.getMainExecutor(context))
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = modifier
    )
}

// ============================================================
// 拍照
// ============================================================
private fun performCapture(context: android.content.Context, imageCapture: ImageCapture) {
    val name = SimpleDateFormat(FILENAME_FORMAT, Locale.getDefault())
        .format(System.currentTimeMillis())
    val contentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, name)
        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/WasteSorting")
        }
    }

    val outputOptions = ImageCapture.OutputFileOptions
        .Builder(
            context.contentResolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues
        )
        .build()

    try {
        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val uri = output.savedUri
                    if (uri != null) {
                        CaptureRecordStore.addRecord(uri.toString())
                    }
                    Toast.makeText(context, "拍照成功，已保存至 Pictures/WasteSorting", Toast.LENGTH_LONG).show()
                }

                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "拍照失败: ${exc.message}", exc)
                    Toast.makeText(context, "拍照失败: ${exc.message}", Toast.LENGTH_SHORT).show()
                }
            }
        )
        Log.d(TAG, "takePicture 已调用")
    } catch (e: Exception) {
        Log.e(TAG, "拍照出错: ${e.message}", e)
        Toast.makeText(context, "拍照出错: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF1B5E20)
@Composable
fun WasteSortingScreenPreview() {
    WasteSortingTheme {
        WasteSortingScreen()
    }
}
