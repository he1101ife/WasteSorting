package com.example.wastesorting.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import com.example.wastesorting.data.db.GarbageDatabase
import com.example.wastesorting.data.db.entity.CaptureRecordEntity
import java.text.SimpleDateFormat
import java.util.Locale

private const val TAG = "StorageUtil"

suspend fun saveAndGetRecordId(context: Context, db: GarbageDatabase, bitmap: Bitmap): Long {
    return try {
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
        val record = CaptureRecordEntity(
            imageUri = uri?.toString() ?: "", itemName = null, categoryName = null,
            confidence = null, isRecognized = false, timestamp = System.currentTimeMillis()
        )
        db.captureRecordDao().insert(record)
    } catch (e: Exception) {
        Log.e(TAG, "保存失败", e); -1L
    }
}
