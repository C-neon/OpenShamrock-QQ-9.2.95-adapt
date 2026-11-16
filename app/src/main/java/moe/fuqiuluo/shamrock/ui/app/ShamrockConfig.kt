package moe.fuqiuluo.shamrock.ui.app

import android.content.Context
import android.util.Log
import moe.fuqiuluo.shamrock.ui.service.internal.broadcastToModule
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.FileReader
import java.util.concurrent.ConcurrentHashMap

object ShamrockConfig {
    private const val CONFIG_FILE_NAME = "shamrock_config.json"
    private const val SHARED_CONFIG_FILE_NAME = "shared_config.xml"
    private const val TAG = "ShamrockConfig"

    // 内存缓存，避免频繁读文件
    private val configCache = ConcurrentHashMap<String, Any?>()
    private val sharedConfigCache = ConcurrentHashMap<String, Any?>()
    private var cacheLoaded = false

    /**
     * 确保文件对所有进程可读（system_server / uid 1000 需要读 shared_config）
     * 父目录需要 executable，文件需要 readable
     */
    private fun makeWorldReadable(file: File) {
        try {
            file.parentFile?.setExecutable(true, false)
            file.setReadable(true, false)
            Log.d(TAG, "makeWorldReadable: ${file.absolutePath} canRead=${file.canRead()} canExecute=${file.parentFile?.canExecute()}")
        } catch (e: Exception) {
            Log.w(TAG, "makeWorldReadable failed for ${file.absolutePath}", e)
        }
    }

    private fun getConfigFile(ctx: Context): File = File(ctx.filesDir, CONFIG_FILE_NAME)

    /**
     * shared_config 必须写到标准 shared_prefs 目录、标准 SharedPreferences XML 格式，
     * 因为 system_server 侧的 KeepAlive 用 XSharedPreferences（通过 vectord binder）读取，
     * 它只认标准 shared_prefs 路径下的 XML。MIUI 会重定向 getSharedPreferences，
     * 所以这里用直接 File 写，绕开重定向。
     */
    private fun getSharedConfigFile(ctx: Context): File =
        File(ctx.getFilesDir().parentFile, "shared_prefs/$SHARED_CONFIG_FILE_NAME")

    /**
     * 加载 config 到内存缓存
     */
    private fun loadConfigCache(ctx: Context) {
        if (cacheLoaded) return
        val file = getConfigFile(ctx)
        if (file.exists()) {
            try {
                FileReader(file).use { reader ->
                    val json = JSONObject(reader.readText())
                    val keys = json.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        configCache[key] = json.get(key)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "loadConfigCache failed", e)
            }
        }
        // 加载 shared_config（标准 SharedPreferences XML）
        val sharedFile = getSharedConfigFile(ctx)
        if (sharedFile.exists()) {
            try {
                parseSharedPrefsXml(sharedFile.readText()).forEach { (k, v) ->
                    sharedConfigCache[k] = v
                }
            } catch (e: Exception) {
                Log.w(TAG, "loadSharedConfigCache failed", e)
            }
        }
        cacheLoaded = true
    }

    /**
     * 同步写入 config 文件
     */
    private fun writeConfigFile(ctx: Context) {
        val file = getConfigFile(ctx)
        try {
            val json = JSONObject(configCache)
            FileOutputStream(file).use { fos ->
                fos.write(json.toString().toByteArray())
            }
            makeWorldReadable(file)
        } catch (e: Exception) {
            Log.e(TAG, "writeConfigFile failed", e)
        }
    }

    /**
     * 同步写入 shared_config 文件（标准 SharedPreferences XML 格式）
     */
    private fun writeSharedConfigFile(ctx: Context) {
        val file = getSharedConfigFile(ctx)
        try {
            file.parentFile?.mkdirs()
            val xml = buildSharedPrefsXml(sharedConfigCache)
            FileOutputStream(file).use { fos ->
                fos.write(xml.toByteArray(Charsets.UTF_8))
            }
            makeWorldReadable(file)
        } catch (e: Exception) {
            Log.e(TAG, "writeSharedConfigFile failed", e)
        }
    }

    /**
     * 生成标准 SharedPreferences XML 格式。
     * 只支持 boolean / int / string 三种类型（本项目 shared_config 只用 boolean）。
     */
    private fun buildSharedPrefsXml(map: Map<String, Any?>): String {
        val sb = StringBuilder()
        sb.append("<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n")
        sb.append("<map>\n")
        map.forEach { (k, v) ->
            when (v) {
                is Boolean -> sb.append("    <boolean name=\"$k\" value=\"$v\" />\n")
                is Int -> sb.append("    <int name=\"$k\" value=\"$v\" />\n")
                is Long -> sb.append("    <long name=\"$k\" value=\"$v\" />\n")
                is String -> sb.append("    <string name=\"$k\">$v</string>\n")
                else -> {}
            }
        }
        sb.append("</map>\n")
        return sb.toString()
    }

    /**
     * 解析标准 SharedPreferences XML（只提取 boolean/int/long/string）。
     */
    private fun parseSharedPrefsXml(xml: String): Map<String, Any> {
        val result = mutableMapOf<String, Any>()
        // boolean: <boolean name="key" value="true" />
        Regex("""<boolean name="([^"]+)" value="(true|false)"\s*/>""").findAll(xml).forEach {
            result[it.groupValues[1]] = it.groupValues[2] == "true"
        }
        // int: <int name="key" value="123" />
        Regex("""<int name="([^"]+)" value="(-?\d+)"\s*/>""").findAll(xml).forEach {
            result[it.groupValues[1]] = it.groupValues[2].toInt()
        }
        // long: <long name="key" value="123" />
        Regex("""<long name="([^"]+)" value="(-?\d+)"\s*/>""").findAll(xml).forEach {
            result[it.groupValues[1]] = it.groupValues[2].toLong()
        }
        // string: <string name="key">value</string>
        Regex("""<string name="([^"]+)">([^<]*)</string>""").findAll(xml).forEach {
            result[it.groupValues[1]] = it.groupValues[2]
        }
        return result
    }

    /** 读取 config 值（带默认值） */
    private fun <T> getConfig(key: String, default: T): T {
        return (configCache[key] as? T) ?: default
    }

    /** 读取 shared_config 值（带默认值） */
    private fun <T> getSharedConfig(key: String, default: T): T {
        return (sharedConfigCache[key] as? T) ?: default
    }

    /** 写入 config 值 */
    private fun putConfig(key: String, value: Any?) {
        if (value == null) {
            configCache.remove(key)
        } else {
            configCache[key] = value
        }
    }

    /** 写入 shared_config 值 */
    private fun putSharedConfig(key: String, value: Any?) {
        if (value == null) {
            sharedConfigCache.remove(key)
        } else {
            sharedConfigCache[key] = value
        }
    }

    // ==================== SSL 相关 ====================
    fun getSSLKeyPath(ctx: Context): String {
        loadConfigCache(ctx)
        return getConfig("key_store", "")
    }

    fun setSSLKeyPath(ctx: Context, path: String) {
        loadConfigCache(ctx)
        putConfig("key_store", path)
        writeConfigFile(ctx)
        pushUpdate(ctx)
    }

    fun getSSLPort(ctx: Context): Int {
        loadConfigCache(ctx)
        return getConfig("ssl_port", 5701)
    }

    fun setSSLPort(ctx: Context, port: Int) {
        loadConfigCache(ctx)
        putConfig("ssl_port", port)
        writeConfigFile(ctx)
        pushUpdate(ctx)
    }

    fun getSSLAlias(ctx: Context): String {
        loadConfigCache(ctx)
        return getConfig("ssl_alias", "")
    }

    fun setSSLAlias(ctx: Context, alias: String) {
        loadConfigCache(ctx)
        putConfig("ssl_alias", alias)
        writeConfigFile(ctx)
        pushUpdate(ctx)
    }

    fun getSSLPwd(ctx: Context): String {
        loadConfigCache(ctx)
        return getConfig("ssl_pwd", "")
    }

    fun setSSLPwd(ctx: Context, alias: String) {
        loadConfigCache(ctx)
        putConfig("ssl_pwd", alias)
        writeConfigFile(ctx)
        pushUpdate(ctx)
    }

    fun getSSLPrivatePwd(ctx: Context): String {
        loadConfigCache(ctx)
        return getConfig("ssl_private_pwd", "")
    }

    fun setSSLPrivatePwd(ctx: Context, alias: String) {
        loadConfigCache(ctx)
        putConfig("ssl_private_pwd", alias)
        writeConfigFile(ctx)
        pushUpdate(ctx)
    }

    // ==================== HTTP / WS 相关 ====================
    fun getHttpAddr(ctx: Context): String {
        loadConfigCache(ctx)
        return getConfig("http_addr", "")
    }

    fun setHttpAddr(ctx: Context, v: String) {
        loadConfigCache(ctx)
        putConfig("http_addr", v)
        writeConfigFile(ctx)
        pushUpdate(ctx)
    }

    fun isPro(ctx: Context): Boolean {
        loadConfigCache(ctx)
        return getConfig("pro_api", false)
    }

    fun setPro(ctx: Context, v: Boolean) {
        loadConfigCache(ctx)
        putConfig("pro_api", v)
        writeConfigFile(ctx)
        ctx.broadcastToModule {
            putExtra("type", "restart")
            putExtra("__cmd", "change_port")
        }
        pushUpdate(ctx)
    }

    fun getToken(ctx: Context): String {
        loadConfigCache(ctx)
        return getConfig("token", "")
    }

    fun setToken(ctx: Context, v: String?) {
        loadConfigCache(ctx)
        putConfig("token", v)
        writeConfigFile(ctx)
        pushUpdate(ctx)
    }

    fun isWs(ctx: Context): Boolean {
        loadConfigCache(ctx)
        return getConfig("ws", false)
    }

    fun setWs(ctx: Context, v: Boolean) {
        loadConfigCache(ctx)
        putConfig("ws", v)
        writeConfigFile(ctx)
        pushUpdate(ctx)
    }

    fun isWsClient(ctx: Context): Boolean {
        loadConfigCache(ctx)
        return getConfig("ws_client", false)
    }

    fun setWsClient(ctx: Context, v: Boolean) {
        loadConfigCache(ctx)
        putConfig("ws_client", v)
        writeConfigFile(ctx)
        pushUpdate(ctx)
    }

    fun isTablet(ctx: Context): Boolean {
        loadConfigCache(ctx)
        return getConfig("tablet", false)
    }

    fun setTablet(ctx: Context, v: Boolean) {
        loadConfigCache(ctx)
        putConfig("tablet", v)
        writeConfigFile(ctx)
        pushUpdate(ctx)
    }

    fun isUseCQCode(ctx: Context): Boolean {
        loadConfigCache(ctx)
        return getConfig("use_cqcode", false)
    }

    fun setUseCQCode(ctx: Context, v: Boolean) {
        loadConfigCache(ctx)
        putConfig("use_cqcode", v)
        writeConfigFile(ctx)
        pushUpdate(ctx)
    }

    fun isWebhook(ctx: Context): Boolean {
        loadConfigCache(ctx)
        return getConfig("webhook", false)
    }

    fun setWebhook(ctx: Context, v: Boolean) {
        loadConfigCache(ctx)
        putConfig("webhook", v)
        writeConfigFile(ctx)
        pushUpdate(ctx)
    }

    fun getWsAddr(ctx: Context): String {
        loadConfigCache(ctx)
        return getConfig("ws_addr", "")
    }

    fun setWsAddr(ctx: Context, v: String) {
        loadConfigCache(ctx)
        putConfig("ws_addr", v)
        writeConfigFile(ctx)
        pushUpdate(ctx)
    }

    fun getHttpPort(ctx: Context): Int {
        loadConfigCache(ctx)
        return getConfig("port", 5700)
    }

    fun setHttpPort(ctx: Context, v: Int) {
        loadConfigCache(ctx)
        putConfig("port", v)
        writeConfigFile(ctx)
        ctx.broadcastToModule {
            putExtra("type", "port")
            putExtra("port", v)
            putExtra("__cmd", "change_port")
        }
        pushUpdate(ctx)
    }

    fun getWsPort(ctx: Context): Int {
        loadConfigCache(ctx)
        return getConfig("ws_port", 5800)
    }

    fun setWsPort(ctx: Context, v: Int) {
        loadConfigCache(ctx)
        putConfig("ws_port", v)
        writeConfigFile(ctx)
        ctx.broadcastToModule {
            putExtra("type", "ws_port")
            putExtra("port", v)
            putExtra("__cmd", "change_port")
        }
        pushUpdate(ctx)
    }

    // ==================== 其他开关 ====================
    fun is2B(ctx: Context): Boolean {
        loadConfigCache(ctx)
        return getConfig("2B", false)
    }

    fun set2B(ctx: Context, v: Boolean) {
        loadConfigCache(ctx)
        putConfig("2B", v)
        writeConfigFile(ctx)
    }

    fun setAutoClean(ctx: Context, v: Boolean) {
        loadConfigCache(ctx)
        putConfig("auto_clear", v)
        writeConfigFile(ctx)
    }

    fun isAutoClean(ctx: Context): Boolean {
        loadConfigCache(ctx)
        return getConfig("auto_clear", false)
    }

    fun isDebug(ctx: Context): Boolean {
        loadConfigCache(ctx)
        return getConfig("debug", false)
    }

    fun setDebug(ctx: Context, v: Boolean) {
        loadConfigCache(ctx)
        putConfig("debug", v)
        writeConfigFile(ctx)
    }

    fun isAntiTrace(ctx: Context): Boolean {
        loadConfigCache(ctx)
        return getConfig("anti_qq_trace", true)
    }

    fun isForbidUselessProcess(ctx: Context): Boolean {
        loadConfigCache(ctx)
        return getConfig("forbid_useless_process", false)
    }

    fun setForbidUselessProcess(ctx: Context, v: Boolean) {
        loadConfigCache(ctx)
        putConfig("forbid_useless_process", v)
        writeConfigFile(ctx)
    }

    fun setAntiTrace(ctx: Context, v: Boolean) {
        loadConfigCache(ctx)
        putConfig("anti_qq_trace", v)
        writeConfigFile(ctx)
    }

    fun isInjectPacket(ctx: Context): Boolean {
        loadConfigCache(ctx)
        return getConfig("inject_packet", false)
    }

    fun setInjectPacket(ctx: Context, v: Boolean) {
        loadConfigCache(ctx)
        putConfig("inject_packet", v)
        writeConfigFile(ctx)
    }

    fun enableAutoStart(ctx: Context): Boolean {
        loadConfigCache(ctx)
        return getConfig("enable_auto_start", false)
    }

    fun disableAutoSyncSetting(ctx: Context): Boolean {
        loadConfigCache(ctx)
        return getConfig("disable_auto_sync_setting", false)
    }

    fun enableAliveReply(ctx: Context): Boolean {
        loadConfigCache(ctx)
        return getConfig("alive_reply", false)
    }

    fun allowShell(ctx: Context): Boolean {
        loadConfigCache(ctx)
        return getConfig("shell", false)
    }

    fun setAutoStart(ctx: Context, v: Boolean) {
        loadConfigCache(ctx)
        putConfig("enable_auto_start", v)
        writeConfigFile(ctx)
        // 同时写入 shared_config，供 KeepAlive (system_server) 读取
        putSharedConfig("enable_auto_start", v)
        writeSharedConfigFile(ctx)
        pushUpdate(ctx)
    }

    fun setDisableAutoSyncSetting(ctx: Context, v: Boolean) {
        loadConfigCache(ctx)
        putConfig("disable_auto_sync_setting", v)
        writeConfigFile(ctx)
    }

    fun setAliveReply(ctx: Context, v: Boolean) {
        loadConfigCache(ctx)
        putConfig("alive_reply", v)
        writeConfigFile(ctx)
        pushUpdate(ctx)
    }

    fun setShellStatus(ctx: Context, v: Boolean) {
        loadConfigCache(ctx)
        putConfig("shell", v)
        writeConfigFile(ctx)
    }

    fun enableSelfMsg(ctx: Context): Boolean {
        loadConfigCache(ctx)
        return getConfig("enable_self_msg", false)
    }

    fun enableSyncMsgAsSentMsg(ctx: Context): Boolean {
        loadConfigCache(ctx)
        return getConfig("enable_sync_msg_as_sent_msg", false)
    }

    // ==================== Lab 页三个实验开关（同时写 config 和 shared_config）====================
    fun isPersistent(ctx: Context): Boolean {
        loadConfigCache(ctx)
        return getSharedConfig("persistent", false) || getConfig("persistent", false)
    }

    fun setPersistent(ctx: Context, v: Boolean) {
        loadConfigCache(ctx)
        putSharedConfig("persistent", v)
        writeSharedConfigFile(ctx)
        putConfig("persistent", v)
        writeConfigFile(ctx)
        pushUpdate(ctx)
    }

    fun isHookDoze(ctx: Context): Boolean {
        loadConfigCache(ctx)
        return getSharedConfig("hook_doze", false) || getConfig("hook_doze", false)
    }

    fun setHookDoze(ctx: Context, v: Boolean) {
        loadConfigCache(ctx)
        putSharedConfig("hook_doze", v)
        writeSharedConfigFile(ctx)
        putConfig("hook_doze", v)
        writeConfigFile(ctx)
        pushUpdate(ctx)
    }

    fun isSuperAnti(ctx: Context): Boolean {
        loadConfigCache(ctx)
        return getSharedConfig("super_anti", false) || getConfig("super_anti", false)
    }

    fun setSuperAnti(ctx: Context, v: Boolean) {
        loadConfigCache(ctx)
        putSharedConfig("super_anti", v)
        writeSharedConfigFile(ctx)
        putConfig("super_anti", v)
        writeConfigFile(ctx)
        pushUpdate(ctx)
    }

    fun setEnableSelfMsg(ctx: Context, v: Boolean) {
        loadConfigCache(ctx)
        putConfig("enable_self_msg", v)
        writeConfigFile(ctx)
    }

    fun setEnableSyncMsgAsSentMsg(ctx: Context, v: Boolean) {
        loadConfigCache(ctx)
        putConfig("enable_sync_msg_as_sent_msg", v)
        writeConfigFile(ctx)
    }

    // ==================== 导出完整配置映射（供 pushUpdate 广播） ====================
    fun getConfigMap(ctx: Context): Map<String, Any?> {
        loadConfigCache(ctx)
        // 合并 config 和 shared_config（shared_config 优先，保持原有行为）
        val merged = mutableMapOf<String, Any?>()
        configCache.forEach { (k, v) -> merged[k] = v }
        sharedConfigCache.forEach { (k, v) -> merged[k] = v }

        return mapOf(
            "tablet" to merged.getOrDefault("tablet", false),
            "port" to merged.getOrDefault("port", 5700),
            "ws" to merged.getOrDefault("ws", false),
            "ws_port" to merged.getOrDefault("ws_port", 5800),
            "ssl_port" to merged.getOrDefault("ssl_port", 5701),
            "http" to merged.getOrDefault("webhook", false),
            "http_addr" to merged.getOrDefault("http_addr", ""),
            "ws_client" to merged.getOrDefault("ws_client", false),
            "use_cqcode" to merged.getOrDefault("use_cqcode", false),
            "ws_addr" to merged.getOrDefault("ws_addr", ""),
            "ssl_alias" to merged.getOrDefault("ssl_alias", ""),
            "pro_api" to merged.getOrDefault("pro_api", false),
            "token" to merged.getOrDefault("token", ""),
            "ssl_pwd" to merged.getOrDefault("ssl_pwd", ""),
            "inject_packet" to merged.getOrDefault("inject_packet", false),
            "debug" to merged.getOrDefault("debug", false),
            "anti_qq_trace" to merged.getOrDefault("anti_qq_trace", true),
            "ssl_private_pwd" to merged.getOrDefault("ssl_private_pwd", ""),
            "key_store" to merged.getOrDefault("key_store", ""),
            "enable_self_msg" to merged.getOrDefault("enable_self_msg", false),
            "echo_number" to merged.getOrDefault("echo_number", false),
            "shell" to merged.getOrDefault("shell", false),
            "alive_reply" to merged.getOrDefault("alive_reply", false),
            "enable_sync_msg_as_sent_msg" to merged.getOrDefault("enable_sync_msg_as_sent_msg", false),
            "disable_auto_sync_setting" to merged.getOrDefault("disable_auto_sync_setting", false),
            "forbid_useless_process" to merged.getOrDefault("forbid_useless_process", false),
            "persistent" to merged.getOrDefault("persistent", false),
            "hook_doze" to merged.getOrDefault("hook_doze", false),
            "super_anti" to merged.getOrDefault("super_anti", false),
            "enable_auto_start" to merged.getOrDefault("enable_auto_start", false)
        )
    }

    fun pushUpdate(ctx: Context) {
        ctx.broadcastToModule {
            getConfigMap(ctx).forEach { (key, value) ->
                if (value == null) {
                    val v: String? = null
                    this.putExtra(key, v)
                } else {
                    when (value) {
                        is Int -> this.putExtra(key, value)
                        is Long -> this.putExtra(key, value)
                        is Short -> this.putExtra(key, value)
                        is Byte -> this.putExtra(key, value)
                        is String -> this.putExtra(key, value)
                        is ByteArray -> this.putExtra(key, value)
                        is Boolean -> this.putExtra(key, value)
                        is Float -> this.putExtra(key, value)
                        is Double -> this.putExtra(key, value)
                    }
                }
            }
            putExtra("__cmd", "push_config")
        }
    }
}