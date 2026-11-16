package moe.fuqiuluo.shamrock.helper

import android.util.Base64
import com.tencent.qqnt.kernel.nativeinterface.MsgConstant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.fuqiuluo.qqinterface.servlet.transfile.RichProtoSvc
import moe.fuqiuluo.shamrock.helper.db.ImageMapping
import mqq.app.MobileQQ
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * 图片取回器。
 *
 * QQ 消息里只带 md5/uuid，官方 gchat 直链在没有正确 uin 段时会 404，
 * 所以这里按可靠性从高到低尝试多种途径，任何一个成功就返回字节：
 *
 *  1. QQ 自己已经落盘的缓存文件(按 md5 在 QQ 图片目录里找) —— 不走网络，最稳；
 *  2. 消息元素里携带的原图地址(originImageUrl) —— QQ 自己给的地址；
 *  3. RichProto 图片下载协议(GRP_PIC_DW / C2C_PIC_DW) 换出的下载地址；
 *  4. 历史遗留的 gchat 直链拼接(仅作最后兜底)。
 */
internal data class PicPayload(
    val bytes: ByteArray,
    /** 成功取到字节的地址，取不到时为本地路径 */
    val url: String,
    /** 取字节的来源: local_cache / origin_url / rich_proto / legacy_url */
    val source: String
)

internal object PicFetcher {
    /** 单张图片最大允许体积，防止把大图整成巨大 base64 撑爆响应 */
    private const val MAX_BYTES = 16 * 1024 * 1024
    private const val CACHE_SCAN_MAX_DEPTH = 5
    private const val CACHE_SCAN_MAX_ENTRIES = 6000

    private val UA = "Mozilla/5.0 (Linux; Android 12; M2010J19SC) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Version/4.0 Chrome/116.0.0.0 Mobile Safari/537.36"

    /**
     * QQ 自己的图片缓存候选目录。运行在 QQ 进程内，因此这些目录都是可读的。
     */
    private val CacheRoots: List<File> by lazy {
        val ctx = MobileQQ.getContext()
        val roots = mutableListOf<File>()
        // /storage/emulated/0/Android/data/com.tencent.mobileqq
        ctx.getExternalFilesDir(null)?.parentFile?.let { pkg ->
            roots += File(pkg, "Tencent/QQ_Images")
            roots += File(pkg, "Tencent/QQ_Collection/pic")
            roots += File(pkg, "Tencent/QQ_NT/pic")
            roots += File(pkg, "Tencent/QQ_NT")
            roots += File(pkg, "Tencent/MobileQQ/photo")
            roots += File(pkg, "Tencent/MobileQQ")
            roots += File(pkg, "cache")
        }
        // /data/user/0/com.tencent.mobileqq
        ctx.filesDir?.let { dir ->
            roots += dir
            dir.parentFile?.let { base ->
                roots += File(base, "cache")
                roots += File(base, "app_qqimages")
            }
        }
        roots.filter { it.isDirectory }
    }

    /** 按 md5 在 QQ 缓存目录里找已经落盘的图片 */
    fun findLocal(md5: String): File? {
        val key = md5.lowercase()
        CacheRoots.forEach { root ->
            var visited = 0
            val queue = ArrayDeque<Pair<File, Int>>()
            queue.add(root to 0)
            while (queue.isNotEmpty()) {
                val (dir, depth) = queue.removeFirst()
                val children = dir.listFiles() ?: continue
                for (child in children) {
                    if (visited++ > CACHE_SCAN_MAX_ENTRIES) break
                    if (child.isDirectory) {
                        if (depth < CACHE_SCAN_MAX_DEPTH) queue.add(child to depth + 1)
                        continue
                    }
                    val name = child.name.lowercase()
                    if (!name.contains(key)) continue
                    if (child.length() <= 0L) continue
                    if (isImageFile(child)) return child
                }
            }
        }
        return null
    }

    private fun isImageFile(file: File): Boolean {
        val head = ByteArray(12)
        val read = runCatching { file.inputStream().use { it.read(head) } }.getOrElse { return false }
        return looksLikeImage(head, read)
    }

    private fun looksLikeImage(head: ByteArray, len: Int): Boolean {
        if (len < 4) return false
        val b = head
        // JPEG
        if (b[0] == 0xFF.toByte() && b[1] == 0xD8.toByte()) return true
        // PNG
        if (len >= 8 && b[0] == 0x89.toByte() && b[1] == 0x50.toByte() && b[2] == 0x4E.toByte()) return true
        // GIF
        if (b[0] == 0x47.toByte() && b[1] == 0x49.toByte() && b[2] == 0x46.toByte()) return true
        // WEBP / RIFF
        if (len >= 12 && b[0] == 0x52.toByte() && b[1] == 0x49.toByte() && b[8] == 0x57.toByte()) return true
        // BMP
        if (b[0] == 0x42.toByte() && b[1] == 0x4D.toByte()) return true
        return false
    }

    private suspend fun httpGet(url: String): ByteArray? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 5000
            conn.readTimeout = 8000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", UA)
            conn.setRequestProperty("Accept", "image/*,*/*")
            try {
                if (conn.responseCode != HttpURLConnection.HTTP_OK) return@runCatching null
                val declared = conn.contentLength.toLong()
                if (declared > MAX_BYTES) return@runCatching null
                val out = ByteArrayOutputStream()
                conn.inputStream.use { input ->
                    val buf = ByteArray(16 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        if (out.size() > MAX_BYTES) return@runCatching null
                    }
                }
                val bytes = out.toByteArray()
                if (bytes.size < 64 || !looksLikeImage(bytes, bytes.size)) return@runCatching null
                bytes
            } finally {
                conn.disconnect()
            }
        }.getOrNull()
    }

    /**
     * 依次尝试所有来源，返回第一个真正拿到图片字节的结果。
     */
    suspend fun fetch(image: ImageMapping, md5: String): PicPayload? {
        // 1. 消息里记录的本地路径
        if (image.sourcePath.isNotBlank()) {
            val f = File(image.sourcePath)
            if (f.isFile && f.length() in 1..MAX_BYTES.toLong()) {
                val bytes = runCatching { f.readBytes() }.getOrNull()
                if (bytes != null && looksLikeImage(bytes, bytes.size)) {
                    return PicPayload(bytes, f.absolutePath, "source_path")
                }
            }
        }

        // 2. 在 QQ 缓存目录里按 md5 找落盘文件
        findLocal(md5)?.let { f ->
            val bytes = runCatching { f.readBytes() }.getOrNull()
            if (bytes != null && bytes.isNotEmpty()) {
                return PicPayload(bytes, f.absolutePath, "local_cache")
            }
        }

        // 3. QQ 自己给出的原图地址
        if (image.originUrl.startsWith("http")) {
            httpGet(image.originUrl)?.let {
                return PicPayload(it, image.originUrl, "origin_url")
            }
        }

        // 4. RichProto 图片下载协议
        val peer = image.peerUin.ifBlank { "0" }
        val protoUrl = when (image.chatType) {
            MsgConstant.KCHATTYPEGROUP -> RichProtoSvc.getGroupPicDownUrl(peer, md5)
            MsgConstant.KCHATTYPEC2C -> RichProtoSvc.getC2CPicDownUrl(peer, md5, image.fileUuid)
            else -> ""
        }
        if (protoUrl.isNotBlank()) {
            httpGet(protoUrl)?.let {
                return PicPayload(it, protoUrl, "rich_proto")
            }
            LogCenter.log("RichProto 图片下载失败，尝试兜底直链: $protoUrl", Level.WARN)
        }

        // 5. 遗留直链拼接
        val legacy = when (image.chatType) {
            MsgConstant.KCHATTYPEGROUP -> RichProtoSvc.getGroupPicDownUrl(md5)
            MsgConstant.KCHATTYPEC2C -> RichProtoSvc.getC2CPicDownUrl(md5)
            else -> ""
        }
        if (legacy.isNotBlank()) {
            httpGet(legacy)?.let {
                return PicPayload(it, legacy, "legacy_url")
            }
        }
        return null
    }

    fun toBase64(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)
}
