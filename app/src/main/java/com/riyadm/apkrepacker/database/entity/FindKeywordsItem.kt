package com.riyadm.apkrepacker.database.entity

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName

@Keep
class FindKeywordsItem {

    @SerializedName("find_keyword")
    private var mKeyword: MutableList<String> = ArrayList()

    @SerializedName("replace_keyword")
    private var mReplaceKeyword: MutableList<String> = ArrayList()

    fun setKeyword(keyword: String) {
        mKeyword.add(keyword)
    }

    fun setReplaceKeyword(keyword: String) {
        mReplaceKeyword.add(keyword)
    }

    fun setKeyword(keyword: List<String>) {
        mKeyword.addAll(keyword)
    }

    fun setReplaceKeyword(keyword: List<String>) {
        mReplaceKeyword.addAll(keyword)
    }

    fun getKeyword(): MutableList<String> {
        return mKeyword
    }

    fun getReplaceKeyword(): MutableList<String> {
        return mReplaceKeyword
    }
}
