package com.riyadm.apkrepacker.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.riyadm.apkrepacker.databinding.ListItemErrorBinding
import com.riyadm.apkrepacker.ui.motion.pressSpring
import java.io.File

/** Build output lines; lines of the form `path:line:message` pointing at an existing file get a preview button. */
class ErrorAdapter : RecyclerView.Adapter<ErrorAdapter.ViewHolder>() {

    private val mErrorLines = mutableListOf<String>()
    private var itemInteractionListener: OnItemInteractionListener? = null

    val errorLines: List<String>
        get() = mErrorLines

    fun setItemInteractionListener(itemInteractionListener: OnItemInteractionListener?) {
        this.itemInteractionListener = itemInteractionListener
    }

    fun updateMessage(errorMessage: String?) {
        val lines = errorMessage.orEmpty().split(LINE_BREAK).filter { it.isNotEmpty() }
        if (lines.isEmpty()) return
        val start = mErrorLines.size
        mErrorLines.addAll(lines)
        notifyItemRangeInserted(start, lines.size)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(ListItemErrorBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bindTo(mErrorLines[position], itemInteractionListener)
    }

    override fun getItemCount(): Int = mErrorLines.size

    interface OnItemInteractionListener {
        fun OnItemClicked(filePath: String?, lineNumber: Int)

        fun OnItemLongClick(message: String?)
    }

    class ViewHolder(private val binding: ListItemErrorBinding) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.pressSpring()
        }

        fun bindTo(message: String, listener: OnItemInteractionListener?) {
            val firstColon = message.indexOf(':')
            val filePath = if (firstColon != -1) message.substring(0, firstColon) else null
            val viewable = filePath != null && File(filePath).exists()

            binding.btnPreview.isVisible = viewable
            if (viewable) {
                val secondColon = message.indexOf(':', firstColon + 1)
                val lineNumber = if (secondColon != -1) {
                    message.substring(firstColon + 1, secondColon).toIntOrNull() ?: -1
                } else {
                    -1
                }
                binding.btnPreview.setOnClickListener {
                    if (lineNumber > 0) listener?.OnItemClicked(filePath, lineNumber)
                }
            } else {
                binding.btnPreview.setOnClickListener(null)
            }

            binding.message.text = message
            binding.root.setOnLongClickListener {
                listener?.OnItemLongClick(message)
                true
            }
        }
    }

    private companion object {
        val LINE_BREAK = Regex("\\r?\\n")
    }
}
