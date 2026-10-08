package com.riyadm.apkrepacker.adapter

import android.content.Context
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.annotation.AttrRes
import com.google.android.material.color.MaterialColors
import com.riyadm.apkrepacker.R
import com.riyadm.apkrepacker.databinding.LogViewItemBinding
import com.riyadm.apkrepacker.utils.StringUtils

/**
 * Rows of the decompile log, tinted by the logcat-style level letter the line starts with.
 * The colors are theme roles, so they stay readable in light, dark and dynamic themes.
 */
class LogAdapter(
    context: Context?,
    textViewId: Int,
    private val objects: List<String>?,
    private var textSizeSp: Int
) : ArrayAdapter<String>(requireNotNull(context), textViewId, objects.orEmpty()) {

    fun textSize(size: Int) {
        textSizeSp = size
    }

    // Reads the live list: callers append to it without notifying.
    override fun getCount(): Int = objects?.size ?: 0

    override fun getItem(position: Int): String? = objects?.get(position)

    override fun getItemId(position: Int): Long = position.toLong()

    @AttrRes
    private fun logColorAttr(line: String): Int = when (line.firstOrNull()) {
        'I' -> R.attr.colorPrimary
        'E', 'S' -> R.attr.colorError
        'W' -> R.attr.colorTertiary
        'D' -> R.attr.colorSecondary
        'A' -> R.attr.colorPrimary
        else -> R.attr.colorOnSurface
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val binding = convertView?.let { LogViewItemBinding.bind(it) }
            ?: LogViewItemBinding.inflate(LayoutInflater.from(context), parent, false)
        binding.root.minimumHeight = 0

        val data = objects?.getOrNull(position)
        if (!data.isNullOrEmpty()) {
            binding.logitemText.apply {
                text = data
                setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp.toFloat())
                setTextColor(MaterialColors.getColor(this, logColorAttr(data)))
                setOnClickListener { StringUtils.setClipboard(context, text.toString(), true) }
            }
        }
        return binding.root
    }
}
