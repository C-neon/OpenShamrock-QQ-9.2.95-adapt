@file:OptIn(DelicateCoroutinesApi::class)
package moe.fuqiuluo.shamrock.xposed.helper.internal

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import moe.fuqiuluo.shamrock.utils.PlatformUtils
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import moe.fuqiuluo.shamrock.helper.Level
import moe.fuqiuluo.shamrock.helper.LogCenter

/**
 * 动态广播
 */
internal object DynamicReceiver: BroadcastReceiver() {
    private val hashHandler = mutableSetOf<IPCRequest>()
    private val cmdHandler = mutableMapOf<String, IPCRequest>()
    private val mutex = Mutex() // 滥用的锁，尽量减少使用

    override fun onReceive(ctx: Context, intent: Intent) {
        GlobalScope.launch(Dispatchers.Default) {
            val hash = intent.getIntExtra("__hash", -1)
            val cmd = intent.getStringExtra("__cmd") ?: ""
            try {
                if (cmd.isNotBlank()) {
                    cmdHandler[cmd].also {
                        if (it == null)
                            LogCenter.log("无常驻包处理器: $cmd, main = ${PlatformUtils.isMainProcess()}", Level.ERROR)
                    }?.callback?.handle(intent)
                } else if (hash != -1) {
                    var matched = false
                    mutex.withLock {
                        hashHandler.removeIf {
                            if (hash == it.hashCode()) {
                                matched = true
                                GlobalScope.launch {
                                    it.callback?.handle(intent)
                                }
                                return@removeIf it.seq != -1
                            }
                            return@removeIf false
                        }
                    }
                    if (!matched) {
                        LogCenter.log("[DynamicReceiver] 收到响应但无匹配 handler hash=$hash 待处理数=${hashHandler.size}", Level.WARN)
                    }
                }
            } catch (e: Throwable) {
                LogCenter.log("包处理器[$cmd]错误: $e", Level.ERROR)
            }
        }
    }

    fun register(cmd: String, request: IPCRequest) {
        cmdHandler[cmd] = request
    }

    fun unregister(cmd: String) {
        cmdHandler.remove(cmd)
    }

    /***
     * 注册临时包处理器
     */
    suspend fun register(request: IPCRequest) {
        LogCenter.log({ "registerHandler[${request.hashCode()}](cmd = ${request.cmd}, seq = ${request.seq})" }, Level.DEBUG)

        mutex.withLock {
            hashHandler.add(request)
        }
    }

    suspend fun unregister(seq: Int) {
        mutex.withLock {
            hashHandler.removeIf { it.seq == seq }
        }
    }
}