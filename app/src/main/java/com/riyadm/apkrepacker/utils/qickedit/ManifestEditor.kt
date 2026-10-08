package com.riyadm.apkrepacker.utils.qickedit

import com.riyadm.apkrepacker.utils.QickEditParams
import pxb.android.axml.AxmlReader
import pxb.android.axml.AxmlVisitor
import pxb.android.axml.AxmlWriter
import pxb.android.axml.NodeVisitor
import pxb.android.axml.Util
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class ManifestEditor(manifestInputStream: File) {

    private val components = arrayOf("activity", "activity-alias", "provider", "receiver", "service")
    private val mManifest: File = manifestInputStream
    private var mVersionCode = -1
    private var mMinimumSdk = -1
    private var mTargetSdk = -1
    private var mInstallLocation = -1
    private var mVersionName: String? = null
    private var mAppName: String? = null
    private var mPackageName: String? = null
    private var mManifestData: ByteArray? = null

    fun setVersionCode(versionCode: Int) {
        mVersionCode = versionCode
    }

    fun setVersionName(versionName: String?): ManifestEditor {
        mVersionName = versionName
        return this
    }

    fun setAppName(appName: String?): ManifestEditor {
        mAppName = appName
        return this
    }

    fun setPackageName(packageName: String?): ManifestEditor {
        mPackageName = packageName
        return this
    }

    fun setMinimumSdk(sdk: Int): ManifestEditor {
        mMinimumSdk = sdk
        return this
    }

    fun setTargetSdk(sdk: Int): ManifestEditor {
        mTargetSdk = sdk
        return this
    }

    fun setInstallLocation(location: Int): ManifestEditor {
        mInstallLocation = location
        return this
    }

    @Throws(IOException::class)
    fun commit(): ManifestEditor {
        val reader = AxmlReader(Util.readFile(mManifest))
        val writer = AxmlWriter()
        reader.accept(object : AxmlVisitor(writer) {
            override fun child(ns: String?, name: String?): NodeVisitor? //manifest
            {
                val manifestNode = super.child(ns, name)
                return object : NodeVisitor(manifestNode) {
                    override fun child(ns: String?, name: String?): NodeVisitor? //manifest's child nodes
                    {
                        if (name.equals("uses-sdk", ignoreCase = true)) {
                            val usesSdkNode = super.child(ns, name)
                            return object : NodeVisitor(usesSdkNode) {

                                override fun attr(ns: String?, name: String?, resourceId: Int, type: Int, obj: Any?) {
                                    var value = obj
                                    var t = type
                                    if (name.equals("minSdkVersion", ignoreCase = true) && mMinimumSdk > 0) {
                                        value = mMinimumSdk
                                        t = NodeVisitor.TYPE_FIRST_INT
                                    } else if (name.equals("targetSdkVersion", ignoreCase = true) && mTargetSdk > 0) {
                                        value = mTargetSdk
                                        t = NodeVisitor.TYPE_FIRST_INT
                                    }
                                    super.attr(ns, name, resourceId, t, value)
                                }
                            }
                        } else if (name.equals("application", ignoreCase = true)) {
                            val applicationNode = super.child(ns, name)
                            return object : NodeVisitor(applicationNode) {
                                override fun child(ns: String?, name: String?): NodeVisitor? {
                                    if (mPackageName == null || QickEditParams.getOldPackage() == null) {
                                        return super.child(ns, name)
                                    }
                                    for (component in components) {
                                        if (name.equals(component, ignoreCase = true)) {
                                            val componentNode = super.child(ns, name)
                                            return object : NodeVisitor(componentNode) {

                                                override fun attr(ns: String?, name: String?, resourceId: Int, type: Int, obj: Any?) {
                                                    var value = obj
                                                    var t = type
                                                    if (name.equals("name", ignoreCase = true) && value is String) {
                                                        val check = value.indexOf(".")
                                                        if (check < 0) {
                                                            value = "$mPackageName.$value"
                                                        } else if (check == 0) {
                                                            value = mPackageName + value
                                                        }
                                                        t = NodeVisitor.TYPE_STRING
                                                    }
                                                    super.attr(ns, name, resourceId, t, value)
                                                }
                                            }
                                        }
                                    }
                                    return super.child(ns, name)
                                }

                                override fun attr(ns: String?, name: String?, resourceId: Int, type: Int, obj: Any?) {
                                    var value = obj
                                    var t = type
                                    if (name.equals("label", ignoreCase = true) && mAppName != null) {
                                        value = mAppName
                                        t = NodeVisitor.TYPE_STRING
                                    } else if (name.equals("extractNativeLibs", ignoreCase = true)) {
                                        return
                                    }
                                    super.attr(ns, name, resourceId, t, value)
                                }
                            }
                        }
                        return super.child(ns, name)
                    }

                    override fun attr(ns: String?, name: String?, resourceId: Int, type: Int, obj: Any?) {
                        var value = obj
                        var t = type
                        if (name.equals("package", ignoreCase = true) && mPackageName != null) {
                            value = mPackageName
                            t = NodeVisitor.TYPE_STRING
                        } else if (name.equals("installLocation", ignoreCase = true)) {
                            val loc = getRealInstallLocation(mInstallLocation)
                            if (loc >= 0) {
                                value = loc
                                t = NodeVisitor.TYPE_FIRST_INT
                            } else {
                                return
                            }

                        } else if (name.equals("versionName", ignoreCase = true) && mVersionName != null) {
                            value = mVersionName
                            t = NodeVisitor.TYPE_STRING
                        } else if (name.equals("versionCode", ignoreCase = true) && mVersionCode > 0) {
                            value = mVersionCode
                            t = NodeVisitor.TYPE_FIRST_INT
                        }
                        super.attr(ns, name, resourceId, t, value)
                    }
                }
            }
        })
        mManifestData = writer.toByteArray()
        return this
    }


    @Throws(IOException::class)
    fun writeTo(manifestOutputStream: FileOutputStream) {
        manifestOutputStream.write(mManifestData!!)
        manifestOutputStream.close()
    }

    /*
        Return real install location from selected item in spinner
     */
    private fun getRealInstallLocation(installLocation: Int): Int {
        return when (installLocation) {
            0 -> -1//default
            1 -> 0//auto
            2 -> 1//internal
            3 -> 2//external
            else -> -1
        }
    }

}
