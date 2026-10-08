/*
 * Copyright (C) 2018 Tran Le Duy
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.riyadm.apkrepacker.database

import android.content.Context
import android.text.TextUtils
import com.google.gson.GsonBuilder
import com.riyadm.apkrepacker.database.entity.FindKeywordsAndFilesItem
import com.riyadm.apkrepacker.database.entity.FindKeywordsItem
import com.riyadm.apkrepacker.database.entity.Project
import com.riyadm.apkrepacker.database.entity.RecentFileItem
import com.riyadm.apkrepacker.utils.ProjectUtils
import com.riyadm.apkrepacker.utils.common.DLog
import org.apache.commons.io.IOUtils
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FileWriter
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.Collections

class JsonDatabase(context: Context) : ITabDatabase {

    private val RECENT_FILES_DATABASE_NAME = "recent_files.json"
    private val KEYWORDS_DATABASE_NAME = "keywords.json"
    private val KEYWORDS_FILES_DATABASE_NAME = "keywords_files.json"
    private val KEYWORDS_PROJECT_DATABASE_NAME = "project.json"
    private val mHelper: RecentFileJsonHelper = RecentFileJsonHelper()
    private val mContext: Context = context
    private val mProjectDatabasePath: File =
        File(ProjectUtils.getProjectPath() + File.separator + ".database" + File.separator)

    override fun addRecentFile(path: String?, encoding: String?) {
        if (TextUtils.isEmpty(path))
            return
        try {
            val database = getRecentFileDatabase()
            val jsonItem: JSONObject
            val recentFile: RecentFileItem
            if (database.has(path)) {
                jsonItem = database.getJSONObject(path!!)
                recentFile = mHelper.read(jsonItem)
                recentFile.setPath(path)
                recentFile.setLastOpen(true)
            } else {
                jsonItem = JSONObject()
                recentFile = RecentFileItem()
                recentFile.setPath(path)
                recentFile.setEncoding(encoding)
                recentFile.setLastOpen(true)
                recentFile.setTime(System.currentTimeMillis())
                database.put(path!!, jsonItem)
            }
            mHelper.write(jsonItem, recentFile)
            saveRecentFileDatabase(database)

        } catch (e: JSONException) {
            e.printStackTrace()
        }
    }

    override fun updateRecentFile(path: String?, lastOpen: Boolean) {
        try {
            val database = getRecentFileDatabase()
            val jsonItem: JSONObject
            val recentFile: RecentFileItem
            if (database.has(path)) {
                jsonItem = database.getJSONObject(path!!)
                recentFile = mHelper.read(jsonItem)
                recentFile.setPath(path)
                recentFile.setLastOpen(lastOpen)

                mHelper.write(jsonItem, recentFile)
                saveRecentFileDatabase(database)
            }
        } catch (e: JSONException) {
            e.printStackTrace()
        }
    }

    override fun updateRecentFile(path: String?, encoding: String?, offset: Int) {
        try {
            val database = getRecentFileDatabase()
            val jsonItem: JSONObject
            val recentFile: RecentFileItem
            if (database.has(path)) {
                jsonItem = database.getJSONObject(path!!)
                recentFile = mHelper.read(jsonItem)
                recentFile.setPath(path)
                recentFile.setOffset(offset)
                mHelper.write(jsonItem, recentFile)
                saveRecentFileDatabase(database)
            } else {
                addRecentFile(path, encoding)
            }
        } catch (e: JSONException) {
            e.printStackTrace()
        }
    }

    override val recentFiles: ArrayList<RecentFileItem>
        get() = getRecentFiles(false)

    override fun getRecentFiles(lastOpenFiles: Boolean): ArrayList<RecentFileItem> {
        val list = ArrayList<RecentFileItem>()
        val db = getRecentFileDatabase()
        val keys = db.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            try {
                val jsonObject = db.getJSONObject(key)
                val file = mHelper.read(jsonObject)
                if (file.isLastOpen() == lastOpenFiles) {
                    list.add(file)
                }
            } catch (e: JSONException) {
                e.printStackTrace()
            }
        }
        return list
    }

    override fun clearRecentFiles() {
        saveRecentFileDatabase(JSONObject())
    }

    override fun addFindKeyword(keyword: String, isReplace: Boolean) {
        try {
            val gson = GsonBuilder().setPrettyPrinting().create()

            val database = File(mProjectDatabasePath.absolutePath + File.separator + KEYWORDS_DATABASE_NAME)
            val recent = gson.fromJson(getKeywordsDatabase(), FindKeywordsItem::class.java)
            if (isReplace) {
                if (recent.getReplaceKeyword().isNotEmpty()) {
                    if (!recent.getReplaceKeyword().contains(keyword))
                        recent.setReplaceKeyword(keyword)
                } else {
                    recent.setReplaceKeyword(keyword)
                }
            } else {
                if (recent.getKeyword().isNotEmpty()) {
                    if (!recent.getKeyword().contains(keyword))
                        recent.setKeyword(keyword)
                } else {
                    recent.setKeyword(keyword)
                }
            }

            val writer = FileWriter(database)
            gson.toJson(recent, writer)
            writer.flush()
            writer.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    override fun addFindKeyword(keyword: List<String>, isReplace: Boolean) {
        try {
            val gson = GsonBuilder().setPrettyPrinting().create()

            val database = File(mProjectDatabasePath.absolutePath + File.separator + KEYWORDS_DATABASE_NAME)
            val recent = gson.fromJson(getKeywordsDatabase(), FindKeywordsItem::class.java)
            if (isReplace) {
                if (Collections.disjoint(recent.getReplaceKeyword(), keyword))
                    recent.getReplaceKeyword().clear()
                else
                    recent.setReplaceKeyword(keyword)
            } else {
                if (Collections.disjoint(recent.getKeyword(), keyword))
                    recent.getKeyword().clear()
                else
                    recent.setKeyword(keyword)
            }

            val writer = FileWriter(database)
            gson.toJson(recent, writer)
            writer.flush()
            writer.close()
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
    }

    override fun getFindKeywords(isReplace: Boolean): ArrayList<String> {
        val list = ArrayList<String>()
        val gson = GsonBuilder().setPrettyPrinting().create()
        val recent = gson.fromJson(getKeywordsDatabase(), FindKeywordsItem::class.java)
        if (isReplace) {
            list.addAll(recent.getReplaceKeyword())
        } else {
            list.addAll(recent.getKeyword())
        }
        if (list.isEmpty()) {
            list.add("")
        }
        return list
    }

    override fun addFindKeywordAndFiles(keyword: String, isFiles: Boolean) {
        try {
            val gson = GsonBuilder().setPrettyPrinting().create()

            val database = File(mProjectDatabasePath.absolutePath + File.separator + KEYWORDS_FILES_DATABASE_NAME)
            val recent = gson.fromJson(getKeywordsAndFilesDatabase(), FindKeywordsAndFilesItem::class.java)
            if (isFiles) {
                if (recent.getFilesKeyword().isNotEmpty()) {
                    if (!recent.getFilesKeyword().contains(keyword))
                        recent.setFilesKeyword(keyword)
                } else {
                    recent.setFilesKeyword(keyword)
                }
            } else {
                if (recent.getKeyword().isNotEmpty()) {
                    if (!recent.getKeyword().contains(keyword))
                        recent.setKeyword(keyword)
                } else {
                    recent.setKeyword(keyword)
                }
            }

            val writer = FileWriter(database)
            gson.toJson(recent, writer)
            writer.flush()
            writer.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    override fun addFindKeywordAndFiles(keyword: List<String>, isFiles: Boolean) {
        try {
            val gson = GsonBuilder().setPrettyPrinting().create()

            val database = File(mProjectDatabasePath.absolutePath + File.separator + KEYWORDS_FILES_DATABASE_NAME)
            val recent = gson.fromJson(getKeywordsAndFilesDatabase(), FindKeywordsAndFilesItem::class.java)
            if (isFiles) {
                if (Collections.disjoint(recent.getFilesKeyword(), keyword))
                    recent.getFilesKeyword().clear()
                else
                    recent.setFilesKeyword(keyword)
            } else {
                if (Collections.disjoint(recent.getKeyword(), keyword))
                    recent.getKeyword().clear()
                else
                    recent.setKeyword(keyword)
            }

            val writer = FileWriter(database)
            gson.toJson(recent, writer)
            writer.flush()
            writer.close()
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
    }

    override fun getFindKeywordsAdnFile(isFiles: Boolean): ArrayList<String> {
        val list = ArrayList<String>()
        val gson = GsonBuilder().setPrettyPrinting().create()
        val recent = gson.fromJson(getKeywordsAndFilesDatabase(), FindKeywordsAndFilesItem::class.java)
        if (isFiles) {
            list.addAll(recent.getFilesKeyword())
        } else {
            list.addAll(recent.getKeyword())
        }
        if (list.isEmpty()) {
            list.add("")
        }
        return list
    }

    override fun getProject(path: String?): Project? {
        val gson = GsonBuilder().setPrettyPrinting().create()
        val project = gson.fromJson(getProjectDatabase(path), Project::class.java)
        return project
    }

    override fun addProjectNotes(notes: String?, path: String?) {
        try {
            val gson = GsonBuilder().setPrettyPrinting().create()
            val database = File(path + "/.database" + File.separator + KEYWORDS_PROJECT_DATABASE_NAME)

            val recent = gson.fromJson(getProjectDatabase(path), Project::class.java)
            recent.setProjectNotes(notes)
            //     recent.setProjectName("");

            val writer = FileWriter(database)
            gson.toJson(recent, writer)
            writer.flush()
            writer.close()
        } catch (ex: Exception) {
            DLog.e(ex.fillInStackTrace())
        }
    }

    override fun getProjectNotes(path: String?): String? {
        val gson = GsonBuilder().setPrettyPrinting().create()
        val project = gson.fromJson(getProjectDatabase(path), Project::class.java)
        return project.getProjectNotes()
    }

    private fun getProjectDatabase(path: String?): String {
        try {
            val database = File(path + "/.database" + File.separator + KEYWORDS_PROJECT_DATABASE_NAME)
            if (!database.exists()) {
                database.parentFile!!.mkdirs()
                database.createNewFile()
                val gson = GsonBuilder().setPrettyPrinting().create()
                val writer = FileWriter(database)
                val item = Project()
                item.setProjectName("")
                item.setProjectNotes("")
                gson.toJson(item, writer)
                writer.flush()
                writer.close()
            }
            val input = FileInputStream(database)
            return IOUtils.toString(input, StandardCharsets.UTF_8)

        } catch (ex: Exception) {
            DLog.e(ex.fillInStackTrace())
        }
        return ""
    }

    private fun getKeywordsDatabase(): String {
        try {
            val database = File(mProjectDatabasePath.absolutePath + File.separator + KEYWORDS_DATABASE_NAME)
            if (!database.exists()) {
                database.parentFile!!.mkdirs()
                database.createNewFile()
                val gson = GsonBuilder().setPrettyPrinting().create()
                val writer = FileWriter(database)
                val item = FindKeywordsItem()
                item.setKeyword("")
                item.setReplaceKeyword("")
                gson.toJson(item, writer)
                writer.flush()
                writer.close()
            }
            val input = FileInputStream(database)
            return IOUtils.toString(input, StandardCharsets.UTF_8)
        } catch (ex: Exception) {
            DLog.e(ex.fillInStackTrace())
        }
        return ""
    }

    private fun getKeywordsAndFilesDatabase(): String {
        try {
            val database = File(mProjectDatabasePath.absolutePath + File.separator + KEYWORDS_FILES_DATABASE_NAME)
            if (!database.exists()) {
                database.parentFile!!.mkdirs()
                database.createNewFile()
                val gson = GsonBuilder().setPrettyPrinting().create()
                val writer = FileWriter(database)
                val item = FindKeywordsAndFilesItem()
                item.setKeyword("")
                item.setFilesKeyword("")
                gson.toJson(item, writer)
                writer.flush()
                writer.close()
            }
            val input = FileInputStream(database)
            return IOUtils.toString(input, StandardCharsets.UTF_8)
        } catch (ex: Exception) {
            DLog.e(ex.fillInStackTrace())
        }
        return ""
    }

    private fun writeJsonToFile(jsonObject: JSONObject, fileName: String) {
        try {
            val file = File(mProjectDatabasePath.absolutePath + File.separator + fileName)
            file.parentFile!!.mkdirs()
            file.createNewFile()
            val output = FileOutputStream(file)
            IOUtils.write(jsonObject.toString(), output, StandardCharsets.UTF_8)
            output.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun saveRecentFileDatabase(database: JSONObject) {
        writeJsonToFile(database, RECENT_FILES_DATABASE_NAME)
    }

    private fun readFromFile(fileName: String): JSONObject {
        try {
            val database = File(mProjectDatabasePath.absolutePath + File.separator + fileName)
            if (!database.exists()) {
                database.createNewFile()
            }
            val input = FileInputStream(database)
            val content = IOUtils.toString(input, StandardCharsets.UTF_8)
            input.close()
            return JSONObject(content)
        } catch (e: IOException) {
            e.printStackTrace()
        } catch (e: JSONException) {
            e.printStackTrace()
        }
        return JSONObject()
    }

    private fun getRecentFileDatabase(): JSONObject {
        return readFromFile(RECENT_FILES_DATABASE_NAME)
    }

    private class RecentFileJsonHelper {

        @Throws(JSONException::class)
        fun write(json: JSONObject, item: RecentFileItem) {
            json.put("time", item.time)
            json.put("path", item.path)
            json.put("encoding", item.encoding)
            json.put("offset", item.offset)
            json.put("isLastOpen", item.isLastOpen)
        }

        @Throws(JSONException::class)
        fun read(json: JSONObject): RecentFileItem {
            val item = RecentFileItem()
            if (json.has("time")) {
                item.time = json.getInt("time").toLong()
            }
            if (json.has("path")) {
                item.path = json.getString("path")
            }
            if (json.has("encoding")) {
                item.encoding = json.getString("encoding")
            }
            if (json.has("offset")) {
                item.offset = json.getInt("offset")
            }
            if (json.has("isLastOpen")) {
                item.isLastOpen = json.getBoolean("isLastOpen")
            }
            return item
        }
    }

    companion object {
        @JvmStatic
        fun getInstance(context: Context): ITabDatabase {
            return JsonDatabase(context.applicationContext)
        }
    }

}
