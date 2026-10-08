package com.riyadm.patchengine

class ReplaceRec(val startPos: Int, val endPos: Int, val replacing: String?) {

    override fun toString(): String {
        return "ReplaceRec{" +
                "endPos=" + endPos +
                ", replacing='" + replacing + '\'' +
                ", startPos=" + startPos +
                '}'
    }
}
