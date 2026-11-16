@file:OptIn(ExperimentalSerializationApi::class)
package protobuf.oidb.cmd0x9067

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import kotlinx.serialization.protobuf.ProtoPacked

@Serializable
data class NtRichMediaReq(
    @ProtoNumber(1) var reqHead: NtReqHead? = null,
    @ProtoNumber(3) var download: NtDownloadReq? = null,
    @ProtoNumber(4) var downloadRKey: NtDownloadRKeyReq? = null,
)

@Serializable
data class NtReqHead(
    @ProtoNumber(1) var common: NtCommonHead? = null,
    @ProtoNumber(2) var scene: NtSceneInfo? = null,
    @ProtoNumber(3) var client: NtClientMeta? = null,
)

@Serializable
data class NtCommonHead(
    @ProtoNumber(1) var requestId: Int? = null,
    @ProtoNumber(2) var command: Int? = null,
)

@Serializable
data class NtSceneInfo(
    @ProtoNumber(101) var requestType: Int? = null,
    @ProtoNumber(102) var businessType: Int? = null,
    @ProtoNumber(200) var sceneType: Int? = null,
    @ProtoNumber(201) var c2c: NtC2CInfo? = null,
    @ProtoNumber(202) var group: NtGroupInfo? = null,
)

@Serializable
data class NtC2CInfo(
    @ProtoNumber(1) var accountType: Int? = null,
    @ProtoNumber(2) var targetUid: String? = null,
)

@Serializable
data class NtGroupInfo(
    @ProtoNumber(1) var groupUin: Int? = null,
)

@Serializable
data class NtClientMeta(
    @ProtoNumber(1) var agentType: Int? = null,
)

@Serializable
data class NtDownloadRKeyReq(
    @ProtoNumber(1) @ProtoPacked var types: List<Int>? = null,
)

@Serializable
data class NtDownloadReq(
    @ProtoNumber(1) var node: NtIndexNode? = null,
)

@Serializable
data class NtIndexNode(
    @ProtoNumber(1) var info: NtFileInfo? = null,
    @ProtoNumber(2) var fileUuid: String? = null,
    @ProtoNumber(3) var storeId: Int? = null,
    @ProtoNumber(4) var uploadTime: Int? = null,
    @ProtoNumber(5) var ttl: Int? = null,
    @ProtoNumber(6) var subType: Int? = null,
)

@Serializable
data class NtFileInfo(
    @ProtoNumber(1) var fileSize: Int? = null,
    @ProtoNumber(2) var fileHash: String? = null,
    @ProtoNumber(3) var fileSha1: String? = null,
    @ProtoNumber(4) var fileName: String? = null,
    @ProtoNumber(6) var width: Int? = null,
    @ProtoNumber(7) var height: Int? = null,
)

@Serializable
data class NtRichMediaRsp(
    @ProtoNumber(1) var respHead: NtRespHead? = null,
    @ProtoNumber(3) var download: NtDownloadResp? = null,
    @ProtoNumber(4) var downloadRKey: NtDownloadRKeyResp? = null,
)

@Serializable
data class NtRespHead(
    @ProtoNumber(1) var common: NtCommonHead? = null,
    @ProtoNumber(2) var retCode: Int? = null,
    @ProtoNumber(3) var message: String? = null,
)

@Serializable
data class NtDownloadRKeyResp(
    @ProtoNumber(1) var rkeys: List<NtRKeyInfo>? = null,
)

@Serializable
data class NtRKeyInfo(
    @ProtoNumber(1) var rkey: String? = null,
    @ProtoNumber(2) var rkeyTtlSec: Long? = null,
    @ProtoNumber(3) var storeId: Int? = null,
    @ProtoNumber(4) var rkeyCreateTime: Int? = null,
    @ProtoNumber(5) var type: Int? = null,
)

@Serializable
data class NtDownloadResp(
    @ProtoNumber(1) var rkeyParam: String? = null,
    @ProtoNumber(2) var rkeyTtlSecond: Int? = null,
    @ProtoNumber(3) var info: NtDownloadInfo? = null,
    @ProtoNumber(4) var rkeyCreateTime: Int? = null,
)

@Serializable
data class NtDownloadInfo(
    @ProtoNumber(1) var domain: String? = null,
    @ProtoNumber(2) var urlPath: String? = null,
    @ProtoNumber(3) var httpsPort: Int? = null,
)

@Serializable
data class Oidb0x9067ReqBody(
    @ProtoNumber(1) var ntRichMediaReq: NtRichMediaReq? = null,
)

@Serializable
data class Oidb0x9067RspBody(
    @ProtoNumber(1) var ntRichMediaRsp: NtRichMediaRsp? = null,
)