package com.riyadm.apkrepacker

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.preference.PreferenceManager
import com.jecelyin.common.utils.UIUtils
import com.nostra13.universalimageloader.core.ImageLoader
import com.nostra13.universalimageloader.core.ImageLoaderConfiguration
import com.riyadm.apkrepacker.activity.MainActivity
import com.riyadm.apkrepacker.ui.filemanager.misc.ThumbnailHelper.imageDecoder
import com.riyadm.apkrepacker.ui.filemanager.utils.CopyHelper
import com.riyadm.apkrepacker.utils.ExceptionHandler
import com.riyadm.apkrepacker.utils.JksSupport
import com.riyadm.apkrepacker.utils.NotificationHelper
import dalvik.system.ZipPathValidator

class App : Application() {

    /**
     * Property form for Kotlin callers (`App.get().copyHelper`); [getCopyHelper] is the
     * explicit-call form. The property getter is renamed on the JVM to avoid a clash.
     */
    @get:JvmName("copyHelperProperty")
    var copyHelper: CopyHelper? = null
        private set

    fun getCopyHelper(): CopyHelper? = copyHelper

    val preferences: SharedPreferences by lazy { PreferenceManager.getDefaultSharedPreferences(this) }

    init {
        instance = this
    }

    override fun onCreate() {
        super.onCreate()
        allowUnsafeZipEntryNames()
        copyHelper = CopyHelper()
        NotificationHelper.createChannels(this)
        ExceptionHandler.get(this).start()
        JksSupport.install()
        UIUtils.install(this)
        // Snackbars on the main screen sit above the bottom navigation bar.
        UIUtils.anchorResolver = { activity -> (activity as? MainActivity)?.snackbarAnchor }
        initImageLoader()
    }

    /**
     * Since targetSdk 34, ZipFile/ZipInputStream throw ZipException for entry names that contain
     * ".." or start with "/". Obfuscated APKs use such names on purpose and this app has to open
     * them, so turn the platform check off. Code that extracts entries to disk guards against path
     * traversal itself (see utils/SafeZip).
     */
    private fun allowUnsafeZipEntryNames() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ZipPathValidator.clearCallback()
        }
    }

    private fun initImageLoader() {
        val config = ImageLoaderConfiguration.Builder(this)
            .diskCacheSize(DISK_CACHE_BYTES)
            .imageDecoder(imageDecoder(applicationContext))
            .build()
        ImageLoader.getInstance().init(config)
    }

    companion object {
        private const val DISK_CACHE_BYTES = 10_240_000 // ~10MB

        private var mContext: Context? = null
        private var instance: App? = null

        /**
         * The context set by [setContext] (the main activity), or the application when MainActivity
         * hasn't run in this process (e.g. another activity restored after process death).
         */
        @JvmStatic
        fun getContext(): Context = mContext ?: get()

        @JvmStatic
        fun setContext(context: Context?) {
            mContext = context
        }

        @JvmStatic
        fun get(): App = instance ?: App().also { instance = it }
    }
}
