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

package com.riyadm.apkrepacker.ide.editor.util

/**
 * Created by jecelyin on 16/1/30.
 */
object EmptyArray {
    @JvmField
    val BOOLEAN = BooleanArray(0)
    @JvmField
    val BYTE = ByteArray(0)
    @JvmField
    val CHAR = CharArray(0)
    @JvmField
    val DOUBLE = DoubleArray(0)
    @JvmField
    val INT = IntArray(0)
    @JvmField
    val CLASS = arrayOf<Class<*>>()
    @JvmField
    val OBJECT = arrayOf<Any>()
    @JvmField
    val STRING = arrayOf<String>()
    @JvmField
    val THROWABLE = arrayOf<Throwable>()
    @JvmField
    val STACK_TRACE_ELEMENT = arrayOf<StackTraceElement>()
}
