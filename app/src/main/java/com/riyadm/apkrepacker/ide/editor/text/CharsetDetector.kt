/*
 * Copyright (C) 2018 Tran Le Duy
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.riyadm.apkrepacker.ide.editor.text

import org.mozilla.intl.chardet.nsDetector
import org.mozilla.intl.chardet.nsICharsetDetectionObserver
import org.mozilla.intl.chardet.nsPSMDetector

import java.io.BufferedInputStream
import java.util.ArrayList

object CharsetDetector {

    @JvmStatic
    @Throws(Exception::class)
    fun detect(bufferedInputStream: BufferedInputStream): String {

        val det = nsDetector(nsPSMDetector.ALL)

        // Set an observer...
        // The Notify() will be called when a matching charset is found.
        val charsets: MutableList<String> = ArrayList()
        det.Init(nsICharsetDetectionObserver { charsets.add(it) })

        val buf = ByteArray(1024)
        var len: Int
        var done = false
        var isAscii = true

        while (bufferedInputStream.read(buf, 0, buf.size).also { len = it } != -1) {

            // Check if the stream is only ascii.
            if (isAscii)
                isAscii = det.isAscii(buf, len)

            // DoIt if non-ascii and not done yet.
            if (!isAscii && !done)
                done = det.DoIt(buf, len, false)
        }
        det.DataEnd()

        var encoding = if (charsets.isEmpty()) "UTF-8" else charsets[0]
        if ("GB2312" == encoding) {
            encoding = "GBK"
        }
        return encoding
    }
}
