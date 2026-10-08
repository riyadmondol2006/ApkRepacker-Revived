package com.riyadm.apkrepacker.utils

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.inputmethod.InputMethodManager
import androidx.fragment.app.Fragment
import com.jecelyin.common.utils.UIUtils
import com.riyadm.apkrepacker.R
import com.riyadm.apkrepacker.utils.grep.ExtGrep

object StringUtils {

    @JvmField
    var extGreps: ExtGrep? = null
    @JvmField
    var bundle: Bundle? = null

    @JvmStatic
    fun setClipboard(context: Context?, text: String?, showToast: Boolean) {
        val clipboard = context!!.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager?
        val clip = ClipData.newPlainText("", text)
        clipboard!!.setPrimaryClip(clip)
        // Android 13+ shows its own confirmation for clipboard writes.
        if (showToast && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU)
            UIUtils.toast(context, context.getString(R.string.toast_copy_to_clipboard))
    }


    @JvmStatic
    fun getClipboard(context: Context): CharSequence? {
        val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager?
            ?: return null
        // Examines the item on the clipboard. If getText() does not return null,
        // the clip item contains the
        // text. Assumes that this application can only handle one item at a time.
        // Null since API 29 when the app isn't focused, and when the clipboard is empty.
        val clip = clipboardManager.primaryClip ?: return null
        if (clip.itemCount == 0) return null
        // Gets the clipboard as text.
        return clip.getItemAt(0).text
    }

    @JvmStatic
    fun setGreap(extGrep: ExtGrep?) {
        extGreps = extGrep
    }

    @JvmStatic
    fun setBundle(value: Bundle?) {
        bundle = value
    }

    @JvmStatic
    fun hideKeyboard(activity: Activity) {
        val inputMethodManager = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager?
        inputMethodManager!!.hideSoftInputFromWindow(activity.window.decorView.windowToken, 0)
    }

    @JvmStatic
    fun hideKeyboard(fragment: Fragment) {
        val activity: Activity? = fragment.activity
        if (activity != null) {
            hideKeyboard(activity)
            return
        }

        val inputMethodManager = fragment.requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager?
        inputMethodManager!!.hideSoftInputFromWindow(fragment.requireView().windowToken, 0)
    }

    // Get the google language code
    // Convert -zh-rCN to zh-CN
    @JvmStatic
    fun getGoogleLangCode(targetLanguageCode: String?): String {
        var code = targetLanguageCode!!.substring(1)
        val pos = code.indexOf("-")
        if (pos != -1) {
            code = code.substring(0, pos + 1) + code.substring(pos + 2)
        }
        return code
    }
}
