# OpenShamrock QQ 9.2.95 Adaptation

This repository is a local OpenShamrock adaptation for QQ `9.2.95`
(versionCode `14200`).

The purpose of this fork is narrow: keep Shamrock loading on QQ `9.2.95` and
keep the OneBot HTTP and reverse WebSocket paths usable for local bot testing.
It is not an official OpenShamrock release.

## Source

This fork is based on:

- Base fork: [Mythologyli/OpenShamrock-fix](https://github.com/Mythologyli/OpenShamrock-fix)
  at commit `238b954`
- Upstream project: [whitechi73/OpenShamrock](https://github.com/whitechi73/OpenShamrock)
- Original lineage: Shamrock / OpenShamrock by the project contributors,
  including `fuqiuluo` and `whitechi73`

The original attribution and license details are kept in [NOTICE.md](NOTICE.md)
and [LICENSE](LICENSE).

## What Changed

- Matched `IKernelGroupListener` to the QQ `9.2.95` runtime interface.
- Matched `IKernelMsgListener` to the QQ `9.2.95` runtime interface.
- Added the minimal QQNT interface types required by those listener signatures.
- Updated Shamrock listener overrides for the newer QQ runtime.
- Updated QQNT message service contact references for QQ `9.2.95`, where
  `Contact` is exposed from `com.tencent.qqnt.kernelpublic.nativeinterface`.
- Added an Xposed entry log marker for this QQ `9.2.95` build.

These changes target QQ `9.2.95` startup, inbound event, and message send
compatibility. They do not claim broad QQ version support.

## Tested Setup

Tested locally with:

- Device: Redmi K50 Pro
- Android environment: Magisk and LSPosed
- QQ: `9.2.95`
- Shamrock build: local arm64 debug build from this branch
- Bot side: AstrBot with OneBot v11 reverse WebSocket

Observed checks:

- QQ main, MSF, and pushservice processes start without the previous listener
  crash.
- `logcat` no longer shows the earlier `AbstractMethodError`, `JNI FatalError`,
  or `QQCrashHandleListener` crash path.
- Shamrock connects to AstrBot with `WebSocketClient onOpen: 101`.
- AstrBot reports `aiocqhttp(OneBot v11) adapter connected`.
- Real QQ group message events reach AstrBot.
- OneBot HTTP `/send_group_msg` and `/send_private_msg` return `retcode=0`.
- OneBot HTTP `/get_msg` can read back the sent group and private messages by
  `message_id`.

## Build

Use JDK 17.

```bash
env JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home bash ./gradlew :app:assembleArm64Debug --no-daemon
```

Expected APK path:

```text
app/build/outputs/apk/arm64/debug/Shamrock-v*-arm64.apk
```

## Known Limits

- Only QQ `9.2.95` was tested for this adaptation.
- QQ updates may change listener signatures again.
- LSPosed, Android, or upstream Shamrock changes may require another pass.
- This fork is for learning, research, and self-owned device testing.
- Some information APIs still depend on QQ internals that changed in `9.2.95`;
  for example, friend or group metadata calls may require separate adaptation.

## License

This fork keeps the upstream GPL license. See [LICENSE](LICENSE).

Unless a file says otherwise, changes in this fork are distributed under the
same GPL-3.0-or-later terms as the upstream project.
