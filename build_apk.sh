#!/bin/bash
set -e

ANDROID_JAR=/usr/lib/android-sdk/platforms/android-23/android.jar
AAPT=/usr/lib/android-sdk/build-tools/29.0.3/aapt
DX=/usr/lib/android-sdk/build-tools/debian/dx
ZIPALIGN=/usr/lib/android-sdk/build-tools/29.0.3/zipalign
APKSIGNER=/usr/bin/apksigner

SRC=/home/user/jdjv/app/src/main
OUT=/tmp/smsbot_build
MANIFEST=$SRC/AndroidManifest.xml
RES=$SRC/res
JAVA_SRC=$SRC/java

rm -rf $OUT
mkdir -p $OUT/gen $OUT/classes $OUT/dex $OUT/apk

echo "=== مرحله ۱: ساخت R.java ==="
$AAPT package -f -m \
    -S $RES \
    -J $OUT/gen \
    -M $MANIFEST \
    -I $ANDROID_JAR

echo "=== مرحله ۲: کامپایل Java ==="
find $JAVA_SRC -name "*.java" > /tmp/java_files.txt
find $OUT/gen -name "*.java" >> /tmp/java_files.txt

javac -source 8 -target 8 \
    -classpath $ANDROID_JAR \
    -d $OUT/classes \
    @/tmp/java_files.txt

echo "=== مرحله ۳: تبدیل به DEX ==="
$DX --dex --output=$OUT/dex/classes.dex $OUT/classes

echo "=== مرحله ۴: بسته‌بندی APK ==="
$AAPT package -f \
    -M $MANIFEST \
    -S $RES \
    -I $ANDROID_JAR \
    -F $OUT/app-unsigned.apk \
    $OUT/dex

echo "=== مرحله ۵: تراز کردن APK (قبل از امضا) ==="
$ZIPALIGN -f 4 $OUT/app-unsigned.apk $OUT/app-aligned.apk

echo "=== مرحله ۶: تولید کلید امضا (دائمی) ==="
KEYSTORE=/home/user/jdjv/smsbot.keystore
if [ ! -f $KEYSTORE ]; then
    keytool -genkey -v \
        -keystore $KEYSTORE \
        -alias smsbot \
        -keyalg RSA \
        -keysize 2048 \
        -validity 10000 \
        -storepass android \
        -keypass android \
        -dname "CN=SmsBat, OU=Dev, O=jdjv, L=Tehran, S=Tehran, C=IR" \
        2>/dev/null
    echo "کلید جدید ساخته شد."
else
    echo "از کلید موجود استفاده می‌شه."
fi

echo "=== مرحله ۷: امضای APK (آخرین مرحله) ==="
$APKSIGNER sign \
    --ks $KEYSTORE \
    --ks-key-alias smsbot \
    --ks-pass pass:android \
    --key-pass pass:android \
    --v1-signing-enabled true \
    --v2-signing-enabled true \
    --out /home/user/jdjv/SmsBat.apk \
    $OUT/app-aligned.apk

echo ""
echo "✅ APK آماده است: /home/user/jdjv/SmsBat.apk"
ls -lh /home/user/jdjv/SmsBat.apk
