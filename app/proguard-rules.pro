# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile
-dontobfuscate
#-dontshrink
-keep class com.unnamed.b.atv.**{*;}
#-keep class sun1.security.**
-keep public class com.riyadm.apkrepacker.ui.projectview.FolderHolder
-keep public class com.riyadm.apkrepacker.ui.projectview.FolderHolder$TreeItem
-keepclassmembers class com.riyadm.apkrepacker.ui.projectview.FolderHolder{
public *;
private *;
}
-keep class sun1.security.x509.**{*;}
-keep class com.android.apksig.**{*;}
-keep class com.google.common.**{*;}
-keep class com.android.tools.smali.**{*;}
# jadx: plugins are discovered through ServiceLoader and instantiated reflectively
-keep class jadx.** {*;}
-dontwarn jadx.**
-dontwarn com.android.tools.smali.**
-dontwarn org.slf4j.**
-dontwarn javax.annotation.**
-dontwarn org.antlr.**
-dontwarn com.google.errorprone.annotations.**
-keepclassmembernames class * {
@com.google.gson.annotations.SerializedName <fields>;
}

#-keep @inteface com.google.gson.annotations.SerializedName
# apktool 3 (org.apktool:apktool-lib + brut.j.*). Keep it whole: it's large, but decode/build
# paths are rarely exercised by R8's reachability analysis the way the app uses them, and the
# app replaces two of its classes (brut.androlib.res.decoder.ResNinePatchStreamDecoder,
# brut.xml.XmlUtils) by name. Its bundled framework is read as a java resource
# (/prebuilt/android-framework.jar), which R8 keeps as-is.
-keep class brut.** { *; }
# Desktop-only APIs referenced by apktool/its dependencies, never reached on Android.
-dontwarn java.awt.**
-dontwarn javax.imageio.**
-dontwarn java.lang.ProcessHandle
-dontwarn org.apache.commons.text.**
