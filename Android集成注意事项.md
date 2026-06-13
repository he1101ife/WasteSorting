# 垃圾分类 TFLite 模型 - Android 集成注意事项

## 一、模型输入输出

### 输入
| 项目 | 说明 |
|------|------|
| 尺寸 | 224 × 224 × 3（RGB） |
| 像素范围 | `[-1, 1]` |
| 预处理公式 | `(pixel / 127.5) - 1.0` |

### 输出
- 40 个 float 值（Softmax 概率，总和 = 1）
- 每个索引对应一个类别（0 ~ 39）
- 取 **argmax** 得到预测类别 ID

> TFLite 只输出数字索引，不输出字符串标签。标签映射需要在 Android 端自行维护。

---

## 二、数据库设计

### 类别映射表

```sql
CREATE TABLE garbage_classes (
    class_id    INTEGER PRIMARY KEY,  -- 0~39，与 TFLite 输出索引对应
    category    TEXT NOT NULL,         -- 一级分类: 其他垃圾/厨余垃圾/可回收物/有害垃圾
    label       TEXT NOT NULL UNIQUE,  -- 二级标签: 一次性快餐盒、烟蒂...
    icon_url    TEXT,                  -- 可选：类别图标
    tips        TEXT DEFAULT ''        -- 投放提示（需手动补充，模型不输出此字段）
);
```

### 数据示例

| class_id | category | label | tips（需手动补充） |
|----------|----------|-------|-------------------|
| 0 | 其他垃圾 | 一次性快餐盒 | |
| 2 | 厨余垃圾 | 茶叶渣 | |
| 12 | 其他垃圾 | 烟蒂 | |
| 37 | 有害垃圾 | 干电池 | |

### 初始化方式

将 `label_map.json` 放入 `app/src/main/assets/`，App 启动时解析到 SQLite（**tips 字段留空，需手动补充**）：

```kotlin
val json = assets.open("label_map.json").bufferedReader().use { it.readText() }
val arr = JSONArray(json)
val db = dbHelper.writableDatabase
for (i in 0 until arr.length()) {
    val obj = arr.getJSONObject(i.toString())
    val category = obj.getString("category")
    val label = obj.getString("label")
    val classId = obj.getInt("index")
    db.execSQL("INSERT INTO garbage_classes (class_id, category, label) VALUES (?, ?, ?)",
        arrayOf(classId, category, label))
}
```

> `label_map.json` 是一个 JSON 数组，已按 TF 字符串排序后的索引排列，直接遍历即可得到 0~39 的正确映射。
> **不要使用 `garbage_dict.json` 做数据库预填充**，它的 key 是数字顺序，与 TFLite 输出索引不一致。

---

## 三、推理流程

```
输入图片 → 预处理到 [-1,1] → TFLite 推理 → 取 argmax 得到 classId → 数据库查询 → 显示
```

```kotlin
// 1. 预处理
val input = (pixel / 127.5f) - 1.0f

// 2. TFLite 推理
interpreter.run(inputBuffer, outputBuffer)
val classId = argmax(outputBuffer)  // 0~39

// 3. 数据库查询显示
val cursor = db.rawQuery(
    "SELECT category, label FROM garbage_classes WHERE class_id = ?",
    arrayOf(classId.toString())
)
```

---

## 四、注意事项（必读）

### 1. 索引顺序不可更改
class_id 0~39 必须与 TFLite 模型输出的 40 个索引严格一一对应。**不允许**在数据库侧对类别做增删改排序。

### 2. 数据库预填充
建议首次启动从 assets 目录的 JSON 文件初始化数据库，不要依赖网络请求。

### 3. TFLite 文件放置位置
```
app/src/main/assets/
├── garbage_classifier_fp16.tflite   ← 推荐使用 Float16 版本
└── label_map.json                   ← 类别映射数据（注意：不要使用 garbage_dict.json）
```

### 4. 预处理必须一致
Android 端的预处理必须与训练时完全一致：`(pixel / 127.5) - 1.0`，否则推理结果会严重偏差。不能使用 `pixel / 255.0`。

### 5. 模型选择建议
| 模型 | 大小 | 精度 | 推荐场景 |
|------|------|------|----------|
| `garbage_classifier_fp16.tflite` | ~4.5 MB | 几乎无损 | **默认推荐** |
| `garbage_classifier.tflite` | ~9 MB | 最高 | 精度优先 |
| `garbage_classifier_int8.tflite` | ~2.5 MB | 略有损失 | 极致压缩 |

### 6. CPU / GPU 推理
- CPU 推理直接用 `Interpreter` 即可
- 如需 GPU 加速（Android 10+），使用 `GpuDelegate`：
```kotlin
val options = Interpreter.Options().apply {
    addDelegate(GpuDelegate())
}
interpreter = Interpreter(model, options)
```

### 7. 内存管理
- TFLite Interpreter 创建后应复用，不要每次推理都重新加载
- Activity 销毁时调用 `interpreter.close()` 释放资源

---

## 五、完整工具类参考结构

```kotlin
class GarbageClassifier(private val context: Context) {
    private var interpreter: Interpreter
    private val dbHelper: DBHelper

    fun classify(bitmap: Bitmap): PredictionResult {
        val input = preprocess(bitmap)       // 224x224 → [-1,1]
        val output = Array(1) { FloatArray(40) }
        interpreter.run(input, output)       // 推理
        val classId = argmax(output[0])      // 取最佳索引
        return queryDatabase(classId)        // 查数据库
    }

    data class PredictionResult(
        val classId: Int,
        val category: String,   // 其他垃圾/厨余垃圾/可回收物/有害垃圾
        val label: String,      // 如: 饮料瓶
        val confidence: Float
    )
}
```

---

*对应 TFLite 模型文件：`garbage_classifier_fp16.tflite`*
*对应标签映射文件：`label_map.json`*
