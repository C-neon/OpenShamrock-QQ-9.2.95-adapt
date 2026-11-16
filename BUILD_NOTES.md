# 9.2.95 适配修复（未编译验证）

## 状态

- 源码改动已完成，**尚未编译**、未在设备上验证。
- 改动由一次自动化会话产出（模型 particle-ai/particle-1:free，通过 OpenRouter）；该会话在收尾阶段遇到上游 provider 错误，因此没有生成 APK，也没有写构建日志。
- 本文件用于记录改动意图与后续构建步骤，避免把「代码已改」误当成「功能已验证」。

## 改动内容

### 1. 图片取回（主要）

背景：消息里的图片段只带 md5/uuid；`get_image` 原本只返回元数据和一个 uin 段全为 0 的畸形 gchat 直链，直链必然 404，导致机器人拿不到图片内容。

改动：
- 新增 `xposed/.../shamrock/helper/PicFetcher.kt`：按可靠性从高到低尝试四种来源取字节
  1. QQ 已落盘的本地缓存（按 md5 在 QQ 图片目录里扫描）
  2. 消息元素携带的 `originImageUrl`
  3. RichProto 图片下载协议（GRP_PIC_DW / C2C_PIC_DW）换出的下载地址
  4. 历史 gchat 直链拼接（仅兜底）
  单张上限 16MB，防止 base64 撑爆响应。
- `GetImage.kt`：改为 suspend，成功取到字节时在 `data` 里返回 `base64`，并把 size 换成真实字节数；取不到时保持原有元数据行为，避免破坏旧调用方。
- `ImageDB.kt` / `MessageElemConverter.kt` / `RichProtoSvc.kt`：补充图片记录与下载地址解析所需的字段。

### 2. 配置持久化

- `xposed/.../remote/service/config/ShamrockConfig.kt`：不再在每次进程启动时删除 `Tencent/Shamrock` 目录，配置在 QQ 重启后仍可读回。
- `app/.../ui/app/ShamrockConfig.kt`、`app/.../ui/fragment/LabFragment.kt`：把 Lab 三个开关从 `MODE_WORLD_READABLE` 的 `shared_config` 挪到可用的路径上（Android 7+ 该 flag 直接抛异常）。
- `xposed/.../xposed/hooks/AntiDetection.kt`：配套调整读取方式。

## 构建

README 给出的命令：

```
env JAVA_HOME=<jdk17> bash ./gradlew :app:assembleArm64Debug --no-daemon
```

产物预期路径：`app/build/outputs/apk/arm64/debug/`。

本次未执行构建：环境缺少 JDK 17 与 Android SDK，自动化会话未能装好即被 provider 错误中断。

## 待办

1. 在具备 JDK17 + Android SDK 的环境编译，确认 `app:compileArm64DebugKotlin` 通过（新增的 suspend 调用可能牵动调用链）。
2. 装机验证：`get_image` 返回 `data.base64`，适配器侧可解码落盘。
3. 验证配置持久化：设置开关 → 重启 QQ → 值仍在。
4. 若 PicFetcher 的本地缓存扫描路径与 QQ 9.2.95 实际布局不符，需要在真机上确认目录（`/data/data/com.tencent.mobileqq/files/` 下与图片相关的目录名）。
