package com.riyadm.patchengine

/**
 * Класс в который добавляются результаты матчинга файлов
 */
class Section(_start: Int, _end: Int, _groupStrs: List<String?>?) {
    @JvmField
    var end: Int = _end

    @JvmField
    var start: Int = _start

    @JvmField
    var groupStrs: List<String?>? = _groupStrs

    /**
     * Возвращет последний инедекс матчинга
     * @return
     */
    fun getEnd(): Int {
        return end
    }

    /**
     * Возвращет первый инедекс матчинга
     * @return
     */
    fun getStart(): Int {
        return start
    }

    /**
     * Возвращет список групп матчинга
     * @return
     */
    fun getGroupStrs(): List<String?>? {
        return groupStrs
    }
}
