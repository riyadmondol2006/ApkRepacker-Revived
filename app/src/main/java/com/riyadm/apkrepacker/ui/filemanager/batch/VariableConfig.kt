package com.riyadm.apkrepacker.ui.filemanager.batch

import android.content.Context

class VariableConfig {
    @JvmField
    var mStartAt = 0

    @JvmField
    var mContext: Context? = null

    class Builder {
        private val variableConfig = VariableConfig()

        fun build(): VariableConfig {
            return this.variableConfig
        }

        fun withNumberingStartAt(i: Int): Builder {
            this.variableConfig.mStartAt = i
            return this
        }

        fun setContext(context: Context?): Builder {
            this.variableConfig.mContext = context
            return this
        }
    }

    companion object {
        @JvmStatic
        fun builder(): Builder {
            return Builder()
        }
    }
}
