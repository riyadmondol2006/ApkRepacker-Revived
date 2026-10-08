package com.riyadm.apkrepacker.utils

/**
 * How many worker threads a bulk file job (copying a project, patching thousands of files) should
 * use on this device. More threads hide storage latency, but every worker holds file contents in
 * memory, so the count follows the app's real heap limit as well as the core count: a flagship
 * gets 8, a low-RAM phone with a small heap gets 2, and nothing is ever swamped.
 */
object WorkerThreads {

    /** Heap a single worker is assumed to need (a large smali file plus a copy while patching it). */
    private const val HEAP_PER_WORKER_MB = 48

    private const val MAX_THREADS = 8

    @JvmStatic
    fun count(): Int {
        val cores = Runtime.getRuntime().availableProcessors()
        val heapMb = (Runtime.getRuntime().maxMemory() / (1024 * 1024)).toInt()
        val byMemory = (heapMb / HEAP_PER_WORKER_MB).coerceAtLeast(1)
        // Twice the cores: workers spend most of their time waiting on storage.
        return minOf(cores * 2, MAX_THREADS, byMemory).coerceAtLeast(2)
    }
}
