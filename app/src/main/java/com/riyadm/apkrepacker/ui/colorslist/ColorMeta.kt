package com.riyadm.apkrepacker.ui.colorslist

import android.os.Parcel
import android.os.Parcelable

/** One `<color name="…">value</color>` entry; [iconUri] mirrors the value for the swatch. */
class ColorMeta(
    @JvmField var label: String?,
    @JvmField var value: String?,
) : Parcelable {

    @JvmField
    var iconUri: String? = null

    private constructor(parcel: Parcel) : this(parcel.readString(), parcel.readString()) {
        iconUri = parcel.readString()
    }

    override fun describeContents() = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(label)
        dest.writeString(value)
        dest.writeString(iconUri)
    }

    class Builder(name: String?) {
        private val colorMeta = ColorMeta(name, "?")

        fun setValue(value: String?) = apply { colorMeta.value = value }

        fun setIcon(color: String?) = apply { colorMeta.iconUri = color }

        fun build() = colorMeta
    }

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<ColorMeta> = object : Parcelable.Creator<ColorMeta> {
            override fun createFromParcel(source: Parcel) = ColorMeta(source)

            override fun newArray(size: Int): Array<ColorMeta?> = arrayOfNulls(size)
        }
    }
}
