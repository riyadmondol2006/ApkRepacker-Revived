package com.riyadm.apkrepacker.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.riyadm.apkrepacker.databinding.ItemThemeBinding
import com.riyadm.apkrepacker.ui.themelist.ThemeView
import com.riyadm.apkrepacker.utils.Theme

/** Grid of live theme previews; the [selected] one is outlined with a check badge. */
class ThemeAdapter(context: Context) : RecyclerView.Adapter<ThemeAdapter.ViewHolder>() {

    private val inflater = LayoutInflater.from(context)
    private var themes: List<Theme.ThemeDescriptor> = emptyList()
    private var listener: OnThemeInteractionListener? = null

    var selected: Theme.ThemeDescriptor? = null
        set(value) {
            if (field == value) return
            field = value
            notifyItemRangeChanged(0, itemCount, PAYLOAD_SELECTION)
        }

    init {
        setHasStableIds(true)
    }

    fun setThemes(themes: List<Theme.ThemeDescriptor>?) {
        this.themes = themes.orEmpty()
        @Suppress("NotifyDataSetChanged")
        notifyDataSetChanged()
    }

    fun setOnThemeInteractionListener(listener: OnThemeInteractionListener?) {
        this.listener = listener
    }

    override fun getItemCount(): Int = themes.size

    override fun getItemId(position: Int): Long = themes[position].id.toLong()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(ItemThemeBinding.inflate(inflater, parent, false).root)

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(themes[position])
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.contains(PAYLOAD_SELECTION)) {
            holder.themeView.setThemeSelected(themes[position] == selected)
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    inner class ViewHolder(val themeView: ThemeView) : RecyclerView.ViewHolder(themeView) {

        init {
            themeView.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) listener?.onThemeClicked(themes[position])
            }
        }

        fun bind(theme: Theme.ThemeDescriptor) {
            themeView.setTheme(theme)
            themeView.setThemeSelected(theme == selected)
        }
    }

    fun interface OnThemeInteractionListener {
        fun onThemeClicked(theme: Theme.ThemeDescriptor?)
    }

    private companion object {
        const val PAYLOAD_SELECTION = "selection"
    }
}
