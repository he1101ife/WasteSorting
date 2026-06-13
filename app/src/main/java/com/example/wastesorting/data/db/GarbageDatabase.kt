package com.example.wastesorting.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.wastesorting.data.db.dao.CaptureRecordDao
import com.example.wastesorting.data.db.dao.GarbageCategoryDao
import com.example.wastesorting.data.db.dao.GarbageItemDao
import com.example.wastesorting.data.db.entity.CaptureRecordEntity
import com.example.wastesorting.data.db.entity.GarbageCategory
import com.example.wastesorting.data.db.entity.GarbageItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject

@Database(
    entities = [
        GarbageCategory::class,
        GarbageItem::class,
        CaptureRecordEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class GarbageDatabase : RoomDatabase() {

    abstract fun garbageCategoryDao(): GarbageCategoryDao
    abstract fun garbageItemDao(): GarbageItemDao
    abstract fun captureRecordDao(): CaptureRecordDao

    companion object {
        @Volatile
        private var INSTANCE: GarbageDatabase? = null

        fun getInstance(context: Context): GarbageDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context): GarbageDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                GarbageDatabase::class.java,
                "waste_sorting.db"
            )
            .fallbackToDestructiveMigration()
            .addCallback(PrepopulateCallback(context))
            .build()
        }
    }

    private class PrepopulateCallback(
        private val context: Context
    ) : RoomDatabase.Callback() {

        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            INSTANCE?.let { database ->
                CoroutineScope(Dispatchers.IO).launch {
                    prepopulate(database, context)
                }
            }
        }
    }
}

private fun prepopulate(database: GarbageDatabase, context: Context) {
    val categoryDao = database.garbageCategoryDao()
    val itemDao = database.garbageItemDao()

    // 四个固定类别
    val categories = listOf(
        GarbageCategory(id = 1, name = "可回收物"),
        GarbageCategory(id = 2, name = "厨余垃圾"),
        GarbageCategory(id = 3, name = "有害垃圾"),
        GarbageCategory(id = 4, name = "其他垃圾")
    )
    categoryDao.insertAll(categories)

    // 从 assets 读取 label_map.json（TFLite 索引顺序）
    val nameToCategoryId = mapOf(
        "可回收物" to 1,
        "厨余垃圾" to 2,
        "有害垃圾" to 3,
        "其他垃圾" to 4
    )

    try {
        val jsonStr = context.assets.open("label_map.json").bufferedReader().use { it.readText() }
        val json = JSONObject(jsonStr)
        val items = mutableListOf<GarbageItem>()

        for (key in json.keys()) {
            val obj = json.getJSONObject(key)
            val classId = obj.getInt("index")       // TFLite 输出索引 0~39
            val category = obj.getString("category")
            val label = obj.getString("label")
            val categoryId = nameToCategoryId[category] ?: 4
            items.add(GarbageItem(name = label, categoryId = categoryId, classId = classId))
        }
        itemDao.insertAll(items)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
