package com.riyadm.apkrepacker.activity

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import com.google.android.material.snackbar.Snackbar
import com.riyadm.apkrepacker.BuildConfig
import com.riyadm.apkrepacker.R
import com.riyadm.apkrepacker.databinding.ActivityExceptionBinding
import com.riyadm.apkrepacker.utils.StringUtils

/** Crash screen shown by ExceptionHandler with the report in the `mError` extra. */
class ExceptionActivity : BaseActivity() {

    private lateinit var binding: ActivityExceptionBinding
    private var error: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        error = intent.getStringExtra(EXTRA_ERROR).orEmpty()

        binding = ActivityExceptionBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.appbar.toolbar)

        binding.errorView.text = error
        binding.btnCopyLog.setOnClickListener {
            StringUtils.setClipboard(this, error, true)
        }
        binding.btnShareLog.setOnClickListener { shareReport() }
        binding.btnRestart.setOnClickListener { restartApp() }

        // Release builds offer to mail the report right away (once, not again on rotation).
        if (!BuildConfig.DEBUG && savedInstanceState == null) sendErrorMail()
    }

    private fun shareReport() {
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, EMAIL_SUBJECT)
            .putExtra(Intent.EXTRA_TEXT, error)
        startChooser(Intent.createChooser(send, getString(R.string.crash_share_chooser)))
    }

    private fun sendErrorMail() {
        val mail = Intent(Intent.ACTION_SEND)
            .setType("message/rfc822")
            .putExtra(Intent.EXTRA_EMAIL, arrayOf(REPORT_EMAIL))
            .putExtra(Intent.EXTRA_SUBJECT, EMAIL_SUBJECT)
            .putExtra(Intent.EXTRA_TEXT, "\n\n$error\n\n")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startChooser(Intent.createChooser(mail, getString(R.string.title_select_mail_app)))
    }

    private fun startChooser(chooser: Intent) {
        try {
            startActivity(chooser)
        } catch (_: ActivityNotFoundException) {
            Snackbar.make(binding.root, R.string.about_not_found_email, Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun restartApp() {
        packageManager.getLaunchIntentForPackage(packageName)?.let { launch ->
            startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        }
        finishAffinity()
    }

    private companion object {
        const val EXTRA_ERROR = "mError"
        const val EMAIL_SUBJECT = "Apk Repacker: Crash Report"
        const val REPORT_EMAIL = "riyadmondol2006@gmail.com"
    }
}
