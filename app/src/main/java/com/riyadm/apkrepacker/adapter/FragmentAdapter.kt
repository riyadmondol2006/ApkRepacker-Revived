package com.riyadm.apkrepacker.adapter

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

/** ViewPager2 adapter over a fixed list of fragments with matching page titles. */
class FragmentAdapter(
    activity: FragmentActivity,
    private val fragments: List<Fragment>,
    private val titles: List<String>,
) : FragmentStateAdapter(activity) {

    fun getPageTitle(position: Int): CharSequence = titles[position]

    override fun createFragment(position: Int): Fragment = fragments[position]

    override fun getItemCount(): Int = fragments.size
}
