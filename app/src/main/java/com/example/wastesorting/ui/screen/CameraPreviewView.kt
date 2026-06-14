package com.example.wastesorting.ui.screen

import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat

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
