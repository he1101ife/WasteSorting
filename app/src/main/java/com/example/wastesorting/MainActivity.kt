package com.example.wastesorting

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
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
import androidx.camera.core.ImageCapture.OnImageCapturedCallback
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.nativeCanvas
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
import com.example.wastesorting.data.db.dao.CategoryStat
import com.example.wastesorting.data.db.entity.CaptureRecordEntity
import com.example.wastesorting.ui.theme.HazardousColor
import com.example.wastesorting.ui.theme.KitchenColor
import com.example.wastesorting.ui.theme.OtherColor
import com.example.wastesorting.ui.theme.RecyclableColor
import com.example.wastesorting.ui.theme.WasteSortingTheme
import com.example.wastesorting.util.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private const val TAG = "WasteSorting"

// ============================================================
// 页面导航
// ============================================================
sealed class Screen {
    object Main : Screen()
    object BrowseRecords : Screen()
    object Statistics : Screen()
    data class Result(val bitmap: Bitmap, val recordId: Long) : Screen()
    data class RecordDetail(val recordId: Long) : Screen()
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
        is Screen.Main -> MainScreen(
            onNavigateToRecords = { navTo(Screen.BrowseRecords) },
            onNavigateToStatistics = { navTo(Screen.Statistics) },
            onNavigateToResult = { bmp, id -> navTo(Screen.Result(bmp, id)) }
        )
        is Screen.BrowseRecords -> BrowseRecordsScreen(
            onBack = { navTo(Screen.Main) },
            onRecordClick = { navTo(Screen.RecordDetail(it)) }
        )
        is Screen.RecordDetail -> RecordDetailScreen(
            recordId = screen.recordId,
            onBack = { navTo(Screen.BrowseRecords) }
        )
        is Screen.Statistics -> StatisticsScreen(
            onBack = { navTo(Screen.Main) }
        )
        is Screen.Result -> ResultScreen(
            bitmap = screen.bitmap,
            recordId = screen.recordId,
            onBack = { navTo(Screen.Main) }
        )
    }
}

// ============================================================
// 主界面
// ============================================================
@Composable
fun MainScreen(
    onNavigateToRecords: () -> Unit,
    onNavigateToStatistics: () -> Unit,
    onNavigateToResult: (Bitmap, Long) -> Unit
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
        if (hasCameraPermission) isCameraActive = true
        else Toast.makeText(context, "需要相机权限", Toast.LENGTH_SHORT).show()
    }

    val toggleCamera: () -> Unit = {
        if (isCameraActive) { isCameraActive = false; imageCaptureRef.value = null }
        else if (hasCameraPermission) isCameraActive = true
        else permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA))
    }

    val takePhoto: () -> Unit = {
        val imageCapture = imageCaptureRef.value
        if (!isCameraActive) {
            Toast.makeText(context, "请先开启摄像头", Toast.LENGTH_SHORT).show()
        } else if (imageCapture == null) {
            Toast.makeText(context, "相机初始化中…", Toast.LENGTH_SHORT).show()
        } else {
            imageCapture.takePicture(
                ContextCompat.getMainExecutor(context),
                object : OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                        val raw = ImageUtils.imageProxyToBitmap(image)
                        val rot = image.imageInfo.rotationDegrees
                        image.close()
                        val bitmap = ImageUtils.rotateBitmap(raw, rot)
                        scope.launch(Dispatchers.IO) {
                            val id = saveAndGetRecordId(context, db, bitmap)
                            withContext(Dispatchers.Main) {
                                isCameraActive = false
                                imageCaptureRef.value = null
                                if (id > 0) onNavigateToResult(bitmap, id)
                                else Toast.makeText(context, "保存失败", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    override fun onError(exc: ImageCaptureException) {
                        Toast.makeText(context, "拍照失败", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            // 相机预览 or 占位
            if (isCameraActive && hasCameraPermission) {
                CameraPreviewView(
                    modifier = Modifier.fillMaxSize(),
                    imageCaptureRef = imageCaptureRef
                )
                Box(
                    modifier = Modifier.fillMaxSize()
                        .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { toggleCamera() }
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(modifier = Modifier.height(104.dp))
                        Text("垃圾分类", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("拍照识别 · 智能分类", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(48.dp))
                        Box(
                            modifier = Modifier
                                .size(88.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .clickable { toggleCamera() },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("📷", fontSize = 36.sp)
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text("点击开启摄像头", fontSize = 14.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)

                        Spacer(modifier = Modifier.weight(1f))

                        // 功能卡片 — 放在下方
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Card(
                                modifier = Modifier.weight(1f).clickable { onNavigateToRecords() },
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text("📋", fontSize = 24.sp)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("历史记录", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                    Text("查看过往识别", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Card(
                                modifier = Modifier.weight(1f).clickable { onNavigateToStatistics() },
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text("📊", fontSize = 24.sp)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("类别统计", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                    Text("占比与趋势", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("垃圾分类 · 绿色生活", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(32.dp))
                    }
                }
            }

            // 相机模式：左上角返回按钮
            if (isCameraActive) {
                IconButton(
                    onClick = toggleCamera,
                    modifier = Modifier.align(Alignment.TopStart).padding(top = 48.dp, start = 8.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = Color.White)
                }
            }

            // 底部拍照按钮
            if (isCameraActive) {
                Box(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 48.dp)
                        .size(72.dp).clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.3f))
                        .clickable { takePhoto() },
                    contentAlignment = Alignment.Center
                ) {
                    Box(modifier = Modifier.size(56.dp).clip(CircleShape).background(Color.White))
                }
            }
        }
    }
}

// ============================================================
// 识别结果页面
// ============================================================
@Composable
fun ResultScreen(bitmap: Bitmap, recordId: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val db = remember { GarbageDatabase.getInstance(context) }
    val classifier = remember { GarbageClassifier.getInstance(context) }
    var result by remember { mutableStateOf<GarbageClassifier.PredictionResult?>(null) }
    var isRecognizing by remember { mutableStateOf(true) }

    LaunchedEffect(recordId) {
        val r = withContext(Dispatchers.IO) { classifier.classify(bitmap) }
        db.captureRecordDao().updateRecognition(recordId, r.label, r.category, r.confidence)
        result = r; isRecognizing = false
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp)) {
            Spacer(modifier = Modifier.height(12.dp))
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
            Text("识别结果", fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
            Spacer(modifier = Modifier.height(16.dp))

            Image(
                bitmap = bitmap.asImageBitmap(), contentDescription = null,
                modifier = Modifier.fillMaxWidth().height(240.dp).clip(RoundedCornerShape(16.dp)),
                contentScale = ContentScale.FillWidth
            )
            Spacer(modifier = Modifier.height(20.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("识别结果", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(8.dp))
                    if (isRecognizing) {
                        Text("正在识别…", fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        val r = result
                        if (r != null && r.classId >= 0) {
                            Text(r.label, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(catColor(r.category)).padding(horizontal = 10.dp, vertical = 4.dp)) {
                                    Text(r.category, color = Color.White, fontSize = 13.sp)
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("置信度 ${"%.1f".format(r.confidence * 100)}%", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else {
                            Text("识别失败", fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            // AI建议占位
            Card(
                modifier = Modifier.fillMaxWidth().weight(1f),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("AI 建议", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("智能投放建议将在此展示", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// ============================================================
// 浏览记录页面
// ============================================================
@Composable
fun BrowseRecordsScreen(onBack: () -> Unit, onRecordClick: (Long) -> Unit) {
    val context = LocalContext.current
    val db = remember { GarbageDatabase.getInstance(context) }
    val records by db.captureRecordDao().getAllFlow().collectAsState(initial = emptyList())
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Row(modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
                Text("历史记录", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            if (records.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("暂无拍照记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                    items(records, key = { it.id }) { record ->
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onRecordClick(record.id) },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    if (record.isRecognized) {
                                        Text("${record.itemName}", fontWeight = FontWeight.Medium, fontSize = 16.sp)
                                        Text(record.categoryName ?: "", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    } else {
                                        Text("未识别", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Text(dateFormat.format(Date(record.timestamp)), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                                }
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, modifier = Modifier.rotate(180f), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ============================================================
// 记录详情页面（图片 + 识别结果 + 删除）
// ============================================================
@Composable
fun RecordDetailScreen(recordId: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val db = remember { GarbageDatabase.getInstance(context) }
    val scope = rememberCoroutineScope()
    var record by remember { mutableStateOf<CaptureRecordEntity?>(null) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(recordId) {
        val r = withContext(Dispatchers.IO) { db.captureRecordDao().getById(recordId) }
        record = r
        if (r != null) {
            try {
                val uri = Uri.parse(r.imageUri)
                val bmp = withContext(Dispatchers.IO) {
                    MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                }
                bitmap = bmp
            } catch (_: Exception) {}
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("确认删除") },
            text = { Text("删除后将无法恢复，确定要删除这条记录吗？") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    scope.launch(Dispatchers.IO) {
                        db.captureRecordDao().deleteById(recordId)
                        withContext(Dispatchers.Main) { onBack() }
                    }
                }) { Text("删除", color = HazardousColor) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("取消") }
            }
        )
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp)) {
            Row(
                modifier = Modifier.padding(vertical = 8.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
                Text("记录详情", fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = { showDeleteDialog = true }) {
                    Icon(Icons.Default.Delete, "删除", tint = HazardousColor)
                }
            }

            val r = record
            if (r == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("加载中…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                val bmp = bitmap
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(), contentDescription = null,
                        modifier = Modifier.fillMaxWidth().height(260.dp).clip(RoundedCornerShape(16.dp)),
                        contentScale = ContentScale.FillWidth
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(260.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface),
                        contentAlignment = Alignment.Center
                    ) { Text("图片加载失败", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                Spacer(modifier = Modifier.height(20.dp))

                if (r.isRecognized) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text("识别结果", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(r.itemName ?: "", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val cat = r.categoryName ?: ""
                                Box(modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(catColor(cat)).padding(horizontal = 10.dp, vertical = 4.dp)) {
                                    Text(cat, color = Color.White, fontSize = 13.sp)
                                }
                                if (r.confidence != null) {
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text("置信度 ${"%.1f".format(r.confidence * 100)}%", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }
                            Text(dateFormat.format(Date(r.timestamp)), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                        }
                    }
                } else {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Box(modifier = Modifier.padding(20.dp)) {
                            Text("未识别", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

// ============================================================
// 类别统计页面（饼图 + 柱状图）
// ============================================================
@Composable
fun StatisticsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val db = remember { GarbageDatabase.getInstance(context) }
    var stats by remember { mutableStateOf<List<CategoryStat>>(emptyList()) }
    var weeklyStats by remember { mutableStateOf<List<CategoryStat>>(emptyList()) }

    LaunchedEffect(Unit) {
        val weekStart = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val all = withContext(Dispatchers.IO) { db.captureRecordDao().getCategoryStats() }
        val week = withContext(Dispatchers.IO) { db.captureRecordDao().getWeeklyStats(weekStart) }
        stats = all; weeklyStats = week
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp)) {
            Row(modifier = Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
                Text("类别统计", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }

            if (stats.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("暂无识别记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                // 饼图
                Text("类别占比", fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 8.dp, bottom = 12.dp))
                PieChart(
                    data = stats.map { it.categoryName to it.cnt },
                    colors = listOf(RecyclableColor, KitchenColor, HazardousColor, OtherColor),
                    modifier = Modifier.fillMaxWidth().height(220.dp)
                )
                // 图例
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    stats.forEachIndexed { i, s ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp)) {
                            Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(catColor(s.categoryName)))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(s.categoryName, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))

                // 柱状图
                Text("本周识别", fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 12.dp))
                BarChart(
                    data = weeklyStats,
                    colors = listOf(RecyclableColor, KitchenColor, HazardousColor, OtherColor),
                    modifier = Modifier.fillMaxWidth().height(200.dp)
                )
            }
        }
    }
}

// ============================================================
// 饼图 Composable
// ============================================================
@Composable
fun PieChart(
    data: List<Pair<String, Int>>,
    colors: List<Color>,
    modifier: Modifier = Modifier
) {
    val total = data.sumOf { it.second }.toFloat()
    if (total == 0f) return
    val onSurface = android.graphics.Color.parseColor("#1C1C1E")
    val onSurfaceVariant = android.graphics.Color.parseColor("#8E8E93")
    Canvas(modifier = modifier) {
        val diameter = minOf(size.width, size.height) * 0.78f
        val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
        var startAngle = -90f
        data.forEachIndexed { i, (_, count) ->
            val sweep = (count / total) * 360f
            drawArc(
                color = colors.getOrElse(i) { Color.Gray },
                startAngle = startAngle,
                sweepAngle = sweep,
                useCenter = true,
                topLeft = topLeft,
                size = Size(diameter, diameter)
            )
            startAngle += sweep
        }
        // center hole (donut)
        val holeR = diameter * 0.38f
        val cx = size.width / 2f
        val cy = size.height / 2f
        drawCircle(Color.White, radius = holeR + 1f, center = Offset(cx, cy))
        // total count
        drawContext.canvas.nativeCanvas.drawText(
            "${total.toInt()}",
            cx, cy - 2f,
            android.graphics.Paint().apply {
                textSize = 42f * density
                textAlign = android.graphics.Paint.Align.CENTER
                this.color = onSurface
                isFakeBoldText = true
            }
        )
        drawContext.canvas.nativeCanvas.drawText(
            "总计",
            cx, cy + 24f * density,
            android.graphics.Paint().apply {
                textSize = 14f * density
                textAlign = android.graphics.Paint.Align.CENTER
                this.color = onSurfaceVariant
            }
        )
    }
}

// ============================================================
// 柱状图 Composable
// ============================================================
@Composable
fun BarChart(
    data: List<CategoryStat>,
    colors: List<Color>,
    modifier: Modifier = Modifier
) {
    val maxVal = data.maxOfOrNull { it.cnt }?.toFloat() ?: 1f
    Canvas(modifier = modifier) {
        val labelHeight = 36f * density         // 底部标签预留高度
        val valueGap = 18f * density            // 柱顶到数字的间距
        val chartArea = size.height - labelHeight
        val barCount = data.size.coerceAtLeast(4)
        val barWidth = (size.width - 16f * density) / barCount
        val gap = barWidth * 0.2f

        data.forEachIndexed { i, stat ->
            val barH = if (maxVal > 0) (stat.cnt / maxVal) * (chartArea - valueGap) else 0f
            val x = 8f * density + i * barWidth
            val bottomY = chartArea

            // 柱子
            drawRoundRect(
                color = colors.getOrElse(i) { Color.Gray },
                topLeft = Offset(x, bottomY - barH),
                size = Size(barWidth - gap, barH),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f * density, 6f * density)
            )
            // 数值（柱顶上）
            if (stat.cnt > 0) {
                drawContext.canvas.nativeCanvas.drawText(
                    "${stat.cnt}",
                    x + (barWidth - gap) / 2f, bottomY - barH - 6f * density,
                    android.graphics.Paint().apply {
                        textSize = 12f * density
                        textAlign = android.graphics.Paint.Align.CENTER
                        this.color = android.graphics.Color.parseColor("#8E8E93")
                        isFakeBoldText = true
                    }
                )
            }
            // 分类标签（底部固定区域）
            drawContext.canvas.nativeCanvas.drawText(
                stat.categoryName.take(2),
                x + (barWidth - gap) / 2f, chartArea + 18f * density,
                android.graphics.Paint().apply {
                    textSize = 12f * density
                    textAlign = android.graphics.Paint.Align.CENTER
                    this.color = android.graphics.Color.parseColor("#1C1C1E")
                }
            )
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
    val previewView = remember { PreviewView(context).apply { implementationMode = PreviewView.ImplementationMode.COMPATIBLE } }

    DisposableEffect(lifecycleOwner) {
        val f = ProcessCameraProvider.getInstance(context)
        f.addListener({
            try {
                val provider = f.get()
                val preview = androidx.camera.core.Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                val capture = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
                imageCaptureRef.value = capture
                provider.unbindAll()
                provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture)
            } catch (e: Exception) {
                imageCaptureRef.value = null
                Toast.makeText(context, "摄像头启动失败", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            imageCaptureRef.value = null
            val pf = ProcessCameraProvider.getInstance(context)
            pf.addListener({ try { pf.get().unbindAll() } catch (_: Exception) {} }, ContextCompat.getMainExecutor(context))
        }
    }
    AndroidView(factory = { previewView }, modifier = modifier)
}

// ============================================================
// 保存照片 + 写数据库
// ============================================================
private suspend fun saveAndGetRecordId(context: android.content.Context, db: GarbageDatabase, bitmap: Bitmap): Long = withContext(Dispatchers.IO) {
    try {
        val name = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.getDefault()).format(System.currentTimeMillis())
        val cv = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "IMG_$name.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/WasteSorting")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv)
        if (uri != null) {
            context.contentResolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                cv.clear(); cv.put(MediaStore.Images.Media.IS_PENDING, 0)
                context.contentResolver.update(uri, cv, null, null)
            }
        }
        val record = CaptureRecordEntity(imageUri = uri?.toString() ?: "", itemName = null, categoryName = null, confidence = null, isRecognized = false, timestamp = System.currentTimeMillis())
        db.captureRecordDao().insert(record)
    } catch (e: Exception) {
        Log.e(TAG, "保存失败", e); -1L
    }
}

// ============================================================
// 类别颜色映射
// ============================================================
private fun catColor(category: String): Color = when (category) {
    "可回收物" -> RecyclableColor
    "厨余垃圾" -> KitchenColor
    "有害垃圾" -> HazardousColor
    "其他垃圾" -> OtherColor
    else -> Color.Gray
}
