package com.riyadm.apkrepacker.ui.appslist

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import com.bumptech.glide.Glide
import com.bumptech.glide.Priority
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.data.DataFetcher
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory
import com.bumptech.glide.signature.ObjectKey

/**
 * An installed app's icon as a Glide model. The scan only lists packages; the icon is loaded from
 * PackageManager on Glide's threads when a row shows it, and Glide's memory cache keeps the recent
 * ones (as small bitmaps at the row's icon size). The version code is part of the cache key, so an
 * updated app gets its new icon.
 */
data class PackageIcon(val packageName: String, val versionCode: Long) {

    private class Loader(private val pm: PackageManager) : ModelLoader<PackageIcon, Drawable> {
        override fun buildLoadData(model: PackageIcon, width: Int, height: Int, options: Options) =
            ModelLoader.LoadData(ObjectKey(model), Fetcher(pm, model.packageName))

        override fun handles(model: PackageIcon) = true
    }

    private class Fetcher(private val pm: PackageManager, private val packageName: String) : DataFetcher<Drawable> {
        override fun loadData(priority: Priority, callback: DataFetcher.DataCallback<in Drawable>) {
            try {
                callback.onDataReady(pm.getApplicationIcon(packageName))
            } catch (e: PackageManager.NameNotFoundException) {
                // Uninstalled since the scan: the request shows its error drawable.
                callback.onLoadFailed(e)
            }
        }

        override fun cleanup() = Unit

        override fun cancel() = Unit

        override fun getDataClass(): Class<Drawable> = Drawable::class.java

        override fun getDataSource(): DataSource = DataSource.LOCAL
    }

    private class Factory(private val pm: PackageManager) : ModelLoaderFactory<PackageIcon, Drawable> {
        override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<PackageIcon, Drawable> = Loader(pm)

        override fun teardown() = Unit
    }

    companion object {
        @Volatile
        private var registered = false

        /**
         * Teaches Glide to load [PackageIcon]s (the app has no generated Glide module to do it in).
         * Call before the first request, on the main thread.
         */
        @JvmStatic
        fun register(context: Context) {
            if (registered) return
            synchronized(this) {
                if (registered) return
                val appContext = context.applicationContext
                Glide.get(appContext).registry
                    .prepend(PackageIcon::class.java, Drawable::class.java, Factory(appContext.packageManager))
                registered = true
            }
        }
    }
}
