package com.riyadm.apkrepacker.ui.appslist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LabelCacheTest {

    @Test
    fun reusesLabelWhileStampIsUnchanged() {
        val cache = LabelCache().apply { useLocales("en-US") }
        cache.put("com.example", 100L, "Example")
        assertEquals("Example", cache.get("com.example", 100L))
    }

    @Test
    fun updatedPackageNeedsANewLabel() {
        val cache = LabelCache().apply { useLocales("en-US") }
        cache.put("com.example", 100L, "Example")
        assertNull(cache.get("com.example", 200L))
        cache.put("com.example", 200L, "Example 2")
        assertEquals("Example 2", cache.get("com.example", 200L))
    }

    @Test
    fun localeChangeDropsEveryLabel() {
        val cache = LabelCache().apply { useLocales("en-US") }
        cache.put("com.example", 100L, "Example")
        cache.useLocales("en-US")
        assertEquals("Example", cache.get("com.example", 100L))
        cache.useLocales("de-DE")
        assertNull(cache.get("com.example", 100L))
        assertEquals(0, cache.size)
    }

    @Test
    fun retainAllForgetsUninstalledPackages() {
        val cache = LabelCache().apply { useLocales("en-US") }
        cache.put("com.kept", 1L, "Kept")
        cache.put("com.removed", 1L, "Removed")
        cache.retainAll(setOf("com.kept", "com.new"))
        assertEquals("Kept", cache.get("com.kept", 1L))
        assertNull(cache.get("com.removed", 1L))
        assertEquals(1, cache.size)
    }
}
