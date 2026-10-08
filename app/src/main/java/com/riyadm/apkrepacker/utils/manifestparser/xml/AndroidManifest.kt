/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.riyadm.apkrepacker.utils.manifestparser.xml

import com.riyadm.apkrepacker.utils.manifestparser.SdkConstants

/** Constants for the AndroidManifest.xml file. */
object AndroidManifest {

    const val NODE_MANIFEST: String = "manifest"
    const val NODE_APPLICATION: String = "application"
    const val NODE_ACTIVITY: String = "activity"
    const val NODE_ACTIVITY_ALIAS: String = "activity-alias"
    const val NODE_SERVICE: String = "service"
    const val NODE_RECEIVER: String = "receiver"
    const val NODE_PROVIDER: String = "provider"
    const val NODE_INTENT: String = "intent-filter"
    const val NODE_ACTION: String = "action"
    const val NODE_CATEGORY: String = "category"
    const val NODE_USES_SDK: String = "uses-sdk"
    const val NODE_PERMISSION: String = "permission"
    const val NODE_PERMISSION_TREE: String = "permission-tree"
    const val NODE_PERMISSION_GROUP: String = "permission-group"
    const val NODE_USES_PERMISSION: String = "uses-permission"
    const val NODE_INSTRUMENTATION: String = "instrumentation"
    const val NODE_USES_LIBRARY: String = "uses-library"
    const val NODE_SUPPORTS_SCREENS: String = "supports-screens"
    const val NODE_COMPATIBLE_SCREENS: String = "compatible-screens"
    const val NODE_USES_CONFIGURATION: String = "uses-configuration"
    const val NODE_USES_FEATURE: String = "uses-feature"
    const val NODE_METADATA: String = "meta-data"
    const val NODE_DATA: String = "data"
    const val NODE_GRANT_URI_PERMISSION: String = "grant-uri-permission"
    const val NODE_PATH_PERMISSION: String = "path-permission"
    const val NODE_SUPPORTS_GL_TEXTURE: String = "supports-gl-texture"

    const val ATTRIBUTE_PACKAGE: String = "package"
    const val ATTRIBUTE_VERSIONCODE: String = "versionCode"
    const val ATTRIBUTE_VERSIONNAME: String = "versionName"
    const val ATTRIBUTE_NAME: String = "name"
    const val ATTRIBUTE_MIME_TYPE: String = "mimeType"
    const val ATTRIBUTE_PORT: String = "port"
    const val ATTRIBUTE_REQUIRED: String = "required"
    const val ATTRIBUTE_GLESVERSION: String = "glEsVersion"
    const val ATTRIBUTE_PROCESS: String = "process"
    const val ATTRIBUTE_DEBUGGABLE: String = "debuggable"
    const val ATTRIBUTE_HASCODE: String = "hasCode"
    const val ATTRIBUTE_LABEL: String = "label"
    const val ATTRIBUTE_ICON: String = "icon"
    const val ATTRIBUTE_MIN_SDK_VERSION: String = "minSdkVersion"
    const val ATTRIBUTE_TARGET_SDK_VERSION: String = "targetSdkVersion"
    const val ATTRIBUTE_TARGET_PACKAGE: String = "targetPackage"
    const val ATTRIBUTE_FUNCTIONAL_TEST: String = "functionalTest"
    const val ATTRIBUTE_HANDLE_PROFILING: String = "handleProfiling"
    const val ATTRIBUTE_INSTRUMENTATION_LABEL: String = "label"
    const val ATTRIBUTE_TARGET_ACTIVITY: String = "targetActivity"
    const val ATTRIBUTE_MANAGE_SPACE_ACTIVITY: String = "manageSpaceActivity"
    const val ATTRIBUTE_EXPORTED: String = "exported"
    const val ATTRIBUTE_RESIZEABLE: String = "resizeable"
    const val ATTRIBUTE_ANYDENSITY: String = "anyDensity"
    const val ATTRIBUTE_SMALLSCREENS: String = "smallScreens"
    const val ATTRIBUTE_NORMALSCREENS: String = "normalScreens"
    const val ATTRIBUTE_LARGESCREENS: String = "largeScreens"
    const val ATTRIBUTE_REQ_5WAYNAV: String = "reqFiveWayNav"
    const val ATTRIBUTE_REQ_NAVIGATION: String = "reqNavigation"
    const val ATTRIBUTE_REQ_HARDKEYBOARD: String = "reqHardKeyboard"
    const val ATTRIBUTE_REQ_KEYBOARDTYPE: String = "reqKeyboardType"
    const val ATTRIBUTE_REQ_TOUCHSCREEN: String = "reqTouchScreen"
    const val ATTRIBUTE_THEME: String = "theme"
    const val ATTRIBUTE_BACKUP_AGENT: String = "backupAgent"
    const val ATTRIBUTE_PARENT_ACTIVITY_NAME: String = "parentActivityName"
    const val ATTRIBUTE_SUPPORTS_RTL: String = "supportsRtl"
    const val ATTRIBUTE_UI_OPTIONS: String = "uiOptions"
    const val ATTRIBUTE_VALUE: String = "value"
    const val ATTRIBUTE_EXTRACT_NATIVE_LIBS: String = "extractNativeLibs"
    const val ATTRIBUTE_SPLIT: String = "split"
    const val ATTRIBUTE_RESIZEABLE_ACTIVITY: String = "resizeableActivity"
    const val ATTRIBUTE_SCREEN_ORIENTATION: String = "screenOrientation"

    const val VALUE_PARENT_ACTIVITY: String =
            SdkConstants.ANDROID_SUPPORT_PKG_PREFIX + "PARENT_ACTIVITY"

}
