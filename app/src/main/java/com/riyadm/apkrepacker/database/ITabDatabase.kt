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

import com.riyadm.apkrepacker.database.entity.Project
import com.riyadm.apkrepacker.database.entity.RecentFileItem

interface ITabDatabase {
    fun addRecentFile(path: String?, encoding: String?)

    fun updateRecentFile(path: String?, lastOpen: Boolean)

    fun updateRecentFile(path: String?, encoding: String?, offset: Int)

    val recentFiles: ArrayList<RecentFileItem>

    fun getRecentFiles(lastOpenFiles: Boolean): ArrayList<RecentFileItem>

    fun clearRecentFiles()

    //void clearFindKeywords(boolean isReplace);

    //void clearFindKeywordAndFiles(boolean isFiles);
    fun addFindKeyword(keyword: List<String>, isReplace: Boolean)

    fun addFindKeyword(keyword: String, isReplace: Boolean)

    fun addFindKeywordAndFiles(keyword: String, isFiles: Boolean)

    fun addFindKeywordAndFiles(keyword: List<String>, isFiles: Boolean)

    fun getFindKeywords(isReplace: Boolean): ArrayList<String>

    fun getFindKeywordsAdnFile(isFiles: Boolean): ArrayList<String>

    fun getProject(path: String?): Project?

    fun getProjectNotes(path: String?): String?

    fun addProjectNotes(notes: String?, path: String?)
}
