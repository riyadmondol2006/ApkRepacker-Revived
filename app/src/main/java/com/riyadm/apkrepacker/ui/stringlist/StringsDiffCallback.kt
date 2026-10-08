package com.riyadm.apkrepacker.ui.stringlist

import androidx.recyclerview.widget.DiffUtil
import com.riyadm.apkrepacker.autotranslator.translator.TranslateItem

class StringsDiffCallback(
    private val oldList: List<TranslateItem>,
    private val newList: List<TranslateItem>,
) : DiffUtil.Callback() {

    override fun getOldListSize() = oldList.size

    override fun getNewListSize() = newList.size

    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int) =
        oldList[oldItemPosition].name == newList[newItemPosition].name

    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        val old = oldList[oldItemPosition]
        val new = newList[newItemPosition]
        return old === new || (old.originValue == new.originValue && old.translatedValue == new.translatedValue)
    }
}
