package com.example.wastesorting.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 垃圾分类类别：可回收物、厨余垃圾、有害垃圾、其他垃圾
 */
@Entity(tableName = "garbage_categories")
data class GarbageCategory(
    @PrimaryKey val id: Int,
    val name: String
)

/**
 * 垃圾物品条目
 */
@Entity(
    tableName = "garbage_items",
    foreignKeys = [
        ForeignKey(
            entity = GarbageCategory::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("categoryId"), Index("classId")]
)
data class GarbageItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val categoryId: Int,
    val classId: Int = -1   // TFLite 模型输出索引 0~39
)

/**
 * 拍照识别记录
 */
@Entity(tableName = "capture_records")
data class CaptureRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val imageUri: String,
    val itemName: String?,
    val categoryName: String?,
    val confidence: Float?,
    val isRecognized: Boolean,
    val timestamp: Long
)
