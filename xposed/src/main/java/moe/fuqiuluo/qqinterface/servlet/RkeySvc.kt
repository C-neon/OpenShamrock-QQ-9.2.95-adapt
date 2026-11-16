package moe.fuqiuluo.qqinterface.servlet

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.fuqiuluo.shamrock.helper.Level
import moe.fuqiuluo.shamrock.helper.LogCenter
import protobuf.oidb.cmd0x9067.NtRichMediaReq
import protobuf.oidb.cmd0x9067.NtRichMediaRsp
import protobuf.oidb.cmd0x9067.NtReqHead
import protobuf.oidb.cmd0x9067.NtCommonHead
import protobuf.oidb.cmd0x9067.NtSceneInfo
import protobuf.oidb.cmd0x9067.NtClientMeta
import protobuf.oidb.cmd0x9067.NtDownloadRKeyReq
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import moe.fuqiuluo.shamrock.tools.slice

internal object RkeySvc: BaseSvc() {

    private data class RkeyEntry(
        val rkey: String,
        val createTime: Long,
        val ttl: Long
    ) {
        fun isExpired(): Boolean {
            return System.currentTimeMillis() > createTime + ttl
        }
    }

    private val rkeyCache = mutableMapOf<String, RkeyEntry>()
    private val lock = Any()

    /**
     * 获取 NT 图片下载 rkey
     * @param appId 应用ID：群图 1407，私聊图 1406
     * @param fileId 文件ID (fileUuid)
     * @param type 类型：10=私聊，20=群
     * @param trpc 是否使用 TRPC 调用
     * @return 规范化后的 rkey (包含 &rkey= 前缀)
     */
    suspend fun getRkey(
        appId: Int,
        fileId: String,
        type: Int,
        trpc: Boolean = false
    ): String? {
        val cacheKey = "$type:$appId:$fileId"
        
        // 检查缓存
        synchronized(lock) {
            rkeyCache[cacheKey]?.let { entry ->
                if (!entry.isExpired()) {
                    LogCenter.log("[RkeySvc] 缓存命中 type=$type appId=$appId fileId=${fileId.take(20)} rkey=${maskRkey(entry.rkey)}", Level.DEBUG)
                    return entry.rkey
                } else {
                    LogCenter.log("[RkeySvc] 缓存过期 type=$type appId=$appId fileId=${fileId.take(20)}", Level.DEBUG)
                    rkeyCache.remove(cacheKey)
                }
            }
        }

        // 从网络获取
        val rkey = fetchRkeyFromNetwork(appId, fileId, type, trpc)
        if (rkey != null) {
            // 缓存 rkey
            synchronized(lock) {
                rkeyCache[cacheKey] = RkeyEntry(
                    rkey = rkey,
                    createTime = System.currentTimeMillis(),
                    ttl = 3600_000L // 默认 1 小时，实际会从响应中获取
                )
            }
            return rkey
        }
        return null
    }

    private suspend fun fetchRkeyFromNetwork(
        appId: Int,
        fileId: String,
        type: Int,
        trpc: Boolean
    ): String? {
        return withContext(Dispatchers.Default) {
            // 构造 NtRichMediaReq 请求结构。注意：OIDB 的 bytes_bodybuffer 直接就是
            // NtRichMediaReq 的编码，不能再包一层 ReqBody（那会导致多一层 field 1）。
            val req = NtRichMediaReq(
                reqHead = NtReqHead(
                    common = NtCommonHead(
                        requestId = 1,
                        command = 202
                    ),
                    scene = NtSceneInfo(
                        requestType = 2,
                        businessType = 1
                    ),
                    client = NtClientMeta(
                        agentType = 2
                    )
                ),
                downloadRKey = NtDownloadRKeyReq(
                    types = listOf(10, 20, 2)
                )
            )
            
            val bodyBytes = ProtoBuf.encodeToByteArray(req)
            LogCenter.log("[RkeySvc] 发送 OIDB 0x9067 type=$type appId=$appId fileId=${fileId.take(20)} bodyLen=${bodyBytes.size} trpc=$trpc", Level.WARN)

            val buffer = sendOidbAW("OidbSvcTrpcTcp.0x9067_202", 0x9067, 202, bodyBytes, trpc)
            
            if (buffer == null) {
                LogCenter.log("[RkeySvc] 发送 OIDB 0x9067 无响应 (buffer=null) type=$type appId=$appId trpc=$trpc", Level.WARN)
                return@withContext null
            }
            
            LogCenter.log("[RkeySvc] 收到 OIDB 0x9067 响应 type=$type appId=$appId bufferLen=${buffer.size}", Level.WARN)

            // 解析响应
            try {
                val oidbPkg = tencent.im.oidb.oidb_sso.OIDBSSOPkg()
                oidbPkg.mergeFrom(buffer.slice(4))
                
                val resultCode = oidbPkg.uint32_result.get()
                LogCenter.log("[RkeySvc] OIDB 0x9067 resultCode=$resultCode type=$type", Level.WARN)
                
                if (resultCode != 0) {
                    val errMsg = oidbPkg.str_error_msg.get()
                    LogCenter.log("[RkeySvc] OIDB 0x9067 返回非0 resultCode=$resultCode errMsg=$errMsg type=$type", Level.WARN)
                    return@withContext null
                }

                // OIDB body 直接就是 NtRichMediaRsp，不要包 RspBody 层
                val ntRsp = ProtoBuf.decodeFromByteArray<NtRichMediaRsp>(
                    oidbPkg.bytes_bodybuffer.get().toByteArray()
                )
                
                if (ntRsp == null) {
                    LogCenter.log("[RkeySvc] OIDB 0x9067 响应中无 ntRichMediaRsp type=$type", Level.WARN)
                    return@withContext null
                }

                val respHead = ntRsp.respHead
                val retCode = respHead?.retCode ?: -1
                val retMsg = respHead?.message ?: "unknown"
                val downloadRKey = ntRsp.downloadRKey
                val rkeys = downloadRKey?.rkeys

                if (rkeys == null || rkeys.isEmpty()) {
                    LogCenter.log("[RkeySvc] OIDB 0x9067 rkeys 为空 type=$type", Level.WARN)
                    return@withContext null
                }

                // 按 type 选择对应的 rkey (type 10=私聊, 20=群)
                val matchedRkey = rkeys.firstOrNull { it.type == type } ?: rkeys.firstOrNull()
                if (matchedRkey == null) {
                    LogCenter.log("[RkeySvc] OIDB 0x9067 无匹配 rkey type=$type rkeys=${rkeys.map { it.type }}", Level.WARN)
                    return@withContext null
                }

                val rkey = matchedRkey.rkey
                val createTime = (matchedRkey.rkeyCreateTime ?: 0L).toLong()
                val ttl = matchedRkey.rkeyTtlSec ?: 3600L
                val actualCreateTime = if (createTime > 0) createTime * 1000L else System.currentTimeMillis()
                val actualTtl = ttl * 1000L

                LogCenter.log("[RkeySvc] OIDB 0x9067 解析: retCode=$retCode retMsg=$retMsg matchedType=${matchedRkey.type} rkeyLen=${rkey?.length ?: 0} rkeyPrefix=${maskRkey(rkey)} createTime=$createTime ttlSec=$ttl type=$type", Level.WARN)

                if (retCode != 0) {
                    LogCenter.log("[RkeySvc] OIDB 0x9067 业务返回非0 retCode=$retCode retMsg=$retMsg type=$type", Level.WARN)
                    return@withContext null
                }

                if (rkey == null || rkey.isBlank()) {
                    LogCenter.log("[RkeySvc] OIDB 0x9067 rkey 为空 type=$type", Level.WARN)
                    return@withContext null
                }

                // 规范化 rkey：确保包含 &rkey= 前缀
                val normalizedRkey = if (rkey.startsWith("&rkey=")) rkey else "&rkey=$rkey"
                LogCenter.log("[RkeySvc] rkey 规范化完成 type=$type normalizedRkeyPrefix=${maskRkey(normalizedRkey)}", Level.DEBUG)
                
                // 更新缓存时使用实际的 createTime 和 ttl
                synchronized(lock) {
                    val cacheKey = "$type:$appId:$fileId"
                    rkeyCache[cacheKey] = RkeyEntry(
                        rkey = normalizedRkey,
                        createTime = actualCreateTime,
                        ttl = actualTtl
                    )
                }
                
                return@withContext normalizedRkey
            } catch (e: Exception) {
                LogCenter.log("[RkeySvc] 解析 OIDB 0x9067 响应异常: ${e.message} type=$type", Level.WARN)
                e.printStackTrace()
                return@withContext null
            }
        }
    }

    private fun maskRkey(rkey: String?): String {
        return rkey?.let {
            if (it.length <= 8) it else "${it.take(8)}..."
        } ?: "null"
    }

    /**
     * 强制刷新 rkey（忽略缓存）
     */
    suspend fun refreshRkey(
        appId: Int,
        fileId: String,
        type: Int,
        trpc: Boolean = false
    ): String? {
        val cacheKey = "$type:$appId:$fileId"
        synchronized(lock) {
            rkeyCache.remove(cacheKey)
        }
        return getRkey(appId, fileId, type, trpc)
    }

    fun clearCache() {
        synchronized(lock) {
            rkeyCache.clear()
        }
    }
}