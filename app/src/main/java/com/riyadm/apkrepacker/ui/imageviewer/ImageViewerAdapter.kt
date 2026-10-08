package com.riyadm.apkrepacker.ui.imageviewer

import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.riyadm.apkrepacker.R
import com.riyadm.apkrepacker.databinding.ImageViewerItemBinding
import com.riyadm.apkrepacker.utils.FileUtil
import java.io.File

/** Pages of the image viewer: a zoomable bitmap, a rendered vector XML, or an error message. */
class ImageViewerAdapter(private val onTap: () -> Unit) :
    ViewPagerAdapter<String, ImageViewerAdapter.PageHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        PageHolder(ImageViewerItemBinding.inflate(LayoutInflater.from(parent.context), parent, false), onTap)

    override fun onBindViewHolder(holder: PageHolder, position: Int) = holder.bind(items[position])

    override fun onViewRecycled(holder: PageHolder) = holder.clear()

    class PageHolder(
        private val binding: ImageViewerItemBinding,
        onTap: () -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.imageView.setOnViewTapListener { _, _, _ -> onTap() }
            binding.vectorView.setOnClickListener { onTap() }
            binding.root.setOnClickListener { onTap() }
        }

        fun bind(path: String) {
            clear()
            val file = File(path)
            when (FileUtil.FileType.getFileType(file)) {
                FileUtil.FileType.IMAGE -> loadImage(file)
                FileUtil.FileType.XML -> loadVector(file)
                else -> showError(null)
            }
        }

        fun clear() {
            Glide.with(binding.root).clear(binding.imageView)
            binding.imageView.setImageDrawable(null)
            binding.imageView.isVisible = false
            binding.vectorView.isVisible = false
            binding.error.isVisible = false
            binding.progress.isVisible = false
        }

        private fun loadImage(file: File) {
            binding.progress.isVisible = true
            Glide.with(binding.root)
                .load(file)
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                .skipMemoryCache(true)
                .dontTransform()
                .transition(DrawableTransitionOptions.withCrossFade())
                .listener(object : RequestListener<Drawable> {
                    override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable>, isFirstResource: Boolean): Boolean {
                        showError(e)
                        return false
                    }

                    override fun onResourceReady(resource: Drawable, model: Any, target: Target<Drawable>?, dataSource: DataSource, isFirstResource: Boolean): Boolean {
                        binding.progress.isVisible = false
                        binding.imageView.isVisible = true
                        return false
                    }
                })
                .into(binding.imageView)
        }

        private fun loadVector(file: File) {
            binding.vectorView.setVectorFile(file)
            if (binding.vectorView.isVector) {
                binding.vectorView.isVisible = true
            } else {
                showError(null)
            }
        }

        private fun showError(e: Exception?) {
            binding.progress.isVisible = false
            binding.error.text = e?.localizedMessage ?: binding.root.context.getString(R.string.viewer_load_failed)
            binding.error.isVisible = true
        }
    }
}
