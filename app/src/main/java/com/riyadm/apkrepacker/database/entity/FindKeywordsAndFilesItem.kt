package com.riyadm.apkrepacker.database.entity

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName

@Keep
class FindKeywordsAndFilesItem {

    @SerializedName("find_keyword")
    private var mKeyword: MutableList<String> = ArrayList()

    @SerializedName("find_files_keyword")
    private var mFiles: MutableList<String> = ArrayList()

    fun setKeyword(keyword: String) {
        mKeyword.add(keyword)
    }

    fun setFilesKeyword(keyword: String) {
        mFiles.add(keyword)
    }

    fun setKeyword(keyword: List<String>) {
        mKeyword.addAll(keyword)
    }

    fun setFilesKeyword(keyword: List<String>) {
        mFiles.addAll(keyword)
    }

    fun getKeyword(): MutableList<String> {
        return mKeyword
    }

    fun getFilesKeyword(): MutableList<String> {
        return mFiles
    }
}
