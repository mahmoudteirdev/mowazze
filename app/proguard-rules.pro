# Mowazze3 ProGuard / R8 Rules

# Preserve Shizuku library and its private newProcess reflection method
-keepclassmembers class rikka.shizuku.Shizuku {
    private static java.lang.Process newProcess(java.lang.String[], java.lang.String[], java.lang.String);
}
-keep class rikka.shizuku.** { *; }

# Preserve HiddenApiBypass
-keep class org.lsposed.hiddenapibypass.** { *; }

# Preserve ZXing for QR code generation
-keep class com.google.zxing.** { *; }

# Preserve Proxy Server
-keep class com.mowazze3.data.proxy.** { *; }

# Preserve dynamic proxy interfaces and system AIDL descriptors
-keep interface android.net.IIntResultListener { *; }
-keep class * implements android.os.IInterface { *; }
-keepclassmembers class * extends android.os.Binder { *; }
