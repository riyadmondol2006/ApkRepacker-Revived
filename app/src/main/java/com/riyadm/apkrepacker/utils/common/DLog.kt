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

package com.riyadm.apkrepacker.utils.common

import android.util.Log
import com.riyadm.apkrepacker.BuildConfig

/**
 * Android logcat
 * Setup Crashlytics https://firebase.google.com/docs/crashlytics/get-started?authuser=0
 */
object DLog {
    /**
     * Show log
     */
    @JvmField
    val DEBUG: Boolean = BuildConfig.DEBUG
    private const val TAG = "DLog"

    /**
     * Android environment
     */
    @JvmField
    var ANDROID = true

    /**
     * Debug log
     *
     * -
     */
    @JvmStatic
    fun d(msg: Any?) {
        if (DEBUG) {
            if (ANDROID) {
                Log.d(TAG, msg!!.toString())
            } else {
                println(TAG + ": " + msg!!.toString())
            }
        }
    }

    /**
     * debug log
     */
    @JvmStatic
    fun d(TAG: String?, message: Any?) {
        if (DEBUG) {
            if (ANDROID) {
                Log.d(TAG, message!!.toString())
            } else {
                println(TAG + ": " + message!!.toString())
            }
        }
    }

    @JvmStatic
    fun d(tag: String?, message: String?, throwable: Throwable?) {
        if (DEBUG) {
            if (ANDROID) {
                Log.d(tag, message, throwable)
            } else {
                println("$TAG: $message")
            }
        }
    }

    /**
     * warning log
     */
    @JvmStatic
    fun w(msg: Any?) {
        if (DEBUG) {
            if (ANDROID) {
                Log.w(TAG, msg!!.toString())
            } else {
                println(TAG + ": " + msg!!.toString())
            }
        }
    }

    /**
     * warning log
     */
    @JvmStatic
    fun w(TAG: String?, msg: Any?) {
        if (DEBUG) {
            if (ANDROID) {
                Log.w(TAG, msg!!.toString())
            } else {
                println(TAG + ": " + msg!!.toString())
            }
        }
    }

    /**
     * warning log
     */
    @JvmStatic
    fun w(TAG: String?, msg: Any?, e: Throwable?) {
        if (DEBUG) {
            if (ANDROID) {
                Log.w(TAG, msg!!.toString(), e)
            } else {
                println(TAG + ": " + msg!!.toString())
                e!!.printStackTrace()
            }
        }
    }

    /**
     * Error log
     */
    @JvmStatic
    fun e(exception: Throwable?) {
        if (DEBUG) {
            if (ANDROID) {
                Log.e(TAG, "Error ", exception)
            } else {
                System.err.println(TAG + ": " + exception!!.message)
                exception.printStackTrace()
            }
        }
    }

    /**
     * Error log
     */
    @JvmStatic
    fun e(TAG: String?, e: Throwable?) {
        if (DEBUG) {
            if (ANDROID) {
                Log.e(TAG, "Error ", e)
            } else {
                System.err.println(TAG + ": " + e!!.message)
                e.printStackTrace()
            }
        }
    }

    /**
     * error log
     */
    @JvmStatic
    fun e(TAG: String?, exception: String?) {
        if (DEBUG) {
            if (ANDROID) {
                Log.e(TAG, exception!!)
            } else {
                System.err.println("$TAG: $exception")
            }
        }
    }

    /**
     * Error log
     */
    @JvmStatic
    fun e(TAG: String?, msg: String?, e: Throwable?) {
        if (DEBUG) {
            if (ANDROID) {
                Log.e(TAG, msg, e)
            } else {
                System.err.println("$TAG: $msg")
                e!!.printStackTrace()
            }
        }
    }

    /**
     * info log
     */
    @JvmStatic
    fun i(msg: Any?) {
        if (DEBUG) {
            if (ANDROID) {
                Log.i(TAG, msg!!.toString())
            } else {
                println(TAG + ": " + msg!!.toString())
            }
        }
    }

    /**
     * info log
     */
    @JvmStatic
    fun i(tag: String?, msg: Any?) {
        if (DEBUG) {
            if (ANDROID) {
                Log.i(tag, msg!!.toString())
            } else {
                System.err.println("$tag: $msg")
            }
        }
    }

    /**
     * Report an error to firebase server
     *
     * @param throwable - any error
     */
    @Deprecated("")
    @JvmStatic
    fun reportServer(throwable: Throwable?) {
        if (ANDROID) {
//            Crashlytics.logException(throwable);
        } else {
            System.err.println("Fatal exception : ")
            throwable!!.printStackTrace()
        }
    }


}
