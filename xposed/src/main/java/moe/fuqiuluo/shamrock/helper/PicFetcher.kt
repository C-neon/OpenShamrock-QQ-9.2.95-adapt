package moe.fuqiuluo.shamrock.helper

import android.util.Base64
import com.tencent.qqnt.kernel.nativeinterface.MsgConstant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.fuqiuluo.qqinterface.servlet.RkeySvc
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
 *  3. **新增：NT 多媒体下载 (multimedia.nt.qq.com.cn) + rkey (OIDB 0x9067)** —— 最优先的网络策略；
 *  4. **新增：备选 0x11c4_200 (fileUuid + md5 -> IndexNode -> domain+urlPath+rKeyParam)**；
 *  5. RichProto 图片下载协议(GRP_PIC_DW / C2C_PIC_DW) 换出的下载地址（降级）；
 *  6. QQ NT 本地图片库：按 fileUuid / md5 找落盘副本；
 *  7. 历史遗留的 gchat 直链拼接(仅作最后兜底)。
 */
internal data class PicPayload(
    val bytes: ByteArray,
    /** 成功取到字节的地址，取不到时为本地路径 */
    val url: String,
    /** 取字节的来源: local_cache / origin_url / nt_download / oidb_11c4 / rich_proto / nt_local / legacy_url */
    val source: String
)

internal object PicFetcher {
    /** 代码版本标识，用于日志确认跑的是新代码 */
    private const val CODE_VERSION = "fix11-nt-rkey"
    
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
            conn.setRequestProperty("Referer", "https://qun.qq.com/")
            try {
                if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                    LogCenter.log("[PicFetcher] HTTP 状态码: ${conn.responseCode} url=${url.take(120)}", Level.WARN)
                    return@runCatching null
                }
                val declared = conn.contentLength.toLong()
                if (declared > MAX_BYTES) {
                    LogCenter.log("[PicFetcher] 文件过大: $declared bytes url=${url.take(120)}", Level.WARN)
                    return@runCatching null
                }
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
                if (bytes.size < 64 || !looksLikeImage(bytes, bytes.size)) {
                    LogCenter.log("[PicFetcher] 非有效图片或过小: ${bytes.size} bytes url=${url.take(120)}", Level.WARN)
                    return@runCatching null
                }
                bytes
            } finally {
                conn.disconnect()
            }
        }.getOrNull()
    }

    /**
     * 带详细日志的 HTTP GET
     */
    private suspend fun httpGetWithLogging(url: String, tag: String): ByteArray? {
        LogCenter.log("[PicFetcher] $tag 尝试下载: $url", Level.WARN)
        val result = httpGet(url)
        if (result != null) {
            LogCenter.log("[PicFetcher] $tag 成功获取图片: ${result.size} bytes url=${url.take(120)}", Level.WARN)
        } else {
            LogCenter.log("[PicFetcher] $tag 下载失败: $url", Level.WARN)
        }
        return result
    }

    /**
     * 解析 originUrl 中的 appid 和 fileid
     * originUrl 格式: /download?appid=1407&fileid=...&spec=0
     */
    private fun parseOriginUrl(originUrl: String): Pair<Int, String>? {
        try {
            val uri = java.net.URI("https://dummy.com$originUrl") // 补全为绝对 URI 以便解析 query
            val query = uri.query ?: return null
            val params = query.split("&").associate { it.split("=").let { (k, v) -> k to v } }
            val appId = params["appid"]?.toIntOrNull() ?: return null
            val fileId = params["fileid"] ?: return null
            return appId to fileId
        } catch (e: Exception) {
            return null
        }
    }

    /**
     * 构建 NT 多媒体下载 URL
     */
    private fun buildNtDownloadUrl(appId: Int, fileId: String, rkey: String): String {
        return "https://multimedia.nt.qq.com.cn/download?appid=$appId&fileid=$fileId$rkey"
    }

    /**
     * NT 多媒体下载策略（最优先网络策略）
     * 解析 originUrl 的 appid/fileid，通过 OIDB 0x9067 获取 rkey，拼接下载 URL
     */
    private suspend fun tryNtDownload(image: ImageMapping, md5: String): PicPayload? {
        // 从 originUrl 解析 appId 和 fileId
        val parsed = if (image.originUrl.isNotBlank()) {
            parseOriginUrl(image.originUrl)
        } else null

        // 如果 originUrl 没有，尝试从其他字段推断
        val appId: Int
        val fileId: String
        if (parsed != null) {
            appId = parsed.first
            fileId = parsed.second
        } else {
            // 群图默认 appid=1407，私聊图默认 appid=1406
            appId = if (image.chatType == MsgConstant.KCHATTYPEGROUP) 1407 else 1406
            fileId = image.fileUuid.ifBlank { md5 }
        }

        val type = if (image.chatType == MsgConstant.KCHATTYPEGROUP) 20 else 10
        
        LogCenter.log("[PicFetcher] NT 下载策略开始: appId=$appId fileId=${fileId.take(20)} type=$type chatType=${image.chatType}", Level.WARN)

        // 尝试获取 rkey（先不带 trpc，失败后重试带 trpc）
        var rkey = RkeySvc.getRkey(appId, fileId, type, trpc = false)
        if (rkey == null) {
            LogCenter.log("[PicFetcher] NT 下载策略: 首次获取 rkey 失败，尝试 trpc=true", Level.WARN)
            rkey = RkeySvc.getRkey(appId, fileId, type, trpc = true)
        }
        
        if (rkey == null) {
            LogCenter.log("[PicFetcher] NT 下载策略: 获取 rkey 最终失败", Level.WARN)
            return null
        }

        // 构建下载 URL
        val downloadUrl = buildNtDownloadUrl(appId, fileId, rkey)
        LogCenter.log("[PicFetcher] NT 下载 URL: $downloadUrl", Level.WARN)

        // 尝试下载
        var bytes = httpGetWithLogging(downloadUrl, "NT下载(首次)")
        if (bytes != null) {
            return PicPayload(bytes, downloadUrl, "nt_download")
        }

        // 如果返回 invalid rkey，强制刷新 rkey 重试一次
        LogCenter.log("[PicFetcher] NT 下载首次失败，强制刷新 rkey 重试", Level.WARN)
        var freshRkey = RkeySvc.refreshRkey(appId, fileId, type, trpc = false)
        if (freshRkey == null) {
            freshRkey = RkeySvc.refreshRkey(appId, fileId, type, trpc = true)
        }
        
        if (freshRkey != null) {
            val retryUrl = buildNtDownloadUrl(appId, fileId, freshRkey)
            LogCenter.log("[PicFetcher] NT 下载重试 URL: $retryUrl", Level.WARN)
            bytes = httpGetWithLogging(retryUrl, "NT下载(重试)")
            if (bytes != null) {
                return PicPayload(bytes, retryUrl, "nt_download_retry")
            }
        }

        LogCenter.log("[PicFetcher] NT 下载策略最终失败", Level.WARN)
        return null
    }

    /**
     * 备选策略：OIDB 0x11c4_200 (fileUuid + md5 -> IndexNode -> domain+urlPath+rKeyParam)
     * 待实现：目前作为占位，返回 null
     */
    private suspend fun tryOidb11c4(image: ImageMapping, md5: String): PicPayload? {
        // TODO: 实现 0x11c4_200 协议
        // 需要构造 IndexNode (fileUuid + md5)，调用 OIDB 获取 domain + urlPath + rKeyParam
        LogCenter.log("[PicFetcher] 0x11c4_200 备选策略暂未实现", Level.DEBUG)
        return null
    }

    /**
     * 依次尝试所有来源，返回第一个真正拿到图片字节的结果。
     */
    suspend fun fetch(image: ImageMapping, md5: String): PicPayload? {
        LogCenter.log(
            "[PicFetcher] ====== 开始取图 code_version=$CODE_VERSION ======\n" +
            "  md5=$md5 chatType=${image.chatType} peer='${image.peerUin}' uuid='${image.fileUuid}' " +
            "size=${image.size} src='${image.sourcePath}' origin='${image.originUrl.take(60)}' " +
            "msgTime=${image.msgTime} sender='${image.senderUin}'",
            Level.WARN
        )
        // 1. 消息里记录的本地路径
        if (image.sourcePath.isNotBlank()) {
            val f = File(image.sourcePath)
            if (f.isFile && f.length() in 1..MAX_BYTES.toLong()) {
                val bytes = runCatching { f.readBytes() }.getOrNull()
                if (bytes != null && looksLikeImage(bytes, bytes.size)) {
                    LogCenter.log("[PicFetcher] 策略1 source_path 成功: ${bytes.size} bytes", Level.WARN)
                    return PicPayload(bytes, f.absolutePath, "source_path")
                }
            }
        }

        // 2. 在 QQ 缓存目录里按 md5 找落盘文件
        findLocal(md5)?.let { f ->
            val bytes = runCatching { f.readBytes() }.getOrNull()
            if (bytes != null && bytes.isNotEmpty()) {
                LogCenter.log("[PicFetcher] 策略2 local_cache 成功: ${bytes.size} bytes path=${f.absolutePath}", Level.WARN)
                return PicPayload(bytes, f.absolutePath, "local_cache")
            }
        }
        LogCenter.log("[PicFetcher] 即将尝试 NT 下载策略 (策略3)", Level.WARN)

        // 3. 【新增】NT 多媒体下载 + rkey (最优先网络策略)
        val ntResult = tryNtDownload(image, md5)
        LogCenter.log("[PicFetcher] NT 下载策略返回: ${ntResult?.source ?: "null"}", Level.WARN)
        ntResult?.let { return it }

        // 4. 【新增】备选 0x11c4_200
        tryOidb11c4(image, md5)?.let { return it }

        // 5. QQ 自己给出的原图地址（支持相对路径，原有逻辑）
        if (image.originUrl.isNotBlank()) {
            val urlList = buildFullImageUrl(image.originUrl)
            for ((url, tag) in urlList) {
                val result = httpGetWithLogging(url, tag)
                if (result != null) {
                    return PicPayload(result, url, "origin_url($tag)")
                }
            }
        }

        // 6. RichProto 图片下载协议（降级策略）
        val peer = image.peerUin.ifBlank { "0" }
        val t = image.msgTime.toInt()
        if (image.chatType == MsgConstant.KCHATTYPEGROUP) {
            val combos = listOf(
                Triple(t, "", "A:second=peer,msgTime=msg"),
                Triple(t, "0", "B:second=0,msgTime=msg"),
                Triple(t, image.senderUin, "C:second=sender,msgTime=msg"),
                Triple(0, "", "D:second=peer,msgTime=0")
            )
            for ((ts, second, tag) in combos) {
                val u = runCatching {
                    RichProtoSvc.getGroupPicDownUrl(peer, md5, ts, second)
                }.getOrElse { e ->
                    LogCenter.log("[PicFetcher] $tag 抛异常: ${e.message}", Level.WARN)
                    ""
                }
                LogCenter.log("[PicFetcher] $tag peer=$peer second='$second' ts=$ts -> '${u.take(80)}'", Level.WARN)
                if (u.isNotBlank()) {
                    httpGet(u)?.let { return PicPayload(it, u, "rich_proto") }
                    LogCenter.log("[PicFetcher] $tag 拿到 URL 但下载失败", Level.WARN)
                }
            }
        } else if (image.chatType == MsgConstant.KCHATTYPEC2C) {
            val u = runCatching {
                RichProtoSvc.getC2CPicDownUrl(peer, md5, image.fileUuid)
            }.getOrNull().orEmpty()
            LogCenter.log("[PicFetcher] C2C peer=$peer uuid='${image.fileUuid}' -> '${u.take(80)}'", Level.WARN)
            if (u.isNotBlank()) {
                httpGet(u)?.let { return PicPayload(it, u, "rich_proto") }
            }
        }

        // 7. QQ NT 本地图片库：按 fileUuid / 文件名直接找落盘副本
        run {
            val keys = listOfNotNull(
                image.fileUuid.takeIf { it.isNotBlank() },
                md5.lowercase(),
                md5.uppercase()
            )
            val ntRoots = listOf(
                "/storage/emulated/0/Android/data/com.tencent.mobileqq/Tencent/QQ_NT",
                "/storage/emulated/0/Android/data/com.tencent.mobileqq/Tencent/QQ_Images",
                "/data/data/com.tencent.mobileqq/files/qqnt",
                "/data/data/com.tencent.mobileqq/files/QQ_NT"
            ).map { File(it) }.filter { it.isDirectory }
            for (root in ntRoots) {
                for (k in keys) {
                    val hit = root.walkTopDown()
                        .maxDepth(6)
                        .take(4000)
                        .firstOrNull { it.isFile && it.length() > 1024 && it.name.contains(k, ignoreCase = true) }
                    if (hit != null) {
                        val bytes = runCatching { hit.readBytes() }.getOrNull()
                        if (bytes != null && looksLikeImage(bytes, bytes.size)) {
                            LogCenter.log("[PicFetcher] NT 本地命中 ${hit.absolutePath}", Level.WARN)
                            return@run PicPayload(bytes, hit.absolutePath, "nt_local")
                        }
                    }
                }
            }
            LogCenter.log("[PicFetcher] NT 本地未命中 roots=${ntRoots.size}", Level.WARN)
        }

        // 8. 遗留直链拼接（最后兜底）
        val legacy = when (image.chatType) {
            MsgConstant.KCHATTYPEGROUP -> RichProtoSvc.getGroupPicDownUrl(
                peer,
                md5,
                image.msgTime.toInt(),
                image.peerUin
            )
            MsgConstant.KCHATTYPEC2C -> RichProtoSvc.getC2CPicDownUrl(
                peer,
                md5,
                image.fileUuid ?: ""
            )
            else -> ""
        }
        if (legacy.isNotBlank()) {
            httpGet(legacy)?.let {
                return PicPayload(it, legacy, "legacy_url")
            }
        }
        LogCenter.log("[PicFetcher] 所有来源均失败 md5=$md5", Level.WARN)
        return null
    }

    /**
     * 构建完整的图片 URL，支持相对路径（以 "/" 开头的路径）。
     * 按优先级顺序尝试不同的前缀：gchat.qpic.cn、multimedia.nt.qq.com.cn
     */
    private fun buildFullImageUrl(originUrl: String): List<Pair<String, String>> {
        if (originUrl.startsWith("http")) {
            return listOf(originUrl to "absolute")
        }
        // 处理相对路径
        if (originUrl.startsWith("/")) {
            val baseUrls = listOf(
                "https://gchat.qpic.cn",
                "https://multimedia.nt.qq.com.cn"
            )
            return baseUrls.map { base ->
                val fullUrl = base + originUrl
                fullUrl to "relative($base)"
            }
        }
        // 其他情况直接返回原 URL
        return listOf(originUrl to "other")
    }

    fun toBase64(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)
}