package pxb.android.axml

class ValueWrapper private constructor(
    @JvmField val type: Int,
    @JvmField val ref: Int,
    @JvmField val raw: String?
) {

    fun replaceRaw(raw: String?): ValueWrapper {
        return ValueWrapper(type, ref, raw)
    }

    companion object {
        const val ID = 1
        const val STYLE = 2
        const val CLASS = 3

        @JvmStatic
        fun wrapId(ref: Int, raw: String?): ValueWrapper {
            return ValueWrapper(ID, ref, raw)
        }

        @JvmStatic
        fun wrapStyle(ref: Int, raw: String?): ValueWrapper {
            return ValueWrapper(STYLE, ref, raw)
        }

        @JvmStatic
        fun wrapClass(ref: Int, raw: String?): ValueWrapper {
            return ValueWrapper(CLASS, ref, raw)
        }
    }
}
