package com.riyadm.apkrepacker.ui.findresult

import android.annotation.SuppressLint
import com.thoughtbot.expandablerecyclerview.models.ExpandableGroup

/** A file with its matching lines; expands in the string search results. */
@SuppressLint("ParcelCreator")
class ParentData(title: String?, items: List<ChildData>?) : ExpandableGroup<ChildData>(title, items)
