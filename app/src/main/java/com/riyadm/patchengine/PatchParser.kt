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
        val br = LinedReader(InputStreamReader(input))
        var line = br.readLine()
        while (line != null) {
            val line2 = line.trim { it <= ' ' }
            if (line2.startsWith("[")) {
                when (line2) {
                    MIN_ENGINE_VER -> result.setRequiredEngine(Integer.parseInt(br.readLine()))
                    AUTHOR -> result.setAuthor(br.readLine())
                    PACKAGE -> result.setPackageName(br.readLine())
                    else -> {
                        val rule = parseRule(br, line2, logger)
                        if (rule != null) {
                            result.setRule(rule)
                        }
                    }
                }
            } else if (!line2.startsWith("#") && "" != line2) {
                logger.error(R.string.patch_error_unknown_rule, br.currentLine, line2)
            }
            line = br.readLine()
        }
        return result
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
            else -> logger.error(R.string.patch_error_unknown_rule, linedReader.currentLine, startLine)
        }
        rule?.parseFrom(linedReader, logger)
        return rule
    }
}
