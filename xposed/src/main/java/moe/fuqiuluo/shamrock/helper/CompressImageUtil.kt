package moe.fuqiuluo.shamrock.helper

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.os.bundleOf
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 图片压缩工具类。
 * 规则：
 * 1. < 200KB 直接返回原字节（小图不重编码）
 * 2. JPEG (FF D8) 直接返回原字节（已压缩格式，避免二次损耗）
 * 3. PNG/GIF/BMP/WEBP 走解码压缩：
 *    - 读宽高，max(宽,高) > 2048 时计算 inSampleSize (2的幂) 降采样
 *    - 有 alpha 通道 → WEBP lossy quality 80 (API 31+)，fallback JPEG
 *    - 无 alpha 通道 → JPEG quality 80
 * 4. 压缩后 >= 原字节 → 返回原字节（负优化保护）
 * 5. 全程在 IO 线程跑，bitmap.recycle() 释放内存
 */
internal object CompressImageUtil {

    private const val SMALL_IMAGE_THRESHOLD = 200 * 1024 // 200KB
    private const val MAX_DIMENSION = 2048
    private const val COMPRESS_QUALITY = 80
    private const val TAG = "CompressImage"

    /**
     * 按需压缩图片字节数组
     * @param bytes 原始图片字节
     * @return 压缩后的字节数组（可能返回原数组）
     */
    @Suppress("UNUSED_PARAMETER")
    suspend fun compressIfNeeded(bytes: ByteArray): ByteArray = withContext(Dispatchers.IO) {
        val originalSize = bytes.size.toLong()

        // 1. 小图直接返回
        if (originalSize < SMALL_IMAGE_THRESHOLD) {
            LogCenter.log("[$TAG] 小图跳过压缩: ${formatBytes(originalSize)}", Level.DEBUG)
            return@withContext bytes
        }

        // 2. 魔数检测格式
        val format = detectFormat(bytes)
        if (format == ImageFormat.JPEG) {
            LogCenter.log("[$TAG] JPEG 已压缩格式跳过: ${formatBytes(originalSize)}", Level.DEBUG)
            return@withContext bytes
        }

        // 3. 解码 Bitmap（带降采样）
        val bitmap = decodeBitmapWithSampling(bytes)
        if (bitmap == null) {
            LogCenter.log("[$TAG] 解码失败，返回原字节: ${formatBytes(originalSize)}", Level.WARN)
            return@withContext bytes
        }

        val decodedWidth = bitmap.width
        val decodedHeight = bitmap.height
        val hasAlpha = bitmap.hasAlpha() || bitmap.config == Bitmap.Config.ARGB_8888 && hasActualAlpha(bitmap)

        // 4. 压缩编码
        val compressedBytes = if (hasAlpha) {
            compressToWebpLossy(bitmap) ?: compressToJpeg(bitmap) // WEBP 不支持时 fallback JPEG
        } else {
            compressToJpeg(bitmap)
        }

        // 5. 负优化保护
        val compressedSize = compressedBytes?.size?.toLong() ?: -1L
        if (compressedBytes == null || compressedSize >= originalSize) {
            LogCenter.log("[$TAG] 负优化保护触发: 原${formatBytes(originalSize)} -> 压缩${compressedBytes?.size?.let { formatBytes(it.toLong()) } ?: "null"}，返回原字节", Level.WARN)
            bitmap.recycle()
            return@withContext bytes
        }

        // 6. 记录详细日志
        val finalFormat = if (hasAlpha) "WEBP_LOSSY" else "JPEG"
        LogCenter.log(
            "[$TAG] 压缩完成: " +
            "原格式=$format/原尺寸=${bitmap.width}x${bitmap.height}/原字节=${formatBytes(originalSize)} -> " +
            "压缩格式=$finalFormat/新尺寸=${decodedWidth}x${decodedHeight}/新字节=${formatBytes(compressedSize)} " +
            "(节省 ${formatBytes(originalSize - compressedSize)}, ${((originalSize - compressedSize) * 100f / originalSize).toString().take(5)}%)",
            Level.WARN
        )

        bitmap.recycle()
        compressedBytes!!
    }

    /**
     * 检测图片格式（通过魔数）
     */
    private fun detectFormat(bytes: ByteArray): ImageFormat {
        if (bytes.size < 2) return ImageFormat.UNKNOWN
        // JPEG: FF D8
        if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()) return ImageFormat.JPEG
        // PNG: 89 50 4E 47
        if (bytes.size >= 4 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() && bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte()) return ImageFormat.PNG
        // GIF: 47 49 46 (GIF87a/GIF89a)
        if (bytes.size >= 3 && bytes[0] == 0x47.toByte() && bytes[1] == 0x49.toByte() && bytes[2] == 0x46.toByte()) return ImageFormat.GIF
        // WEBP: RIFF....WEBP
        if (bytes.size >= 12 && bytes[0] == 0x52.toByte() && bytes[1] == 0x49.toByte() && bytes[2] == 0x46.toByte() && bytes[3] == 0x46.toByte()
            && bytes[8] == 0x57.toByte() && bytes[9] == 0x45.toByte() && bytes[10] == 0x42.toByte() && bytes[11] == 0x50.toByte()) return ImageFormat.WEBP
        // BMP: 42 4D
        if (bytes.size >= 2 && bytes[0] == 0x42.toByte() && bytes[1] == 0x4D.toByte()) return ImageFormat.BMP
        return ImageFormat.UNKNOWN
    }

    /**
     * 解码 Bitmap，必要时降采样（inSampleSize 为 2 的幂）
     */
    private fun decodeBitmapWithSampling(bytes: ByteArray): Bitmap? {
        // 第一次解码：只读 bounds
        val boundsOptions = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOptions)

        val outWidth = boundsOptions.outWidth
        val outHeight = boundsOptions.outHeight
        if (outWidth <= 0 || outHeight <= 0) return null

        // 计算 inSampleSize（2 的幂），使最大边 <= MAX_DIMENSION
        var inSampleSize = 1
        val maxDim = max(outWidth, outHeight)
        if (maxDim > MAX_DIMENSION) {
            val targetSize = MAX_DIMENSION.toFloat()
            var scale = maxDim.toFloat() / targetSize
            // 取最近的 2 的幂
            inSampleSize = 1
            while (inSampleSize < scale) {
                inSampleSize *= 2
            }
        }

        // 第二次解码：实际加载像素
        val decodeOptions = BitmapFactory.Options().apply {
            inJustDecodeBounds = false
            inSampleSize = inSampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888 // 保留 alpha 通道信息
            inMutable = true // 便于后续 recycle
        }

        return try {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
        } catch (e: OutOfMemoryError) {
            // OOM 时尝试更大的 inSampleSize
            decodeOptions.inSampleSize = inSampleSize * 2
            try {
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
            } catch (e2: Exception) {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 压缩为 WEBP lossy (Android 12+ / API 31+)
     */
    private fun compressToWebpLossy(bitmap: Bitmap): ByteArray? {
        // WEBP_LOSSY 是 API 31 (Android 12) 引入
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) {
            return null
        }
        return try {
            val out = java.io.ByteArrayOutputStream()
            val success = bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, COMPRESS_QUALITY, out)
            if (success) out.toByteArray() else null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 压缩为 JPEG
     */
    private fun compressToJpeg(bitmap: Bitmap): ByteArray? {
        return try {
            val out = java.io.ByteArrayOutputStream()
            val success = bitmap.compress(Bitmap.CompressFormat.JPEG, COMPRESS_QUALITY, out)
            if (success) out.toByteArray() else null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 检查 bitmap 是否实际包含透明像素（ARGB_8888 但可能全不透明）
     */
    private fun hasActualAlpha(bitmap: Bitmap): Boolean {
        // 采样检查：只检查四角和中心，避免全图遍历
        val w = bitmap.width
        val h = bitmap.height
        val checkPoints = listOf(
            0 to 0,
            w - 1 to 0,
            0 to h - 1,
            w - 1 to h - 1,
            w / 2 to h / 2
        )
        for ((x, y) in checkPoints) {
            if (x >= 0 && x < w && y >= 0 && y < h) {
                val pixel = bitmap.getPixel(x, y)
                val alpha = pixel shr 24 and 0xFF
                if (alpha < 255) return true
            }
        }
        return false
    }

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1024 -> "${bytes}B"
            bytes < 1024 * 1024 -> String.format("%.1fKB", bytes / 1024.0)
            else -> String.format("%.1fMB", bytes / (1024.0 * 1024.0))
        }
    }

    private enum class ImageFormat {
        JPEG, PNG, GIF, WEBP, BMP, UNKNOWN
    }
}