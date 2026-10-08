package com.riyadm.apkrepacker.activity

import android.content.Intent
import android.os.Bundle

/**
 * Entry point other screens use to open a file in the code editor ("filePath" / "offset"
 * extras). It forwards to [TextEditorActivity], which is the editor itself.
 */
class CodeEditorActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(Intent(this, TextEditorActivity::class.java).apply { putExtras(intent) })
        finish()
    }
}
