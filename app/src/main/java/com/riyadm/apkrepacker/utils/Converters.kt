package com.riyadm.apkrepacker.utils

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object Converters {
    //  @TypeConverter
    @JvmStatic
    fun fromString(value: String?): ArrayList<String>? {
        val listType = object : TypeToken<ArrayList<String>>() {}.type
        return Gson().fromJson(value, listType)
    }

    // @TypeConverter
    @JvmStatic
    fun fromArrayList(list: ArrayList<String>?): String {
        return Gson().toJson(list)
    }
}
