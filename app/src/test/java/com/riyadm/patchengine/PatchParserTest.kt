package com.riyadm.patchengine

import com.riyadm.patchengine.interfaces.IPatchContext
import com.riyadm.patchengine.rules.PatchRuleMatchReplace
import com.riyadm.patchengine.utils.PatchZip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class PatchParserTest {

    /** Collects error() calls; resolves no strings (the ids are enough here). */
    private class Ctx(private val root: String = "") : IPatchContext {
        val errors = ArrayList<String>()
        override fun getActivities(): List<String>? = null
        override fun getApplicationManifest(): String? = null
        override fun getDecodeRootPath(): String? = root
        override fun getLauncherActivities(): List<String>? = null
        override fun getPatchNames(): List<String>? = null
        override fun getSmaliFolders(): List<String>? = null
        override fun getVariableValue(str: String?): String? = null
        override fun error(resourceId: Int, vararg objArr: Any?) {
            errors += objArr.joinToString(" ")
        }
        override fun info(resourceId: Int, bold: Boolean, vararg objArr: Any?) {}
        override fun info(str: String?, bold: Boolean, vararg objArr: Any?) {}
        override fun patchFinished() {}
        override fun setVariableValue(key: String?, value: String?) {}
    }

    private fun parse(text: String, ctx: Ctx = Ctx()): Patch = PatchParser.parse(text.toByteArray().inputStream(), ctx)

    @Suppress("UNCHECKED_CAST")
    private fun field(rule: Any, name: String): Any? =
        rule.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(rule)

    @Test
    fun bomCrlfAndSpaces() {
        val ctx = Ctx()
        val patch = parse(
            "\uFEFF[MIN_ENGINE_VER]\r\n 2 \r\n[AUTHOR]\r\nNai\r\n\r\nkhair\r\n[PACKAGE]\r\n\t* \r\n\r\n" +
                "  [MATCH_REPLACE]\t\r\nTARGET:\r\n  smali*/*.smali \r\nMATCH:\r\n\r\n  a\\(b\\) \r\nREGEX:\r\n true\r\n" +
                "REPLACE:\r\n\tx\r\n  y  \r\n  [/MATCH_REPLACE]  \r\n",
            ctx,
        )
        assertEquals(emptyList<String>(), ctx.errors)
        assertEquals(2, patch.requiredEngine)
        assertEquals("Nai, khair", patch.author)
        assertEquals("*", patch.packagename)
        assertEquals(1, patch.rules.size)
        val rule = patch.rules[0]
        assertEquals(listOf("a\\(b\\)"), field(rule, "matches"))
        assertEquals(listOf("\tx", "  y  "), field(rule, "replaces"))
        assertEquals("smali*/*.smali", field(rule, "targetText"))
    }

    @Test
    fun missingEndTagAndUnknownRule() {
        val ctx = Ctx()
        val patch = parse(
            "[MATCH_REPLACE]\nTARGET:\na.xml\nMATCH:\nfoo\nREPLACE:\nbar\n" +
                "[MATCH_REPLACE]\nTARGET:\nb.xml\nMATCH:\nbaz\nREPLACE:\nqux\n[/MATCH_REPLACE]\n" +
                "[FOO]\nTARGET:\nx\n[/FOO]\n",
            ctx,
        )
        assertEquals(2, patch.rules.size)
        assertEquals("a.xml", field(patch.rules[0], "targetText"))
        assertEquals(listOf("bar"), field(patch.rules[0], "replaces"))
        assertEquals("b.xml", field(patch.rules[1], "targetText"))
        assertEquals(8, patch.rules[1].startLine)
        // One for the missing end tag, one for [FOO] (its body is skipped quietly).
        assertEquals(ctx.errors.toString(), 2, ctx.errors.size)
        assertTrue(ctx.errors[0], ctx.errors[0].startsWith("8 missing [/MATCH_REPLACE]"))
    }

    @Test
    fun badEngineVersionDoesNotAbort() {
        val ctx = Ctx()
        val patch = parse("[MIN_ENGINE_VER]\nabc\n[DUMMY]\n[/DUMMY]\n", ctx)
        assertEquals(1, ctx.errors.size)
        assertEquals(1, patch.rules.size)
    }

    /** Example patches (unzipped, one folder each, in $PATCH_EXAMPLES_DIR) parse without a single error. */
    @Test
    fun examplePatches() {
        val dir = File(System.getenv("PATCH_EXAMPLES_DIR") ?: return)
        val files = dir.listFiles()?.mapNotNull { File(it, "patch.txt").takeIf(File::isFile) }.orEmpty()
        for (f in files) {
            val ctx = Ctx()
            val patch = PatchParser.parse(f.inputStream(), ctx)
            assertEquals(f.path, emptyList<String>(), ctx.errors)
            assertTrue(f.path, patch.rules.isNotEmpty())
        }
    }

    @Test
    fun plainReplaceKeepsTheRestOfTheFile() {
        val root = Files.createTempDirectory("pp").toFile()
        File(root, "a.xml").writeText("x\r\n\r\n  <a/>\r\n\r\n\t<b/>  \r\n\r\nz")
        val ctx = Ctx(root.path)
        val patch = parse("[MATCH_REPLACE]\nTARGET:\na.xml\nMATCH:\n<a/>\n\n<b/>\nREGEX:\nfalse\nREPLACE:\n<c/>\n[/MATCH_REPLACE]\n", ctx)
        val helper = ProjectHelper().apply { mProject = root.path }
        val zipFile = File(root, "p.zip")
        ZipOutputStream(zipFile.outputStream()).use { it.putNextEntry(ZipEntry("patch.txt")); it.closeEntry() }
        ZipFile(zipFile).use { patch.rules[0].executeRule(helper, it, ctx) }
        assertEquals(emptyList<String>(), ctx.errors)
        assertEquals("x\r\n\r\n<c/>\r\n\r\nz", File(root, "a.xml").readText())
        root.deleteRecursively()
    }

    @Test
    fun patchTxtInAFolderWithWindowsNames() {
        val root = Files.createTempDirectory("pz").toFile()
        val zipFile = File(root, "p.zip")
        ZipOutputStream(zipFile.outputStream()).use { out ->
            for (name in listOf("MyPatch/", "MyPatch\\PATCH.TXT", "MyPatch\\smali\\Fix.smali", "__MACOSX/MyPatch/._patch.txt")) {
                out.putNextEntry(ZipEntry(name))
                if (!name.endsWith("/")) out.write(name.toByteArray())
                out.closeEntry()
            }
        }
        PatchZip.open(zipFile.path, root).use { opened ->
            assertNotNull(opened.patchTxt)
            assertEquals("patch.txt", opened.patchTxt!!.name)
            assertNotNull(opened.zip.getEntry("smali/Fix.smali"))
            assertEquals("MyPatch\\PATCH.TXT", opened.normalizedFrom)
        }
        // The normalized copy is deleted again.
        assertEquals(listOf("p.zip"), root.list()!!.toList())
        root.deleteRecursively()
    }

    @Test
    fun batchingKeyIgnoresInvalidRules() {
        val patch = parse("[MATCH_REPLACE]\nTARGET:\na\nMATCH:\nx\nREGEX:\ntrue\n[/MATCH_REPLACE]\n")
        assertEquals("a", (patch.rules[0] as PatchRuleMatchReplace).batchKey())
    }

    @Test
    fun launcherActivitiesWithAliasAndOtherPrefix() {
        val f = Files.createTempFile("m", ".xml").toFile()
        f.writeText(
            """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:a="http://schemas.android.com/apk/res/android" package="com.ex">
  <application a:name=".App">
    <activity a:name=".Main"/>
    <activity a:name="Other" a:exported="true">
      <intent-filter><action a:name="android.intent.action.VIEW"/><action a:name="android.intent.action.MAIN"/>
      <category a:name="android.intent.category.LAUNCHER"/></intent-filter>
    </activity>
    <activity-alias a:name=".Alias" a:targetActivity=".Main">
      <intent-filter><action a:name="android.intent.action.MAIN"/><category a:name="android.intent.category.LAUNCHER"/></intent-filter>
    </activity-alias>
    <activity a:name="org.x.Hidden" a:exported="false">
      <intent-filter><action a:name="android.intent.action.MAIN"/><category a:name="android.intent.category.LAUNCHER"/></intent-filter>
    </activity>
  </application>
</manifest>"""
        )
        val (activities, launchers) = com.riyadm.apkrepacker.service.PatchService.readComponents(f)
        assertEquals(listOf("com.ex.Main", "com.ex.Other", "org.x.Hidden"), activities)
        assertEquals(listOf("com.ex.Other", "com.ex.Main"), launchers)
        f.delete()
    }
}
