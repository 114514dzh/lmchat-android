#!/bin/bash
# LM Chat 无 gradle 构建
# 真实域名/邀请码/签名口令来自 local.properties（不入库），仓库里只有 .example 模板。
set -e
S=/opt/android-sdk
BT=$S/build-tools/34.0.0
AJ=$S/platforms/android-35/android.jar
L=$S/libs
A=$(cd "$(dirname "$0")" && pwd)
cd "$A"

# ---------- 读取本机配置 ----------
if [ ! -f local.properties ]; then
  echo "缺少 local.properties，请从模板复制：cp local.properties.example local.properties"
  exit 1
fi
get() { grep -E "^$1=" local.properties | head -1 | cut -d= -f2- ; }
SRV=$(get server)
KSPASS=$(get keystore.pass)
[ -n "$KSPASS" ] || { echo "local.properties 缺少 keystore.pass"; exit 1; }
if [ -z "$SRV" ]; then
  echo "提示: local.properties 的 server 为空 —— 构建出的包不含默认服务器地址，"
  echo "      安装后需在首次配置界面手动填写。"
fi

# ---------- 生成 Local.java（不入库）----------
mkdir -p app/src/im/lilmouse/chat
cat > app/src/im/lilmouse/chat/Local.java <<EOF
package im.lilmouse.chat;

/** 由 build.sh 自动生成，请勿手改；模板见 Local.java.example */
public final class Local {
    public static final String SERVER = "$SRV";
}
EOF

mkdir -p build/gen build/classes build/dex build/dex2 build/jni dist

if [ ! -f app.keystore ]; then
  keytool -genkeypair -keystore app.keystore -alias app -keyalg RSA -keysize 2048 \
    -validity 10000 -storepass "$KSPASS" -keypass "$KSPASS" -dname "CN=LM Chat" >/dev/null 2>&1
fi

rm -f build/res.zip build/base.apk build/clj.jar dist/lmchat.apk dist/lmchat-aligned.apk
find build/gen build/classes -name '*.java' -delete 2>/dev/null || true
find build/classes -name '*.class' -delete 2>/dev/null || true
rm -f build/dex/classes.dex build/dex2/classes.dex build/dex2/classes2.dex

# 1) 资源
$BT/aapt2 compile --dir app/res -o build/res.zip
$BT/aapt2 link -o build/base.apk -I $AJ --manifest app/AndroidManifest.xml --java build/gen \
  -R build/res.zip --min-sdk-version 23 --target-sdk-version 35 --auto-add-overlay

# 2) javac
find app/src build/gen -name '*.java' > build/sources.txt
javac -J-Xmx1024m -source 8 -target 8 -encoding UTF-8 \
  -classpath $AJ:$L/clj:$L/json.jar:$L/ws.jar -d build/classes @build/sources.txt || { echo "JAVAC FAILED"; exit 1; }

# 3) d8 两个 dex（防方法数爆）
java -Xmx1024m -cp $BT/lib/d8.jar com.android.tools.r8.D8 --release --lib $AJ --min-api 24 --output build/dex \
  $(find build/classes -name '*.class') $L/json.jar $L/ws.jar $L/slf4j-api.jar
jar cf build/clj.jar -C $L/clj org
java -Xmx1024m -cp $BT/lib/d8.jar com.android.tools.r8.D8 --release --lib $AJ --min-api 24 --output build/dex2 build/clj.jar $L/kotlin.jar
mv build/dex2/classes.dex build/dex2/classes2.dex

# 4) 组包
cp build/base.apk dist/lmchat.apk
(cd build/dex && zip -qX $A/dist/lmchat.apk classes.dex)
(cd build/dex2 && zip -qX $A/dist/lmchat.apk classes2.dex)
rm -rf build/jni
mkdir -p build/jni
for abi in arm64-v8a armeabi-v7a; do
  mkdir -p build/jni/lib/$abi
  cp $L/aar/jni/$abi/libsignal_jni.so build/jni/lib/$abi/
done
(cd build/jni && zip -qr $A/dist/lmchat.apk lib)

# 5) 对齐 + 签名
$BT/zipalign -f -p 4 dist/lmchat.apk dist/lmchat-aligned.apk
mv dist/lmchat-aligned.apk dist/lmchat.apk
$BT/apksigner sign --ks app.keystore --ks-key-alias app \
  --ks-pass pass:"$KSPASS" --key-pass pass:"$KSPASS" \
  --v1-signing-enabled true dist/lmchat.apk
$BT/apksigner verify dist/lmchat.apk && echo BUILD_OK
ls -lh dist/lmchat.apk
