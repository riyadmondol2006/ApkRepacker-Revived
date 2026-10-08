package com.riyadm.apkrepacker.ui.findresult

import android.os.Parcel
import android.os.Parcelable
import android.text.SpannableStringBuilder

/** One matching line of a string search: the highlighted line text, its file and where it is. */
class ChildData : Parcelable {

    var name: String? = null
    var path: String? = null
        private set
    var offset: Int = 0
    private var spannableName: SpannableStringBuilder? = null

    constructor(parcel: Parcel) {
        name = parcel.readString()
    }

    constructor(name: String?) {
        this.name = name
    }

    constructor(name: SpannableStringBuilder?, path: String?, offset: Int) {
        this.spannableName = name
        this.path = path
        this.offset = offset
    }

    fun getSpannableName(): SpannableStringBuilder? = spannableName

    override fun describeContents(): Int = 0

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(name)
    }

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<ChildData> = object : Parcelable.Creator<ChildData> {
            override fun createFromParcel(source: Parcel): ChildData = ChildData(source)

            override fun newArray(size: Int): Array<ChildData?> = arrayOfNulls(size)
        }
    }
}
