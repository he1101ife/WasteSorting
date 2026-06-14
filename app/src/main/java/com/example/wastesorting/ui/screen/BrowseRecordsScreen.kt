package com.example.wastesorting.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.wastesorting.data.db.GarbageDatabase
import com.example.wastesorting.ui.theme.HazardousColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BrowseRecordsScreen(onBack: () -> Unit, onRecordClick: (Long) -> Unit) {
    val context = LocalContext.current
    val db = remember { GarbageDatabase.getInstance(context) }
    val scope = rememberCoroutineScope()
    val records by db.captureRecordDao().getAllFlow().collectAsState(initial = emptyList())
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var isSelectMode by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val isInSelecting = isSelectMode

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("确认删除") },
            text = { Text("确定要删除选中的 ${selectedIds.size} 条记录吗？") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    scope.launch(Dispatchers.IO) {
                        db.captureRecordDao().deleteByIds(selectedIds.toList())
                        withContext(Dispatchers.Main) { isSelectMode = false; selectedIds = emptySet() }
                    }
                }) { Text("删除", color = HazardousColor) }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("取消") } }
        )
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Row(
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    if (isInSelecting) { isSelectMode = false; selectedIds = emptySet() }
                    else onBack()
                }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
                Text(
                    if (isInSelecting) "已选 ${selectedIds.size} 项" else "历史记录",
                    fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)
                )
                if (records.isNotEmpty()) {
                    if (isInSelecting) {
                        TextButton(onClick = { showDeleteDialog = true }) {
                            Text("删除", color = HazardousColor, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        TextButton(onClick = { isSelectMode = true; selectedIds = emptySet() }) {
                            Text("选择", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }

            if (records.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("暂无拍照记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                    items(records, key = { it.id }) { record ->
                        val isSelected = record.id in selectedIds
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable {
                                if (isInSelecting) selectedIds = if (isSelected) selectedIds - record.id else selectedIds + record.id
                                else onRecordClick(record.id)
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surface)
                        ) {
                            Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                if (isInSelecting) {
                                    Box(
                                        modifier = Modifier.size(22.dp).clip(CircleShape)
                                            .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                                            .then(if (isSelected) Modifier else Modifier.border(2.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f), CircleShape)),
                                        contentAlignment = Alignment.Center
                                    ) { if (isSelected) Text("✓", color = Color.White, fontSize = 13.sp) }
                                    Spacer(modifier = Modifier.width(12.dp))
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    if (record.isRecognized) {
                                        Text("${record.itemName}", fontWeight = FontWeight.Medium, fontSize = 16.sp)
                                        Text(record.categoryName ?: "", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    } else {
                                        Text("未识别", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Text(dateFormat.format(Date(record.timestamp)), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                                }
                                if (!isInSelecting) Icon(Icons.AutoMirrored.Filled.ArrowBack, null, modifier = Modifier.rotate(180f), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}
