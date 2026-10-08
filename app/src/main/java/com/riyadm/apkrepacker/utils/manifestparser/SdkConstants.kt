package com.riyadm.apkrepacker.utils.manifestparser

/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.File

/**
 * Constant definition class.<br>
 * <br>
 * Most constants have a prefix defining the content.
 * <ul>
 * <li><code>OS_</code> OS path constant. These paths are different depending on the platform.</li>
 * <li><code>FN_</code> File name constant.</li>
 * <li><code>FD_</code> Folder name constant.</li>
 * <li><code>TAG_</code> XML element tag name</li>
 * <li><code>ATTR_</code> XML attribute name</li>
 * <li><code>VALUE_</code> XML attribute value</li>
 * <li><code>CLASS_</code> Class name</li>
 * <li><code>DOT_</code> File name extension, including the dot </li>
 * <li><code>EXT_</code> File name extension, without the dot </li>
 * </ul>
 */
@Suppress("unused") // Not documenting all the fields here
object SdkConstants {
    const val PLATFORM_UNKNOWN: Int = 0
    const val PLATFORM_LINUX: Int = 1
    const val PLATFORM_WINDOWS: Int = 2
    const val PLATFORM_DARWIN: Int = 3

    /**
     * Returns current platform, one of {@link #PLATFORM_WINDOWS}, {@link #PLATFORM_DARWIN}, {@link
     * #PLATFORM_LINUX} or {@link #PLATFORM_UNKNOWN}.
     */
    @JvmField
    val CURRENT_PLATFORM: Int = currentPlatform()

    /**
     * ANDROID_HOME environment variable that specifies the installation path of an Android SDK.
     *
     * @see <a href="https://developer.android.com/studio/command-line/variables">Android SDK
     * environment variables</a>
     * @deprecated Use {@link #ANDROID_SDK_ROOT_ENV} instead.
     */
    @Deprecated("Use ANDROID_SDK_ROOT_ENV instead.")
    const val ANDROID_HOME_ENV: String = "ANDROID_HOME"

    /**
     * ANDROID_SDK_ROOT environment variable that specifies the installation path of an Android SDK.
     *
     * @see <a href="https://developer.android.com/studio/command-line/variables">Android SDK
     * environment variables</a>
     */
    const val ANDROID_SDK_ROOT_ENV: String = "ANDROID_SDK_ROOT"

    /**
     * Property in local.properties file that specifies the path of the Android SDK.
     */
    const val SDK_DIR_PROPERTY: String = "sdk.dir"

    /**
     * Property in local.properties file that specifies the path of the Android NDK.
     */
    const val NDK_DIR_PROPERTY: String = "ndk.dir"

    /**
     * Property in local.properties file that specifies the path of CMake.
     */
    const val CMAKE_DIR_PROPERTY: String = "cmake.dir"

    /**
     * Property in local.properties file that specifies the path to symlink the NDK under.
     */
    const val NDK_SYMLINK_DIR: String = "ndk.symlinkdir"

    /**
     * Property in gradle-wrapper.properties file that specifies the URL to the correct Gradle
     * distribution.
     */
    const val GRADLE_DISTRIBUTION_URL_PROPERTY: String = "distributionUrl"

    /**
     * The encoding we strive to use for all files we write.
     *
     * <p>When possible, use the APIs which take a {@link java.nio.charset.Charset} and pass in
     * {@link com.google.common.base.Charsets#UTF_8} instead of using the String encoding method.
     */
    const val UTF_8: String = "UTF-8"

    /**
     * Charset for the ini file handled by the SDK.
     */
    const val INI_CHARSET: String = UTF_8

    /**
     * Path separator used by Gradle
     */
    const val GRADLE_PATH_SEPARATOR: String = ":"

    /**
     * An SDK Project's AndroidManifest.xml file
     */
    const val FN_ANDROID_MANIFEST_XML: String = "AndroidManifest.xml"

    const val FN_SHARED_LIBRARY_ANDROID_MANIFEST_XML: String = "SharedLibraryAndroidManifest.xml" // $NON-NLS-1$
    /**
     * pre-dex jar filename. i.e. "classes.jar"
     */
    const val FN_CLASSES_JAR: String = "classes.jar"
    /**
     * api.jar filename
     */
    const val FN_API_JAR: String = "api.jar"
    /**
     * Dex filename inside the APK. i.e. "classes.dex"
     */
    const val FN_APK_CLASSES_DEX: String = "classes.dex"
    /**
     * Dex filename inside the APK. e.g. "classes2.dex"
     */
    const val FN_APK_CLASSES_N_DEX: String = "classes%d.dex"
    /**
     * Regex to match dex filenames inside the APK. e.g., classes.dex, classes2.dex
     */
    const val REGEX_APK_CLASSES_DEX: String = "classes\\d*\\.dex"

    /**
     * intermediate publishing between projects
     */
    const val FN_INTERMEDIATE_RES_JAR: String = "res.jar"
    const val FN_INTERMEDIATE_FULL_JAR: String = "full.jar"

    /**
     * list of splits for a variant
     */
    const val FN_APK_LIST: String = "apk-list.gson"

    /**
     * An SDK Project's build.xml file
     */
    const val FN_BUILD_XML: String = "build.xml"
    /**
     * An SDK Project's build.gradle file
     */
    const val FN_BUILD_GRADLE: String = "build.gradle"
    /**
     * An SDK Project's build.gradle Kotlin script file
     */
    const val FN_BUILD_GRADLE_KTS: String = "build.gradle.kts"
    /**
     * An SDK Project's settings.gradle file
     */
    const val FN_SETTINGS_GRADLE: String = "settings.gradle"
    /**
     * An SDK Project's settings.gradle Kotlin script file
     */
    const val FN_SETTINGS_GRADLE_KTS: String = "settings.gradle.kts"
    /**
     * An SDK Project's gradle.properties file
     */
    const val FN_GRADLE_PROPERTIES: String = "gradle.properties"
    /**
     * An SDK Project's gradle daemon executable
     */
    const val FN_GRADLE_UNIX: String = "gradle"
    /**
     * An SDK Project's gradle.bat daemon executable (gradle for windows)
     */
    const val FN_GRADLE_WIN: String = FN_GRADLE_UNIX + ".bat"
    /**
     * An SDK Project's gradlew file
     */
    const val FN_GRADLE_WRAPPER_UNIX: String = "gradlew"
    /**
     * An SDK Project's gradlew.bat file (gradlew for windows)
     */
    const val FN_GRADLE_WRAPPER_WIN: String = FN_GRADLE_WRAPPER_UNIX + ".bat"
    /**
     * An SDK Project's gradle wrapper library
     */
    const val FN_GRADLE_WRAPPER_JAR: String = "gradle-wrapper.jar"
    /**
     * Name of the framework library, i.e. "android.jar"
     */
    const val FN_FRAMEWORK_LIBRARY: String = "android.jar"
    /**
     * Name of the framework library, i.e. "uiautomator.jar"
     */
    const val FN_UI_AUTOMATOR_LIBRARY: String = "uiautomator.jar"
    /**
     * Name of the layout attributes, i.e. "attrs.xml"
     */
    const val FN_ATTRS_XML: String = "attrs.xml"
    /**
     * Name of the layout attributes, i.e. "attrs_manifest.xml"
     */
    const val FN_ATTRS_MANIFEST_XML: String = "attrs_manifest.xml"
    /**
     * framework aidl import file
     */
    const val FN_FRAMEWORK_AIDL: String = "framework.aidl"
    /**
     * framework renderscript folder
     */
    const val FN_FRAMEWORK_RENDERSCRIPT: String = "renderscript"
    /**
     * framework include folder
     */
    const val FN_FRAMEWORK_INCLUDE: String = "include"
    /**
     * framework include (clang) folder
     */
    const val FN_FRAMEWORK_INCLUDE_CLANG: String = "clang-include"
    /**
     * layoutlib.jar file
     */
    const val FN_LAYOUTLIB_JAR: String = "layoutlib.jar"
    /**
     * widget list file
     */
    const val FN_WIDGETS: String = "widgets.txt"
    /**
     * Intent activity actions list file
     */
    const val FN_INTENT_ACTIONS_ACTIVITY: String = "activity_actions.txt"
    /**
     * Intent broadcast actions list file
     */
    const val FN_INTENT_ACTIONS_BROADCAST: String = "broadcast_actions.txt"
    /**
     * Intent service actions list file
     */
    const val FN_INTENT_ACTIONS_SERVICE: String = "service_actions.txt"
    /**
     * Intent category list file
     */
    const val FN_INTENT_CATEGORIES: String = "categories.txt"
    /**
     * Name of the lint library, i.e. "lint.jar"
     */
    const val FN_LINT_JAR: String = "lint.jar"

    /**
     * annotations support jar
     */
    const val FN_ANNOTATIONS_JAR: String = "annotations.jar"

    /**
     * platform build property file
     */
    const val FN_BUILD_PROP: String = "build.prop"
    /**
     * plugin properties file
     */
    const val FN_PLUGIN_PROP: String = "plugin.prop"
    /**
     * add-on manifest file
     */
    const val FN_MANIFEST_INI: String = "manifest.ini"
    /**
     * add-on layout device XML file.
     */
    const val FN_DEVICES_XML: String = "devices.xml"
    /**
     * hardware properties definition file
     */
    const val FN_HARDWARE_INI: String = "hardware-properties.ini"

    /**
     * project property file
     */
    const val FN_PROJECT_PROPERTIES: String = "project.properties"

    /**
     * project local property file
     */
    const val FN_LOCAL_PROPERTIES: String = "local.properties"

    /**
     * project ant property file
     */
    const val FN_ANT_PROPERTIES: String = "ant.properties"

    /**
     * project local property file
     */
    const val FN_GRADLE_WRAPPER_PROPERTIES: String = "gradle-wrapper.properties"

    /**
     * Skin layout file
     */
    const val FN_SKIN_LAYOUT: String = "layout"

    /**
     * dx.jar file
     */
    const val FN_DX_JAR: String = "dx.jar"

    /**
     * dx executable (with extension for the current OS)
     */
    @JvmField
    val FN_DX: String = "dx" + ext(".bat", "")

    /**
     * aapt executable (with extension for the current OS)
     */
    @JvmField
    val FN_AAPT: String = "aapt" + ext(".exe", "")

    /**
     * aapt2 executable (with extension for the current OS)
     */
    @JvmField
    val FN_AAPT2: String = "aapt2" + ext(".exe", "")

    /**
     * aidl executable (with extension for the current OS)
     */
    @JvmField
    val FN_AIDL: String = "aidl" + ext(".exe", "")

    /**
     * renderscript executable (with extension for the current OS)
     */
    @JvmField
    val FN_RENDERSCRIPT: String = "llvm-rs-cc" + ext(".exe", "")

    /**
     * renderscript support exe (with extension for the current OS)
     */
    @JvmField
    val FN_BCC_COMPAT: String = "bcc_compat" + ext(".exe", "")

    /**
     * renderscript support linker for ARM (with extension for the current OS)
     */
    @JvmField
    val FN_LD_ARM: String = "arm-linux-androideabi-ld" + ext(".exe", "")

    /**
     * renderscript support linker for ARM64 (with extension for the current OS)
     */
    @JvmField
    val FN_LD_ARM64: String = "aarch64-linux-android-ld" + ext(".exe", "")

    /**
     * renderscript support linker for X86 (with extension for the current OS)
     */
    @JvmField
    val FN_LD_X86: String = "i686-linux-android-ld" + ext(".exe", "")

    /**
     * renderscript support linker for X86_64 (with extension for the current OS)
     */
    @JvmField
    val FN_LD_X86_64: String = "x86_64-linux-android-ld" + ext(".exe", "")

    /**
     * renderscript support linker for MIPS (with extension for the current OS)
     */
    @JvmField
    val FN_LD_MIPS: String = "mipsel-linux-android-ld" + ext(".exe", "")

    /**
     * 64 bit (host) renderscript support linker for all ABIs (with extension for the current OS)
     */
    @JvmField
    val FN_LLD: String = "lld" + ext(".exe", "")

    /**
     * adb executable (with extension for the current OS)
     */
    @JvmField
    val FN_ADB: String = "adb" + ext(".exe", "")

    /**
     * emulator executable for the current OS
     */
    @JvmField
    val FN_EMULATOR: String = "emulator" + ext(".exe", "")

    /**
     * emulator-check executable for the current OS
     */
    @JvmField
    val FN_EMULATOR_CHECK: String = "emulator-check" + ext(".exe", "")

    /**
     * zipalign executable (with extension for the current OS)
     */
    @JvmField
    val FN_ZIPALIGN: String = "zipalign" + ext(".exe", "")

    /**
     * dexdump executable (with extension for the current OS)
     */
    @JvmField
    val FN_DEXDUMP: String = "dexdump" + ext(".exe", "")

    /**
     * proguard executable (with extension for the current OS)
     */
    @JvmField
    val FN_PROGUARD: String = "proguard" + ext(".bat", ".sh")

    /**
     * find_lock for Windows (with extension for the current OS)
     */
    @JvmField
    val FN_FIND_LOCK: String = "find_lock" + ext(".exe", "")

    /**
     * hprof-conv executable (with extension for the current OS)
     */
    @JvmField
    val FN_HPROF_CONV: String = "hprof-conv" + ext(".exe", "")

    /**
     * jack.jar
     */
    const val FN_JACK: String = "jack.jar"
    /**
     * jill.jar
     */
    const val FN_JILL: String = "jill.jar"
    /**
     * code coverage plugin for jack
     */
    const val FN_JACK_COVERAGE_PLUGIN: String = "jack-coverage-plugin.jar"
    /**
     * jack-jacoco-report.jar
     */
    const val FN_JACK_JACOCO_REPORTER: String = "jack-jacoco-reporter.jar"
    /**
     * core-lambda-stubs.jar necessary for lambda compilation.
     */
    const val FN_CORE_LAMBDA_STUBS: String = "core-lambda-stubs.jar" // $NON-NLS-1$

    /**
     * split-select
     */
    @JvmField
    val FN_SPLIT_SELECT: String = "split-select" + ext(".exe", "")

    /**
     * glslc
     */
    const val FD_SHADER_TOOLS: String = "shader-tools"

    @JvmField
    val FN_GLSLC: String = "glslc" + ext(".exe", "")

    /**
     * properties file for SDK Updater packages
     */
    const val FN_SOURCE_PROP: String = "source.properties"
    /**
     * properties file for content hash of installed packages
     */
    const val FN_CONTENT_HASH_PROP: String = "content_hash.properties"
    /**
     * properties file for the SDK
     */
    const val FN_SDK_PROP: String = "sdk.properties"

    const val FN_ANDROIDX_RS_JAR: String = "androidx-rs.jar"
    const val FN_RENDERSCRIPT_V8_JAR: String = "renderscript-v8.jar"

    const val FN_ANDROIDX_RENDERSCRIPT_PACKAGE: String = "androidx.renderscript"
    const val FN_RENDERSCRIPT_V8_PACKAGE: String = "android.support.v8.renderscript"

    /**
     * filename for gdbserver.
     */
    const val FN_GDBSERVER: String = "gdbserver"

    const val FN_GDB_SETUP: String = "gdb.setup"

    /**
     * proguard config file in a bundle.
     */
    const val FN_PROGUARD_TXT: String = "proguard.txt"
    /**
     * global Android proguard config file
     */
    const val FN_ANDROID_PROGUARD_FILE: String = "proguard-android.txt"
    /**
     * global Android proguard config file with optimization enabled
     */
    const val FN_ANDROID_OPT_PROGUARD_FILE: String = "proguard-android-optimize.txt"
    /**
     * default proguard config file with new file extension (for project specific stuff)
     */
    const val FN_PROJECT_PROGUARD_FILE: String = "proguard-project.txt"
    /**
     * proguard rules generated by aapt
     */
    const val FN_AAPT_RULES: String = "aapt_rules.txt"
    /**
     * merged proguard rules generated by aapt, from base and its features
     */
    const val FN_MERGED_AAPT_RULES: String = "merged_aapt_rules.txt"
    /**
     * File holding a list of advanced features
     */
    const val FN_ADVANCED_FEATURES: String = "advancedFeatures.ini"
    /**
     * File holding a list of advanced features when user is on canary channel
     */
    const val FN_ADVANCED_FEATURES_CANARY: String = "advancedFeaturesCanary.ini"
    /**
     * File contains a serialized AndroidGradlePluginAttributionData object to be deserialized and
     * used in the IDE build attribution.
     */
    const val FN_AGP_ATTRIBUTION_DATA: String = "androidGradlePluginAttributionData"

    /* Folder Names for Android Projects . */

    /**
     * Resources folder name, i.e. "res".
     */
    const val FD_RESOURCES: String = "res"
    /**
     * Assets folder name, i.e. "assets"
     */
    const val FD_ASSETS: String = "assets"
    /**
     * Default source folder name in an SDK project, i.e. "src".
     *
     * <p>Note: this is not the same as {@link #FD_PKG_SOURCES} which is an SDK sources folder for
     * packages.
     */
    const val FD_SOURCES: String = "src"
    /**
     * Default main source set folder name, i.e. "main"
     */
    const val FD_MAIN: String = "main"
    /**
     * Default test source set folder name, i.e. "androidTest"
     */
    const val FD_TEST: String = "androidTest"
    /**
     * Default unit test source set folder name, i.e. "test"
     */
    const val FD_UNIT_TEST: String = "test"
    /**
     * Default java code folder name, i.e. "java"
     */
    const val FD_JAVA: String = "java"
    /**
     * Default native code folder name, i.e. "jni"
     */
    const val FD_JNI: String = "jni"
    /**
     * Default gradle folder name, i.e. "gradle"
     */
    const val FD_GRADLE: String = "gradle"
    /**
     * Default gradle wrapper folder name, i.e. "gradle/wrapper"
     */
    @JvmField
    val FD_GRADLE_WRAPPER: String = FD_GRADLE + File.separator + "wrapper"
    /**
     * Default generated source folder name, i.e. "gen"
     */
    const val FD_GEN_SOURCES: String = "gen"
    /**
     * Default native library folder name inside the project, i.e. "libs" While the folder inside
     * the .apk is "lib", we call that one libs because that's what we use in ant for both .jar and
     * .so and we need to make the 2 development ways compatible.
     */
    const val FD_NATIVE_LIBS: String = "libs"
    /**
     * Native lib folder inside the APK: "lib"
     */
    const val FD_APK_NATIVE_LIBS: String = "lib"
    /**
     * Default output folder name, i.e. "bin"
     */
    const val FD_OUTPUT: String = "bin"
    /**
     * Classes output folder name, i.e. "classes"
     */
    const val FD_CLASSES_OUTPUT: String = "classes"
    /**
     * proguard output folder for mapping, etc.. files
     */
    const val FD_PROGUARD: String = "proguard"
    /**
     * aidl output folder for copied aidl files
     */
    const val FD_AIDL: String = "aidl"
    /**
     * aar libs folder
     */
    const val FD_AAR_LIBS: String = "libs"
    /**
     * symbols output folder
     */
    const val FD_SYMBOLS: String = "symbols"
    /**
     * resource blame output folder
     */
    const val FD_BLAME: String = "blame"

    /**
     * rs Libs output folder for support mode
     */
    const val FD_RS_LIBS: String = "rsLibs"
    /**
     * rs Libs output folder for support mode
     */
    const val FD_RS_OBJ: String = "rsObj"

    /**
     * jars folder
     */
    const val FD_JARS: String = "jars"

    /* Folder Names for the Android SDK */

    /**
     * Name of the SDK platforms folder.
     */
    const val FD_PLATFORMS: String = "platforms"
    /**
     * Name of the SDK addons folder.
     */
    const val FD_ADDONS: String = "add-ons"
    /**
     * Name of the SDK system-images folder.
     */
    const val FD_SYSTEM_IMAGES: String = "system-images"
    /**
     * Name of the SDK sources folder where source packages are installed.
     *
     * <p>Note this is not the same as {@link #FD_SOURCES} which is the folder name where sources
     * are installed inside a project.
     */
    const val FD_PKG_SOURCES: String = "sources"
    /**
     * Name of the legacy SDK tools folder.
     */
    const val FD_TOOLS: String = "tools"
    /**
     * Name of the SDK command-line tools folder.
     */
    const val FD_CMDLINE_TOOLS: String = "cmdline-tools"
    /**
     * Name of the SDK emulator folder.
     */
    const val FD_EMULATOR: String = "emulator"
    /**
     * Name of the SDK tools/support folder.
     */
    const val FD_SUPPORT: String = "support"
    /**
     * Name of the SDK platform tools folder.
     */
    const val FD_PLATFORM_TOOLS: String = "platform-tools"
    /**
     * Name of the SDK build tools folder.
     */
    const val FD_BUILD_TOOLS: String = "build-tools"
    /**
     * Name of the SDK tools/lib folder.
     */
    const val FD_LIB: String = "lib"
    /**
     * Name of the SDK docs folder.
     */
    const val FD_DOCS: String = "docs"
    /**
     * Name of the doc folder containing API reference doc (javadoc)
     */
    const val FD_DOCS_REFERENCE: String = "reference"
    /**
     * Name of the SDK images folder.
     */
    const val FD_IMAGES: String = "images"
    /**
     * Name of the ABI to support.
     */
    const val ABI_ARMEABI: String = "armeabi"

    const val ABI_ARMEABI_V7A: String = "armeabi-v7a"
    const val ABI_ARM64_V8A: String = "arm64-v8a"
    const val ABI_INTEL_ATOM: String = "x86"
    const val ABI_INTEL_ATOM64: String = "x86_64"
    const val ABI_MIPS: String = "mips"
    const val ABI_MIPS64: String = "mips64"
    /**
     * Name of the CPU arch to support.
     */
    const val CPU_ARCH_ARM: String = "arm"

    const val CPU_ARCH_ARM64: String = "arm64"
    const val CPU_ARCH_INTEL_ATOM: String = "x86"
    const val CPU_ARCH_INTEL_ATOM64: String = "x86_64"
    const val CPU_ARCH_MIPS: String = "mips"
    /**
     * TODO double-check this is appropriate value for mips64
     */
    const val CPU_ARCH_MIPS64: String = "mips64"
    /**
     * Name of the CPU model to support.
     */
    const val CPU_MODEL_CORTEX_A8: String = "cortex-a8"

    /**
     * Name of the SDK skins folder.
     */
    const val FD_SKINS: String = "skins"
    /**
     * Name of the SDK samples folder.
     */
    const val FD_SAMPLES: String = "samples"
    /**
     * Name of the SDK extras folder.
     */
    const val FD_EXTRAS: String = "extras"

    const val FD_ANDROID_EXTRAS: String = "android"
    const val FD_M2_REPOSITORY: String = "m2repository"
    const val FD_NDK: String = "ndk-bundle"
    const val FD_LLDB: String = "lldb"
    const val FD_CMAKE: String = "cmake"
    const val FD_NDK_SIDE_BY_SIDE: String = "ndk"
    const val FD_GAPID: String = "gapid"
    /**
     * Sample data for the project sample data
     */
    const val FD_SAMPLE_DATA: String = "sampledata"

    /**
     * Name of an extra's sample folder. Ideally extras should have one {@link #FD_SAMPLES} folder
     * containing one or more sub-folders (one per sample). However some older extras might contain
     * a single "sample" folder with directly the samples files in it. When possible we should
     * encourage extras' owners to move to the multi-samples format.
     */
    const val FD_SAMPLE: String = "sample"
    /**
     * Name of the SDK templates folder, i.e. "templates"
     */
    const val FD_TEMPLATES: String = "templates"
    /**
     * Name of the SDK Ant folder, i.e. "ant"
     */
    const val FD_ANT: String = "ant"
    /**
     * Name of the SDK data folder, i.e. "data"
     */
    const val FD_DATA: String = "data"
    /**
     * Name of the SDK renderscript folder, i.e. "rs"
     */
    const val FD_RENDERSCRIPT: String = "rs"
    /**
     * Name of the Java resources folder, i.e. "resources"
     */
    const val FD_JAVA_RES: String = "resources"
    /**
     * Name of the SDK resources folder, i.e. "res"
     */
    const val FD_RES: String = "res"
    /**
     * Name of the SDK font folder, i.e. "fonts"
     */
    const val FD_FONTS: String = "fonts"
    /**
     * Name of the android sources directory and the root of the SDK sources package folder.
     */
    const val FD_ANDROID_SOURCES: String = "sources"
    /**
     * Name of the addon libs folder.
     */
    const val FD_ADDON_LIBS: String = "libs"
    /**
     * Name of the merged resources folder.
     */
    const val FD_MERGED: String = "merged"
    /**
     * Name of the compiled resources folder.
     */
    const val FD_COMPILED: String = "compiled"
    /**
     * Name of the folder containing partial R files.
     */
    const val FD_PARTIAL_R: String = "partial-r"
    /**
     * Name of the output dex folder.
     */
    const val FD_DEX: String = "dex"
    /**
     * Name of the generated source folder.
     */
    const val FD_SOURCE_GEN: String = "source"
    /**
     * Name of the generated R.class source folder
     */
    const val FD_RES_CLASS: String = "r"

    /**
     * Name of the cache folder in the $HOME/.android.
     */
    const val FD_CACHE: String = "cache"

    /**
     * Name of the build attribution internal output folder
     */
    const val FD_BUILD_ATTRIBUTION: String = "build-attribution"

    /**
     * API codename of a release (non preview) system image or platform.
     */
    const val CODENAME_RELEASE: String = "REL"

    /**
     * Namespace pattern for the custom resource XML, i.e. "http://schemas.android.com/apk/res/%s"
     *
     * <p>This string contains a %s. It must be combined with the desired Java package, e.g.:
     *
     * <pre>
     *    String.format(SdkConstants.NS_CUSTOM_RESOURCES_S, "android");
     *    String.format(SdkConstants.NS_CUSTOM_RESOURCES_S, "com.test.mycustomapp");
     * </pre>
     * <p>
     * Note: if you need an URI specifically for the "android" namespace, consider using {@link
     * #ANDROID_URI} instead.
     */
    const val NS_CUSTOM_RESOURCES_S: String = "http://schemas.android.com/apk/res/%1\$s"

    /**
     * The name of the uses-library that provides "android.test.runner"
     */
    const val ANDROID_TEST_RUNNER_LIB: String = "android.test.runner"

    /* Folder path relative to the SDK root */
    /**
     * Path of the documentation directory relative to the sdk folder. This is an OS path, ending
     * with a separator.
     */
    @JvmField
    val OS_SDK_DOCS_FOLDER: String = FD_DOCS + File.separator

    /**
     * Path of the platform tools directory relative to the sdk folder. This is an OS path, ending
     * with a separator.
     */
    @JvmField
    val OS_SDK_PLATFORM_TOOLS_FOLDER: String = FD_PLATFORM_TOOLS + File.separator

    /* Folder paths relative to a platform or add-on folder */

    /**
     * Path of the images directory relative to a platform or addon folder. This is an OS path,
     * ending with a separator.
     */
    @JvmField
    val OS_IMAGES_FOLDER: String = FD_IMAGES + File.separator

    /**
     * Path of the skin directory relative to a platform or addon folder. This is an OS path, ending
     * with a separator.
     */
    @JvmField
    val OS_SKINS_FOLDER: String = FD_SKINS + File.separator

    /* Folder paths relative to a Platform folder */

    /**
     * Path of the data directory relative to a platform folder. This is an OS path, ending with a
     * separator.
     */
    @JvmField
    val OS_PLATFORM_DATA_FOLDER: String = FD_DATA + File.separator

    /**
     * Path of the renderscript directory relative to a platform folder. This is an OS path, ending
     * with a separator.
     */
    @JvmField
    val OS_PLATFORM_RENDERSCRIPT_FOLDER: String = FD_RENDERSCRIPT + File.separator

    /**
     * Path of the samples directory relative to a platform folder. This is an OS path, ending with
     * a separator.
     */
    @JvmField
    val OS_PLATFORM_SAMPLES_FOLDER: String = FD_SAMPLES + File.separator

    /**
     * Path of the resources directory relative to a platform folder. This is an OS path, ending
     * with a separator.
     */
    @JvmField
    val OS_PLATFORM_RESOURCES_FOLDER: String = OS_PLATFORM_DATA_FOLDER + FD_RES + File.separator

    /**
     * Path of the fonts directory relative to a platform folder. This is an OS path, ending with a
     * separator.
     */
    @JvmField
    val OS_PLATFORM_FONTS_FOLDER: String = OS_PLATFORM_DATA_FOLDER + FD_FONTS + File.separator

    /**
     * Path of the android source directory relative to a platform folder. This is an OS path,
     * ending with a separator.
     */
    @JvmField
    val OS_PLATFORM_SOURCES_FOLDER: String = FD_ANDROID_SOURCES + File.separator

    /**
     * Path of the android templates directory relative to a platform folder. This is an OS path,
     * ending with a separator.
     */
    @JvmField
    val OS_PLATFORM_TEMPLATES_FOLDER: String = FD_TEMPLATES + File.separator

    /**
     * Path of the Ant build rules directory relative to a platform folder. This is an OS path,
     * ending with a separator.
     */
    @JvmField
    val OS_PLATFORM_ANT_FOLDER: String = FD_ANT + File.separator
    /**
     * Path of the layoutlib.jar file relative to a platform folder.
     */
    @JvmField
    val OS_PLATFORM_LAYOUTLIB_JAR: String = OS_PLATFORM_DATA_FOLDER + FN_LAYOUTLIB_JAR
    /**
     * Path of the renderscript include folder relative to a platform folder.
     */
    @JvmField
    val OS_FRAMEWORK_RS: String = FN_FRAMEWORK_RENDERSCRIPT + File.separator + FN_FRAMEWORK_INCLUDE
    /**
     * Path of the renderscript (clang) include folder relative to a platform folder.
     */
    @JvmField
    val OS_FRAMEWORK_RS_CLANG: String = FN_FRAMEWORK_RENDERSCRIPT + File.separator + FN_FRAMEWORK_INCLUDE_CLANG
    /**
     * Path of the images directory relative to a folder folder. This is an OS path, ending with a
     * separator.
     */
    @JvmField
    val OS_ADDON_LIBS_FOLDER: String = FD_ADDON_LIBS + File.separator
    /**
     * Skin default
     */
    const val SKIN_DEFAULT: String = "default"

    /* Folder paths relative to a addon folder */
    /**
     * SDK property: ant templates revision
     */
    const val PROP_SDK_ANT_TEMPLATES_REVISION: String = "sdk.ant.templates.revision"
    /**
     * SDK property: default skin
     */
    const val PROP_SDK_DEFAULT_SKIN: String = "sdk.skin.default"
    /**
     * LLDB SDK package major.minor revision compatible with the current version of Studio
     */
    const val LLDB_PINNED_REVISION: String = "3.1"
    /* Android Class Constants */
    const val CLASS_ACTIVITY: String = "android.app.Activity"
    const val CLASS_APPLICATION: String = "android.app.Application"
    const val CLASS_SERVICE: String = "android.app.Service"
    const val CLASS_BROADCASTRECEIVER: String = "android.content.BroadcastReceiver"
    const val CLASS_CONTENTPROVIDER: String = "android.content.ContentProvider"
    const val CLASS_ATTRIBUTE_SET: String = "android.util.AttributeSet"
    const val CLASS_INSTRUMENTATION: String = "android.app.Instrumentation"
    const val CLASS_INSTRUMENTATION_RUNNER: String = "android.test.InstrumentationTestRunner"
    const val CLASS_BUNDLE: String = "android.os.Bundle"
    const val CLASS_R: String = "android.R"
    const val CLASS_R_PREFIX: String = CLASS_R + "."
    const val CLASS_MANIFEST: String = "android.Manifest"
    const val CLASS_MANIFEST_PERMISSION: String = "android.Manifest\$permission"
    const val CLASS_INTENT: String = "android.content.Intent"
    const val CLASS_CONTEXT: String = "android.content.Context"
    const val CLASS_RESOURCES: String = "android.content.res.Resources"
    const val CLS_TYPED_ARRAY: String = "android.content.res.TypedArray"
    const val CLASS_VIEW: String = "android.view.View"
    const val CLASS_VIEWGROUP: String = "android.view.ViewGroup"
    const val CLASS_VIEWSTUB: String = "android.view.ViewStub"
    const val CLASS_NAME_LAYOUTPARAMS: String = "LayoutParams"
    const val CLASS_VIEWGROUP_LAYOUTPARAMS: String = CLASS_VIEWGROUP + "\$" + CLASS_NAME_LAYOUTPARAMS
    const val CLASS_NAME_FRAMELAYOUT: String = "FrameLayout"
    const val CLASS_FRAMELAYOUT: String = "android.widget." + CLASS_NAME_FRAMELAYOUT
    const val CLASS_ADAPTER: String = "android.widget.Adapter"
    const val CLASS_PREFERENCE: String = "android.preference.Preference"
    const val CLASS_ACTION_PROVIDER: String = "android.view.ActionProvider"
    const val CLASS_V4_ACTION_PROVIDER: String = "android.support.v4.view.ActionProvider"
    const val CLASS_ANDROIDX_ACTION_PROVIDER: String = "androidx.core.view.ActionProvider"
    const val CLASS_BACKUP_AGENT: String = "android.app.backup.BackupAgent"
    /**
     * MockView is part of the layoutlib bridge and used to display classes that have no rendering
     * in the graphical layout editor.
     */
    const val CLASS_MOCK_VIEW: String = "com.android.layoutlib.bridge.MockView"
    const val CLASS_LAYOUT_INFLATER: String = "android.view.LayoutInflater"
    const val CLASS_AD_VIEW: String = "com.google.android.gms.ads.AdView"
    const val CLASS_MAP_FRAGMENT: String = "com.google.android.gms.maps.MapFragment"
    const val CLASS_MAP_VIEW: String = "com.google.android.gms.maps.MapView"
    const val CLASS_BOTTOM_APP_BAR: String = "com.google.android.material.bottomappbar.BottomAppBar"
    const val CLASS_CHIP: String = "com.google.android.material.chip.Chip"
    const val CLASS_CHIP_GROUP: String = "com.google.android.material.chip.ChipGroup"
    const val CLASS_MATERIAL_BUTTON: String = "com.google.android.material.button.MaterialButton"
    const val CONSTRAINT_LAYOUT_LIB_GROUP_ID: String = "com.android.support.constraint"
    const val CONSTRAINT_LAYOUT_LIB_ARTIFACT_ID: String = "constraint-layout"
    const val CONSTRAINT_LAYOUT_LIB_ARTIFACT: String = CONSTRAINT_LAYOUT_LIB_GROUP_ID + ":" + CONSTRAINT_LAYOUT_LIB_ARTIFACT_ID
    /**
     * Latest known version of the ConstraintLayout library (as a string)
     */
    const val LATEST_CONSTRAINT_LAYOUT_VERSION: String = "1.0.2"
    /* FlexboxLayout constants */
    const val CLASS_FLEXBOX_LAYOUT: String = "com.google.android.flexbox.FlexboxLayout"
    const val FLEXBOX_LAYOUT: String = CLASS_FLEXBOX_LAYOUT
    const val FLEXBOX_LAYOUT_LIB_GROUP_ID: String = "com.google.android"
    const val FLEXBOX_LAYOUT_LIB_ARTIFACT_ID: String = "flexbox"
    const val FLEXBOX_LAYOUT_LIB_ARTIFACT: String = FLEXBOX_LAYOUT_LIB_GROUP_ID + ":" + FLEXBOX_LAYOUT_LIB_ARTIFACT_ID
    const val LATEST_FLEXBOX_LAYOUT_VERSION: String = "0.2.3"
    const val CLASS_SIMPLE_EXO_PLAYER_VIEW: String = "com.google.android.exoplayer2.ui.SimpleExoPlayerView"
    const val CLASS_EXO_PLAYBACK_CONTROL_VIEW: String = "com.google.android.exoplayer2.ui.PlaybackControlView"
    const val SIMPLE_EXO_PLAYER_VIEW: String = CLASS_SIMPLE_EXO_PLAYER_VIEW
    const val EXO_PLAYBACK_CONTROL_VIEW: String = CLASS_EXO_PLAYBACK_CONTROL_VIEW
    const val EXO_PLAYER_GROUP_ID: String = "com.google.android.exoplayer"
    const val EXO_PLAYER_ARTIFACT_ID: String = "exoplayer"
    const val EXO_PLAYER_ARTIFACT: String = EXO_PLAYER_GROUP_ID + ":" + EXO_PLAYER_ARTIFACT_ID
    /* Compose constants */
    const val CLASS_COMPOSE: String = "androidx.compose.Compose"
    const val CLASS_COMPOSE_VIEW_ADAPTER: String = "androidx.ui.tooling.preview.ComposeViewAdapter"
    /**
     * Default anim resource folder name, i.e. "anim"
     */
    const val FD_RES_ANIM: String = "anim"
    /**
     * Default animator resource folder name, i.e. "animator"
     */
    const val FD_RES_ANIMATOR: String = "animator"
    /**
     * Default color resource folder name, i.e. "color"
     */
    const val FD_RES_COLOR: String = "color"
    /**
     * Default drawable resource folder name, i.e. "drawable"
     */
    const val FD_RES_DRAWABLE: String = "drawable"
    /**
     * Default interpolator resource folder name, i.e. "interpolator"
     */
    const val FD_RES_INTERPOLATOR: String = "interpolator"
    /**
     * Default layout resource folder name, i.e. "layout"
     */
    const val FD_RES_LAYOUT: String = "layout"
    /**
     * Default menu resource folder name, i.e. "menu"
     */
    const val FD_RES_MENU: String = "menu"
    /**
     * Default mipmap resource folder name, i.e. "mipmap"
     */
    const val FD_RES_MIPMAP: String = "mipmap"
    /**
     * Default navigation resource folder name, i.e. "navigation"
     */
    const val FD_RES_NAVIGATION: String = "navigation"
    /**
     * Default values resource folder name, i.e. "values"
     */
    const val FD_RES_VALUES: String = "values"
    /**
     * Path of the attrs.xml file relative to a platform folder.
     */
    @JvmField
    val OS_PLATFORM_ATTRS_XML: String = OS_PLATFORM_RESOURCES_FOLDER +
                    SdkConstants.FD_RES_VALUES +
                    File.separator +
                    FN_ATTRS_XML
    /**
     * Path of the attrs_manifest.xml file relative to a platform folder.
     */
    @JvmField
    val OS_PLATFORM_ATTRS_MANIFEST_XML: String = OS_PLATFORM_RESOURCES_FOLDER +
                    SdkConstants.FD_RES_VALUES +
                    File.separator +
                    FN_ATTRS_MANIFEST_XML
    /**
     * Default xml resource folder name, i.e. "xml"
     */
    const val FD_RES_XML: String = "xml"
    /**
     * Default raw resource folder name, i.e. "raw"
     */
    const val FD_RES_RAW: String = "raw"
    /**
     * Base name for the resource package files
     */
    const val FN_RES_BASE: String = "resources"
    /**
     * Separator between the resource folder qualifier.
     */
    const val RES_QUALIFIER_SEP: String = "-"
    /**
     * URI of the reserved "xml" prefix.
     */
    const val XML_NAMESPACE_URI: String = "http://www.w3.org/XML/1998/namespace"
    /**
     * URI of the reserved "xmlns" prefix
     */
    const val XMLNS_URI: String = "http://www.w3.org/2000/xmlns/"
    /**
     * The "xmlns" attribute name
     */
    const val XMLNS: String = "xmlns"
    /**
     * The default prefix used for the {@link #XMLNS_URI}
     */
    const val XMLNS_PREFIX: String = "xmlns:"

    // ---- XML ----
    /**
     * Qualified name of the xmlns android declaration element
     */
    const val XMLNS_ANDROID: String = "xmlns:android"
    /**
     * The default prefix used for the {@link #ANDROID_URI} name space
     */
    const val ANDROID_NS_NAME: String = "android"
    /**
     * The default prefix used for the {@link #ANDROID_URI} name space including the colon
     */
    const val ANDROID_NS_NAME_PREFIX: String = "android:"
    @JvmField
    val ANDROID_NS_NAME_PREFIX_LEN: Int = ANDROID_NS_NAME_PREFIX.length
    /**
     * The default prefix used for the {@link #TOOLS_URI} name space
     */
    const val TOOLS_NS_NAME: String = "tools"
    /**
     * The default prefix used for the {@link #TOOLS_URI} name space including the colon
     */
    const val TOOLS_NS_NAME_PREFIX: String = "tools:"
    /**
     * The default prefix used for the app
     */
    const val APP_PREFIX: String = "app"
    /**
     * The entity for the ampersand character
     */
    const val AMP_ENTITY: String = "&amp;"
    /**
     * The entity for the quote character
     */
    const val QUOT_ENTITY: String = "&quot;"
    /**
     * The entity for the apostrophe character
     */
    const val APOS_ENTITY: String = "&apos;"
    /**
     * The entity for the less than character
     */
    const val LT_ENTITY: String = "&lt;"
    /**
     * The entity for the greater than character
     */
    const val GT_ENTITY: String = "&gt;"
    /**
     * The entity for a newline
     */
    const val NEWLINE_ENTITY: String = "&#xA;"
    /**
     * Namespace URI prefix used for all resources.
     */
    const val URI_DOMAIN_PREFIX: String = "http://schemas.android.com/"
    /**
     * Namespace URI prefix used together with a package names.
     */
    const val URI_PREFIX: String = "http://schemas.android.com/apk/res/"
    /**
     * Namespace used in XML files for Android attributes
     */
    const val ANDROID_URI: String = "http://schemas.android.com/apk/res/android"
    /**
     * @deprecated Use {@link #ANDROID_URI}.
     */
    @Deprecated("Use ANDROID_URI.")
    const val NS_RESOURCES: String = ANDROID_URI

    // ---- Elements and Attributes ----
    /**
     * Namespace used in XML files for Android Tooling attributes
     */
    const val TOOLS_URI: String = "http://schemas.android.com/tools"
    /**
     * Namespace used for auto-adjusting namespaces
     */
    const val AUTO_URI: String = "http://schemas.android.com/apk/res-auto"
    /**
     * Namespace used for specifying module distribution
     */
    const val DIST_URI: String = "http://schemas.android.com/apk/distribution"
    const val AAPT_URI: String = "http://schemas.android.com/aapt"
    /**
     * Namespace for xliff in string resources.
     */
    const val XLIFF_URI: String = "urn:oasis:names:tc:xliff:document:1.2"
    /**
     * Default prefix used for tools attributes
     */
    const val TOOLS_PREFIX: String = "tools"
    /**
     * Default prefix used for xliff tags.
     */
    const val XLIFF_PREFIX: String = "xliff"
    /**
     * Default prefix used for aapt attributes
     */
    const val AAPT_PREFIX: String = "aapt"
    /**
     * Default prefix used for distribution attributes
     */
    const val DIST_PREFIX: String = "dist"
    const val R_CLASS: String = "R"
    const val ANDROID_PKG: String = "android"
    const val ANDROID_SUPPORT_PKG: String = "android.support"
    const val ANDROIDX_PKG: String = "androidx"
    const val MATERIAL2_PKG: String = "com.google.android.material"
    const val MATERIAL1_PKG: String = "android.support.design.widget"
    const val SHERPA_PREFIX: String = "app"
    const val SHERPA_URI: String = "http://schemas.android.com/apk/res-auto"
    /**
     * Namespace for Instant App attributes in manifest files
     */

    // Tags: Manifest
    const val TAG_MANIFEST: String = "manifest"
    const val TAG_SERVICE: String = "service"
    const val TAG_PERMISSION: String = "permission"
    const val TAG_PERMISSION_GROUP: String = "permission-group"
    const val TAG_USES_FEATURE: String = "uses-feature"
    const val TAG_USES_PERMISSION: String = "uses-permission"
    const val TAG_USES_PERMISSION_SDK_23: String = "uses-permission-sdk-23"
    const val TAG_USES_PERMISSION_SDK_M: String = "uses-permission-sdk-m"
    const val TAG_USES_LIBRARY: String = "uses-library"
    const val TAG_USES_SPLIT: String = "uses-split"
    const val TAG_APPLICATION: String = "application"
    const val TAG_INTENT_FILTER: String = "intent-filter"
    const val TAG_CATEGORY: String = "category"
    const val TAG_USES_SDK: String = "uses-sdk"
    const val TAG_ACTIVITY: String = "activity"
    const val TAG_ACTIVITY_ALIAS: String = "activity-alias"
    const val TAG_RECEIVER: String = "receiver"
    const val TAG_PACKAGE: String = "package"
    const val TAG_PROVIDER: String = "provider"
    const val TAG_GRANT_PERMISSION: String = "grant-uri-permission"
    const val TAG_PATH_PERMISSION: String = "path-permission"
    const val TAG_ACTION: String = "action"
    const val TAG_INSTRUMENTATION: String = "instrumentation"
    const val TAG_META_DATA: String = "meta-data"
    const val TAG_RESOURCE: String = "resource"
    const val TAG_MODULE: String = "module"
    const val TAG_NAV_GRAPH: String = "nav-graph"
    const val TAG_QUERIES: String = "queries"
    const val TAG_INTENT: String = "intent"
    // Tags: Resources
    const val TAG_RESOURCES: String = "resources"
    const val TAG_STRING: String = "string"
    const val TAG_ARRAY: String = "array"
    const val TAG_STYLE: String = "style"
    const val TAG_ITEM: String = "item"
    const val TAG_GROUP: String = "group"
    const val TAG_STRING_ARRAY: String = "string-array"
    const val TAG_PLURALS: String = "plurals"
    const val TAG_INTEGER_ARRAY: String = "integer-array"
    const val TAG_COLOR: String = "color"
    const val TAG_DIMEN: String = "dimen"
    const val TAG_DRAWABLE: String = "drawable"
    const val TAG_MENU: String = "menu"
    const val TAG_ENUM: String = "enum"
    const val TAG_FLAG: String = "flag"
    const val TAG_ATTR: String = "attr"
    const val TAG_DECLARE_STYLEABLE: String = "declare-styleable"
    const val TAG_EAT_COMMENT: String = "eat-comment"
    const val TAG_SKIP: String = "skip"
    const val TAG_PUBLIC: String = "public"
    const val TAG_PUBLIC_GROUP: String = "public-group"
    // Tags: Adaptive icon
    const val TAG_ADAPTIVE_ICON: String = "adaptive-icon"
    const val TAG_MASKABLE_ICON: String = "maskable-icon"
    // Font family tag
    const val TAG_FONT_FAMILY: String = "font-family"
    const val TAG_FONT: String = "font"
    // Tags: XML
    const val TAG_HEADER: String = "header"
    const val TAG_APPWIDGET_PROVIDER: String = "appwidget-provider"
    const val TAG_PREFERENCE_SCREEN: String = "PreferenceScreen"
    // Tags: Layouts
    const val VIEW_TAG: String = "view"
    const val VIEW_INCLUDE: String = "include"
    const val VIEW_MERGE: String = "merge"
    const val VIEW_FRAGMENT: String = "fragment"
    const val REQUEST_FOCUS: String = "requestFocus"
    const val TAG: String = "tag"
    // Tags: Navigation
    const val TAG_INCLUDE: String = "include"
    const val TAG_DEEP_LINK: String = "deepLink"
    const val TAG_NAVIGATION: String = "navigation"
    const val ATTR_MODULE_NAME: String = "moduleName"
    const val VIEW: String = "View"
    const val VIEW_GROUP: String = "ViewGroup"
    const val FRAME_LAYOUT: String = "FrameLayout"
    const val LINEAR_LAYOUT: String = "LinearLayout"
    const val RELATIVE_LAYOUT: String = "RelativeLayout"
    const val GRID_LAYOUT: String = "GridLayout"
    const val SCROLL_VIEW: String = "ScrollView"
    const val BUTTON: String = "Button"
    const val COMPOUND_BUTTON: String = "CompoundButton"
    const val ADAPTER_VIEW: String = "AdapterView"
    const val STACK_VIEW: String = "StackView"
    const val GALLERY: String = "Gallery"
    const val GRID_VIEW: String = "GridView"
    const val TAB_HOST: String = "TabHost"
    const val RADIO_GROUP: String = "RadioGroup"
    const val RADIO_BUTTON: String = "RadioButton"
    const val SWITCH: String = "Switch"
    const val EDIT_TEXT: String = "EditText"
    const val LIST_VIEW: String = "ListView"
    const val TEXT_VIEW: String = "TextView"
    const val CHECKED_TEXT_VIEW: String = "CheckedTextView"
    const val IMAGE_VIEW: String = "ImageView"
    const val SURFACE_VIEW: String = "SurfaceView"
    const val ABSOLUTE_LAYOUT: String = "AbsoluteLayout"
    const val TABLE_LAYOUT: String = "TableLayout"
    const val TABLE_ROW: String = "TableRow"
    const val TAB_WIDGET: String = "TabWidget"
    const val IMAGE_BUTTON: String = "ImageButton"
    const val ZOOM_BUTTON: String = "ZoomButton"
    const val SEEK_BAR: String = "SeekBar"
    const val VIEW_STUB: String = "ViewStub"
    const val SPINNER: String = "Spinner"
    const val WEB_VIEW: String = "WebView"
    const val TOGGLE_BUTTON: String = "ToggleButton"
    const val CHECK_BOX: String = "CheckBox"
    const val ABS_LIST_VIEW: String = "AbsListView"
    const val PROGRESS_BAR: String = "ProgressBar"
    const val RATING_BAR: String = "RatingBar"
    const val ABS_SPINNER: String = "AbsSpinner"
    const val ABS_SEEK_BAR: String = "AbsSeekBar"
    const val VIEW_ANIMATOR: String = "ViewAnimator"
    const val VIEW_FLIPPER: String = "ViewFlipper"
    const val VIEW_SWITCHER: String = "ViewSwitcher"
    const val TEXT_SWITCHER: String = "TextSwitcher"
    const val IMAGE_SWITCHER: String = "ImageSwitcher"
    const val EXPANDABLE_LIST_VIEW: String = "ExpandableListView"
    const val HORIZONTAL_SCROLL_VIEW: String = "HorizontalScrollView"
    const val MULTI_AUTO_COMPLETE_TEXT_VIEW: String = "MultiAutoCompleteTextView"
    const val AUTO_COMPLETE_TEXT_VIEW: String = "AutoCompleteTextView"
    const val CHECKABLE: String = "Checkable"
    const val TEXTURE_VIEW: String = "TextureView"
    const val DIALER_FILTER: String = "DialerFilter"
    const val ADAPTER_VIEW_FLIPPER: String = "AdapterViewFlipper"
    const val ADAPTER_VIEW_ANIMATOR: String = "AdapterViewAnimator"
    const val VIDEO_VIEW: String = "VideoView"
    const val SEARCH_VIEW: String = "SearchView"
    const val BOTTOM_APP_BAR: String = CLASS_BOTTOM_APP_BAR
    const val MATERIAL_BUTTON: String = CLASS_MATERIAL_BUTTON
    const val CONSTRAINT_BARRIER_TOP: String = "top"
    const val CONSTRAINT_BARRIER_BOTTOM: String = "bottom"
    const val CONSTRAINT_BARRIER_LEFT: String = "left"
    const val CONSTRAINT_BARRIER_RIGHT: String = "right"
    const val CONSTRAINT_BARRIER_START: String = "start"
    const val CONSTRAINT_BARRIER_END: String = "end"
    const val CONSTRAINT_REFERENCED_IDS: String = "constraint_referenced_ids"
    // Tags: Drawables
    const val TAG_ANIMATED_SELECTOR: String = "animated-selector"
    const val TAG_ANIMATED_VECTOR: String = "animated-vector"
    const val TAG_BITMAP: String = "bitmap"
    const val TAG_CLIP_PATH: String = "clip-path"
    const val TAG_GRADIENT: String = "gradient"
    const val TAG_INSET: String = "inset"
    const val TAG_LAYER_LIST: String = "layer-list"
    const val TAG_NINE_PATCH: String = "nine-patch"
    const val TAG_PATH: String = "path"
    const val TAG_RIPPLE: String = "ripple"
    const val TAG_ROTATE: String = "rotate"
    const val TAG_SHAPE: String = "shape"
    const val TAG_SELECTOR: String = "selector"
    const val TAG_TRANSITION: String = "transition"
    const val TAG_VECTOR: String = "vector"
    const val TAG_LEVEL_LIST: String = "level-list"
    // Tags: Data-Binding
    const val TAG_LAYOUT: String = "layout"
    const val TAG_DATA: String = "data"
    const val TAG_VARIABLE: String = "variable"
    const val TAG_IMPORT: String = "import"
    // Attributes: Manifest
    const val ATTR_EXPORTED: String = "exported"
    const val ATTR_PERMISSION: String = "permission"
    const val ATTR_PROCESS: String = "process"
    const val ATTR_MIN_SDK_VERSION: String = "minSdkVersion"
    const val ATTR_TARGET_SDK_VERSION: String = "targetSdkVersion"
    const val ATTR_ICON: String = "icon"
    const val ATTR_ROUND_ICON: String = "roundIcon"
    const val ATTR_PACKAGE: String = "package"
    const val ATTR_CORE_APP: String = "coreApp"
    const val ATTR_THEME: String = "theme"
    const val ATTR_SCHEME: String = "scheme"
    const val ATTR_MIME_TYPE: String = "mimeType"
    const val ATTR_HOST: String = "host"
    const val ATTR_PORT: String = "port"
    const val ATTR_PATH: String = "path"
    const val ATTR_PATH_PREFIX: String = "pathPrefix"
    const val ATTR_PATH_PATTERN: String = "pathPattern"
    const val ATTR_ALLOW_BACKUP: String = "allowBackup"
    const val ATTR_DEBUGGABLE: String = "debuggable"
    const val ATTR_READ_PERMISSION: String = "readPermission"
    const val ATTR_WRITE_PERMISSION: String = "writePermission"
    const val ATTR_VERSION_CODE: String = "versionCode"
    const val ATTR_VERSION_NAME: String = "versionName"
    const val ATTR_FULL_BACKUP_CONTENT: String = "fullBackupContent"
    const val ATTR_TEST_ONLY: String = "testOnly"
    const val ATTR_HAS_CODE: String = "hasCode"
    const val ATTR_AUTHORITIES: String = "authorities"
    const val ATTR_MULTIPROCESS: String = "multiprocess"
    const val ATTR_SPLIT: String = "split"
    const val ATTR_SHARED_USER_ID: String = "sharedUserId"
    const val ATTR_FUNCTIONAL_TEST: String = "functionalTest"
    const val ATTR_HANDLE_PROFILING: String = "handleProfiling"
    const val ATTR_TARGET_PACKAGE: String = "targetPackage"
    const val ATTR_EXTRACT_NATIVE_LIBS: String = "extractNativeLibs"
    const val ATTR_USE_EMBEDDED_DEX: String = "useEmbeddedDex"
    const val ATTR_SPLIT_NAME: String = "splitName"
    const val ATTR_FEATURE_SPLIT: String = "featureSplit"
    const val ATTR_TARGET_SANDBOX_VERSION: String = "targetSandboxVersion"
    const val ATTR_REQUIRED: String = "required"
    const val ATTR_ON_DEMAND: String = "onDemand"
    const val MANIFEST_ATTR_TITLE: String = "title"
    const val ATTR_TARGET_ACTIVITY: String = "targetActivity"
    // Attributes: Resources
    const val ATTR_ATTR: String = "attr"
    const val ATTR_NAME: String = "name"
    const val ATTR_FRAGMENT: String = "fragment"
    const val ATTR_TYPE: String = "type"
    const val ATTR_PARENT: String = "parent"
    const val ATTR_TRANSLATABLE: String = "translatable"
    const val ATTR_COLOR: String = "color"
    const val ATTR_DRAWABLE: String = "drawable"
    const val ATTR_VALUE: String = "value"
    const val ATTR_QUANTITY: String = "quantity"
    const val ATTR_FORMAT: String = "format"
    const val ATTR_PREPROCESSING: String = "preprocessing"
    // Attributes: Data Binding
    const val ATTR_ALIAS: String = "alias"
    // Attributes: View Binding
    const val ATTR_VIEW_BINDING_IGNORE: String = "viewBindingIgnore"
    // Attributes: Layout
    const val ATTR_LAYOUT_RESOURCE_PREFIX: String = "layout_"
    const val ATTR_CLASS: String = "class"
    const val ATTR_STYLE: String = "style"
    const val ATTR_CONTEXT: String = "context"
    const val ATTR_ID: String = "id"
    const val ATTR_AUTOFILL_HINTS: String = "autofillHints"
    const val ATTR_TEXT: String = "text"
    const val ATTR_TEXT_SIZE: String = "textSize"
    const val ATTR_ALPHA: String = "alpha"
    const val ATTR_LABEL: String = "label"
    const val ATTR_HINT: String = "hint"
    const val ATTR_PROMPT: String = "prompt"
    const val ATTR_ON_CLICK: String = "onClick"
    const val ATTR_INPUT_TYPE: String = "inputType"
    const val ATTR_INPUT_METHOD: String = "inputMethod"
    const val ATTR_LAYOUT_GRAVITY: String = "layout_gravity"
    const val ATTR_LAYOUT_WIDTH: String = "layout_width"
    const val ATTR_LAYOUT_HEIGHT: String = "layout_height"
    const val ATTR_LAYOUT_WEIGHT: String = "layout_weight"
    const val ATTR_PADDING: String = "padding"
    const val ATTR_PADDING_BOTTOM: String = "paddingBottom"
    const val ATTR_PADDING_TOP: String = "paddingTop"
    const val ATTR_PADDING_RIGHT: String = "paddingRight"
    const val ATTR_PADDING_LEFT: String = "paddingLeft"
    const val ATTR_PADDING_START: String = "paddingStart"
    const val ATTR_PADDING_END: String = "paddingEnd"
    const val ATTR_FOREGROUND: String = "foreground"
    const val ATTR_BACKGROUND: String = "background"
    const val ATTR_ORIENTATION: String = "orientation"
    const val ATTR_BARRIER_DIRECTION: String = "barrierDirection"
    const val ATTR_BARRIER_ALLOWS_GONE_WIDGETS: String = "barrierAllowsGoneWidgets"
    const val ATTR_LAYOUT_OPTIMIZATION_LEVEL: String = "layout_optimizationLevel"
    const val ATTR_TRANSITION: String = "transition"
    const val ATTR_TRANSITION_SHOW_PATHS: String = "showPaths"
    const val ATTR_TRANSITION_STATE: String = "transitionState"
    const val ATTR_TRANSITION_POSITION: String = "transitionPosition"
    const val ATTR_LAYOUT: String = "layout"
    const val ATTR_ROW_COUNT: String = "rowCount"
    const val ATTR_COLUMN_COUNT: String = "columnCount"
    const val ATTR_LABEL_FOR: String = "labelFor"
    const val ATTR_BASELINE_ALIGNED: String = "baselineAligned"
    const val ATTR_CONTENT_DESCRIPTION: String = "contentDescription"
    const val ATTR_IME_ACTION_LABEL: String = "imeActionLabel"
    const val ATTR_PRIVATE_IME_OPTIONS: String = "privateImeOptions"
    const val VALUE_NONE: String = "none"
    const val VALUE_NO: String = "no"
    const val VALUE_NO_EXCLUDE_DESCENDANTS: String = "noExcludeDescendants"
    const val VALUE_YES: String = "yes"
    const val VALUE_YES_EXCLUDE_DESCENDANTS: String = "yesExcludeDescendants"
    const val ATTR_NUMERIC: String = "numeric"
    const val ATTR_IME_ACTION_ID: String = "imeActionId"
    const val ATTR_IME_OPTIONS: String = "imeOptions"
    const val ATTR_FREEZES_TEXT: String = "freezesText"
    const val ATTR_EDITOR_EXTRAS: String = "editorExtras"
    const val ATTR_EDITABLE: String = "editable"
    const val ATTR_DIGITS: String = "digits"
    const val ATTR_CURSOR_VISIBLE: String = "cursorVisible"
    const val ATTR_CAPITALIZE: String = "capitalize"
    const val ATTR_PHONE_NUMBER: String = "phoneNumber"
    const val ATTR_PASSWORD: String = "password"
    const val ATTR_BUFFER_TYPE: String = "bufferType"
    const val ATTR_AUTO_TEXT: String = "autoText"
    const val ATTR_ENABLED: String = "enabled"
    const val ATTR_SINGLE_LINE: String = "singleLine"
    const val ATTR_SELECT_ALL_ON_FOCUS: String = "selectAllOnFocus"
    const val ATTR_SCALE_TYPE: String = "scaleType"
    const val ATTR_VISIBILITY: String = "visibility"
    const val ATTR_TEXT_IS_SELECTABLE: String = "textIsSelectable"
    const val ATTR_IMPORTANT_FOR_AUTOFILL: String = "importantForAutofill"
    const val ATTR_IMPORTANT_FOR_ACCESSIBILITY: String = "importantForAccessibility"
    const val ATTR_ACCESSIBILITY_TRAVERSAL_BEFORE: String = "accessibilityTraversalBefore"
    const val ATTR_ACCESSIBILITY_TRAVERSAL_AFTER: String = "accessibilityTraversalAfter"
    const val ATTR_LIST_PREFERRED_ITEM_PADDING_LEFT: String = "listPreferredItemPaddingLeft"
    const val ATTR_LIST_PREFERRED_ITEM_PADDING_RIGHT: String = "listPreferredItemPaddingRight"
    const val ATTR_LIST_PREFERRED_ITEM_PADDING_START: String = "listPreferredItemPaddingStart"
    const val ATTR_LIST_PREFERRED_ITEM_PADDING_END: String = "listPreferredItemPaddingEnd"
    const val ATTR_INDEX: String = "index"
    const val ATTR_ACTION_BAR_NAV_MODE: String = "actionBarNavMode"
    const val ATTR_MENU: String = "menu"
    const val ATTR_OPEN_DRAWER: String = "openDrawer"
    const val ATTR_SHOW_IN: String = "showIn"
    const val ATTR_PARENT_TAG: String = "parentTag"
    const val ATTR_WIDTH: String = "width"
    const val ATTR_HEIGHT: String = "height"
    const val ATTR_NAV_GRAPH: String = "navGraph"
    const val ATTR_USE_TAG: String = "useTag"
    // ConstraintLayout Flow
    const val ATTR_FLOW_WRAP_MODE: String = "flow_wrapMode"
    const val ATTR_FLOW_MAX_ELEMENTS_WRAP: String = "flow_maxElementsWrap"
    const val ATTR_FLOW_FIRST_HORIZONTAL_BIAS: String = "flow_firstHorizontalBias"
    const val ATTR_FLOW_FIRST_HORIZONTAL_STYLE: String = "flow_firstHorizontalStyle"
    const val ATTR_FLOW_HORIZONTAL_BIAS: String = "flow_horizontalBias"
    const val ATTR_FLOW_HORIZONTAL_STYLE: String = "flow_horizontalStyle"
    const val ATTR_FLOW_HORIZONTAL_ALIGN: String = "flow_horizontalAlign"
    const val ATTR_FLOW_HORIZONTAL_GAP: String = "flow_horizontalGap"
    const val ATTR_FLOW_LAST_HORIZONTAL_BIAS: String = "flow_lastHorizontalBias"
    const val ATTR_FLOW_LAST_HORIZONTAL_STYLE: String = "flow_lastHorizontalStyle"
    const val ATTR_FLOW_FIRST_VERTICAL_BIAS: String = "flow_firstVerticalBias"
    const val ATTR_FLOW_FIRST_VERTICAL_STYLE: String = "flow_firstVerticalStyle"
    const val ATTR_FLOW_VERTICAL_BIAS: String = "flow_verticalBias"
    const val ATTR_FLOW_VERTICAL_STYLE: String = "flow_verticalStyle"
    const val ATTR_FLOW_VERTICAL_ALIGN: String = "flow_verticalAlign"
    const val ATTR_FLOW_VERTICAL_GAP: String = "flow_verticalGap"
    const val ATTR_FLOW_LAST_VERTICAL_BIAS: String = "flow_lastVerticalBias"
    const val ATTR_FLOW_LAST_VERTICAL_STYLE: String = "flow_lastVerticalStyle"
    // Attributes: Drawable
    const val ATTR_VIEWPORT_HEIGHT: String = "viewportHeight"
    const val ATTR_VIEWPORT_WIDTH: String = "viewportWidth"
    const val ATTR_PATH_DATA: String = "pathData"
    const val ATTR_FILL_COLOR: String = "fillColor"
    // Attributes: Gradients
    const val ATTR_END_X: String = "endX"
    const val ATTR_END_Y: String = "endY"
    const val ATTR_START_X: String = "startX"
    const val ATTR_START_Y: String = "startY"
    const val ATTR_CENTER_X: String = "centerX"
    const val ATTR_CENTER_Y: String = "centerY"
    const val ATTR_GRADIENT_RADIUS: String = "gradientRadius"
    const val ATTR_STOP_COLOR: String = "color"
    const val ATTR_STOP_OFFSET: String = "offset"
    // Attributes: Navigation
    const val ATTR_GRAPH: String = "graph"
    const val ATTR_URI: String = "uri"
    const val ATTR_AUTO_VERIFY: String = "autoVerify"
    const val ATTR_DEFAULT_NAV_HOST: String = "defaultNavHost"
    const val ATTR_START_DESTINATION: String = "startDestination"
    const val ATTR_NULLABLE: String = "nullable"
    const val ATTR_ARG_TYPE: String = "argType"
    // android.view.View
    const val ATTR_NEXT_CLUSTER_FORWARD: String = "nextClusterForward"
    const val ATTR_NEXT_FOCUS_DOWN: String = "nextFocusDown"
    const val ATTR_NEXT_FOCUS_FORWARD: String = "nextFocusForward"
    const val ATTR_NEXT_FOCUS_LEFT: String = "nextFocusLeft"
    const val ATTR_NEXT_FOCUS_RIGHT: String = "nextFocusRight"
    const val ATTR_NEXT_FOCUS_UP: String = "nextFocusUp"
    const val ATTR_SCROLLBAR_THUMB_HORIZONTAL: String = "scrollbarThumbHorizontal"
    const val ATTR_SCROLLBAR_THUMB_VERTICAL: String = "scrollbarThumbVertical"
    const val ATTR_SCROLLBAR_TRACK_HORIZONTAL: String = "scrollbarTrackHorizontal"
    const val ATTR_SCROLLBAR_TRACK_VERTICAL: String = "scrollbarTrackVertical"
    // android.view.ViewGroup
    const val ATTR_LAYOUT_MARGIN_HORIZONTAL: String = "layout_marginHorizontal"
    const val ATTR_LAYOUT_MARGIN_VERTICAL: String = "layout_marginVertical"
    const val ATTR_PADDING_HORIZONTAL: String = "layout_paddingHorizontal"
    const val ATTR_PADDING_VERTICAL: String = "layout_paddingVertical"
    // AutoCompleteTextView
    const val ATTR_DROP_DOWN_ANCHOR: String = "dropDownAnchor"
    // ProgressBar
    const val ATTR_INTERPOLATOR: String = "interpolator"
    // AppCompatSeekBar
    const val ATTR_TICK_MARK: String = "tickMark"
    // AbsSeekBar
    const val ATTR_TICK_MARK_TINT: String = "tickMarkTint"
    // Toolbar
    const val ATTR_COLLAPSE_ICON: String = "collapseIcon"
    const val ATTR_LOGO: String = "logo"
    const val ATTR_TITLE_TEXT_COLOR: String = "titleTextColor"
    const val ATTR_SUBTITLE_TEXT_COLOR: String = "subtitleTextColor"
    // ViewAnimator
    const val ATTR_IN_ANIMATION: String = "inAnimation"
    const val ATTR_OUT_ANIMATION: String = "outAnimation"
    // TabWidget
    const val ATTR_TAB_STRIP_LEFT: String = "tabStripLeft"
    const val ATTR_TAB_STRIP_RIGHT: String = "tabStripRight"
    // DatePicker
    const val ATTR_CALENDAR_TEXT_COLOR: String = "calendarTextColor"
    const val ATTR_DAY_OF_WEEK_BACKGROUND: String = "dayOfWeekBackground"
    const val ATTR_YEAR_LIST_SELECTOR_COLOR: String = "yearListSelectorColor"
    const val ATTR_HEADER_BACKGROUND: String = "headerBackground"
    // TimePicker
    const val ATTR_AM_PM_BACKGROUND_COLOR: String = "amPmBackgroundColor"
    const val ATTR_AM_PM_TEXT_COLOR: String = "amPmTextColor"
    const val ATTR_NUMBERS_INNER_TEXT_COLOR: String = "numbersInnerTextColor"
    const val ATTR_NUMBERS_SELECTOR_COLOR: String = "numbersSelectorColor"
    const val ATTR_NUMBERS_TEXT_COLOR: String = "numbersTextColor"
    const val ATTR_NUMBERS_BACKGROUND_COLOR: String = "numbersBackgroundColor"
    // RelativeLayout
    const val ATTR_IGNORE_GRAVITY: String = "ignoreGravity"
    // AnalogClock
    const val ATTR_DIAL: String = "dial"
    const val ATTR_HAND_HOUR: String = "hand_hour"
    const val ATTR_HAND_MINUTE: String = "hand_minute"
    // CalendarView
    const val ATTR_SELECTED_DATE_VERTICAL_BAR: String = "selectedDateVerticalBar"
    // TextView attributes
    const val ATTR_TEXT_APPEARANCE: String = "textAppearance"
    const val ATTR_FONT_FAMILY: String = "fontFamily"
    const val ATTR_TYPEFACE: String = "typeface"
    const val ATTR_LINE_SPACING_EXTRA: String = "lineSpacingExtra"
    const val ATTR_TEXT_STYLE: String = "textStyle"
    const val ATTR_TEXT_ALIGNMENT: String = "textAlignment"
    const val ATTR_TEXT_COLOR: String = "textColor"
    const val ATTR_TEXT_COLOR_HINT: String = "textColorHint"
    const val ATTR_TEXT_COLOR_LINK: String = "textColorLink"
    const val ATTR_TEXT_ALL_CAPS: String = "textAllCaps"
    const val ATTR_SHADOW_COLOR: String = "shadowColor"
    const val ATTR_TEXT_COLOR_HIGHLIGHT: String = "textColorHighlight"
    const val ATTR_AUTO_SIZE_PRESET_SIZES: String = "autoSizePresetSizes"
    // Tools attributes for AdapterView inheritors
    const val ATTR_LISTFOOTER: String = "listfooter"
    const val ATTR_LISTHEADER: String = "listheader"
    const val ATTR_LISTITEM: String = "listitem"
    const val ATTR_ITEM_COUNT: String = "itemCount"
    // Tools attributes for scrolling
    const val ATTR_SCROLLX: String = "scrollX"
    const val ATTR_SCROLLY: String = "scrollY"
    // Tools attribute for using a different view at design time
    const val ATTR_USE_HANDLER: String = "useHandler"
    // AbsoluteLayout layout params
    const val ATTR_LAYOUT_Y: String = "layout_y"
    const val ATTR_LAYOUT_X: String = "layout_x"
    // GridLayout layout params
    const val ATTR_LAYOUT_ROW: String = "layout_row"
    const val ATTR_LAYOUT_ROW_SPAN: String = "layout_rowSpan"
    const val ATTR_LAYOUT_COLUMN: String = "layout_column"
    const val ATTR_LAYOUT_COLUMN_SPAN: String = "layout_columnSpan"
    // ProgressBar/RatingBar attributes
    const val ATTR_MAXIMUM: String = "max"
    const val ATTR_PROGRESS: String = "progress"
    const val ATTR_PROGRESS_DRAWABLE: String = "progressDrawable"
    const val ATTR_PROGRESS_TINT: String = "progressTint"
    const val ATTR_PROGRESS_BACKGROUND_TINT: String = "progressBackgroundTint"
    const val ATTR_SECONDARY_PROGRESS_TINT: String = "secondaryProgressTint"
    const val ATTR_INDETERMINATE: String = "indeterminate"
    const val ATTR_INDETERMINATE_DRAWABLE: String = "indeterminateDrawable"
    const val ATTR_INDETERMINATE_TINT: String = "indeterminateTint"
    const val ATTR_RATING: String = "rating"
    const val ATTR_NUM_STARS: String = "numStars"
    const val ATTR_STEP_SIZE: String = "stepSize"
    const val ATTR_IS_INDICATOR: String = "isIndicator"
    const val ATTR_THUMB: String = "thumb"
    // ImageView attributes
    const val ATTR_ADJUST_VIEW_BOUNDS: String = "adjustViewBounds"
    const val ATTR_CROP_TO_PADDING: String = "cropToPadding"
    // Font attributes of a TAG_FONT_FAMILY element
    const val ATTR_FONT_PROVIDER_AUTHORITY: String = "fontProviderAuthority"
    const val ATTR_FONT_PROVIDER_QUERY: String = "fontProviderQuery"
    const val ATTR_FONT_PROVIDER_PACKAGE: String = "fontProviderPackage"
    const val ATTR_FONT_PROVIDER_CERTS: String = "fontProviderCerts"
    // Font attributes of a TAG_FONT element
    const val ATTR_FONT_STYLE: String = "fontStyle"
    const val ATTR_FONT_WEIGHT: String = "fontWeight"
    const val ATTR_FONT: String = "font"
    // ConstraintLayout layout params
    const val ATTR_LAYOUT_EDITOR_ABSOLUTE_X: String = "layout_editor_absoluteX"
    const val ATTR_LAYOUT_EDITOR_ABSOLUTE_Y: String = "layout_editor_absoluteY"
    const val ATTR_LAYOUT_LEFT_CREATOR: String = "layout_constraintLeft_creator"
    const val ATTR_LAYOUT_RIGHT_CREATOR: String = "layout_constraintRight_creator"
    const val ATTR_LAYOUT_TOP_CREATOR: String = "layout_constraintTop_creator"
    const val ATTR_LAYOUT_BOTTOM_CREATOR: String = "layout_constraintBottom_creator"
    const val ATTR_LAYOUT_BASELINE_CREATOR: String = "layout_constraintBaseline_creator"
    const val ATTR_LAYOUT_CENTER_CREATOR: String = "layout_constraintCenter_creator"
    const val ATTR_LAYOUT_CENTER_X_CREATOR: String = "layout_constraintCenterX_creator"
    const val ATTR_LAYOUT_CENTER_Y_CREATOR: String = "layout_constraintCenterY_creator"
    const val ATTR_LAYOUT_LEFT_TO_LEFT_OF: String = "layout_constraintLeft_toLeftOf"
    const val ATTR_LAYOUT_LEFT_TO_RIGHT_OF: String = "layout_constraintLeft_toRightOf"
    const val ATTR_LAYOUT_RIGHT_TO_LEFT_OF: String = "layout_constraintRight_toLeftOf"
    const val ATTR_LAYOUT_RIGHT_TO_RIGHT_OF: String = "layout_constraintRight_toRightOf"
    const val ATTR_LAYOUT_TOP_TO_TOP_OF: String = "layout_constraintTop_toTopOf"
    const val ATTR_LAYOUT_TOP_TO_BOTTOM_OF: String = "layout_constraintTop_toBottomOf"
    const val ATTR_LAYOUT_BOTTOM_TO_TOP_OF: String = "layout_constraintBottom_toTopOf"
    const val ATTR_LAYOUT_BOTTOM_TO_BOTTOM_OF: String = "layout_constraintBottom_toBottomOf"
    const val ATTR_LAYOUT_BASELINE_TO_BASELINE_OF: String = "layout_constraintBaseline_toBaselineOf"
    const val ATTR_LAYOUT_START_TO_END_OF: String = "layout_constraintStart_toEndOf"
    const val ATTR_LAYOUT_START_TO_START_OF: String = "layout_constraintStart_toStartOf"
    const val ATTR_LAYOUT_END_TO_START_OF: String = "layout_constraintEnd_toStartOf"
    const val ATTR_LAYOUT_END_TO_END_OF: String = "layout_constraintEnd_toEndOf"
    const val ATTR_LAYOUT_GONE_MARGIN_LEFT: String = "layout_goneMarginLeft"
    const val ATTR_LAYOUT_GONE_MARGIN_TOP: String = "layout_goneMarginTop"
    const val ATTR_LAYOUT_GONE_MARGIN_RIGHT: String = "layout_goneMarginRight"
    const val ATTR_LAYOUT_GONE_MARGIN_BOTTOM: String = "layout_goneMarginBottom"
    const val ATTR_LAYOUT_GONE_MARGIN_START: String = "layout_goneMarginStart"
    const val ATTR_LAYOUT_GONE_MARGIN_END: String = "layout_goneMarginEnd"
    const val ATTR_LAYOUT_HORIZONTAL_BIAS: String = "layout_constraintHorizontal_bias"
    const val ATTR_LAYOUT_VERTICAL_BIAS: String = "layout_constraintVertical_bias"
    const val ATTR_LAYOUT_WIDTH_DEFAULT: String = "layout_constraintWidth_default"
    const val ATTR_LAYOUT_HEIGHT_DEFAULT: String = "layout_constraintHeight_default"
    const val ATTR_LAYOUT_WIDTH_MIN: String = "layout_constraintWidth_min"
    const val ATTR_LAYOUT_WIDTH_MAX: String = "layout_constraintWidth_max"
    const val ATTR_LAYOUT_WIDTH_PERCENT: String = "layout_constraintWidth_percent"
    const val ATTR_LAYOUT_HEIGHT_MIN: String = "layout_constraintHeight_min"
    const val ATTR_LAYOUT_HEIGHT_MAX: String = "layout_constraintHeight_max"
    const val ATTR_LAYOUT_HEIGHT_PERCENT: String = "layout_constraintHeight_percent"
    const val ATTR_LAYOUT_DIMENSION_RATIO: String = "layout_constraintDimensionRatio"
    const val ATTR_LAYOUT_VERTICAL_CHAIN_STYLE: String = "layout_constraintVertical_chainStyle"
    const val ATTR_LAYOUT_HORIZONTAL_CHAIN_STYLE: String = "layout_constraintHorizontal_chainStyle"
    const val ATTR_LAYOUT_VERTICAL_WEIGHT: String = "layout_constraintVertical_weight"
    const val ATTR_LAYOUT_HORIZONTAL_WEIGHT: String = "layout_constraintHorizontal_weight"
    const val ATTR_LAYOUT_CHAIN_SPREAD: String = "spread"
    const val ATTR_LAYOUT_CHAIN_SPREAD_INSIDE: String = "spread_inside"
    const val ATTR_LAYOUT_CHAIN_PACKED: String = "packed"
    const val ATTR_LAYOUT_CHAIN_HELPER_USE_RTL: String = "chainUseRtl"
    const val ATTR_LAYOUT_CONSTRAINTSET: String = "constraintSet"
    const val ATTR_LAYOUT_CONSTRAINT_CIRCLE: String = "layout_constraintCircle"
    const val ATTR_LAYOUT_CONSTRAINT_CIRCLE_ANGLE: String = "layout_constraintCircleAngle"
    const val ATTR_LAYOUT_CONSTRAINT_CIRCLE_RADIUS: String = "layout_constraintCircleRadius"
    const val ATTR_LAYOUT_CONSTRAINED_HEIGHT: String = "layout_constrainedHeight"
    const val ATTR_LAYOUT_CONSTRAINED_WIDTH: String = "layout_constrainedWidth"
    const val ATTR_CONSTRAINT_SET_START: String = "constraintSetStart"
    const val ATTR_CONSTRAINT_SET_END: String = "constraintSetEnd"
    const val ATTR_DERIVE_CONSTRAINTS_FROM: String = "deriveConstraintsFrom"
    const val ATTR_GUIDELINE_ORIENTATION_HORIZONTAL: String = "horizontal"
    const val ATTR_GUIDELINE_ORIENTATION_VERTICAL: String = "vertical"
    const val LAYOUT_CONSTRAINT_GUIDE_BEGIN: String = "layout_constraintGuide_begin"
    const val LAYOUT_CONSTRAINT_GUIDE_END: String = "layout_constraintGuide_end"
    const val LAYOUT_CONSTRAINT_GUIDE_PERCENT: String = "layout_constraintGuide_percent"
    const val LAYOUT_CONSTRAINT_DEPRECATED_GUIDE_PERCENT: String = "layout_constraintGuide_Percent"
    const val ATTR_LOCKED: String = "locked"
    const val ATTR_CONSTRAINT_LAYOUT_DESCRIPTION: String = "layoutDescription"
    // MotionLayout
    const val ATTR_MOTION_TARGET: String = "motionTarget"
    const val ATTR_MOTION_WAVE_OFFSET: String = "waveOffset"
    const val ATTR_MOTION_TARGET_ID: String = "targetId"
    const val ATTR_MOTION_TOUCH_ANCHOR_ID: String = "touchAnchorId"
    const val ATTR_MOTION_TOUCH_REGION_ID: String = "touchRegionId"
    // AbsListView
    const val ATTR_LIST_SELECTOR: String = "listSelector"
    // ListView
    const val ATTR_OVER_SCROLL_FOOTER: String = "overScrollFooter"
    const val ATTR_OVER_SCROLL_HEADER: String = "overScrollHeader"
    const val ATTR_CHILD_DIVIDER: String = "childDivider"
    // SearchView
    const val ATTR_QUERY_BACKGROUND: String = "queryBackground"
    const val ATTR_SUBMIT_BACKGROUND: String = "submitBackground"
    // SimpleExoPlayerView
    const val ATTR_RESIZE_MODE: String = "resize_mode"
    const val ATTR_FAST_FORWARD_INCREMENT: String = "fastforward_increment"
    const val ATTR_REWIND_INCREMENT: String = "rewind_increment"
    // FlexboxLayout params
    const val ATTR_FLEX_DIRECTION: String = "flexDirection"
    const val ATTR_FLEX_WRAP: String = "flexWrap"
    const val ATTR_JUSTIFY_CONTENT: String = "justifyContent"
    const val ATTR_ALIGN_ITEMS: String = "alignItems"
    const val ATTR_ALIGN_CONTENT: String = "alignContent"
    // FlexboxLayout layout params
    const val ATTR_LAYOUT_ORDER: String = "layout_order"
    const val ATTR_LAYOUT_FLEX_GROW: String = "layout_flexGrow"
    const val ATTR_LAYOUT_FLEX_SHRINK: String = "layout_flexShrink"
    const val ATTR_LAYOUT_ALIGN_SELF: String = "layout_alignSelf"
    const val ATTR_LAYOUT_FLEX_BASIS_PERCENT: String = "layout_flexBasisPercent"
    const val ATTR_LAYOUT_MIN_WIDTH: String = "layout_minWidth"
    const val ATTR_LAYOUT_MIN_HEIGHT: String = "layout_minHeight"
    const val ATTR_LAYOUT_MAX_WIDTH: String = "layout_maxWidth"
    const val ATTR_LAYOUT_MAX_HEIGHT: String = "layout_maxHeight"
    const val ATTR_LAYOUT_WRAP_BEFORE: String = "layout_wrapBefore"
    // TableRow
    const val ATTR_LAYOUT_SPAN: String = "layout_span"
    // RelativeLayout layout params:
    const val ATTR_LAYOUT_ALIGN_LEFT: String = "layout_alignLeft"
    const val ATTR_LAYOUT_ALIGN_RIGHT: String = "layout_alignRight"
    const val ATTR_LAYOUT_ALIGN_START: String = "layout_alignStart"
    const val ATTR_LAYOUT_ALIGN_END: String = "layout_alignEnd"
    const val ATTR_LAYOUT_ALIGN_TOP: String = "layout_alignTop"
    const val ATTR_LAYOUT_ALIGN_BOTTOM: String = "layout_alignBottom"
    const val ATTR_LAYOUT_ALIGN_PARENT_LEFT: String = "layout_alignParentLeft"
    const val ATTR_LAYOUT_ALIGN_PARENT_RIGHT: String = "layout_alignParentRight"
    const val ATTR_LAYOUT_ALIGN_PARENT_START: String = "layout_alignParentStart"
    const val ATTR_LAYOUT_ALIGN_PARENT_END: String = "layout_alignParentEnd"
    const val ATTR_LAYOUT_ALIGN_PARENT_TOP: String = "layout_alignParentTop"
    const val ATTR_LAYOUT_ALIGN_PARENT_BOTTOM: String = "layout_alignParentBottom"
    const val ATTR_LAYOUT_ALIGN_WITH_PARENT_MISSING: String = "layout_alignWithParentIfMissing"
    const val ATTR_LAYOUT_ALIGN_BASELINE: String = "layout_alignBaseline"
    const val ATTR_LAYOUT_CENTER_IN_PARENT: String = "layout_centerInParent"
    const val ATTR_LAYOUT_CENTER_VERTICAL: String = "layout_centerVertical"
    const val ATTR_LAYOUT_CENTER_HORIZONTAL: String = "layout_centerHorizontal"
    const val ATTR_LAYOUT_TO_RIGHT_OF: String = "layout_toRightOf"
    const val ATTR_LAYOUT_TO_LEFT_OF: String = "layout_toLeftOf"
    const val ATTR_LAYOUT_TO_START_OF: String = "layout_toStartOf"
    const val ATTR_LAYOUT_TO_END_OF: String = "layout_toEndOf"
    const val ATTR_LAYOUT_BELOW: String = "layout_below"
    const val ATTR_LAYOUT_ABOVE: String = "layout_above"
    // Spinner
    const val ATTR_DROPDOWN_SELECTOR: String = "dropDownSelector"
    const val ATTR_POPUP_BACKGROUND: String = "popupBackground"
    const val ATTR_SPINNER_MODE: String = "spinnerMode"
    // Margins
    const val ATTR_LAYOUT_MARGIN: String = "layout_margin"
    const val ATTR_LAYOUT_MARGIN_LEFT: String = "layout_marginLeft"
    const val ATTR_LAYOUT_MARGIN_RIGHT: String = "layout_marginRight"
    const val ATTR_LAYOUT_MARGIN_START: String = "layout_marginStart"
    const val ATTR_LAYOUT_MARGIN_END: String = "layout_marginEnd"
    const val ATTR_LAYOUT_MARGIN_TOP: String = "layout_marginTop"
    const val ATTR_LAYOUT_MARGIN_BOTTOM: String = "layout_marginBottom"
    // Attributes: Drawables
    const val ATTR_TILE_MODE: String = "tileMode"
    // Attributes: Design and support lib
    const val ATTR_LAYOUT_ANCHOR: String = "layout_anchor"
    const val ATTR_LAYOUT_ANCHOR_GRAVITY: String = "layout_anchorGravity"
    const val ATTR_LAYOUT_BEHAVIOR: String = "layout_behavior"
    const val ATTR_LAYOUT_KEYLINE: String = "layout_keyline"
    const val ATTR_BACKGROUND_TINT: String = "backgroundTint"
    const val ATTR_BACKGROUND_TINT_MODE: String = "backgroundTintMode"
    const val ATTR_DRAWABLE_TINT: String = "drawableTint"
    const val ATTR_FOREGROUND_TINT: String = "foregroundTint"
    const val ATTR_FOREGROUND_TINT_MODE: String = "foregroundTintMode"
    const val ATTR_RIPPLE_COLOR: String = "rippleColor"
    const val ATTR_TINT: String = "tint"
    const val ATTR_FAB_SIZE: String = "fabSize"
    const val ATTR_ELEVATION: String = "elevation"
    const val ATTR_FITS_SYSTEM_WINDOWS: String = "fitsSystemWindows"
    const val ATTR_EXPANDED: String = "expanded"
    const val ATTR_LAYOUT_SCROLL_FLAGS: String = "layout_scrollFlags"
    const val ATTR_LAYOUT_COLLAPSE_MODE: String = "layout_collapseMode"
    const val ATTR_COLLAPSE_PARALLAX_MULTIPLIER: String = "layout_collapseParallaxMultiplier"
    const val ATTR_SCROLLBAR_STYLE: String = "scrollbarStyle"
    const val ATTR_FILL_VIEWPORT: String = "fillViewport"
    const val ATTR_CLIP_TO_PADDING: String = "clipToPadding"
    const val ATTR_CLIP_CHILDREN: String = "clipChildren"
    const val ATTR_HEADER_LAYOUT: String = "headerLayout"
    const val ATTR_ITEM_BACKGROUND: String = "itemBackground"
    const val ATTR_ITEM_ICON_TINT: String = "itemIconTint"
    const val ATTR_ITEM_TEXT_APPEARANCE: String = "itemTextAppearance"
    const val ATTR_ITEM_TEXT_COLOR: String = "itemTextColor"
    const val ATTR_POPUP_THEME: String = "popupTheme"
    const val ATTR_MIN_HEIGHT: String = "minHeight"
    const val ATTR_MAX_HEIGHT: String = "maxHeight"
    const val ATTR_ACTION_BAR: String = "actionBar"
    const val ATTR_TOOLBAR_ID: String = "toolbarId"
    const val ATTR_CACHE_COLOR_HINT: String = "cacheColorHint"
    const val ATTR_DIVIDER: String = "divider"
    const val ATTR_DIVIDER_PADDING: String = "dividerPadding"
    const val ATTR_DIVIDER_HEIGHT: String = "dividerHeight"
    const val ATTR_FOOTER_DIVIDERS_ENABLED: String = "footerDividersEnabled"
    const val ATTR_HEADER_DIVIDERS_ENABLED: String = "headerDividersEnabled"
    const val ATTR_CARD_BACKGROUND_COLOR: String = "cardBackgroundColor"
    const val ATTR_CARD_CORNER_RADIUS: String = "cardCornerRadius"
    const val ATTR_CONTENT_PADDING: String = "contentPadding"
    const val ATTR_CARD_ELEVATION: String = "cardElevation"
    const val ATTR_CARD_PREVENT_CORNER_OVERLAP: String = "cardPreventCornerOverlap"
    const val ATTR_CARD_USE_COMPAT_PADDING: String = "cardUseCompatPadding"
    const val ATTR_ENTRIES: String = "entries"
    const val ATTR_MIN_WIDTH: String = "minWidth"
    const val ATTR_MAX_WIDTH: String = "maxWidth"
    const val ATTR_DROPDOWN_HEIGHT: String = "dropDownHeight"
    const val ATTR_DROPDOWN_WIDTH: String = "dropDownWidth"
    const val ATTR_DRAW_SELECTOR_ON_TOP: String = "drawSelectorOnTop"
    const val ATTR_SCROLLBARS: String = "scrollbars"
    const val ATTR_COMPLETION_HINT: String = "completionHint"
    const val ATTR_COMPLETION_HINT_VIEW: String = "completionHintView"
    const val ATTR_LAYOUT_MANAGER: String = "layoutManager"
    const val ATTR_SPAN_COUNT: String = "spanCount"
    const val ATTR_NAVIGATION_ICON: String = "navigationIcon"
    const val ATTR_LIFT_ON_SCROLL_TARGET_VIEW_ID: String = "liftOnScrollTargetViewId"
    const val ATTR_STATUS_BAR_FOREGROUND: String = "statusBarForeground"
    // Material BottomAppBar Attributes
    const val ATTR_FAB_ALIGNMENT_MODE: String = "fabAlignmentMode"
    const val ATTR_FAB_ANIMATION_MODE: String = "fabAnimationMode"
    const val ATTR_FAB_CRADLE_MARGIN: String = "fabCradleMargin"
    const val ATTR_FAB_CRADLE_ROUNDED_CORNER_RADIUS: String = "fabCradleRoundedCornerRadius"
    const val ATTR_FAB_CRADLE_VERTICAL_OFFSET: String = "fabCradleVerticalOffset"
    // Material Button Attributes
    const val ATTR_INSET_LEFT: String = "insetLeft"
    const val ATTR_INSET_RIGHT: String = "insetRight"
    const val ATTR_INSET_TOP: String = "insetTop"
    const val ATTR_INSET_BOTTOM: String = "insetBottom"
    const val ATTR_ICON_PADDING: String = "iconPadding"
    const val ATTR_ICON_TINT: String = "iconTint"
    const val ATTR_ICON_TINT_MODE: String = "iconTintMode"
    const val ATTR_ADDITIONAL_PADDING_START_FOR_ICON: String = "additionalPaddingStartForIcon"
    const val ATTR_ADDITIONAL_PADDING_END_FOR_ICON: String = "additionalPaddingEndForIcon"
    const val ATTR_STROKE_COLOR: String = "strokeColor"
    const val ATTR_STROKE_WIDTH: String = "strokeWidth"
    const val ATTR_CORNER_RADIUS: String = "cornerRadius"
    // Material CollapsingToolbarLayout
    const val ATTR_CONTENT_SCRIM: String = "contentScrim"
    const val ATTR_STATUS_BAR_SCRIM: String = "statusBarScrim"
    // Material FloatingActionButton Attributes
    const val ATTR_FAB_CUSTOM_SIZE: String = "fabCustomSize"
    const val ATTR_HOVERED_FOCUSED_TRANSLATION_Z: String = "hoveredFocusedTranslationZ"
    const val ATTR_PRESSED_TRANSLATION_Z: String = "pressedTranslationZ"
    const val ATTR_BORDER_WIDTH: String = "borderWidth"
    const val ATTR_COMPAT_PADDING: String = "useCompatPadding"
    const val ATTR_MAX_IMAGE_SIZE: String = "maxImageSize"
    const val ATTR_SHOW_MOTION_SPEC: String = "showMotionSpec"
    const val ATTR_HIDE_MOTION_SPEC: String = "hideMotionSpec"
    // Material NavigationView
    const val ATTR_INSET_BACKGROUND: String = "insetBackground"
    const val ATTR_INSET_FOREGROUND: String = "insetForeground"
    const val ATTR_ITEM_SHAPE_APPEARANCE: String = "itemShapeAppearance"
    const val ATTR_ITEM_SHAPE_APPEARANCE_OVERLAY: String = "itemShapeAppearanceOverlay"
    const val ATTR_ITEM_SHAPE_FILL_COLOR: String = "itemShapeFillColor"
    // Material BottomNavigationView Attributes
    const val ATTR_ITEM_HORIZONTAL_TRANSLATION_ENABLED: String = "itemHorizontalTranslationEnabled"
    const val ATTR_ITEM_RIPPLE_COLOR: String = "itemRippleColor"
    const val ATTR_LABEL_VISIBILITY_MODE: String = "labelVisibilityMode"
    // Material ChipGroup Attributes
    const val ATTR_CHIP_SPACING: String = "chipSpacing"
    const val ATTR_CHIP_SPACING_HORIZONTAL: String = "chipSpacingHorizontal"
    const val ATTR_CHIP_SPACING_VERTICAL: String = "chipSpacingVertical"
    const val ATTR_SINGLE_SELECTION: String = "singleSelection"
    const val ATTR_CHECKED_CHIP: String = "checkedChip"
    // Material Chip (ChipDrawable) Attributes
    const val ATTR_CHIP_BACKGROUND_COLOR: String = "chipBackgroundColor"
    const val ATTR_CHIP_TEXT: String = "chipText"
    const val ATTR_CHIP_ICON: String = "chipIcon"
    const val ATTR_CHIP_ICON_TINT: String = "chipIconTint"
    const val ATTR_CHIP_ICON_VISIBLE: String = "chipIconVisible"
    const val ATTR_CHIP_STROKE_COLOR: String = "chipStrokeColor"
    const val ATTR_CHIP_SURFACE_COLOR: String = "chipSurfaceColor"
    const val ATTR_CHECKED_ICON: String = "checkedIcon"
    const val ATTR_CHECKED_ICON_VISIBLE: String = "checkedIconVisible"
    const val ATTR_CLOSE_ICON: String = "closeIcon"
    const val ATTR_CLOSE_ICON_TINT: String = "closeIconTint"
    const val ATTR_CLOSE_ICON_VISIBLE: String = "closeIconVisible"
    // Material TabLayout Attributes
    const val ATTR_TAB_INDICATOR_HEIGHT: String = "tabIndicatorHeight"
    const val ATTR_TAB_BACKGROUND: String = "tabBackground"
    const val ATTR_TAB_INDICATOR: String = "tabIndicator"
    const val ATTR_TAB_INDICATOR_GRAVITY: String = "tabIndicatorGravity"
    const val ATTR_TAB_INDICATOR_ANIMATION_DURATION: String = "tabIndicatorAnimationDuration"
    const val ATTR_TAB_INDICATOR_FULL_WIDTH: String = "tabIndicatorFullWidth"
    const val ATTR_TAB_MODE: String = "tabMode"
    const val ATTR_TAB_GRAVITY: String = "tabGravity"
    const val ATTR_TAB_CONTENT_START: String = "tabContentStart"
    const val ATTR_TAB_INDICATOR_COLOR: String = "tabIndicatorColor"
    const val ATTR_TAB_SELECTED_TEXT_COLOR: String = "tabSelectedTextColor"
    const val ATTR_TAB_TEXT_APPEARANCE: String = "tabTextAppearance"
    const val ATTR_TAB_INLINE_LABEL: String = "tabInlineLabel"
    const val ATTR_TAB_MIN_WIDTH: String = "tabMinWidth"
    const val ATTR_TAB_MAX_WIDTH: String = "tabMaxWidth"
    const val ATTR_TAB_TEXT_COLOR: String = "tabTextColor"
    const val ATTR_TAB_PADDING: String = "tabPadding"
    const val ATTR_TAB_PADDING_START: String = "tabPaddingStart"
    const val ATTR_TAB_PADDING_END: String = "tabPaddingEnd"
    const val ATTR_TAB_PADDING_TOP: String = "tabPaddingTop"
    const val ATTR_TAB_PADDING_BOTTOM: String = "tabPaddingBottom"
    const val ATTR_TAB_ICON_TINT: String = "tabIconTint"
    const val ATTR_TAB_ICON_TINT_MODE: String = "tabIconTintMode"
    const val ATTR_TAB_RIPPLE_COLOR: String = "tabRippleColor"
    const val ATTR_TAB_UNBOUNDED_RIPPLE: String = "tabUnboundedRipple"
    const val ATTR_LAYOUT_SCROLL_INTERPOLATOR: String = "layout_scrollInterpolator"
    // Material TextInputLayout Attributes
    const val ATTR_END_ICON_TINT: String = "endIconTint"
    const val ATTR_ERROR_TEXT_COLOR: String = "errorTextColor"
    const val ATTR_HELPER_TEXT_TEXT_COLOR: String = "helperTextTextColor"
    const val ATTR_HINT_ENABLED: String = "hintEnabled"
    const val ATTR_HINT_ANIMATION_ENABLED: String = "hintAnimationEnabled"
    const val ATTR_HINT_TEXT_APPEARANCE: String = "hintTextAppearance"
    const val ATTR_HINT_TEXT_COLOR: String = "hintTextColor"
    const val ATTR_HELPER_TEXT: String = "helperText"
    const val ATTR_HELPER_TEXT_ENABLED: String = "helperTextEnabled"
    const val ATTR_HELPER_TEXT_TEXT_APPEARANCE: String = "helperTextTextAppearance"
    const val ATTR_SHAPE_APPEARANCE: String = "shapeAppearance"
    const val ATTR_SHAPE_APPEARANCE_OVERLAY: String = "shapeAppearanceOverlay"
    const val ATTR_START_ICON_TINT: String = "startIconTint"
    const val ATTR_ERROR_ENABLED: String = "errorEnabled"
    const val ATTR_ERROR_TEXT_APPEARANCE: String = "errorTextAppearance"
    const val ATTR_COUNTER_ENABLED: String = "counterEnabled"
    const val ATTR_COUNTER_MAX_LENGTH: String = "counterMaxLength"
    const val ATTR_COUNTER_TEXT_APPEARANCE: String = "counterTextAppearance"
    const val ATTR_COUNTER_OVERFLOW_TEXT_APPEARANCE: String = "counterOverflowTextAppearance"
    const val ATTR_PASSWORD_TOGGLE_ENABLED: String = "passwordToggleEnabled"
    const val ATTR_PASSWORD_TOGGLE_DRAWABLE: String = "passwordToggleDrawable"
    const val ATTR_PASSWORD_TOGGLE_CONTENT_DESCRIPTION: String = "passwordToggleContentDescription"
    const val ATTR_PASSWORD_TOGGLE_TINT: String = "passwordToggleTint"
    const val ATTR_PASSWORD_TOGGLE_TINT_MODE: String = "passwordToggleTintMode"
    const val ATTR_BOX_BACKGROUND_MODE: String = "boxBackgroundMode"
    const val ATTR_BOX_COLLAPSED_PADDING_TOP: String = "boxCollapsedPaddingTop"
    const val ATTR_BOX_STROKE_COLOR: String = "boxStrokeColor"
    const val ATTR_BOX_BACKGROUND_COLOR: String = "boxBackgroundColor"
    const val ATTR_BOX_STROKE_WIDTH: String = "boxStrokeWidth"
    // Values: Manifest
    const val VALUE_SPLIT_ACTION_BAR_WHEN_NARROW: String = "splitActionBarWhenNarrow" // NON-NLS-$1
    // Values: Layouts
    const val VALUE_FILL_PARENT: String = "fill_parent"
    const val VALUE_MATCH_PARENT: String = "match_parent"
    const val VALUE_MATCH_CONSTRAINT: String = "0dp"
    const val VALUE_VERTICAL: String = "vertical"
    const val VALUE_TRUE: String = "true"
    const val VALUE_EDITABLE: String = "editable"
    const val VALUE_AUTO_FIT: String = "auto_fit"
    const val VALUE_SELECTABLE_ITEM_BACKGROUND: String = "?android:attr/selectableItemBackground"
    // Values: Resources
    const val VALUE_ID: String = "id"
    // Values: Drawables
    const val VALUE_DISABLED: String = "disabled"
    const val VALUE_CLAMP: String = "clamp"
    // Value delimiters: Manifest
    const val VALUE_DELIMITER_PIPE: String = "|"
    // Menus
    const val ATTR_CHECKABLE: String = "checkable"
    const val ATTR_CHECKABLE_BEHAVIOR: String = "checkableBehavior"
    const val ATTR_ORDER_IN_CATEGORY: String = "orderInCategory"
    const val ATTR_SHOW_AS_ACTION: String = "showAsAction"
    const val ATTR_TITLE: String = "title"
    const val ATTR_VISIBLE: String = "visible"
    const val VALUE_IF_ROOM: String = "ifRoom"
    const val VALUE_ALWAYS: String = "always"
    // Units
    const val UNIT_DP: String = "dp"
    const val UNIT_DIP: String = "dip"
    const val UNIT_SP: String = "sp"
    const val UNIT_PX: String = "px"
    const val UNIT_IN: String = "in"
    const val UNIT_MM: String = "mm"
    const val UNIT_PT: String = "pt"
    // Filenames and folder names
    const val ANDROID_MANIFEST_XML: String = "AndroidManifest.xml"
    const val OLD_PROGUARD_FILE: String = "proguard.cfg"
    @JvmField
    val CLASS_FOLDER: String = "bin" + File.separator + "classes"
    const val GEN_FOLDER: String = "gen"
    const val SRC_FOLDER: String = "src"
    const val LIBS_FOLDER: String = "libs"
    const val BIN_FOLDER: String = "bin"
    const val RES_FOLDER: String = "res"
    const val DOT_XML: String = ".xml"
    const val DOT_XSD: String = ".xsd"
    const val DOT_GIF: String = ".gif"
    const val DOT_JPG: String = ".jpg"
    const val DOT_JPEG: String = ".jpeg"
    const val DOT_WEBP: String = ".webp"
    const val DOT_PNG: String = ".png"
    const val DOT_9PNG: String = ".9.png"
    const val DOT_JAVA: String = ".java"
    const val DOT_KT: String = ".kt"
    const val DOT_KTS: String = ".kts"
    const val DOT_CLASS: String = ".class"
    const val DOT_JAR: String = ".jar"
    const val DOT_SRCJAR: String = ".srcjar"
    const val DOT_GRADLE: String = ".gradle"
    const val DOT_PROPERTIES: String = ".properties"
    const val DOT_JSON: String = ".json"
    const val DOT_PSD: String = ".psd"
    const val DOT_TTF: String = ".ttf"
    const val DOT_TTC: String = ".ttc"
    const val DOT_OTF: String = ".otf"
    /**
     * Extension of the Application package Files, i.e. "apk".
     */
    const val EXT_ANDROID_PACKAGE: String = "apk"
    /**
     * Extension of the InstantApp package Files, i.e. "iapk".
     */
    const val EXT_INSTANTAPP_PACKAGE: String = "iapk"
    /**
     * Extension for Android archive files
     */
    const val EXT_AAR: String = "aar"
    /**
     * Extension for Android atom files.
     */
    const val EXT_ATOM: String = "atom"
    /**
     * Extension of java files, i.e. "java"
     */
    const val EXT_JAVA: String = "java"
    /**
     * Extension of compiled java files, i.e. "class"
     */
    const val EXT_CLASS: String = "class"
    /**
     * Extension of xml files, i.e. "xml"
     */
    const val EXT_XML: String = "xml"
    /**
     * Extension of gradle files, i.e. "gradle"
     */
    const val EXT_GRADLE: String = "gradle"
    /**
     * Extension of Kotlin gradle files, i.e. "gradle.kts"
     */
    const val EXT_GRADLE_KTS: String = "gradle.kts"
    /**
     * Extension of jar files, i.e. "jar"
     */
    const val EXT_JAR: String = "jar"
    /**
     * Extension of ZIP files, i.e. "zip"
     */
    const val EXT_ZIP: String = "zip"
    /**
     * Extension of aidl files, i.e. "aidl"
     */
    const val EXT_AIDL: String = "aidl"
    /**
     * Extension of Renderscript files, i.e. "rs"
     */
    const val EXT_RS: String = "rs"
    /**
     * Extension of Renderscript files, i.e. "rsh"
     */
    const val EXT_RSH: String = "rsh"
    /**
     * Extension of FilterScript files, i.e. "fs"
     */
    const val EXT_FS: String = "fs"
    /**
     * Extension of Renderscript bitcode files, i.e. "bc"
     */
    const val EXT_BC: String = "bc"
    /**
     * Extension of dependency files, i.e. "d"
     */
    const val EXT_DEP: String = "d"
    /**
     * Extension of native libraries, i.e. "so"
     */
    const val EXT_NATIVE_LIB: String = "so"
    /**
     * Extension of dex files, i.e. "dex"
     */
    const val EXT_DEX: String = "dex"
    /**
     * Extension for temporary resource files, ie "ap_
     */
    const val EXT_RES: String = "ap_"
    /**
     * Extension for pre-processable images. Right now pngs
     */
    const val EXT_PNG: String = "png"
    /**
     * Extension of app bundle files, i.e. "aab"
     */
    const val EXT_APP_BUNDLE: String = "aab"
    const val EXT_HPROF: String = "hprof"
    const val EXT_GZ: String = "gz"
    const val EXT_JSON: String = "json"
    const val EXT_CSV: String = "csv"
    /**
     * Dot-Extension for BMP files, i.e. ".bmp"
     */
    const val DOT_BMP: String = ".bmp"
    /**
     * Dot-Extension for SVG files, i.e. ".svg"
     */
    const val DOT_SVG: String = ".svg"
    /**
     * Dot-Extension for template files
     */
    const val DOT_FTL: String = ".ftl"
    /**
     * Dot-Extension of text files, i.e. ".txt"
     */
    const val DOT_TXT: String = ".txt"
    /**
     * Resource base name for java files and classes
     */
    const val FN_RESOURCE_BASE: String = "R"
    /**
     * Resource java class filename, i.e. "R.java"
     */
    const val FN_RESOURCE_CLASS: String = FN_RESOURCE_BASE + DOT_JAVA
    /**
     * Resource class file filename, i.e. "R.class"
     */
    const val FN_COMPILED_RESOURCE_CLASS: String = FN_RESOURCE_BASE + DOT_CLASS
    /**
     * Resource text filename, i.e. "R.txt"
     */
    const val FN_RESOURCE_TEXT: String = FN_RESOURCE_BASE + DOT_TXT
    /**
     * Filename for public resources in AAR archives
     */
    const val FN_PUBLIC_TXT: String = "public.txt"
    /**
     * Resource static library
     */
    const val FN_RESOURCE_STATIC_LIBRARY: String = "res.apk"
    /**
     * Resource shared library
     */
    const val FN_RESOURCE_SHARED_STATIC_LIBRARY: String = "shared.apk"
    /**
     * R class jar, used for resource static library
     */
    const val FN_R_CLASS_JAR: String = "R.jar"
    /**
     * R file containing only the local resources.
     */
    const val FN_R_DEF_TXT: String = "R-def.txt"
    /**
     * Generated manifest class name
     */
    const val FN_MANIFEST_BASE: String = "Manifest"
    /**
     * Generated BuildConfig class name
     */
    const val FN_BUILD_CONFIG_BASE: String = "BuildConfig"
    /**
     * Manifest java class filename, i.e. "Manifest.java"
     */
    const val FN_MANIFEST_CLASS: String = FN_MANIFEST_BASE + DOT_JAVA
    /**
     * BuildConfig java class filename, i.e. "BuildConfig.java"
     */
    const val FN_BUILD_CONFIG: String = FN_BUILD_CONFIG_BASE + DOT_JAVA
    const val DRAWABLE_FOLDER: String = "drawable"
    const val MIPMAP_FOLDER: String = "mipmap"
    const val DRAWABLE_XHDPI: String = "drawable-xhdpi"
    const val DRAWABLE_XXHDPI: String = "drawable-xxhdpi"
    const val DRAWABLE_XXXHDPI: String = "drawable-xxxhdpi"
    const val DRAWABLE_HDPI: String = "drawable-hdpi"
    const val DRAWABLE_MDPI: String = "drawable-mdpi"
    const val DRAWABLE_LDPI: String = "drawable-ldpi"
    // Resources
    const val PREFIX_RESOURCE_REF: String = "@"
    const val PREFIX_THEME_REF: String = "?"
    const val PREFIX_BINDING_EXPR: String = "@{"
    const val PREFIX_TWOWAY_BINDING_EXPR: String = "@={"
    const val MANIFEST_PLACEHOLDER_PREFIX: String = "\${"
    const val MANIFEST_PLACEHOLDER_SUFFIX: String = "}"
    const val ANDROID_PREFIX: String = "@android:"
    const val ANDROID_THEME_PREFIX: String = "?android:"
    const val LAYOUT_RESOURCE_PREFIX: String = "@layout/"
    const val STYLE_RESOURCE_PREFIX: String = "@style/"
    const val COLOR_RESOURCE_PREFIX: String = "@color/"
    const val NEW_ID_PREFIX: String = "@+id/"
    const val ID_PREFIX: String = "@id/"
    const val DRAWABLE_PREFIX: String = "@drawable/"
    const val STRING_PREFIX: String = "@string/"
    const val DIMEN_PREFIX: String = "@dimen/"
    const val MIPMAP_PREFIX: String = "@mipmap/"
    const val FONT_PREFIX: String = "@font/"
    const val AAPT_ATTR_PREFIX: String = "@aapt:_aapt/"
    const val SAMPLE_PREFIX: String = "@sample/"
    const val NAVIGATION_PREFIX: String = "@navigation/"
    const val TOOLS_SAMPLE_PREFIX: String = "@tools:sample/"
    const val ANDROID_LAYOUT_RESOURCE_PREFIX: String = "@android:layout/"
    const val ANDROID_STYLE_RESOURCE_PREFIX: String = "@android:style/"
    const val ANDROID_COLOR_RESOURCE_PREFIX: String = "@android:color/"
    const val ANDROID_ID_PREFIX: String = "@android:id/"
    const val ANDROID_DRAWABLE_PREFIX: String = "@android:drawable/"
    const val ANDROID_STRING_PREFIX: String = "@android:string/"
    const val RESOURCE_CLZ_ID: String = "id"
    const val RESOURCE_CLZ_COLOR: String = "color"
    const val RESOURCE_CLZ_ARRAY: String = "array"
    const val RESOURCE_CLZ_ATTR: String = "attr"
    const val RESOURCE_CLZ_STYLEABLE: String = "styleable"
    const val NULL_RESOURCE: String = "@null"
    const val TRANSPARENT_COLOR: String = "@android:color/transparent"
    const val REFERENCE_STYLE: String = "style/"
    const val PREFIX_ANDROID: String = "android:"
    const val PREFIX_APP: String = "app:"
    // Resource Types
    const val DRAWABLE_TYPE: String = "drawable"
    const val MENU_TYPE: String = "menu"
    // Packages
    const val ANDROID_PKG_PREFIX: String = ANDROID_PKG + "."
    const val ANDROIDX_PKG_PREFIX: String = ANDROIDX_PKG + "."
    const val WIDGET_PKG_PREFIX: String = "android.widget."
    const val VIEW_PKG_PREFIX: String = "android.view."
    // Project properties
    const val ANDROID_LIBRARY: String = "android.library"
    const val PROGUARD_CONFIG: String = "proguard.config"
    const val ANDROID_LIBRARY_REFERENCE_FORMAT: String = "android.library.reference.%1\$d"
    const val PROJECT_PROPERTIES: String = "project.properties"
    // Java References
    const val ATTR_REF_PREFIX: String = "?attr/"
    const val R_PREFIX: String = "R."
    const val R_ID_PREFIX: String = "R.id."
    const val R_LAYOUT_RESOURCE_PREFIX: String = "R.layout."
    const val R_DRAWABLE_PREFIX: String = "R.drawable."
    const val R_STYLEABLE_PREFIX: String = "R.styleable."
    const val R_ATTR_PREFIX: String = "R.attr."
    // Attributes related to tools
    const val ATTR_IGNORE: String = "ignore"
    const val ATTR_LOCALE: String = "locale"
    // SuppressLint
    const val SUPPRESS_ALL: String = "all"
    const val SUPPRESS_LINT: String = "SuppressLint"
    const val TARGET_API: String = "TargetApi"
    const val ATTR_TARGET_API: String = "targetApi"
    const val FQCN_SUPPRESS_LINT: String = "android.annotation." + SUPPRESS_LINT
    const val FQCN_TARGET_API: String = "android.annotation." + TARGET_API
    const val KOTLIN_SUPPRESS: String = "kotlin.Suppress"
    // Class Names
    const val CONSTRUCTOR_NAME: String = "<init>"
    const val CLASS_CONSTRUCTOR: String = "<clinit>"
    // Method Names
    const val FORMAT_METHOD: String = "format"
    const val GET_STRING_METHOD: String = "getString"
    const val SET_CONTENT_VIEW_METHOD: String = "setContentView"
    const val INFLATE_METHOD: String = "inflate"
    const val ATTR_TAG: String = "tag"
    const val ATTR_NUM_COLUMNS: String = "numColumns"
    // Some common layout element names
    const val CALENDAR_VIEW: String = "CalendarView"
    const val CHRONOMETER: String = "Chronometer"
    const val TEXT_CLOCK: String = "TextClock"
    const val SPACE: String = "Space"
    const val GESTURE_OVERLAY_VIEW: String = "GestureOverlayView"
    const val QUICK_CONTACT_BADGE: String = "QuickContactBadge"
    const val ATTR_HANDLE: String = "handle"
    const val ATTR_BUTTON: String = "button"
    const val ATTR_BUTTON_TINT: String = "buttonTint"
    const val ATTR_CONTENT: String = "content"
    const val ATTR_CHECKED: String = "checked"
    const val ATTR_CHECK_MARK: String = "checkMark"
    const val ATTR_CHECK_MARK_TINT: String = "checkMarkTint"
    const val ATTR_DUPLICATE_PARENT_STATE: String = "duplicateParentState"
    const val ATTR_FOCUSABLE: String = "focusable"
    const val ATTR_CLICKABLE: String = "clickable"
    const val ATTR_TEXT_OFF: String = "textOff"
    const val ATTR_TEXT_ON: String = "textOn"
    const val ATTR_CHECKED_BUTTON: String = "checkedButton"
    const val ATTR_SWITCH_TEXT_APPEARANCE: String = "switchTextAppearance"
    const val ATTR_SWITCH_MIN_WIDTH: String = "switchMinWidth"
    const val ATTR_SWITCH_PADDING: String = "switchPadding"
    const val ATTR_THUMB_TINT: String = "thumbTint"
    const val ATTR_TRACK: String = "track"
    const val ATTR_TRACK_TINT: String = "trackTint"
    const val ATTR_SHOW_TEXT: String = "showText"
    const val ATTR_SPLIT_TRACK: String = "splitTrack"
    const val ATTR_STATE_LIST_ANIMATOR: String = "stateListAnimator"
    const val ATTR_LAYOUT_ANIMATION: String = "layoutAnimation"
    // TextView
    const val ATTR_DRAWABLE_RIGHT: String = "drawableRight"
    const val ATTR_DRAWABLE_LEFT: String = "drawableLeft"
    const val ATTR_DRAWABLE_START: String = "drawableStart"
    const val ATTR_DRAWABLE_END: String = "drawableEnd"
    const val ATTR_DRAWABLE_BOTTOM: String = "drawableBottom"
    const val ATTR_DRAWABLE_TOP: String = "drawableTop"
    const val ATTR_DRAWABLE_PADDING: String = "drawablePadding"
    const val ATTR_USE_DEFAULT_MARGINS: String = "useDefaultMargins"
    const val ATTR_MARGINS_INCLUDED_IN_ALIGNMENT: String = "marginsIncludedInAlignment"
    const val VALUE_WRAP_CONTENT: String = "wrap_content"
    const val VALUE_FALSE: String = "false"
    const val VALUE_N_DP: String = "%ddp"
    const val VALUE_ZERO_DP: String = "0dp"
    const val VALUE_ONE_DP: String = "1dp"
    const val VALUE_TOP: String = "top"
    const val VALUE_BOTTOM: String = "bottom"
    const val VALUE_CENTER_VERTICAL: String = "center_vertical"
    const val VALUE_CENTER_HORIZONTAL: String = "center_horizontal"
    const val VALUE_FILL_HORIZONTAL: String = "fill_horizontal"
    const val VALUE_FILL_VERTICAL: String = "fill_vertical"
    const val VALUE_0: String = "0"
    const val VALUE_1: String = "1"
    // Gravity values. These have the GRAVITY_ prefix in front of value because we already
    // have VALUE_CENTER_HORIZONTAL defined for layouts, and its definition conflicts
    // (centerHorizontal versus center_horizontal)
    const val GRAVITY_VALUE_: String = "center"
    const val GRAVITY_VALUE_CENTER: String = "center"
    const val GRAVITY_VALUE_LEFT: String = "left"
    const val GRAVITY_VALUE_RIGHT: String = "right"
    const val GRAVITY_VALUE_START: String = "start"
    const val GRAVITY_VALUE_END: String = "end"
    const val GRAVITY_VALUE_BOTTOM: String = "bottom"
    const val GRAVITY_VALUE_TOP: String = "top"
    const val GRAVITY_VALUE_FILL_HORIZONTAL: String = "fill_horizontal"
    const val GRAVITY_VALUE_FILL_VERTICAL: String = "fill_vertical"
    const val GRAVITY_VALUE_CENTER_HORIZONTAL: String = "center_horizontal"
    const val GRAVITY_VALUE_CENTER_VERTICAL: String = "center_vertical"
    const val GRAVITY_VALUE_CLIP_HORIZONTAL: String = "clip_horizontal"
    const val GRAVITY_VALUE_CLIP_VERTICAL: String = "clip_vertical"
    const val GRAVITY_VALUE_FILL: String = "fill"
    // Mockup
    const val ATTR_MOCKUP: String = "mockup"
    const val ATTR_MOCKUP_CROP: String = "mockup_crop"
    const val ATTR_MOCKUP_POSITION: String = "mockup_crop"
    const val ATTR_MOCKUP_OPACITY: String = "mockup_opacity"
    /**
     * Root tag in baseline files (which can be the XML output report files from lint, or a
     * subset of these
     */
    @Suppress("unused") // used from IDE
    const val TAG_ISSUES: String = "issues"
    const val TAG_ISSUE: String = "issue"
    const val TAG_LOCATION: String = "location"
    const val ATTR_MESSAGE: String = "message"
    const val ATTR_FILE: String = "file"
    const val ATTR_LINE: String = "line"
    const val ATTR_COLUMN: String = "column"
    /**
     * The top level android package as a prefix, "android.".
     */
    const val ANDROID_SUPPORT_PKG_PREFIX: String = ANDROID_PKG_PREFIX + "support."
    /**
     * Architecture component package prefix
     */
    const val ANDROID_ARCH_PKG_PREFIX: String = ANDROID_PKG_PREFIX + "arch."
    /**
     * The android.view. package prefix
     */
    const val ANDROID_VIEW_PKG: String = ANDROID_PKG_PREFIX + "view."
    /**
     * The android.widget. package prefix
     */
    const val ANDROID_WIDGET_PREFIX: String = ANDROID_PKG_PREFIX + "widget."
    /**
     * The android.webkit. package prefix
     */
    const val ANDROID_WEBKIT_PKG: String = ANDROID_PKG_PREFIX + "webkit."
    /**
     * The android.app. package prefix
     */
    const val ANDROID_APP_PKG: String = ANDROID_PKG_PREFIX + "app."
    /**
     * The android.support.v4. package prefix
     */
    const val ANDROID_SUPPORT_V4_PKG: String = ANDROID_SUPPORT_PKG_PREFIX + "v4."
    /**
     * The android.support.v7. package prefix
     */
    const val ANDROID_SUPPORT_V7_PKG: String = ANDROID_SUPPORT_PKG_PREFIX + "v7."
    /**
     * The android.support.design. package prefix
     */
    const val ANDROID_SUPPORT_DESIGN_PKG: String = ANDROID_SUPPORT_PKG_PREFIX + "design."
    /**
     * The com.google.android.material. package prefix
     */
    const val ANDROID_MATERIAL_PKG: String = "com.google.android.material."
    /**
     * The android.support.constraint. package prefix
     */
    const val CONSTRAINT_LAYOUT_PKG: String = "android.support.constraint."

    // Baselines
    /**
     * The androidx.constraintlayout. package prefix
     */
    const val ANDROIDX_CONSTRAINT_LAYOUT_PKG: String = "androidx.constraintlayout."
    /**
     * The androidx.recyclerview. package prefix
     */
    const val ANDROIDX_RECYCLER_VIEW_PKG: String = "androidx.recyclerview."
    /**
     * The androidx.cardview. package prefix
     */
    const val ANDROIDX_CARD_VIEW_PKG: String = "androidx.cardview."
    /**
     * The androidx.gridlayout. package prefix
     */
    const val ANDROIDX_GRID_LAYOUT_PKG: String = "androidx.gridlayout."
    /**
     * The androidx.leanback. package prefix
     */
    const val ANDROIDX_LEANBACK_PKG: String = "androidx.leanback."
    /**
     * The androidx.core. package prefix
     */
    const val ANDROIDX_CORE_PKG: String = "androidx.core."
    /**
     * The androidx.viewpager. package prefix
     */
    const val ANDROIDX_VIEWPAGER_PKG: String = "androidx.viewpager."
    /**
     * The androidx.appcompat. package prefix
     */
    const val ANDROIDX_APPCOMPAT_PKG: String = "androidx.appcompat."
    /**
     * The android.support.v17.leanback. package prefix
     */
    const val ANDROID_SUPPORT_LEANBACK_V17_PKG: String = ANDROID_SUPPORT_PKG_PREFIX + "v17.leanback."
    /**
     * The com.google.android.gms. package prefix
     */
    const val GOOGLE_PLAY_SERVICES_PKG: String = "com.google.android.gms."
    /**
     * The com.google.android.gms.ads. package prefix
     */
    const val GOOGLE_PLAY_SERVICES_ADS_PKG: String = GOOGLE_PLAY_SERVICES_PKG + "ads."
    /**
     * The com.google.android.gms.ads. package prefix
     */
    const val GOOGLE_PLAY_SERVICES_MAPS_PKG: String = GOOGLE_PLAY_SERVICES_PKG + "maps."
    /**
     * The LayoutParams inner-class name suffix, .LayoutParams
     */
    const val DOT_LAYOUT_PARAMS: String = ".LayoutParams"
    /**
     * The fully qualified class name of an EditText view
     */
    const val FQCN_EDIT_TEXT: String = "android.widget.EditText"
    /**
     * The fully qualified class name of a LinearLayout view
     */
    const val FQCN_LINEAR_LAYOUT: String = "android.widget.LinearLayout"
    /**
     * The fully qualified class name of a RelativeLayout view
     */
    const val FQCN_RELATIVE_LAYOUT: String = "android.widget.RelativeLayout"
    /**
     * The fully qualified class name of a GridLayout view
     */
    const val FQCN_GRID_LAYOUT: String = "android.widget.GridLayout"
    /**
     * The fully qualified class name of a FrameLayout view
     */
    const val FQCN_FRAME_LAYOUT: String = "android.widget.FrameLayout"
    /**
     * The fully qualified class name of a TableRow view
     */
    const val FQCN_TABLE_ROW: String = "android.widget.TableRow"
    /**
     * The fully qualified class name of a TableLayout view
     */
    const val FQCN_TABLE_LAYOUT: String = "android.widget.TableLayout"
    /**
     * The fully qualified class name of a GridView view
     */
    const val FQCN_GRID_VIEW: String = "android.widget.GridView"
    /**
     * The fully qualified class name of a TabWidget view
     */
    const val FQCN_TAB_WIDGET: String = "android.widget.TabWidget"
    /**
     * The fully qualified class name of a Button view
     */
    const val FQCN_BUTTON: String = "android.widget.Button"
    /**
     * The fully qualified class name of a CheckBox view
     */
    const val FQCN_CHECK_BOX: String = "android.widget.CheckBox"
    /**
     * The fully qualified class name of a CheckedTextView view
     */
    const val FQCN_CHECKED_TEXT_VIEW: String = "android.widget.CheckedTextView"
    /**
     * The fully qualified class name of an ImageButton view
     */
    const val FQCN_IMAGE_BUTTON: String = "android.widget.ImageButton"
    /**
     * The fully qualified class name of a RatingBar view
     */
    const val FQCN_RATING_BAR: String = "android.widget.RatingBar"
    /**
     * The fully qualified class name of a SeekBar view
     */
    const val FQCN_SEEK_BAR: String = "android.widget.SeekBar"
    /**
     * The fully qualified class name of a MultiAutoCompleteTextView view
     */
    const val FQCN_AUTO_COMPLETE_TEXT_VIEW: String = "android.widget.AutoCompleteTextView"
    /**
     * The fully qualified class name of a MultiAutoCompleteTextView view
     */
    const val FQCN_MULTI_AUTO_COMPLETE_TEXT_VIEW: String = "android.widget.MultiAutoCompleteTextView"
    /**
     * The fully qualified class name of a RadioButton view
     */
    const val FQCN_RADIO_BUTTON: String = "android.widget.RadioButton"
    /**
     * The fully qualified class name of a ToggleButton view
     */
    const val FQCN_TOGGLE_BUTTON: String = "android.widget.ToggleButton"
    /**
     * The fully qualified class name of a Spinner view
     */
    const val FQCN_SPINNER: String = "android.widget.Spinner"
    /**
     * The fully qualified class name of an AdapterView
     */
    const val FQCN_ADAPTER_VIEW: String = "android.widget.AdapterView"
    /**
     * The fully qualified class name of a ListView
     */
    const val FQCN_LIST_VIEW: String = "android.widget.ListView"
    /**
     * The fully qualified class name of an ExpandableListView
     */
    const val FQCN_EXPANDABLE_LIST_VIEW: String = "android.widget.ExpandableListView"
    /**
     * The fully qualified class name of a GestureOverlayView
     */
    const val FQCN_GESTURE_OVERLAY_VIEW: String = "android.gesture.GestureOverlayView"
    /**
     * The fully qualified class name of a DatePicker
     */
    const val FQCN_DATE_PICKER: String = "android.widget.DatePicker"
    /**
     * The fully qualified class name of a TimePicker
     */
    const val FQCN_TIME_PICKER: String = "android.widget.TimePicker"
    /**
     * The fully qualified class name of a RadioGroup
     */
    const val FQCN_RADIO_GROUP: String = "android.widgets.RadioGroup"
    /**
     * The fully qualified class name of a Space
     */
    const val FQCN_SPACE: String = "android.widget.Space"
    /**
     * The fully qualified class name of a TextView view
     */
    const val FQCN_TEXT_VIEW: String = "android.widget.TextView"
    /**
     * The fully qualified class name of an ImageView view
     */
    const val FQCN_IMAGE_VIEW: String = "android.widget.ImageView"
    /**
     * The fully qualified class name of NavHostFragment Fragment subclass
     */
    const val FQCN_NAV_HOST_FRAGMENT: String = "androidx.navigation.fragment.NavHostFragment"
    /**
     * The fully qualified class name of a ScrollView
     */
    const val FQCN_SCROLL_VIEW: String = "android.widget.ScrollView"
    const val ATTR_SRC: String = "src"
    const val ATTR_SRC_COMPAT: String = "srcCompat"
    const val ATTR_GRAVITY: String = "gravity"
    const val ATTR_WEIGHT_SUM: String = "weightSum"
    const val ATTR_EMS: String = "ems"
    const val VALUE_HORIZONTAL: String = "horizontal"
    const val GRADLE_PLUGIN_NAME: String = "com.android.tools.build:gradle:"
    const val GRADLE_MINIMUM_VERSION: String = "6.1.1"
    const val GRADLE_LATEST_VERSION: String = GRADLE_MINIMUM_VERSION
    const val GRADLE_PLUGIN_MINIMUM_VERSION: String = "1.0.0"
    const val GRADLE_PLUGIN_RECOMMENDED_VERSION: String = "3.3.2"
    // Temporary - can be removed once the recommended version supports AIA (with splits).
    const val GRADLE_PLUGIN_LATEST_VERSION: String = GRADLE_PLUGIN_RECOMMENDED_VERSION
    /**
     * use api or implementation
     */
    @Deprecated("use api or implementation")
    const val GRADLE_COMPILE_CONFIGURATION: String = "compile"
    /**
     * use api or implementation
     */
    @Deprecated("use api or implementation")
    const val GRADLE_TEST_COMPILE_CONFIGURATION: String = "testCompile"
    /**
     * use api or implementation
     */
    @Deprecated("use api or implementation")
    const val GRADLE_ANDROID_TEST_COMPILE_CONFIGURATION: String = "androidTestCompile"
    const val GRADLE_IMPLEMENTATION_CONFIGURATION: String = "implementation"
    const val GRADLE_API_CONFIGURATION: String = "api"
    const val GRADLE_ANDROID_TEST_IMPLEMENTATION_CONFIGURATION: String = "androidTestImplementation"
    const val GRADLE_ANDROID_TEST_API_CONFIGURATION: String = "androidTestApi"
    const val GRADLE_ANDROID_TEST_UTIL_CONFIGURATION: String = "androidTestUtil"
    const val CURRENT_BUILD_TOOLS_VERSION: String = "29.0.2"
    const val SUPPORT_LIB_GROUP_ID: String = "com.android.support"
    const val SUPPORT_LIB_ARTIFACT: String = "com.android.support:support-v4"
    const val DESIGN_LIB_ARTIFACT: String = "com.android.support:design"
    const val APPCOMPAT_LIB_ARTIFACT_ID: String = "appcompat-v7"
    const val APPCOMPAT_LIB_ARTIFACT: String = SUPPORT_LIB_GROUP_ID + ":" + APPCOMPAT_LIB_ARTIFACT_ID
    const val CARD_VIEW_LIB_ARTIFACT: String = "com.android.support:cardview-v7"
    const val GRID_LAYOUT_LIB_ARTIFACT: String = "com.android.support:gridlayout-v7"
    const val RECYCLER_VIEW_LIB_ARTIFACT: String = "com.android.support:recyclerview-v7"
    const val MAPS_ARTIFACT: String = "com.google.android.gms:play-services-maps"
    const val ADS_ARTIFACT: String = "com.google.android.gms:play-services-ads"
    const val LEANBACK_V17_ARTIFACT: String = "com.android.support:leanback-v17"
    const val ANNOTATIONS_LIB_ARTIFACT_ID: String = "support-annotations"
    const val ANNOTATIONS_LIB_ARTIFACT: String = SUPPORT_LIB_GROUP_ID + ":" + ANNOTATIONS_LIB_ARTIFACT_ID
    const val MEDIA_ROUTER_LIB_ARTIFACT: String = "com.android.support:mediarouter-v7"
    const val ANDROIDX_MATERIAL_ARTIFACT: String = "com.google.android.material:material"
    const val ANDROIDX_CORE_UI_ARTIFACT: String = "androidx.core:core-ui"
    const val ANDROIDX_CARD_VIEW_ARTIFACT: String = "androidx.cardview:cardview"
    const val ANDROIDX_GRID_LAYOUT_ARTIFACT: String = "androidx.gridlayout:gridlayout"
    const val ANDROIDX_RECYCLER_VIEW_ARTIFACT: String = "androidx.recyclerview:recyclerview"
    const val ANDROIDX_LEANBACK_ARTIFACT: String = "androidx.leanback:leanback"
    const val ANDROIDX_ANNOTATIONS_ARTIFACT: String = "androidx.annotation:annotation"
    const val ANDROIDX_SUPPORT_LIB_ARTIFACT: String = "androidx.legacy:legacy-support-v4"
    const val ANDROIDX_VIEW_PAGER_LIB_ARTIFACT: String = "androidx.viewpager:viewpager"
    const val ANDROIDX_VIEW_PAGER2_LIB_ARTIFACT: String = "androidx.viewpager2:viewpager2"
    const val ANDROIDX_APPCOMPAT_LIB_ARTIFACT: String = "androidx.appcompat:appcompat"
    const val ANDROIDX_CONSTRAINT_LAYOUT_LIB_ARTIFACT: String = "androidx.constraintlayout:constraintlayout"
    const val TYPE_DEF_VALUE_ATTRIBUTE: String = "value"
    const val TYPE_DEF_FLAG_ATTRIBUTE: String = "flag"
    const val FN_ANNOTATIONS_ZIP: String = "annotations.zip"
    const val VIEW_BINDING_ARTIFACT: String = "com.android.databinding:viewbinding"
    const val ANDROIDX_VIEW_BINDING_ARTIFACT: String = "androidx.databinding:viewbinding"
    // Data Binding MISC
    const val DATA_BINDING_LIB_ARTIFACT: String = "com.android.databinding:library"
    // processor is always AndroidX
    const val DATA_BINDING_ANNOTATION_PROCESSOR_ARTIFACT: String = "androidx.databinding:databinding-compiler"
    const val DATA_BINDING_ADAPTER_LIB_ARTIFACT: String = "com.android.databinding:adapters"
    const val ANDROIDX_DATA_BINDING_LIB_ARTIFACT: String = "androidx.databinding:databinding-runtime"
    const val DATA_BINDING_BASELIB_ARTIFACT: String = "com.android.databinding:baseLibrary"
    const val ANDROIDX_DATA_BINDING_BASELIB_ARTIFACT: String = "androidx.databinding:databinding-common"
    const val ANDROIDX_DATA_BINDING_ADAPTER_LIB_ARTIFACT: String = "androidx.databinding:databinding-adapters"
    @JvmField
    val TAGS_DATA_BINDING: Array<String> = arrayOf(TAG_VARIABLE, TAG_IMPORT, TAG_LAYOUT, TAG_DATA)
    @JvmField
    val ATTRS_DATA_BINDING: Array<String> = arrayOf(ATTR_NAME, ATTR_TYPE, ATTR_CLASS, ATTR_ALIAS)
    /**
     * Name of keep attribute in XML
     */
    const val ATTR_KEEP: String = "keep"
    /**
     * Name of discard attribute in XML (to mark resources as not referenced, despite guesses)
     */
    const val ATTR_DISCARD: String = "discard"
    /**
     * Name of attribute in XML to control whether we should guess resources to keep
     */
    const val ATTR_SHRINK_MODE: String = "shrinkMode"
    /**
     * {@linkplain #ATTR_SHRINK_MODE} value to only shrink explicitly encountered resources
     */
    const val VALUE_STRICT: String = "strict"
    /**
     * {@linkplain #ATTR_SHRINK_MODE} value to keep possibly referenced resources
     */
    const val VALUE_SAFE: String = "safe"
    /**
     * Prefix of the Android Support Repository path
     */
    const val ANDROID_SUPPORT_ARTIFACT_PREFIX: String = "com.android."
    /**
     * Prefix of the Google Repository path
     */
    const val GOOGLE_SUPPORT_ARTIFACT_PREFIX: String = "com.google.android."
    /**
     * Prefix of firebase groupIds
     */
    const val FIREBASE_ARTIFACT_PREFIX: String = "com.google.firebase."
    /**
     * Folder where proguard rules are located in jar, aar and project generated resources
     */
    const val PROGUARD_RULES_FOLDER: String = "meta-inf/proguard"
    /**
     * Folder where configuration files for R8 and other tools are located in jar files
     */
    const val COM_ANDROID_TOOLS_FOLDER: String = "com.android.tools"
    /**
     * Folder where configuration files for R8 and other tools are located in jar files
     */
    const val TOOLS_CONFIGURATION_FOLDER: String = "meta-inf/" + COM_ANDROID_TOOLS_FOLDER
    const val FD_PREFAB_PACKAGE: String = "prefab"
    private const val DOT: String = "."
    /**
     * Dot-Extension of the Application package Files, i.e. ".apk".
     */
    const val DOT_ANDROID_PACKAGE: String = DOT + EXT_ANDROID_PACKAGE
    /**
     * Dot-Extension for Android archive files
     */
    const val DOT_AAR: String = DOT + EXT_AAR
    /**
     * Dot-Extension of zip files, i.e. ".zip"
     */
    const val DOT_ZIP: String = DOT + EXT_ZIP
    /**
     * Dot-Extension of aidl files, i.e. ".aidl"
     */
    const val DOT_AIDL: String = DOT + EXT_AIDL
    /**
     * Dot-Extension of renderscript files, i.e. ".rs"
     */
    const val DOT_RS: String = DOT + EXT_RS
    /**
     * Dot-Extension of renderscript header files, i.e. ".rsh"
     */
    const val DOT_RSH: String = DOT + EXT_RSH
    /**
     * Dot-Extension of FilterScript files, i.e. ".fs"
     */
    const val DOT_FS: String = DOT + EXT_FS
    /**
     * Dot-Extension of renderscript bitcode files, i.e. ".bc"
     */
    const val DOT_BC: String = DOT + EXT_BC
    /**
     * Dot-Extension of dependency files, i.e. ".d"
     */
    const val DOT_DEP: String = DOT + EXT_DEP
    /**
     * Dot-Extension of native dynamic libraries, i.e. ".so"
     */
    const val DOT_NATIVE_LIBS: String = DOT + EXT_NATIVE_LIB
    /**
     * Dot-Extension of dex files, i.e. ".dex"
     */
    const val DOT_DEX: String = DOT + EXT_DEX
    /**
     * Dot-Extension for temporary resource files, ie "ap_
     */
    const val DOT_RES: String = DOT + EXT_RES
    /**
     * Dot-Extension for Java heap dumps.
     */
    const val DOT_HPROF: String = DOT + EXT_HPROF

    /**
     * Returns the appropriate name for the 'mksdcard' command, which is 'mksdcard.exe' for Windows
     * and 'mksdcard' for all other platforms.
     */
    @JvmStatic
    fun mkSdCardCmdName(): String {
        val os = System.getProperty("os.name")
        var cmd = "mksdcard"
        if (os!!.startsWith("Windows")) {
            cmd += ".exe"
        }
        return cmd
    }

    /**
     * Returns current platform
     *
     * @return one of [PLATFORM_WINDOWS], [PLATFORM_DARWIN], [PLATFORM_LINUX]
     * or [PLATFORM_UNKNOWN].
     */
    @JvmStatic
    fun currentPlatform(): Int {
        val os = System.getProperty("os.name")
        if (os!!.startsWith("Mac OS")) {
            return PLATFORM_DARWIN
        } else if (os.startsWith("Windows")) {
            return PLATFORM_WINDOWS
        } else if (os.startsWith("Linux")) {
            return PLATFORM_LINUX
        }

        return PLATFORM_UNKNOWN
    }

    /**
     * Returns current platform's UI name
     *
     * @return one of "Windows", "Mac OS X", "Linux" or "other".
     */
    @JvmStatic
    fun currentPlatformName(): String {
        val os = System.getProperty("os.name")
        if (os!!.startsWith("Mac OS")) {
            return "Mac OS X"
        } else if (os.startsWith("Windows")) {
            return "Windows"
        } else if (os.startsWith("Linux")) {
            return "Linux"
        }

        return "Other"
    }

    private fun ext(windowsExtension: String, nonWindowsExtension: String): String {
        return if (CURRENT_PLATFORM == PLATFORM_WINDOWS) {
            windowsExtension
        } else {
            nonWindowsExtension
        }
    }

    @Deprecated("The \"android\" command is no longer included in the SDK.")
    @JvmStatic
    fun androidCmdName(): String {
        throw UnsupportedOperationException(
            "The \"android\" command is no longer included in the SDK. Any references to it (e.g. " +
                "by third-party plugins) should be removed."
        )
    }

    object ImageViewAttributes {
        const val TINT: String = "tint"
    }

    object PreferenceTags {
        const val CHECK_BOX_PREFERENCE: String = "CheckBoxPreference"
        const val EDIT_TEXT_PREFERENCE: String = "EditTextPreference"
        const val LIST_PREFERENCE: String = "ListPreference"
        const val MULTI_SELECT_LIST_PREFERENCE: String = "MultiSelectListPreference"
        const val PREFERENCE_CATEGORY: String = "PreferenceCategory"
        const val PREFERENCE_SCREEN: String = "PreferenceScreen"
        const val RINGTONE_PREFERENCE: String = "RingtonePreference"
        const val SWITCH_PREFERENCE: String = "SwitchPreference"
        const val INTENT: String = "intent"
    }

    object PreferenceAttributes {
        const val ATTR_DEFAULT_VALUE: String = "defaultValue"
        const val ATTR_DEPENDENCY: String = "dependency"
        const val ATTR_DIALOG_ICON: String = "dialogIcon"
        const val ATTR_DISABLE_DEPENDENTS_STATE: String = "disableDependentsState"
        const val ATTR_ENTRIES: String = "entries"
        const val ATTR_ENTRY_VALUES: String = "entryValues"
        const val ATTR_ICON: String = "icon"
        const val ATTR_KEY: String = "key"
        const val ATTR_PERSISTENT: String = "persistent"
        const val ATTR_RINGTONE_TYPE: String = "ringtoneType"
        const val ATTR_SHOW_DEFAULT: String = "showDefault"
        const val ATTR_SHOW_SILENT: String = "showSilent"
        const val ATTR_SINGLE_LINE: String = "singleLine"
        const val ATTR_SUMMARY: String = "summary"
        const val ATTR_SUMMARY_ON: String = "summaryOn"
        const val ATTR_SUMMARY_OFF: String = "summaryOff"
        const val ATTR_SWITCH_TEXT_ON: String = "switchTextOn"
        const val ATTR_SWITCH_TEXT_OFF: String = "switchTextOff"
    }

    object MotionSceneTags {
        const val MOTION_SCENE: String = "MotionScene"
        const val TRANSITION: String = "Transition"
        const val STATE_SET: String = "StateSet"
        const val CONSTRAINT_SET: String = "ConstraintSet"
        const val CONSTRAINT: String = "Constraint"
        const val KEY_FRAME_SET: String = "KeyFrameSet"
        const val KEY_ATTRIBUTE: String = "KeyAttribute"
        const val KEY_CYCLE: String = "KeyCycle"
        const val KEY_POSITION: String = "KeyPosition"
        const val KEY_TRIGGER: String = "KeyTrigger"
        const val KEY_TIME_CYCLE: String = "KeyTimeCycle"
        const val ON_CLICK: String = "OnClick"
        const val ON_SWIPE: String = "OnSwipe"
        const val LAYOUT: String = "Layout"
        const val MOTION: String = "Motion"
        const val PROPERTY_SET: String = "PropertySet"
        const val TRANSFORM: String = "Transform"
        const val CUSTOM_ATTRIBUTE: String = "CustomAttribute"
        const val STATE: String = "State"
        const val VARIANT: String = "Variant"
    }

    object MotionSceneAttributes {
        const val ATTR_CUSTOM_ATTRIBUTE_NAME: String = "attributeName"
        const val ATTR_CUSTOM_COLOR_VALUE: String = "customColorValue"
        const val ATTR_CUSTOM_COLOR_DRAWABLE_VALUE: String = "customColorDrawableValue"
        const val ATTR_CUSTOM_INTEGER_VALUE: String = "customIntegerValue"
        const val ATTR_CUSTOM_FLOAT_VALUE: String = "customFloatValue"
        const val ATTR_CUSTOM_STRING_VALUE: String = "customStringValue"
        const val ATTR_CUSTOM_DIMENSION_VALUE: String = "customDimension"
        const val ATTR_CUSTOM_PIXEL_DIMENSION_VALUE: String = "customPixelDimension"
        const val ATTR_CUSTOM_BOOLEAN_VALUE: String = "customBoolean"
    }

    // Text Alignment values.
    object TextAlignment {
        const val NONE: String = "none"
        const val INHERIT: String = "inherit"
        const val GRAVITY: String = "gravity"
        const val TEXT_START: String = "textStart"
        const val TEXT_END: String = "textEnd"
        const val CENTER: String = "center"
        const val VIEW_START: String = "viewStart"
        const val VIEW_END: String = "viewEnd"
    }

    object TextStyle {
        const val VALUE_NORMAL: String = "normal"
        const val VALUE_BOLD: String = "bold"
        const val VALUE_ITALIC: String = "italic"
    }

    object ViewAttributes {
        const val MIN_HEIGHT: String = "minHeight"
    }
}
