package com.example.wastesorting.ui.screen

import android.graphics.Bitmap
import android.widget.Toast
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCapture.OnImageCapturedCallback
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.wastesorting.data.db.GarbageDatabase
import com.example.wastesorting.util.ImageUtils
import com.example.wastesorting.util.saveAndGetRecordId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun AiModeScreen(
    onBack: () -> Unit,
    onNavigateToResult: (Bitmap, Long) -> Unit
) {
    val context = LocalContext.current
    val db = remember { GarbageDatabase.getInstance(context) }
    val scope = rememberCoroutineScope()
    val imageCaptureRef = remember { mutableStateOf<ImageCapture?>(null) }
    var isCameraActive by remember { mutableStateOf(true) }
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isListening by remember { mutableStateOf(false) }

    val takePhoto: () -> Unit = {
        val imageCapture = imageCaptureRef.value
        if (imageCapture == null) {
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
                        capturedBitmap = bitmap
                        isCameraActive = false
                        scope.launch(Dispatchers.IO) { saveAndGetRecordId(context, db, bitmap) }
                    }
                    override fun onError(exc: ImageCaptureException) {
                        Toast.makeText(context, "拍照失败", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (isCameraActive) {
                CameraPreviewView(modifier = Modifier.fillMaxSize(), imageCaptureRef = imageCaptureRef)
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.align(Alignment.TopStart).padding(top = 48.dp, start = 8.dp)
                ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = Color.White) }
                Box(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 48.dp)
                        .size(72.dp).clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.3f))
                        .clickable { takePhoto() },
                    contentAlignment = Alignment.Center
                ) {
                    Box(modifier = Modifier.size(56.dp).clip(CircleShape).background(Color.White))
                }
            } else {
                Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
                        Text("AI 识别模式", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                    val bmp = capturedBitmap
                    if (bmp != null) {
                        Image(
                            bitmap = bmp.asImageBitmap(), contentDescription = null,
                            modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(16.dp)),
                            contentScale = ContentScale.FillWidth
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("重新拍照", fontSize = 14.sp, color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable { isCameraActive = true; capturedBitmap = null })
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                    // 麦克风
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { isListening = !isListening },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = if (isListening) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface)
                    ) {
                        Row(modifier = Modifier.padding(20.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("🎤", fontSize = 28.sp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("语音输入", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                                Text(if (isListening) "正在聆听…" else "点击开始语音输入", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                    // 识别按钮
                    Card(
                        modifier = Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(14.dp)).clickable {
                            Toast.makeText(context, "识别功能即将上线", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("开始识别", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                    // AI建议
                    Card(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                    ) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("🤖 AI 建议", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.height(6.dp))
                                Text("智能投放建议将在此展示", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}
