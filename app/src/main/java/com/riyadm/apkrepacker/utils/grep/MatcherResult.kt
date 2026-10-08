/*
 * Copyright (C) 2016 Jecelyin Peng <jecelyin@gmail.com>
 *
 * This file is part of 920 Text Editor.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.riyadm.apkrepacker.utils.grep

import java.util.regex.Matcher

/**
 * @author Jecelyin Peng <jecelyin@gmail.com>
 */
class MatcherResult(m: Matcher) {
    private val start: Int = m.start()
    private val end: Int = m.end()
    private val groupCount: Int = m.groupCount() + 1
    private val groups: Array<String?> = arrayOfNulls(groupCount)

    init {
        for (i in 0 until groupCount) {
            groups[i] = m.group(i)
        }
    }

    fun start(): Int {
        return start
    }

    fun end(): Int {
        return end
    }

    fun groupCount(): Int {
        return groupCount
    }

    fun group(group: Int): String? {
        return groups[group]
    }
}
