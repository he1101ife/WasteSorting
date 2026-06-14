package com.example.wastesorting.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.wastesorting.data.db.entity.CaptureRecordEntity
import com.example.wastesorting.data.db.entity.GarbageCategory
import com.example.wastesorting.data.db.entity.GarbageItem
import kotlinx.coroutines.flow.Flow

@Dao
interface GarbageCategoryDao {
    @Query("SELECT * FROM garbage_categories ORDER BY id")
    fun getAll(): List<GarbageCategory>

    @Query("SELECT * FROM garbage_categories WHERE name = :name")
    fun getByName(name: String): GarbageCategory?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(categories: List<GarbageCategory>)
}

@Dao
interface GarbageItemDao {
    @Query("SELECT * FROM garbage_items WHERE categoryId = :categoryId")
    fun getByCategory(categoryId: Int): List<GarbageItem>

    @Query("SELECT gi.*, gc.name AS categoryName FROM garbage_items gi " +
           "INNER JOIN garbage_categories gc ON gi.categoryId = gc.id " +
           "WHERE gi.name = :name LIMIT 1")
    fun getByName(name: String): GarbageItemWithCategory?

    @Query("SELECT gi.*, gc.name AS categoryName FROM garbage_items gi " +
           "INNER JOIN garbage_categories gc ON gi.categoryId = gc.id " +
           "WHERE gi.classId = :classId LIMIT 1")
    fun getByClassId(classId: Int): GarbageItemWithCategory?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(items: List<GarbageItem>)
}

data class GarbageItemWithCategory(
    val id: Int,
    val name: String,
    val categoryId: Int,
    val classId: Int,
    val categoryName: String
)

@Dao
interface CaptureRecordDao {
    @Query("SELECT * FROM capture_records ORDER BY timestamp DESC")
    fun getAllFlow(): Flow<List<CaptureRecordEntity>>

    @Query("SELECT * FROM capture_records ORDER BY timestamp DESC")
    suspend fun getAll(): List<CaptureRecordEntity>

    @Insert
    suspend fun insert(record: CaptureRecordEntity): Long

    @Query("SELECT * FROM capture_records WHERE id = :id")
    suspend fun getById(id: Long): CaptureRecordEntity?

    @Query("UPDATE capture_records SET itemName = :itemName, categoryName = :categoryName, " +
           "confidence = :confidence, isRecognized = 1 WHERE id = :id")
    suspend fun updateRecognition(id: Long, itemName: String, categoryName: String, confidence: Float)

    @Query("DELETE FROM capture_records WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM capture_records WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("SELECT categoryName, COUNT(*) as cnt FROM capture_records WHERE isRecognized = 1 GROUP BY categoryName")
    suspend fun getCategoryStats(): List<CategoryStat>

    @Query("SELECT categoryName, COUNT(*) as cnt FROM capture_records WHERE isRecognized = 1 AND timestamp >= :weekStart GROUP BY categoryName")
    suspend fun getWeeklyStats(weekStart: Long): List<CategoryStat>
}

data class CategoryStat(
    val categoryName: String,
    val cnt: Int
)
