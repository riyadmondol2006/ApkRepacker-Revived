package com.riyadm.apkrepacker.task

import com.riyadm.apkrepacker.autotranslator.translator.TranslateItem
import com.riyadm.apkrepacker.autotranslator.translator.Translator
import com.riyadm.apkrepacker.fragment.StringsFragment
import com.riyadm.apkrepacker.task.base.CoroutinesAsyncTask
import com.riyadm.apkrepacker.utils.StringUtils
import com.riyadm.apkrepacker.utils.common.DLog
import java.util.Random

class TranslateTask(
    private var untranslated: List<TranslateItem>,
    private val fragment: StringsFragment
) : CoroutinesAsyncTask<Void, ArrayList<TranslateItem>, Boolean>() {

    private val r = Random()
    private val translatedItems: MutableList<TranslateItem> = ArrayList()

    private var targetLanguageCode: String? = null
    private var mSkipTranslated = false
    private var mSkipSupport = false

    fun setSkipTranslated(skip: Boolean) {
        mSkipTranslated = skip
    }

    fun setSkipSupport(skipSupport: Boolean) {
        this.mSkipSupport = skipSupport
    }

    fun setTranslateItems(translateItems: List<TranslateItem>) {
        this.untranslated = translateItems
    }

    fun setTargetLanguageCode(targetLanguageCode: String?) {
        this.targetLanguageCode = targetLanguageCode
    }

    override fun onPreExecute() {
        fragment.showProgress()
    }

    override fun onPostExecute(result: Boolean?) {
        fragment.hideProgress()
        //activityRef.get().translateCompleted();
        if (result!!) {
            fragment.stringsAdapter!!.setItems(translatedItems)
        }
    }

    override fun doInBackground(vararg params: Void?): Boolean {
        val r = Random(System.currentTimeMillis())
        val maxItems = 10 + r.nextInt(5)
        val maxChars = 900

        val code = StringUtils.getGoogleLangCode(targetLanguageCode)

        val translator = Translator(code)

        val todo: MutableList<TranslateItem> = ArrayList()
        for (item in untranslated) {
            if (isCancelled) {
                return false
            }
            //устанавливаем проверку ли переведён елемент. Если переведен пропускаем его
            if ((mSkipTranslated and (item.translatedValue != null)) && item.translatedValue!!.isNotEmpty()) {
                translatedItems.add(item)
                DLog.d("Skip translated")
                continue
            }
            //устанавливаем проверку ли елемент содержит ключи строк саппортов(androidx, appcompat)
            if (mSkipSupport and item.name!!.startsWith("abc_")) {
                translatedItems.add(item)
                DLog.d("Skip support")
                continue
            }

            // Multiple lines
            if (item.originValue!!.contains("\n")) {
                if (todo.isNotEmpty()) {
                    doTranslate(translator, todo)
                }

                todo.add(item)
                doTranslate(translator, todo)
            }
            //переводим по 900 слов
            else if (todo.size >= maxItems || (getTotalCharaters(todo) + item.originValue!!.length) > maxChars) {
                doTranslate(translator, todo)
                todo.add(item)
            } else {
                todo.add(item)
            }

        }

        if (this.isCancelled) {
            return todo.isEmpty()
        }

        // Remaining
        if (todo.isNotEmpty()) {
            doTranslate(translator, todo)
        }

        return true
    }

    // Compute how many chars
    private fun getTotalCharaters(todo: List<TranslateItem>): Int {
        var totalLen = 0
        for (item in todo) {
            totalLen += item.originValue!!.length
        }
        return totalLen
    }

    private fun doTranslate(translator: Translator, todo: MutableList<TranslateItem>) {
        // No string need to be translated
        if (todo.isEmpty()) {
            return
        }

        // Manually sleep awhile, so that I am not taken as a robot
        try {
            Thread.sleep((300 + r.nextInt(700)).toLong())
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }

        if (this.isCancelled) {
            return
        }

        translator.translate(todo)
        checkResult(todo)
        todo.clear()
    }

    // Check how many items are successfully translated
    private fun checkResult(items: List<TranslateItem>) {
        //  ArrayList<TranslateItem> copied = new ArrayList<>();
        translatedItems.addAll(items)
        //  this.publishProgress(copied);
    }

}
