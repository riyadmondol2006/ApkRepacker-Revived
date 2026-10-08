package com.jecelyin.editor.v2.adapter

import android.os.Parcel
import android.os.Parcelable
import com.riyadm.apkrepacker.ide.editor.EditorDelegate

class SavedState : Parcelable {

    @JvmField
    var states: Array<EditorDelegate.SavedState?>? = null

    constructor()

    constructor(`in`: Parcel) {
//            states = in.readParcelableArray();
        states = `in`.createTypedArray(EditorDelegate.SavedState.CREATOR)
    }

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeParcelableArray(states, flags)
    }

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<SavedState> = object : Parcelable.Creator<SavedState> {
            override fun createFromParcel(`in`: Parcel): SavedState {
                return SavedState(`in`)
            }

            override fun newArray(size: Int): Array<SavedState?> {
                return arrayOfNulls(size)
            }
        }
    }
}
