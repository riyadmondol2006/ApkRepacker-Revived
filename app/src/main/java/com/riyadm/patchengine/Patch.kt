package com.riyadm.patchengine

class Patch {

    @JvmField
    var author: String? = null

    @JvmField
    var packagename: String? = null

    @JvmField
    var requiredEngine: Int = 0

    @JvmField
    var rules: MutableList<PatchRule> = ArrayList()

    fun getAuthor(): String? {
        return author
    }

    fun getRequiredEngine(): Int {
        return requiredEngine
    }

    fun getPackageName(): String? {
        return packagename
    }

    fun getRules(): MutableList<PatchRule> {
        return rules
    }

    fun setAuthor(author: String?) {
        this.author = author
    }

    fun setPackageName(packageName: String?) {
        this.packagename = packageName
    }

    fun setRequiredEngine(requiredEngine: Int) {
        this.requiredEngine = requiredEngine
    }

    fun setRule(rule: PatchRule) {
        this.rules.add(rule)
    }

    companion object {
        const val engineVersion = 2
    }
}
