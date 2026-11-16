package moe.fuqiuluo.shamrock.remote.action.handlers

import com.tencent.qqnt.kernel.nativeinterface.MsgConstant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import moe.fuqiuluo.qqinterface.servlet.transfile.RichProtoSvc
import moe.fuqiuluo.shamrock.helper.CompressImageUtil
import moe.fuqiuluo.shamrock.helper.Level
import moe.fuqiuluo.shamrock.helper.LogCenter
import moe.fuqiuluo.shamrock.helper.PicFetcher
import moe.fuqiuluo.shamrock.helper.db.ImageDB
import moe.fuqiuluo.shamrock.remote.action.ActionSession
import moe.fuqiuluo.shamrock.remote.action.IActionHandler
import moe.fuqiuluo.shamrock.tools.EmptyJsonString
import moe.fuqiuluo.symbols.OneBotHandler

@OneBotHandler("get_image")
internal object GetImage: IActionHandler() {
    override suspend fun internalHandle(session: ActionSession): String {
        val echo = session.echo
        val file = session.getString("file")
        return invoke(file, echo)
    }

    suspend operator fun invoke(file: String, echo: JsonElement = EmptyJsonString): String {
        LogCenter.log("[GetImage] enter file=$file", Level.WARN)
        val fileMd5 = file
            .replace("{", "")
            .replace("}", "")
            .replace("-", "")
            .split(".")[0].uppercase().trim()
        if (fileMd5.length != 32) {
            return badParam("图片缓存文件名不合法", echo = echo)
        }

        val image = ImageDB.getInstance().imageMappingDao().queryByFileName(fileMd5)
            ?: return logic("只能查询已缓存的图片", echo = echo)

        // 先尝试真正把图片字节取回来（本地缓存 / NT kernel / 原图地址 / QQ 图片下载协议）
        val payload = PicFetcher.fetch(image, fileMd5)

        val fallbackUrl = when (image.chatType) {
            MsgConstant.KCHATTYPEGROUP -> RichProtoSvc.getGroupPicDownUrl(
                image.peerUin.ifBlank { "0" },
                fileMd5,
                image.msgTime.toInt(),
                image.peerUin
            )
            MsgConstant.KCHATTYPEC2C -> RichProtoSvc.getC2CPicDownUrl(
                image.peerUin.ifBlank { "0" },
                fileMd5,
                image.fileUuid
            )
            else -> error("Not supported chat type: ${image.chatType}, convertMsgElementsToMsgSegment::Pic")
        }

        if (payload == null) {
            return ok(GetImageResult(
                image.size,
                image.fileName,
                fallbackUrl,
                null,
                "none",
                "fix11-nt-kernel"
            ), echo = echo)
        }

        // 压缩图片（如需要）
        val compressedBytes = CompressImageUtil.compressIfNeeded(payload.bytes)

        return ok(GetImageResult(
            compressedBytes.size.toLong(),
            image.fileName,
            payload.url,
            PicFetcher.toBase64(compressedBytes),
            payload.source,
            "fix11-nt-kernel"
        ), echo = echo)
    }

    override val requiredParams: Array<String> = arrayOf("file")

    @Serializable
    data class GetImageResult(
        val size: Long,
        val filename: String,
        val url: String,
        /** 图片内容的 base64（取到字节时才有），可直接落盘成图片 */
        val base64: String? = null,
        /** 字节来源: source_path / local_cache / nt_kernel / nt_download / oidb_11c4 / origin_url / rich_proto / nt_local / legacy_url */
        val source: String? = null,
        /** 构建标记：无默认值，保证一定被序列化 */
        val build: String
    )
}
