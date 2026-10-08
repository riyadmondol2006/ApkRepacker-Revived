package com.riyadm.patchengine.utils

object HexUtil {

    @JvmStatic
    fun bytesToHexString(src: ByteArray?): String? {
        val stringBuilder = StringBuilder()
        if (src == null || src.size <= 0) {
            return null
        }
        for (b in src) {
            val hv = Integer.toHexString(b.toInt() and 255)
            if (hv.length < 2) {
                stringBuilder.append(0)
            }
            stringBuilder.append(hv)
        }
        return stringBuilder.toString()
    }
}
