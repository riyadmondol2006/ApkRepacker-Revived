package com.riyadm.apkrepacker.autotranslator.translator

class TranslateItem {

    @JvmField
    var name: String?

    @JvmField
    var originValue: String?

    @JvmField
    var translatedValue: String? = null

    constructor(name: String?, originValue: String?) {
        this.name = name
        this.originValue = originValue
    }

    constructor(name: String?, originValue: String?, translatedValue: String?) {
        this.name = name
        this.originValue = originValue
        this.translatedValue = translatedValue
    }
}
