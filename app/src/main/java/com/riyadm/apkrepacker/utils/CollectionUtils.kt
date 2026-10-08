package com.riyadm.apkrepacker.utils

import androidx.core.util.ObjectsCompat
import java.util.AbstractList
import java.util.Collections
import java.util.RandomAccess

object CollectionUtils {

    @JvmStatic
    fun <E> first(list: List<E>): E {
        return list[0]
    }

    @JvmStatic
    fun <E> last(list: List<E>): E {
        return list[list.size - 1]
    }

    @JvmStatic
    fun <E> firstOrNull(list: List<E>): E? {
        return getOrNull(list, 0)
    }

    @JvmStatic
    fun <E> lastOrNull(list: List<E>): E? {
        return getOrNull(list, list.size - 1)
    }

    @JvmStatic
    fun <E> getOrNull(list: List<E>, index: Int): E? {
        return if (index >= 0 && index < list.size) list[index] else null
    }

    @JvmStatic
    fun <E> first(collection: Collection<E>): E {
        return collection.iterator().next()
    }

    @JvmStatic
    fun <E> firstOrNull(collection: Collection<E>): E? {
        return if (collection.size > 0) first(collection) else null
    }

    @JvmStatic
    fun <E> peek(list: List<E>): E? {
        return lastOrNull(list)
    }

    @JvmStatic
    fun <E> push(list: MutableList<in E>, element: E) {
        list.add(element)
    }

    @JvmStatic
    fun <E> pop(list: MutableList<out E>): E {
        return list.removeAt(list.size - 1)
    }

    @JvmStatic
    fun <E> popOrNull(list: MutableList<out E>): E? {
        return if (!list.isEmpty()) pop(list) else null
    }

    @JvmStatic
    fun isEmpty(collection: Collection<*>?): Boolean {
        return collection == null || collection.isEmpty()
    }

    @JvmStatic
    fun size(collection: Collection<*>?): Int {
        return collection?.size ?: 0
    }

    @JvmStatic
    fun <E> startsWith(list1: List<E>, list2: List<E>): Boolean {
        val list2Size = list2.size
        if (list1.size < list2Size) {
            return false
        }
        for (i in 0 until list2Size) {
            if (!ObjectsCompat.equals(list1[i], list2[i])) {
                return false
            }
        }
        return true
    }

    @JvmStatic
    fun <E> endsWith(list1: List<E>, list2: List<E>): Boolean {
        val list1Size = list1.size
        val list2Size = list2.size
        if (list1Size < list2Size) {
            return false
        }
        for (i in 1..list2Size) {
            if (!ObjectsCompat.equals(list1[list1Size - i], list2[list2Size - i])) {
                return false
            }
        }
        return true
    }

    @JvmStatic
    fun <E> singletonLinkedSet(element: E?): LinkedHashSet<E?> {
        val set = LinkedHashSet<E?>(1, 1f)
        set.add(element)
        return set
    }

    @JvmStatic
    fun <E> singletonOrNull(element: E?): Set<E>? {
        return if (element != null) Collections.singleton(element) else null
    }

    @JvmStatic
    fun <E> singletonListOrNull(element: E?): List<E>? {
        return if (element != null) Collections.singletonList(element) else null
    }

    @JvmStatic
    fun <E> singletonOrEmpty(element: E?): Set<E> {
        return if (element != null) Collections.singleton(element) else Collections.emptySet()
    }

    @JvmStatic
    fun <E> singletonListOrEmpty(element: E?): List<E> {
        return if (element != null) Collections.singletonList(element) else Collections.emptyList()
    }

    @JvmStatic
    fun <E> difference(set1: Set<E>, set2: Set<E>): Set<E> {
        val result: MutableSet<E> = HashSet()
        difference(set1, set2, result)
        return result
    }

    @JvmStatic
    fun <E> symmetricDifference(set1: Set<E>, set2: Set<E>): Set<E> {
        val result: MutableSet<E> = HashSet()
        difference(set1, set2, result)
        difference(set2, set1, result)
        return result
    }

    private fun <E> difference(set1: Set<E>, set2: Set<E>, result: MutableSet<E>) {
        for (element in set1) {
            if (!set2.contains(element)) {
                result.add(element)
            }
        }
    }

    @JvmStatic
    fun <E> join(list1: List<E>, list2: List<E>): List<E> {
        return if (list1 is RandomAccess && list2 is RandomAccess) {
            RandomAccessJoinedList(list1, list2)
        } else {
            JoinedList(list1, list2)
        }
    }

    private open class JoinedList<E>(private val mList1: List<E>, private val mList2: List<E>) : AbstractList<E>() {

        override fun get(index: Int): E {
            val list1Size = mList1.size
            return if (index < list1Size) mList1[index] else mList2[index - list1Size]
        }

        override val size: Int
            get() = mList1.size + mList2.size
    }

    private class RandomAccessJoinedList<E>(list1: List<E>, list2: List<E>) : JoinedList<E>(list1, list2), RandomAccess

    @JvmStatic
    fun <E> toArrayList(list: List<E>): ArrayList<E> {
        return if (list is ArrayList<*>) list as ArrayList<E> else ArrayList(list)
    }

    @JvmStatic
    fun <E> toArrayListOrNull(list: List<E>?): ArrayList<E>? {
        return if (list != null) toArrayList(list) else null
    }
}
