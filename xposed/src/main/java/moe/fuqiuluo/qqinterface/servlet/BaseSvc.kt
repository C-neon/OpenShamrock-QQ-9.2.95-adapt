@file:OptIn(DelicateCoroutinesApi::class)

package moe.fuqiuluo.qqinterface.servlet

import android.os.Bundle
import com.tencent.mobileqq.app.QQAppInterface
import com.tencent.mobileqq.pb.ByteStringMicro
import kotlinx.atomicfu.atomic
import com.tencent.qphone.base.remote.ToServiceMsg
import io.ktor.utils.io.core.BytePacketBuilder
import io.ktor.utils.io.core.readBytes
import io.ktor.utils.io.core.writeFully
import io.ktor.utils.io.core.writeInt
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import moe.fuqiuluo.shamrock.helper.Level
import moe.fuqiuluo.shamrock.helper.LogCenter
import moe.fuqiuluo.shamrock.tools.slice
import moe.fuqiuluo.shamrock.utils.DeflateTools
import moe.fuqiuluo.shamrock.utils.PlatformUtils
import moe.fuqiuluo.shamrock.xposed.helper.AppRuntimeFetcher
import moe.fuqiuluo.shamrock.xposed.helper.internal.DynamicReceiver
import moe.fuqiuluo.shamrock.xposed.helper.internal.IPCRequest
import protobuf.oidb.TrpcOidb
import mqq.app.MobileQQ
import tencent.im.oidb.oidb_sso
import kotlin.coroutines.resume

internal abstract class BaseSvc {
    companion object {
        // QQ 9.2.95 起 nextSeq 从 static 变为实例方法，静态调用会导致
        // LSPosed 解析失败(expected static / found virtual)。这里改用自增计数器，
        // seq 仅用于 DynamicReceiver 按 (cmd+seq) 关联并发请求与响应，无需 QQ 侧生成。
        private val seqFactory = atomic(0)
        val nextSeq: Int
            get() {
                if (seqFactory.value > 1_000_000) seqFactory.lazySet(0)
                return seqFactory.incrementAndGet()
            }

        val currentUin: String
            get() = app.currentAccountUin

        val app: QQAppInterface
            get() = AppRuntimeFetcher.appRuntime as QQAppInterface

        fun createToServiceMsg(cmd: String): ToServiceMsg {
            return ToServiceMsg("mobileqq.service", app.currentAccountUin, cmd)
        }

        suspend fun sendOidbAW(cmd: String, cmdId: Int, serviceId: Int, data: ByteArray, trpc: Boolean = false, timeout: Long = 5000L): ByteArray? {
            val seq = nextSeq
            LogCenter.log("[BaseSvc] sendOidbAW cmd=$cmd cmdId=$cmdId serviceId=$serviceId seq=$seq trpc=$trpc dataLen=${data.size}", Level.WARN)
            var cont: kotlinx.coroutines.CancellableContinuation<ByteArray>? = null
            // 先完成注册再发送，避免响应早于注册导致 hash 匹配失败
            DynamicReceiver.register(IPCRequest(cmd, seq) { intent ->
                val buf = intent.getByteArrayExtra("buffer")
                LogCenter.log("[BaseSvc] OIDB 响应到达 cmd=$cmd seq=$seq bufferLen=${buf?.size}", Level.WARN)
                if (buf != null) cont?.resume(buf)
            })
            val buffer = withTimeoutOrNull(timeout) {
                suspendCancellableCoroutine<ByteArray> { c ->
                    cont = c
                    if (trpc) sendTrpcOidb(cmd, cmdId, serviceId, data, seq)
                    else sendOidb(cmd, cmdId, serviceId, data, seq)
                }
            }
            if (buffer == null) {
                LogCenter.log("[BaseSvc] OIDB 超时 cmd=$cmd seq=$seq", Level.WARN)
                DynamicReceiver.unregister(seq)
            }
            try {
                if (buffer != null && buffer.size >= 5 && buffer[4] == 120.toByte()) {
                    val builder = BytePacketBuilder()
                    val deBuffer = DeflateTools.uncompress(buffer.slice(4))
                    builder.writeInt(deBuffer.size)
                    builder.writeFully(deBuffer)
                    return builder.build().readBytes()
                }
            } catch (_: Exception) { }
            return buffer
        }

        suspend fun sendBufferAW(cmd: String, isPb: Boolean, data: ByteArray, timeout: Long = 5000L): ByteArray? {
            val seq = nextSeq
            val buffer = withTimeoutOrNull<ByteArray?>(timeout) {
                suspendCancellableCoroutine { continuation ->
                    GlobalScope.launch(Dispatchers.Default) {
                        DynamicReceiver.register(IPCRequest(cmd, seq) {
                            val buffer = it.getByteArrayExtra("buffer")!!
                            continuation.resume(buffer)
                        })
                        sendBuffer(cmd, isPb, data, seq)
                    }
                }
            }.also {
                if (it == null)
                    DynamicReceiver.unregister(seq)
            }?.copyOf()
            try {
                if (buffer != null && buffer.size >= 5 && buffer[4] == 120.toByte()) {
                    val builder = BytePacketBuilder()
                    val deBuffer = DeflateTools.uncompress(buffer.slice(4))
                    builder.writeInt(deBuffer.size)
                    builder.writeFully(deBuffer)
                    return builder.build().readBytes()
                }
            } catch (_: Exception) { }
            return buffer
        }

        fun sendOidb(cmd: String, cmdId: Int, serviceId: Int, buffer: ByteArray, seq: Int = -1, trpc: Boolean = false) {
            if (trpc) {
                sendTrpcOidb(cmd, cmdId, serviceId, buffer, seq)
                return
            }
            val to = createToServiceMsg(cmd)
            val oidb = oidb_sso.OIDBSSOPkg()
            oidb.uint32_command.set(cmdId)
            oidb.uint32_service_type.set(serviceId)
            oidb.bytes_bodybuffer.set(ByteStringMicro.copyFrom(buffer))
            oidb.str_client_version.set(PlatformUtils.getClientVersion(MobileQQ.getContext()))
            to.putWupBuffer(oidb.toByteArray())
            to.addAttribute("req_pb_protocol_flag", true)
            if (seq != -1) {
                to.addAttribute("shamrock_seq", seq)
            }
            app.sendToService(to)
        }

        fun sendTrpcOidb(cmd: String, cmdId: Int, serviceId: Int, buffer: ByteArray, seq: Int = -1) {
            val to = createToServiceMsg(cmd)

            val oidb = TrpcOidb(
                cmd = cmdId,
                service = serviceId,
                buffer = buffer,
                flag = 0
            )
            to.putWupBuffer(ProtoBuf.encodeToByteArray(oidb))

            to.addAttribute("req_pb_protocol_flag", true)
            if (seq != -1) {
                to.addAttribute("shamrock_seq", seq)
            }
            app.sendToService(to)
        }

        fun sendBuffer(cmd: String, isPb: Boolean, buffer: ByteArray, seq: Int = nextSeq) {
            val toServiceMsg = ToServiceMsg("mobileqq.service", app.currentUin, cmd)
            toServiceMsg.putWupBuffer(buffer)
            toServiceMsg.addAttribute("req_pb_protocol_flag", isPb)
            toServiceMsg.addAttribute("shamrock_seq", seq)
            app.sendToService(toServiceMsg)
        }
    }

    protected fun send(toServiceMsg: ToServiceMsg) {
        app.sendToService(toServiceMsg)
    }

    protected suspend fun sendAW(toServiceMsg: ToServiceMsg, timeout: Long = 5000L): ByteArray? {
        val seq = nextSeq
        val buffer = withTimeoutOrNull<ByteArray?>(timeout) {
            suspendCancellableCoroutine { continuation ->
                GlobalScope.launch(Dispatchers.Default) {
                    DynamicReceiver.register(IPCRequest(toServiceMsg.serviceCmd, seq) {
                        val buffer = it.getByteArrayExtra("buffer")!!
                        continuation.resume(buffer)
                    })
                    toServiceMsg.addAttribute("shamrock_seq", seq)
                    send(toServiceMsg)
                }
            }
        }.also {
            if (it == null) DynamicReceiver.unregister(seq)
        }?.copyOf()
        try {
            if (buffer != null && buffer.size >= 5 && buffer[4] == 120.toByte()) {
                val builder = BytePacketBuilder()
                val deBuffer = DeflateTools.uncompress(buffer.slice(4))
                builder.writeInt(deBuffer.size)
                builder.writeFully(deBuffer)
                return builder.build().readBytes()
            }
        } catch (_: Exception) { }
        return buffer
    }

    protected fun sendExtra(cmd: String, builder: (Bundle) -> Unit) {
        val toServiceMsg = createToServiceMsg(cmd)
        builder(toServiceMsg.extraData)
        app.sendToService(toServiceMsg)
    }

    protected fun sendPb(cmd: String, buffer: ByteArray, seq: Int) {
        val toServiceMsg = createToServiceMsg(cmd)
        toServiceMsg.putWupBuffer(buffer)
        toServiceMsg.addAttribute("req_pb_protocol_flag", true)
        toServiceMsg.addAttribute("shamrock_seq", seq)
        app.sendToService(toServiceMsg)
    }
}