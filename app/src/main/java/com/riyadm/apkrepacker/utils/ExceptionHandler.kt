package com.riyadm.apkrepacker.utils

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.util.Log
import com.riyadm.apkrepacker.activity.ExceptionActivity
import java.io.PrintWriter
import java.io.StringWriter
import java.io.Writer
import java.util.Date
import kotlin.system.exitProcess

class ExceptionHandler private constructor(private val application: Application) : Thread.UncaughtExceptionHandler {
    private var previousHandler: Thread.UncaughtExceptionHandler? = null

    private var versionName: String? = null
    private var versionBuild: String? = null
    private var packageName: String? = null
    private var phoneModel: String? = null
    private var androidVersion: String? = null
    private var board: String? = null
    private var brand: String? = null
    private var device: String? = null
    private var display: String? = null
    private var fingerPrint: String? = null
    private var host: String? = null
    private var id: String? = null
    private var manufacturer: String? = null
    private var model: String? = null
    private var product: String? = null
    private var tags: String? = null
    private var time: Long = 0
    private var type: String? = null
    private var user: String? = null
    private val customParameters = HashMap<String, String>()

    fun start() {
        previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(this)
    }

    private fun createCustomInfoString(): String {
        val customInfo = StringBuilder()
        for (currentKey in customParameters.keys) {
            val currentVal = customParameters[currentKey]
            customInfo.append(currentKey).append(" = ").append(currentVal).append("\n")
        }
        return customInfo.toString()
    }

    @Suppress("DEPRECATION")
    private fun getAvailableInternalMemorySize(): Long {
        val path = Environment.getDataDirectory()
        val stat = StatFs(path.path)
        val blockSize = stat.blockSize.toLong()
        val availableBlocks = stat.availableBlocks.toLong()
        return availableBlocks * blockSize / (1024 * 1024)
    }

    @Suppress("DEPRECATION")
    private fun getTotalInternalMemorySize(): Long {
        val path = Environment.getDataDirectory()
        val stat = StatFs(path.path)
        val blockSize = stat.blockSize.toLong()
        val totalBlocks = stat.blockCount.toLong()
        return totalBlocks * blockSize / (1024 * 1024)
    }

    @Suppress("DEPRECATION")
    private fun recordInformations(context: Context) {
        try {
            val pm = context.packageManager
            // Version
            val pi = pm.getPackageInfo(context.packageName, 0)
            versionName = pi.versionName
            versionBuild = (if (AppUtils.apiIsAtLeast(Build.VERSION_CODES.P)) pi.longVersionCode else pi.versionCode.toLong()).toString()
            //buildNumber = currentVersionNumber(context);
            // Package name
            packageName = pi.packageName

            // Device model
            phoneModel = Build.MODEL
            // Android version
            androidVersion = Build.VERSION.RELEASE

            board = Build.BOARD
            brand = Build.BRAND
            device = Build.DEVICE
            display = Build.DISPLAY
            //fingerPrint = Build.FINGERPRINT;
            host = Build.HOST
            id = Build.ID
            model = Build.MODEL
            product = Build.PRODUCT
            manufacturer = Build.MANUFACTURER
            tags = Build.TAGS
            time = Build.TIME
            type = Build.TYPE
            user = Build.USER

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createInformationString(): String {
        recordInformations(application)
        val infoStringBuffer = StringBuilder()
        infoStringBuffer.append("\nVERSION\t\t: ").append(String.format("%1s(%2s)", versionName, versionBuild))
        infoStringBuffer.append("\nPACKAGE      : ").append(packageName)
        //  infoStringBuffer.append("\nFILE-PATH    : ").append(filePath);
        infoStringBuffer.append("\nPHONE-MODEL  : ").append(phoneModel)
        infoStringBuffer.append("\nANDROID_VERSION : ").append(androidVersion)
        infoStringBuffer.append("\nBOARD        : ").append(board)
        infoStringBuffer.append("\nBRAND        : ").append(brand)
        infoStringBuffer.append("\nDEVICE       : ").append(device)
        infoStringBuffer.append("\nTYPE         : ").append(display)
        //infoStringBuffer.append("\nFINGER-PRINT : ").append(fingerPrint);
        infoStringBuffer.append("\nHOST         : ").append(host)
        infoStringBuffer.append("\nID           : ").append(id)
        infoStringBuffer.append("\nMODEL        : ").append(model)
        infoStringBuffer.append("\nPRODUCT      : ").append(product)
        infoStringBuffer.append("\nMANUFACTURER : ").append(manufacturer)
        infoStringBuffer.append("\nTAGS         : ").append(tags)
        infoStringBuffer.append("\nTIME         : ").append(time)
        infoStringBuffer.append("\nUSER         : ").append(type)
        infoStringBuffer.append("\nDISPLAY      : ").append(user)
        infoStringBuffer.append("\nTOTAL-INTERNAL-MEMORY     : ").append(getTotalInternalMemorySize().toString() + " mb")
        infoStringBuffer.append("\nAVAILABLE-INTERNAL-MEMORY : ").append(getAvailableInternalMemorySize().toString() + " mb")

        return infoStringBuffer.toString()
    }

    override fun uncaughtException(t: Thread, e: Throwable) {
        showLog("====uncaughtException")

        val reportStringBuffer = StringBuilder()
        reportStringBuffer.append("Error Report collected on : ").append(Date().toString())
        reportStringBuffer.append("\n\nDevice Informations :\n==============")
        reportStringBuffer.append(createInformationString())
        val customInfo = createCustomInfoString()
        if (customInfo != "") {
            reportStringBuffer.append("\n\nCustom Informations :\n==============\n")
            reportStringBuffer.append(customInfo)
        }

        reportStringBuffer.append("\n\nStack :\n==============\n")
        val result: Writer = StringWriter()
        val printWriter = PrintWriter(result)
        e.printStackTrace(printWriter)
        reportStringBuffer.append(result.toString())

        reportStringBuffer.append("\nCause :\n==============")
        // If the exception was thrown in a background thread inside
        // AsyncTask, then the actual exception can be found with getCause
        var cause = e.cause
        while (cause != null) {
            cause.printStackTrace(printWriter)
            reportStringBuffer.append(result.toString())
            cause = cause.cause
        }
        printWriter.close()
        reportStringBuffer.append("\n\n**** End of current Report ***")
        showLog("====uncaughtException \n Report: $reportStringBuffer")
        //записываем лог в файл
        // writeToFile(reportStringBuffer.toString());

        //вызываем активити с диалогом
        val intent = Intent(application.applicationContext, ExceptionActivity::class.java)
        intent.putExtra("mError", reportStringBuffer.toString())
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        application.applicationContext.startActivity(intent)
        //  Intent intent = new Intent(application, ErrorReporterActivity.class);
        //  intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        //  application.startActivity(intent);

        //  previousHandler.uncaughtException(t, e);
        android.os.Process.killProcess(android.os.Process.myPid())
        exitProcess(0)
    }

    private fun showLog(msg: String) {
        if (DEBUGABLE) Log.i(TAG, msg)
    }

    companion object {
        private val TAG = ExceptionHandler::class.java.simpleName
        private const val DEBUGABLE = false
        private var sInstance: ExceptionHandler? = null

        @JvmStatic
        fun get(application: Application): ExceptionHandler {
            if (sInstance == null)
                sInstance = ExceptionHandler(application)
            return sInstance!!
        }
    }
}
