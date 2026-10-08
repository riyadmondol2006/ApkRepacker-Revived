package com.riyadm.apkrepacker.utils

import android.content.ContentResolver
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Parcel
import android.os.Parcelable

class PackageMeta : Parcelable {

    @JvmField
    var packageName: String
    @JvmField
    var label: String?
    @JvmField
    var hasSplits = false
    @JvmField
    var isSystemApp = false
    @JvmField
    var versionCode: Long = 0
    @JvmField
    var versionName: String? = null
    @JvmField
    var iconUri: Uri? = null
    @JvmField
    var iconDrawable: Drawable? = null

    constructor(packageName: String, label: String?) {
        this.packageName = packageName
        this.label = label
    }

    @Suppress("DEPRECATION")
    private constructor(`in`: Parcel) {
        packageName = `in`.readString()!!
        label = `in`.readString()
        hasSplits = `in`.readInt() == 1
        isSystemApp = `in`.readInt() == 1
        versionCode = `in`.readLong()
        versionName = `in`.readString()
        iconUri = `in`.readParcelable(Uri::class.java.classLoader)
        iconDrawable = `in`.readParcelable<Parcelable>(Drawable::class.java.classLoader) as Drawable?
    }

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(packageName)
        dest.writeString(label)
        dest.writeInt(if (hasSplits) 1 else 0)
        dest.writeInt(if (isSystemApp) 1 else 0)
        dest.writeLong(versionCode)
        dest.writeString(versionName)
        dest.writeParcelable(iconUri, 0)
        dest.writeParcelable(iconDrawable as Parcelable?, 0)
    }

    class Builder(packageName: String) {
        private val mPackageMeta: PackageMeta = PackageMeta(packageName, "?")

        fun setLabel(label: String?): Builder {
            mPackageMeta.label = label
            return this
        }

        fun setHasSplits(hasSplits: Boolean): Builder {
            mPackageMeta.hasSplits = hasSplits
            return this
        }

        fun setIsSystemApp(isSystemApp: Boolean): Builder {
            mPackageMeta.isSystemApp = isSystemApp
            return this
        }

        fun serVersionCode(versionCode: Long): Builder {
            mPackageMeta.versionCode = versionCode
            return this
        }

        fun setVersionName(versionName: String?): Builder {
            mPackageMeta.versionName = versionName
            return this
        }

        fun setIcon(iconResId: Int): Builder {
            if (iconResId == 0) {
                mPackageMeta.iconUri = null
                return this
            }

            mPackageMeta.iconUri = Uri.Builder()
                    .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
                    .authority(mPackageMeta.packageName)
                    .path(iconResId.toString())
                    .build()

            return this
        }

        fun setIconDrawable(iconDrawable: Drawable?): Builder {
            mPackageMeta.iconDrawable = iconDrawable
            return this
        }

        fun build(): PackageMeta {
            return mPackageMeta
        }
    }

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<PackageMeta> = object : Parcelable.Creator<PackageMeta> {
            override fun createFromParcel(`in`: Parcel): PackageMeta {
                return PackageMeta(`in`)
            }

            override fun newArray(size: Int): Array<PackageMeta?> {
                return arrayOfNulls(size)
            }
        }

        @Suppress("DEPRECATION")
        @JvmStatic
        fun forPackage(context: Context, packageName: String): PackageMeta? {
            return try {
                val pm = context.packageManager

                val applicationInfo = pm.getApplicationInfo(packageName, 0)
                val packageInfo = pm.getPackageInfo(packageName, 0)

                Builder(applicationInfo.packageName)
                        .setLabel(applicationInfo.loadLabel(pm).toString())
                        .setHasSplits(applicationInfo.splitPublicSourceDirs != null && applicationInfo.splitPublicSourceDirs!!.isNotEmpty())
                        .setIsSystemApp((applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0)
                        .serVersionCode(if (AppUtils.apiIsAtLeast(Build.VERSION_CODES.P)) packageInfo.longVersionCode else packageInfo.versionCode.toLong())
                        .setVersionName(packageInfo.versionName)
                        .setIcon(applicationInfo.icon)
                        .setIconDrawable(applicationInfo.loadIcon(pm))
                        .build()

            } catch (e: PackageManager.NameNotFoundException) {
                null
            }
        }
    }
}
