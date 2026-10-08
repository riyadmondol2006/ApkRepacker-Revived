package com.riyadm.apkrepacker.adapter

import com.riyadm.patchengine.PatchInspector

class PatchItem(patchName: String?, path: String?) {

    @JvmField
    var mPath: String? = path

    @JvmField
    var mPatchName: String? = patchName

    /** Parsed header/rules of the patch, filled in off the main thread after the row is added. */
    var info: PatchInspector.PatchInfo? = null
}
