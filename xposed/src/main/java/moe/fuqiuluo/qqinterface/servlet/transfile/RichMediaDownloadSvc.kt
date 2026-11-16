@file:OptIn(DelicateCoroutinesApi::class)

package moe.fuqiuluo.qqinterface.servlet.transfile

import com.tencent.qqnt.kernel.nativeinterface.FileTransNotifyInfo
import com.tencent.qqnt.kernel.nativeinterface.IKernelRichMediaService
import com.tencent.qqnt.kernel.nativeinterface.MsgConstant
import com.tencent.qqnt.kernel.nativeinterface.MsgElement
import com.tencent.qqnt.kernel.nativeinterface.RichDownLoadReq
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import moe.fuqiuluo.shamrock.helper.Level
import moe.fuqiuluo.shamrock.helper.LogCenter
import moe.fuqiuluo.shamrock.helper.db.ImageMapping
import moe.fuqiuluo.shamrock.remote.service.listener.AioListener
import moe.fuqiuluo.shamrock.xposed.helper.NTServiceFetcher

/**
 * NT Kernel 富媒体下载服务。
 * 使用 IKernelRichMediaService.downloadRichMediaInVisit 触发下载，
 * 通过 AioListener.onRichMediaDownloadComplete 回调等待完成。
 */
internal object RichMediaDownloadSvc {

    /** 单张图片下载超时时间 */
    private const val DOWNLOAD_TIMEOUT_MS = 15_000L

    /** 等待回调的完成器映射：key = "msgId:elementId" */
    private val pendingDownloads = java.util.concurrent.ConcurrentHashMap<String, CompletableDeferred<FileTransNotifyInfo>>()

    /**
     * 根据 ImageMapping 触发 NT kernel 富媒体下载，挂起等待完成回调。
     * @return 成功时返回落盘文件路径，失败返回 null
     */
    suspend fun downloadImage(mapping: ImageMapping): String? {
        // 检查必要字段
        if (mapping.msgId == 0L || mapping.elementId == 0L) {
            LogCenter.log("[RichMediaDownload] 缺少必要字段: msgId=${mapping.msgId} elementId=${mapping.elementId} md5=${mapping.fileName}", Level.WARN)
            return null
        }

        val richMediaService = getRichMediaService()
        if (richMediaService == null) {
            LogCenter.log("[RichMediaDownload] 无法获取 IKernelRichMediaService", Level.WARN)
            return null
        }

        val key = "${mapping.msgId}:${mapping.elementId}"
        val completer = CompletableDeferred<FileTransNotifyInfo>()
        pendingDownloads[key] = completer

        LogCenter.log("[RichMediaDownload] 开始下载 key=$key msgId=${mapping.msgId} elementId=${mapping.elementId} peer=${mapping.peerUin}", Level.WARN)

        return try {
            // 构造 MsgElement，至少包含 elementId 和 picElement
            val elem = MsgElement().apply {
                elementType = MsgConstant.KELEMTYPEPIC
                elementId = mapping.elementId
                // 填充必要的 picElement 字段
                val picElement = com.tencent.qqnt.kernel.nativeinterface.PicElement().apply {
                    fileUuid = mapping.fileUuid
                    md5HexStr = mapping.fileName
                    fileSize = mapping.size
                }
                setPicElement(picElement)
            }

            // 构造 RichDownLoadReq (使用全参构造器，参数顺序: downloadType, thumbSize, msgId, msgRandom, msgSeq, msgTime, chatType, senderUid, peerUid, guildId, elem, useHttps)
            val req = RichDownLoadReq(
                1,                          // downloadType: 1=原图
                0,                          // thumbSize: 0=不缩略
                mapping.msgId,              // msgId
                mapping.msgRandom,          // msgRandom
                mapping.msgSeq,             // msgSeq
                mapping.msgTime,            // msgTime
                mapping.chatType,           // chatType
                mapping.senderUin.ifBlank { "0" },  // senderUid
                mapping.peerUin.ifBlank { "0" },    // peerUid
                "",                         // guildId: 群消息不需要
                elem,                       // elem
                1                           // useHttps: 1=使用 HTTPS
            )

            LogCenter.log("[RichMediaDownload] 调用 downloadRichMediaInVisit req=$req", Level.WARN)

            // 触发下载
            richMediaService.downloadRichMediaInVisit(req)

            // 挂起等待回调
            val notifyInfo = withTimeoutOrNull(DOWNLOAD_TIMEOUT_MS) {
                completer.await()
            }

            if (notifyInfo == null) {
                LogCenter.log("[RichMediaDownload] 下载超时 key=$key (${DOWNLOAD_TIMEOUT_MS}ms)", Level.WARN)
                return null
            }

            LogCenter.log("[RichMediaDownload] 收到完成回调 key=$key fileErrCode=${notifyInfo.fileErrCode} fileErrMsg='${notifyInfo.fileErrMsg}' filePath='${notifyInfo.filePath}' msgId=${notifyInfo.msgId} msgElementId=${notifyInfo.msgElementId}", Level.WARN)

            if (notifyInfo.fileErrCode != 0L) {
                LogCenter.log("[RichMediaDownload] 下载失败 key=$key errCode=${notifyInfo.fileErrCode} errMsg=${notifyInfo.fileErrMsg}", Level.WARN)
                return null
            }

            val filePath = notifyInfo.filePath
            if (filePath.isBlank()) {
                LogCenter.log("[RichMediaDownload] 下载成功但 filePath 为空 key=$key", Level.WARN)
                return null
            }

            LogCenter.log("[RichMediaDownload] 下载成功 key=$key filePath=$filePath", Level.WARN)
            filePath

        } catch (e: Exception) {
            LogCenter.log("[RichMediaDownload] 下载异常 key=$key: ${e.message}", Level.WARN)
            null
        } finally {
            pendingDownloads.remove(key)
        }
    }

    /**
     * 获取 IKernelRichMediaService 实例
     */
    private fun getRichMediaService(): IKernelRichMediaService? {
        return try {
            val kernelService = NTServiceFetcher.kernelService
            val wrapperSession = kernelService.wrapperSession
            wrapperSession?.richMediaService
        } catch (e: Exception) {
            LogCenter.log("[RichMediaDownload] 获取 richMediaService 失败: ${e.message}", Level.WARN)
            null
        }
    }

    /**
     * 处理下载完成回调，由 AioListener.onRichMediaDownloadComplete 调用
     */
    internal fun onRichMediaDownloadComplete(notifyInfo: FileTransNotifyInfo) {
        val key = "${notifyInfo.msgId}:${notifyInfo.msgElementId}"
        val completer = pendingDownloads[key]
        if (completer != null) {
            LogCenter.log("[RichMediaDownload] 匹配到等待的下载 key=$key 完成", Level.WARN)
            completer.complete(notifyInfo)
        } else {
            LogCenter.log("[RichMediaDownload] 收到回调但无匹配的等待者 key=$key msgId=${notifyInfo.msgId} msgElementId=${notifyInfo.msgElementId}", Level.DEBUG)
        }
    }
}