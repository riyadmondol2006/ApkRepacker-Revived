package com.riyadm.patchengine

import com.riyadm.apkrepacker.R
import com.riyadm.patchengine.interfaces.IPatchContext
import com.riyadm.patchengine.rules.PatchRuleAddFiles
import com.riyadm.patchengine.rules.PatchRuleDummy
import com.riyadm.patchengine.rules.PatchRuleExecDex
import com.riyadm.patchengine.rules.PatchRuleFuncReplace
import com.riyadm.patchengine.rules.PatchRuleGoto
import com.riyadm.patchengine.rules.PatchRuleMatchAssign
import com.riyadm.patchengine.rules.PatchRuleMatchGoto
import com.riyadm.patchengine.rules.PatchRuleMatchReplace
import com.riyadm.patchengine.rules.PatchRuleMerge
import com.riyadm.patchengine.rules.PatchRuleRemoveFiles
import com.riyadm.patchengine.rules.PatchRuleReviseSig
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

object PatchParser {

    //правила в patch.txt
    private const val ADD_FILES = "[ADD_FILES]"
    private const val AUTHOR = "[AUTHOR]"
    private const val DUMMY = "[DUMMY]"
    private const val EXECUTE_DEX = "[EXECUTE_DEX]"
    private const val FUNCTION_REPLACE = "[FUNCTION_REPLACE]"
    private const val GOTO = "[GOTO]"
    private const val MATCH_ASSIGN = "[MATCH_ASSIGN]"
    private const val MATCH_GOTO = "[MATCH_GOTO]"
    private const val MATCH_REPLACE = "[MATCH_REPLACE]"
    private const val MERGE = "[MERGE]"
    private const val MIN_ENGINE_VER = "[MIN_ENGINE_VER]"
    private const val PACKAGE = "[PACKAGE]"
    private const val REMOVE_FILES = "[REMOVE_FILES]"
    private const val SIGNATURE_REVISE = "[SIGNATURE_REVISE]"

    //парсим patch.txt
    @JvmStatic
    @Throws(Exception::class)
    fun parse(input: InputStream, logger: IPatchContext): Patch {
        logger.info(R.string.patch_start_parse, true)
        val result = Patch()
        // patch.txt is UTF-8 (a BOM is dropped by LinedReader); CRLF / CR endings are handled by readLine.
        val br = LinedReader(InputStreamReader(input, StandardCharsets.UTF_8))
        br.missingEnd = { line, endTag -> logger.error(R.string.patch_error_cannot_parse, line, "missing $endTag before this line") }
        // Free text after [AUTHOR] (a second line of names) belongs to the author, not an unknown rule.
        var inAuthor = false
        var line = br.readLine()
        while (line != null) {
            val line2 = line.trim { it <= ' ' }
            if (line2.startsWith("[")) {
                inAuthor = false
                when (line2) {
                    MIN_ENGINE_VER -> {
                        val value = readValue(br)
                        // "2 " or a missing value no longer aborts the whole patch with a NumberFormatException.
                        val version = value?.toIntOrNull()
                        if (version != null) {
                            result.setRequiredEngine(version)
                        } else {
                            logger.error(R.string.patch_error_cannot_parse, br.currentLine, value ?: line2)
                        }
                    }
                    AUTHOR -> {
                        result.setAuthor(readValue(br))
                        inAuthor = true
                    }
                    PACKAGE -> result.setPackageName(readValue(br))
                    else -> {
                        val rule = parseRule(br, line2, logger)
                        if (rule != null) {
                            result.setRule(rule)
                        }
                    }
                }
            } else if (inAuthor && "" != line2 && !line2.startsWith("#")) {
                result.setAuthor(result.getAuthor()?.let { "$it, $line2" } ?: line2)
            } else if (!line2.startsWith("#") && "" != line2) {
                logger.error(R.string.patch_error_unknown_rule, br.currentLine, line2)
            }
            line = br.readLine()
        }
        return result
    }

    /** The trimmed value line of a header tag, or null when the next line is already another tag. */
    private fun readValue(br: LinedReader): String? {
        val value = br.readLine() ?: return null
        if (value.trim { it <= ' ' }.startsWith("[")) {
            br.unread(value)
            return null
        }
        return value.trim { it <= ' ' }
    }

    //запускаем парсинг правил внутри patch.txt
    @Throws(IOException::class)
    private fun parseRule(linedReader: LinedReader, startLine: String, logger: IPatchContext): PatchRule? {
        var rule: PatchRule? = null
        when (startLine) {
            ADD_FILES -> rule = PatchRuleAddFiles()
            REMOVE_FILES -> rule = PatchRuleRemoveFiles()
            MERGE -> rule = PatchRuleMerge()
            MATCH_REPLACE -> rule = PatchRuleMatchReplace()
            MATCH_GOTO -> rule = PatchRuleMatchGoto()
            MATCH_ASSIGN -> rule = PatchRuleMatchAssign()
            FUNCTION_REPLACE -> rule = PatchRuleFuncReplace()
            SIGNATURE_REVISE -> rule = PatchRuleReviseSig()
            GOTO -> rule = PatchRuleGoto()
            DUMMY -> rule = PatchRuleDummy()
            EXECUTE_DEX -> rule = PatchRuleExecDex()
            else -> {
                logger.error(R.string.patch_error_unknown_rule, linedReader.currentLine, startLine)
                skipUnknown(linedReader, startLine)
            }
        }
        if (rule != null) {
            linedReader.ruleEnd = "[/" + startLine.substring(1)
            try {
                rule.parseFrom(linedReader, logger)
            } catch (e: NullPointerException) {
                // A keyword on the last line, without its value: drop the rule, keep the others.
                logger.error(R.string.patch_error_cannot_parse, linedReader.currentLine, startLine)
                return null
            } finally {
                linedReader.ruleEnd = null
            }
        }
        return rule
    }

    /**
     * Skips the body of an unknown rule `[NAME]` up to `[/NAME]`, so its lines aren't each reported
     * again. Stops early at the start of a known rule (the end tag may be missing). A stray end
     * tag like `[/NAME]` has no body.
     */
    private fun skipUnknown(br: LinedReader, startLine: String) {
        if (startLine.startsWith("[/") || !startLine.endsWith("]")) return
        val end = "[/" + startLine.substring(1)
        while (true) {
            val line = br.readLine() ?: return
            val trimmed = line.trim { it <= ' ' }
            if (trimmed == end) return
            if (trimmed in LinedReader.RULE_TAGS || trimmed == MIN_ENGINE_VER || trimmed == AUTHOR || trimmed == PACKAGE) {
                br.unread(line)
                return
            }
        }
    }
}
