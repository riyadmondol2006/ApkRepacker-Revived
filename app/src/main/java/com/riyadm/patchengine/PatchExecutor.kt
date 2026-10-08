package com.riyadm.patchengine

import com.riyadm.apkrepacker.R
import com.riyadm.patchengine.interfaces.IPatchContext
import com.riyadm.patchengine.interfaces.IRulesInfo
import com.riyadm.patchengine.rules.PatchRuleMatchReplace
import java.util.zip.ZipFile

class PatchExecutor(
    private val mProjectHelper: ProjectHelper,
    private val mPatchPath: String?,
    private val mRulesInfo: IRulesInfo?,
    private val mPatchContext: IPatchContext
) {

    private var mPatch: Patch? = null
    private var mSourceZip: ZipFile? = null

    //применяем патч
    fun applyPatch() {
        try {
            val zipFile = ZipFile(mPatchPath)
            mSourceZip = zipFile
            val entry = zipFile.getEntry("patch.txt")
            if (entry == null) {
                mSourceZip!!.close()
                mSourceZip = null
                mPatchContext.error(R.string.patch_error_no_entry, "patch.txt")
                return
            }
            val input = mSourceZip!!.getInputStream(entry)
            mPatch = PatchParser.parse(input, mPatchContext)
            input.close()
            /*
            boolean needToDecode = false;
            if (!mProjectHelper.smaliClicked()) {
                Iterator<PatchRule> it = patch.rules.iterator();
                while (it.hasNext() && !(needToDecode = it.next().isSmaliNeeded())) {
                    while (it.hasNext() && !(needToDecode = it.next().isSmaliNeeded())) {
                    }
                }
                if (needToDecode) {
                    patchContext.info(R.string.decode_dex_file, true, new Object[0]);
                    ((ApkInfoActivity) activityRef.get()).decodeDex(this);
                }
            }
            if (!needToDecode) {

             */
            applyRules(mPatch!!.getRules(), mSourceZip!!)
            // }
        } catch (e: Exception) {
            mPatchContext.error(R.string.general_error, e.message)
            e.printStackTrace()
        }
    }

    /* private void applyRules(final List<PatchRule> rules, final ZipFile sourceZip) {
         new Thread() {
             public void run() {
                 int index = 0;
                 while (index < rules.size()) {
                     PatchRule rule = rules.get(index);
                     PatchExecutor.this.mPatchContext.info(R.string.patch_start_apply, true, rule.startLine);
                     String nextRule = null;
                     if (rule.isValid(PatchExecutor.this.mPatchContext)) {
                         nextRule = rule.executeRule(PatchExecutor.this.mProjectHelper, sourceZip, PatchExecutor.this.mPatchContext);
                     }
                     if (nextRule != null) {
                         index = PatchExecutor.this.findTargetRule(rules, nextRule);
                     } else {
                         index++;
                     }
                 }
                 PatchExecutor.this.mPatchContext.info(R.string.all_rules_applied, true);
                 PatchExecutor.this.mPatchContext.patchFinished();
             }
         }.start();
     }*/
    /**
     * Applies the patch's rules, in order, on the calling thread. [PatchService] already runs this
     * on a background coroutine, so there is no inner thread any more (the old one made several
     * queued patches race and did its zip parsing on the main thread). Rule iteration is unchanged.
     */
    private fun applyRules(rules: List<PatchRule>, sourceZip: ZipFile) {
        val total = rules.size
        mRulesInfo?.allRules(total)
        var index = 0
        while (index < total) {
            mRulesInfo?.currentRules(index)

            // Consecutive regex MATCH_REPLACE rules on the same files run in one pass over those
            // files (see PatchRuleMatchReplace.runBatch); the result is the same as one by one.
            val batch = batchAt(rules, index)
            if (batch.size >= 2) {
                PatchRuleMatchReplace.runBatch(batch, mProjectHelper, mPatchContext) { done ->
                    mRulesInfo?.currentRules(index + done + 1)
                }
                index += batch.size
                continue
            }

            val rule = rules[index]
            mPatchContext.info(R.string.patch_start_apply, true, rule.startLine)
            val started = System.nanoTime()
            if (rule.isValid(mPatchContext)) {
                rule.executeRule(mProjectHelper, sourceZip, mPatchContext)
            }
            val seconds = (System.nanoTime() - started) / 1e9
            if (seconds >= 1.0) mPatchContext.info("Took ${"%.1f".format(seconds)} s", false)
            index++
        }
        mRulesInfo?.currentRules(total)
        mPatchContext.info(R.string.all_rules_applied, true)
        mPatchContext.patchFinished()
    }

    /** The run of rules from [start] that [PatchRuleMatchReplace.runBatch] can do together (may be just one). */
    private fun batchAt(rules: List<PatchRule>, start: Int): List<PatchRuleMatchReplace> {
        if (!PatchRuleMatchReplace.batchingEnabled) return emptyList()
        val first = rules[start] as? PatchRuleMatchReplace ?: return emptyList()
        val key = first.batchKey() ?: return emptyList()
        val batch = ArrayList<PatchRuleMatchReplace>()
        batch += first
        var i = start + 1
        while (i < rules.size) {
            val next = rules[i] as? PatchRuleMatchReplace ?: break
            if (next.batchKey() != key) break
            batch += next
            i++
        }
        return batch
    }

    fun findTargetRule(rules: List<PatchRule>, name: String): Int {
        for (i in rules.indices) {
            if (name == rules[i].ruleName) {
                return i
            }
        }
        return -1
    }

    fun callbackFunc() {
        val patch = mPatch
        if (patch != null && patch.rules != null && mSourceZip != null) {
            applyRules(patch.rules, mSourceZip!!)
        }
    }

    //получаем список правил патча
    fun getRuleNames(): List<String?> {
        val names: MutableList<String?> = ArrayList()
        val patch = mPatch
        if (!(patch == null || patch.getRules() == null)) {
            for (rule in patch.getRules()) {
                names.add(rule.ruleName)
            }
        }
        return names
    }
}
