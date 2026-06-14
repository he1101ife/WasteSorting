package com.example.wastesorting.ui.screen

import android.graphics.Bitmap
import android.widget.Toast
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCapture.OnImageCapturedCallback
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.wastesorting.data.db.GarbageDatabase
import com.example.wastesorting.util.ImageUtils
import com.example.wastesorting.util.saveAndGetRecordId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SimpleCameraScreen(
    onBack: () -> Unit,
    onNavigateToResult: (Bitmap, Long) -> Unit
) {
    val context = LocalContext.current
    val db = remember { GarbageDatabase.getInstance(context) }
    val scope = rememberCoroutineScope()
    val imageCaptureRef = remember { mutableStateOf<ImageCapture?>(null) }

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
                        scope.launch(Dispatchers.IO) {
                            val id = saveAndGetRecordId(context, db, bitmap)
                            withContext(Dispatchers.Main) {
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

    Box(modifier = Modifier.fillMaxSize()) {
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
    }
}
