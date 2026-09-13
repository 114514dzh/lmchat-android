# LM Chat · Android 客户端

端到端加密聊天的安卓客户端。服务端见 [lmchat-server](https://github.com/114514dzh/lmchat-server)。

- 无 Gradle：纯 `aapt2 + javac + d8 + apksigner` 手工构建链
- 加密：libsignal（X3DH + 双棘轮），服务端只做密文转发
- 最低 Android 6.0（API 23），目标 API 35

## 构建

需要 Android SDK（build-tools 34.0.0 / platform android-35）与 JDK 8+。

```bash
cp local.properties.example local.properties   # 填入你自己的值
./build.sh
# 产物：dist/lmchat.apk
```

`local.properties` 字段：

| 键 | 说明 |
|---|---|
| `server` | 服务器地址，形如 `https://your-host/im-api`。**这是全工程唯一写地址的地方**，更新地址会自动从它推导（见下） |
| `keystore.pass` | APK 签名密钥口令 |

首次构建若 `app.keystore` 不存在，`build.sh` 会用上面的口令自动生成一个。

## 地址的推导规则

域名只在 `server` 里写一次，其余全部由 `P.siteRoot()` 推导：

```
server            https://host/im-api
  → siteRoot      https://host
  → 更新清单      https://host/im-update/version.json
  → 新版本 APK    https://host/im-update/lmchat.apk
```

因此换服务器只需改 `local.properties` 一行；用户若在 App 内改了服务器地址，自动更新也会跟着切到新地址。

## 不入库的文件

`.gitignore` 已排除，请勿提交：

- `local.properties` —— 含真实服务器地址与签名口令
- `app.keystore` —— **签名私钥**。泄露后他人可签出与你签名一致、能覆盖安装的假 APK
- `build/`、`dist/` —— 中间产物与产物
- `app/src/im/lilmouse/chat/Local.java` —— 由 `build.sh` 生成（模板见 `Local.java.example`）

## 注册

服务端 `config.json` 的 `invite` 为空时开放注册；非空时客户端需带上对应邀请码。
