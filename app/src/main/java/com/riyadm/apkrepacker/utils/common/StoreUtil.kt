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

import com.jecelyin.common.utils.UIUtils

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import com.jecelyin.common.utils.DLog

/**
 * Created by Duy on 10-Jul-17.
 */
object StoreUtil {
    private const val TAG = "StoreUtil"

    /**
     * Go to Google Play app
     *
     * @param context - android context
     * @param appId   - an application id
     */
    @JvmStatic
    fun gotoPlayStore(context: Activity, appId: String?) {
        if (DLog.DEBUG)
            DLog.d(TAG, "gotoPlayStore() called with: context = [$context], appId = [$appId]")
        val uri = Uri.parse(String.format("market://details?id=%s", appId))
        val goToMarket = Intent(Intent.ACTION_VIEW, uri)
        // To count with Play market backstack, After pressing back button,
        // to taken back to our application, we need to add following flags to intent.
        goToMarket.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        try {
            context.startActivity(goToMarket)
        } catch (e: Exception) {
            if (DLog.DEBUG) DLog.e(e)
            try {
                val link = Uri.parse("http://play.google.com/store/apps/details?id=$appId")
                val intent = Intent(Intent.ACTION_VIEW, link)
                context.startActivity(intent)
            } catch (e2: Exception) {
                if (DLog.DEBUG) DLog.e(e2)
            }
        }
    }

    /**
     * Go to Google Play app
     *
     * @param context     - android context
     * @param appId       - an application id
     * @param requestCode - activity request code
     */
    @JvmStatic
    fun gotoPlayStore(context: Activity, appId: String?, requestCode: Int) {
        val uri = Uri.parse("market://details?id=$appId")
        val goToMarket = Intent(Intent.ACTION_VIEW, uri)
        // To count with Play market backstack, After pressing back button,
        // to taken back to our application, we need to add following flags to intent.
        goToMarket.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        try {
            context.startActivity(goToMarket)
        } catch (e: ActivityNotFoundException) {
            val uriString = "http://play.google.com/store/apps/details?id=$appId"
            gotoToLink(context, uriString, requestCode)
        }
    }

    /**
     * Use openBrowser
     *
     * @param context
     * @param uriString
     * @param request
     */
    @Deprecated("")
    @JvmStatic
    fun gotoToLink(context: Activity, uriString: String?, request: Int) {
        openBrowser(context, uriString, request)
    }

    @Suppress("DEPRECATION")
    @JvmStatic
    fun openBrowser(context: Activity, uriString: String?, request: Int) {
        val link = Uri.parse(uriString)
        val intent = Intent(Intent.ACTION_VIEW, link)
        try {
            context.startActivityForResult(intent, request)
        } catch (e: Exception) {
            UIUtils.toast(context, e.message)
        }
    }

    /**
     * [ShareUtil.shareApp]
     */
    @Deprecated("")
    @JvmStatic
    fun shareApp(context: Activity, appId: String?) {
        ShareUtil.shareApp(context, appId)
    }

    @JvmStatic
    fun shareThisApp(context: Activity) {
        ShareUtil.shareThisApp(context)
    }

    @JvmStatic
    fun moreApp(mainActivity: Activity) {
        val location = "https://play.google.com/store/apps/dev?id=7055567654109499514"
        val uri = Uri.parse(location)
        val goToMarket = Intent(Intent.ACTION_VIEW, uri)
        // To count with Play market backstack, After pressing back button,
        // to taken back to our application, we need to add following flags to intent.
        goToMarket.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        try {
            mainActivity.startActivity(goToMarket)
        } catch (e: ActivityNotFoundException) {
            mainActivity.startActivity(Intent(Intent.ACTION_VIEW,
                    Uri.parse(location)))
        }
    }

    @JvmStatic
    fun isAppInstalled(context: Context, appId: String): Boolean {
        return try {
            context.packageManager.getApplicationInfo(appId, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }
}
