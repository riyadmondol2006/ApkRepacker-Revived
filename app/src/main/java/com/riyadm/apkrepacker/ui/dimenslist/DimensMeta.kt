package com.riyadm.apkrepacker.ui.dimenslist

import android.os.Parcel
import android.os.Parcelable

/** One `<dimen name="…">value</dimen>` entry. */
class DimensMeta(
    @JvmField var label: String?,
    @JvmField var value: String?,
) : Parcelable {

    private constructor(parcel: Parcel) : this(parcel.readString(), parcel.readString())

    override fun describeContents() = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(label)
        dest.writeString(value)
    }

    class Builder(name: String?) {
        private val dimensMeta = DimensMeta(name, "?")

        fun setValue(value: String?) = apply { dimensMeta.value = value }

        fun build() = dimensMeta
    }

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<DimensMeta> = object : Parcelable.Creator<DimensMeta> {
            override fun createFromParcel(source: Parcel) = DimensMeta(source)

            override fun newArray(size: Int): Array<DimensMeta?> = arrayOfNulls(size)
        }
    }
}
