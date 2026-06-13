package com.example.wastesorting.data

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.example.wastesorting.data.db.GarbageDatabase
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * 本地 TFLite 垃圾分类推理器
 *
 * 输入: Bitmap → 224×224×3 RGB, 归一化到 [-1, 1]
 * 输出: 40 个 float 值 (Softmax 概率), 取 argmax 得到 classId
 * 标签映射: 通过 classId 查询本地 Room 数据库获取类别和物品名
 */
class GarbageClassifier(private val context: Context) {

    private var interpreter: Interpreter? = null
    private val db: GarbageDatabase by lazy { GarbageDatabase.getInstance(context) }

    // 输入尺寸
    private val inputSize = 224
    private val channels = 3
    private val numClasses = 40

    // 输入 ByteBuffer (复用, 避免重复分配)
    private val inputBuffer: ByteBuffer = ByteBuffer.allocateDirect(
        inputSize * inputSize * channels * java.lang.Float.SIZE / java.lang.Byte.SIZE
    ).apply { order(ByteOrder.nativeOrder()) }

    // 输出数组
    private val outputArray = Array(1) { FloatArray(numClasses) }

    /**
     * 加载模型 (线程安全, 延迟初始化)
     */
    @Synchronized
    private fun ensureLoaded() {
        if (interpreter != null) return
        interpreter = Interpreter(loadModelFile()).also {
            Log.d(TAG, "TFLite 模型加载成功")
        }
    }

    /**
     * 从 assets 加载模型文件
     */
    private fun loadModelFile(): MappedByteBuffer {
        val descriptor = context.assets.openFd(MODEL_FILE)
        val inputStream = FileInputStream(descriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        return fileChannel.map(
            FileChannel.MapMode.READ_ONLY,
            descriptor.startOffset,
            descriptor.declaredLength
        )
    }

    /**
     * 执行垃圾分类识别
     *
     * @param bitmap 输入图片
     * @return PredictionResult 识别结果
     */
    fun classify(bitmap: Bitmap): PredictionResult {
        ensureLoaded()
        val interpreter = interpreter ?: return PredictionResult.error("模型未加载")

        // 1. 预处理: 缩放 + 归一化
        val resized = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
        preprocess(resized)
        if (resized != bitmap) resized.recycle()

        // 2. TFLite 推理
        interpreter.run(inputBuffer, outputArray)
        val probabilities = outputArray[0]

        // 3. 取 argmax
        val classId = probabilities.indices.maxByOrNull { probabilities[it] } ?: 0
        val confidence = probabilities[classId]

        Log.d(TAG, "推理完成: classId=$classId, confidence=${"%.2f".format(confidence * 100)}%")

        // 4. 查询数据库获取类别和物品名
        val item = db.garbageItemDao().getByClassId(classId)
        if (item != null) {
            return PredictionResult(
                classId = classId,
                category = item.categoryName,
                label = item.name,
                confidence = confidence
            )
        }

        // 数据库没有对应条目 (不应该发生)
        return PredictionResult(
            classId = classId,
            category = "未知",
            label = "未知物品",
            confidence = confidence
        )
    }

    /**
     * 预处理: Bitmap → ByteBuffer, 归一化到 [-1, 1]
     * 公式: (pixel / 127.5) - 1.0
     */
    private fun preprocess(bitmap: Bitmap) {
        inputBuffer.rewind()
        val pixels = IntArray(inputSize * inputSize)
        bitmap.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)

        for (pixel in pixels) {
            val r = ((pixel shr 16) and 0xFF) / 127.5f - 1.0f
            val g = ((pixel shr 8) and 0xFF) / 127.5f - 1.0f
            val b = (pixel and 0xFF) / 127.5f - 1.0f
            inputBuffer.putFloat(r)
            inputBuffer.putFloat(g)
            inputBuffer.putFloat(b)
        }
        inputBuffer.rewind()
    }

    /**
     * 释放 TFLite 资源
     */
    fun close() {
        interpreter?.close()
        interpreter = null
        Log.d(TAG, "TFLite 资源已释放")
    }

    /**
     * 识别结果
     */
    data class PredictionResult(
        val classId: Int,
        val category: String,    // 其他垃圾/厨余垃圾/可回收物/有害垃圾
        val label: String,       // 如: 一次性快餐盒
        val confidence: Float
    ) {
        companion object {
            fun error(msg: String) = PredictionResult(
                classId = -1,
                category = "未知",
                label = msg,
                confidence = 0f
            )
        }
    }

    companion object {
        private const val TAG = "GarbageClassifier"
        private const val MODEL_FILE = "garbage_classifier_fp16.tflite"

        @Volatile
        private var INSTANCE: GarbageClassifier? = null

        /**
         * 获取单例 (复用 Interpreter 避免重复加载)
         */
        fun getInstance(context: Context): GarbageClassifier {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: GarbageClassifier(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
