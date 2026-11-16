# Shamrock 配置持久化修复 TODO

## Bug 1: persistent + hook_doze 在 system_server 读不到 (最关键)
- [x] 理解问题：app 侧用 MODE_PRIVATE 写 shared_config.xml (0600)，system_server (uid 1000) 读不到
- [x] 修复方案：app 侧写入 shared_config 后，手动将 shared_prefs/shared_config.xml 设为 world-readable (0644)，目录 0711
- [ ] 验证：logcat 抓 "[Shamrock] allowPersistent = true/false" 和 "unable to load XSharedPreferences"

## Bug 2: enable_auto_start 完全没实现
- [ ] app 侧 setAutoStart 加 pushUpdate
- [ ] xposed 侧 ShamrockConfig 增加 enableAutoStart() 读取函数
- [ ] 实现 QQ 进程死亡自动拉起逻辑（在 KeepAlive 的 KeepThread 里补）

## Bug 3: MMKV 持久化可靠性，关键开关也要写 config.json
- [ ] ServiceConfig 添加 ws, ws_client, super_anti, persistent, hook_doze, enable_auto_start 等字段
- [ ] xposed 侧 updateConfig(intent) 里同步这些字段到 Config 对象
- [ ] 验证 MMKV 实际存储路径

## 编译验证
- [ ] ./gradlew :app:assembleArm64Debug --no-daemon 编译通过
- [ ] adb install -r -t apk 安装成功

## 报告输出
- [ ] 改动文件清单
- [ ] 每个 bug 修复方案和关键代码
- [ ] MMKV 存储路径 adb 探查结果
- [ ] 用户验证步骤清单（明确哪些需重启手机、哪些只需重启 QQ）
- [ ] Commit hash