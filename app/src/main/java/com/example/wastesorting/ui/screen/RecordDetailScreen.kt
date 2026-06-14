package com.example.wastesorting.ui.screen

import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.example.wastesorting.data.db.GarbageDatabase
import com.example.wastesorting.data.db.entity.CaptureRecordEntity
import com.example.wastesorting.ui.theme.HazardousColor
import com.example.wastesorting.util.catColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
                bitmap = withContext(Dispatchers.IO) { MediaStore.Images.Media.getBitmap(context.contentResolver, uri) }
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
                    scope.launch(Dispatchers.IO) { db.captureRecordDao().deleteById(recordId); withContext(Dispatchers.Main) { onBack() } }
                }) { Text("删除", color = HazardousColor) }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("取消") } }
        )
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp)) {
            Row(modifier = Modifier.padding(vertical = 8.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
                Text("记录详情", fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = { showDeleteDialog = true }) { Icon(Icons.Default.Delete, "删除", tint = HazardousColor) }
            }

            val r = record
            if (r == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("加载中…", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                val bmp = bitmap
                if (bmp != null) {
                    Image(bitmap = bmp.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxWidth().height(260.dp).clip(RoundedCornerShape(16.dp)), contentScale = ContentScale.FillWidth)
                } else {
                    Box(modifier = Modifier.fillMaxWidth().height(260.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface), contentAlignment = Alignment.Center) {
                        Text("图片加载失败", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
                if (r.isRecognized) {
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
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
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Box(modifier = Modifier.padding(20.dp)) { Text("未识别", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
        }
    }
}
