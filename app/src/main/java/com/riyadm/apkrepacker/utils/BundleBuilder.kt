package com.riyadm.apkrepacker.utils

import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.Parcel
import android.os.Parcelable
import android.os.PersistableBundle
import android.util.Size
import android.util.SizeF
import android.util.SparseArray
import androidx.annotation.RequiresApi
import androidx.core.app.BundleCompat
import java.io.Serializable

class BundleBuilder private constructor(bundle: Bundle) {

    private var mBundle: Bundle? = bundle

    constructor() : this(Bundle())

    fun build(): Bundle {
        val bundle = mBundle
        mBundle = null
        return bundle!!
    }


    fun setClassLoader(loader: ClassLoader): BundleBuilder {
        mBundle!!.setClassLoader(loader)
        return this
    }

    fun clear(): BundleBuilder {
        mBundle!!.clear()
        return this
    }

    fun remove(key: String?): BundleBuilder {
        mBundle!!.remove(key)
        return this
    }

    fun putAll(bundle: Bundle): BundleBuilder {
        mBundle!!.putAll(bundle)
        return this
    }

    fun putByte(key: String?, value: Byte): BundleBuilder {
        mBundle!!.putByte(key, value)
        return this
    }

    fun putChar(key: String?, value: Char): BundleBuilder {
        mBundle!!.putChar(key, value)
        return this
    }

    fun putShort(key: String?, value: Short): BundleBuilder {
        mBundle!!.putShort(key, value)
        return this
    }

    fun putFloat(key: String?, value: Float): BundleBuilder {
        mBundle!!.putFloat(key, value)
        return this
    }

    fun putCharSequence(key: String?, value: CharSequence?): BundleBuilder {
        mBundle!!.putCharSequence(key, value)
        return this
    }

    fun putParcelable(key: String?, value: Parcelable?): BundleBuilder {
        mBundle!!.putParcelable(key, value)
        return this
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    fun putSize(key: String?, value: Size?): BundleBuilder {
        mBundle!!.putSize(key, value)
        return this
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    fun putSizeF(key: String?, value: SizeF?): BundleBuilder {
        mBundle!!.putSizeF(key, value)
        return this
    }

    fun putParcelableArray(key: String?, value: Array<Parcelable>?): BundleBuilder {
        mBundle!!.putParcelableArray(key, value)
        return this
    }

    fun putParcelableArrayList(key: String?, value: ArrayList<out Parcelable>?): BundleBuilder {
        mBundle!!.putParcelableArrayList(key, value)
        return this
    }

    fun putSparseParcelableArray(key: String?, value: SparseArray<out Parcelable>?): BundleBuilder {
        mBundle!!.putSparseParcelableArray(key, value)
        return this
    }

    fun putIntegerArrayList(key: String?, value: ArrayList<Int>?): BundleBuilder {
        mBundle!!.putIntegerArrayList(key, value)
        return this
    }

    fun putStringArrayList(key: String?, value: ArrayList<String>?): BundleBuilder {
        mBundle!!.putStringArrayList(key, value)
        return this
    }

    fun putCharSequenceArrayList(key: String?, value: ArrayList<CharSequence>?): BundleBuilder {
        mBundle!!.putCharSequenceArrayList(key, value)
        return this
    }

    fun putSerializable(key: String?, value: Serializable?): BundleBuilder {
        mBundle!!.putSerializable(key, value)
        return this
    }

    fun putByteArray(key: String?, value: ByteArray?): BundleBuilder {
        mBundle!!.putByteArray(key, value)
        return this
    }

    fun putShortArray(key: String?, value: ShortArray?): BundleBuilder {
        mBundle!!.putShortArray(key, value)
        return this
    }

    fun putCharArray(key: String?, value: CharArray?): BundleBuilder {
        mBundle!!.putCharArray(key, value)
        return this
    }

    fun putFloatArray(key: String?, value: FloatArray?): BundleBuilder {
        mBundle!!.putFloatArray(key, value)
        return this
    }

    fun putCharSequenceArray(key: String?, value: Array<CharSequence>?): BundleBuilder {
        mBundle!!.putCharSequenceArray(key, value)
        return this
    }

    fun putBundle(key: String?, value: Bundle?): BundleBuilder {
        mBundle!!.putBundle(key, value)
        return this
    }

    fun putBinder(key: String?, value: IBinder?): BundleBuilder {
        BundleCompat.putBinder(mBundle!!, key, value)
        return this
    }

    fun writeToParcel(parcel: Parcel, flags: Int): BundleBuilder {
        mBundle!!.writeToParcel(parcel, flags)
        return this
    }

    fun readFromParcel(parcel: Parcel): BundleBuilder {
        mBundle!!.readFromParcel(parcel)
        return this
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    fun putAll(bundle: PersistableBundle): BundleBuilder {
        mBundle!!.putAll(bundle)
        return this
    }

    fun putBoolean(key: String?, value: Boolean): BundleBuilder {
        mBundle!!.putBoolean(key, value)
        return this
    }

    fun putInt(key: String?, value: Int): BundleBuilder {
        mBundle!!.putInt(key, value)
        return this
    }

    fun putLong(key: String?, value: Long): BundleBuilder {
        mBundle!!.putLong(key, value)
        return this
    }

    fun putDouble(key: String?, value: Double): BundleBuilder {
        mBundle!!.putDouble(key, value)
        return this
    }

    fun putString(key: String?, value: String?): BundleBuilder {
        mBundle!!.putString(key, value)
        return this
    }

    fun putBooleanArray(key: String?, value: BooleanArray?): BundleBuilder {
        mBundle!!.putBooleanArray(key, value)
        return this
    }

    fun putIntArray(key: String?, value: IntArray?): BundleBuilder {
        mBundle!!.putIntArray(key, value)
        return this
    }

    fun putLongArray(key: String?, value: LongArray?): BundleBuilder {
        mBundle!!.putLongArray(key, value)
        return this
    }

    fun putDoubleArray(key: String?, value: DoubleArray?): BundleBuilder {
        mBundle!!.putDoubleArray(key, value)
        return this
    }

    fun putStringArray(key: String?, value: Array<String>?): BundleBuilder {
        mBundle!!.putStringArray(key, value)
        return this
    }

    companion object {
        @JvmStatic
        fun buildUpon(bundle: Bundle): BundleBuilder {
            return BundleBuilder(bundle)
        }
    }
}
