package com.riyadm.apkrepacker.task

import com.riyadm.apkrepacker.autotranslator.dictionary.DictionaryReader
import com.riyadm.apkrepacker.autotranslator.translator.TranslateItem
import com.riyadm.apkrepacker.fragment.StringsFragment
import com.riyadm.apkrepacker.task.base.CoroutinesAsyncTask
import java.io.File

class TranslateDictionaryTask(fragment: StringsFragment) : CoroutinesAsyncTask<Void, Void, Boolean>() {

    private var mSkipTranslated = false
    private var mSkipSupport = false
    private var mReverseDictionary = false
    private var mDictionaryPath: String? = null

    private var mTranslateItems: List<TranslateItem> = ArrayList()
    private val stringsFragment: StringsFragment = fragment
    private val translatedItems: MutableList<TranslateItem> = ArrayList()

    fun setSkipTranslated(skip: Boolean) {
        mSkipTranslated = skip
    }

    fun setReverseDictionary(reverseDictionary: Boolean) {
        this.mReverseDictionary = reverseDictionary
    }

    fun setSkipSupport(skipSupport: Boolean) {
        this.mSkipSupport = skipSupport
    }

    fun setTranslateItems(translateItems: List<TranslateItem>) {
        this.mTranslateItems = translateItems
    }

    override fun onPreExecute() {
        super.onPreExecute()
        stringsFragment.showProgress()
    }

    fun setDictionaryPath(dictionaryPath: String?) {
        this.mDictionaryPath = dictionaryPath
    }

    override fun doInBackground(vararg params: Void?): Boolean {

        val dictionaryReader = DictionaryReader(File(mDictionaryPath!!))
        dictionaryReader.readDictionary()
        var i = 0
        while (i < mTranslateItems.size) {
            val item = mTranslateItems[i]
            //устанавливаем проверку ли переведён елемент. Если переведен пропускаем его
            if (mSkipTranslated and (item.translatedValue != null)) {
                i++
                //translatedItems.add(item);
                i++ // loop increment (Java for-loop update on continue)
                continue
            }
            //устанавливаем проверку ли елемент содержит ключи строк саппортов(androidx, appcompat)
            if (mSkipSupport and item.name!!.startsWith("abc_")) {
                i++
                // translatedItems.add(item);
                i++ // loop increment (Java for-loop update on continue)
                continue
            }

            for ((original, value) in dictionaryReader.getDictionaryMap()) {
                val translated = value.translated
                if (item.originValue == (if (mReverseDictionary) translated else original)) {
                    val finalI = i
                    stringsFragment.activity!!.runOnUiThread {
                        stringsFragment.stringsAdapter!!.setUpdateValue(translated, finalI)
                    }
                    //mTranslateItems.get(i).translatedValue = mReverseDictionary ? original : translated;
                    //translatedItems.add(item.name,);
                    ///*new TranslateItem(item.name, item.originValue, mReverseDictionary ? original : translated)*/);
                }
                /* else {
                     translatedItems.add(item);
                 }*/
            }

            i++
        }
        return true
    }

    override fun onPostExecute(result: Boolean?) {
        super.onPostExecute(result)
        stringsFragment.hideProgress()
        //  stringsFragment.getStringsAdapter().setItems(mTranslateItems);
    }
}
