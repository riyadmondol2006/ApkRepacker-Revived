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

package com.riyadm.apkrepacker.database.entity

class RecentFileItem {
    @JvmField
    var time: Long = 0

    @JvmField
    var path: String? = null

    @JvmField
    var encoding: String? = null

    @JvmField
    var offset: Int = 0

    @JvmField
    var isLastOpen: Boolean = false

    fun getTime(): Long {
        return time
    }

    fun setTime(time: Long) {
        this.time = time
    }

    fun getPath(): String? {
        return path
    }

    fun setPath(path: String?) {
        this.path = path
    }

    fun getEncoding(): String? {
        return encoding
    }

    fun setEncoding(encoding: String?) {
        this.encoding = encoding
    }

    fun getOffset(): Int {
        return offset
    }

    fun setOffset(offset: Int) {
        this.offset = offset
    }

    fun isLastOpen(): Boolean {
        return isLastOpen
    }

    fun setLastOpen(lastOpen: Boolean) {
        isLastOpen = lastOpen
    }
}
