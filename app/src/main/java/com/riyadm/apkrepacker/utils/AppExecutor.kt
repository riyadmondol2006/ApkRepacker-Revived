package com.riyadm.apkrepacker.utils

import java.util.concurrent.Executor
import java.util.concurrent.Executors

class AppExecutor private constructor() {

    val diskIO: Executor = Executors.newSingleThreadExecutor()

    companion object {
        private var instance: AppExecutor? = null

        @JvmStatic
        fun getInstance(): AppExecutor {
            if (instance == null) {
                instance = AppExecutor()
            }
            return instance!!
        }
    }
}
