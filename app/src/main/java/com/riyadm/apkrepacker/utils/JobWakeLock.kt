package com.riyadm.apkrepacker.utils

import android.content.Context
import android.os.PowerManager
import com.riyadm.apkrepacker.utils.common.DLog

/**
 * A partial wake lock held while a long job (decompile, build, patch, project export/import) runs
 * in a foreground service, so the CPU keeps working when the screen turns off. A foreground
 * service alone keeps the process alive, but many devices (and Doze) still suspend the CPU.
 *
 * It always has a timeout, so a job that never reports back can't drain the battery.
 * Reference counted: several jobs can share one lock.
 */
class JobWakeLock(context: Context, tag: String) {

    private val lock: PowerManager.WakeLock? = try {
        (context.applicationContext.getSystemService(Context.POWER_SERVICE) as? PowerManager)
            ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ApkRepacker:$tag")
            ?.apply { setReferenceCounted(true) }
    } catch (e: Exception) {
        DLog.e("JobWakeLock", e)
        null
    }

    /** Takes the lock for at most [timeoutMs] (default 2 hours). */
    fun acquire(timeoutMs: Long = DEFAULT_TIMEOUT_MS) {
        try {
            lock?.acquire(timeoutMs)
        } catch (e: Exception) {
            // Missing WAKE_LOCK or a vendor restriction: the job still runs, just without the lock.
            DLog.e("JobWakeLock", e)
        }
    }

    fun release() {
        try {
            if (lock?.isHeld == true) lock.release()
        } catch (e: Exception) {
            // Already released by the timeout.
        }
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 2L * 60 * 60 * 1000
    }
}
