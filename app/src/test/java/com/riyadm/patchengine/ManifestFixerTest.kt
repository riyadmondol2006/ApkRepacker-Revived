package com.riyadm.patchengine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ManifestFixerTest {

    private fun manifest(body: String, prefix: String = "android") =
        """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:$prefix="http://schemas.android.com/apk/res/android" package="a.b">
    <application>
$body
    </application>
</manifest>
"""

    @Test
    fun addsExportedOnlyWhereAFilterHasNone() {
        val text = manifest(
            """        <activity android:name=".Main" android:exported="false"><intent-filter><action android:name="x"/></intent-filter></activity>
        <activity android:name=".Splash" android:theme="@style/S">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
            </intent-filter>
        </activity>
        <activity android:name=".Plain"/>
        <activity android:name=".NoFilter"><meta-data android:name="m" android:value="v"/></activity>
        <!-- <activity android:name=".Commented"><intent-filter/></activity> -->
        <receiver android:name=".R"><intent-filter android:priority="1"><action android:name="y"/></intent-filter></receiver>"""
        )
        val (fixed, count) = ManifestFixer.addMissingExported(text)!!
        assertEquals(2, count)
        assertEquals(text.replace("<activity android:name=\".Splash\"", "<activity android:exported=\"true\" android:name=\".Splash\"")
            .replace("<receiver android:name=\".R\"", "<receiver android:exported=\"true\" android:name=\".R\""), fixed)
    }

    @Test
    fun usesTheManifestsOwnPrefixAndLeavesValidManifestsAlone() {
        val text = manifest("""        <activity a:name=".S"><intent-filter><action a:name="x"/></intent-filter></activity>""", prefix = "a")
        assertEquals(text.replace("<activity a:name", "<activity a:exported=\"true\" a:name"), ManifestFixer.addMissingExported(text)!!.first)
        assertNull(ManifestFixer.addMissingExported(manifest("""        <service android:exported="true" android:name=".S"><intent-filter/></service>""")))
    }

    @Test
    fun nestedFilterInsideAnotherElementDoesNotCount() {
        assertNull(ManifestFixer.addMissingExported(manifest("""        <activity android:name=".A"><other><intent-filter/></other></activity>""")))
    }

    @Test
    fun spacedSelfClosingTagInAPatchMatchesApktoolOutput() {
        val regex = Regex(PatchRule.regexSource("""  <category android:name="android.intent.category.LAUNCHER" />  """))
        assertEquals(true, regex.containsMatchIn("""<category android:name="android.intent.category.LAUNCHER"/>"""))
        assertEquals(true, regex.containsMatchIn("""<category android:name="android.intent.category.LAUNCHER" />"""))
        assertEquals("a\\s*/>", PatchRule.regexSource("a   />"))
    }
}
