package pxb.android.axml

/**
 * Created by Stardust on 2017/10/23.
 */
class DumpEditor : DumpAdapter {

    private val mValueModifications: Map<String?, String?>?

    constructor(valueModifications: Map<String?, String?>?) : super() {
        mValueModifications = valueModifications
    }

    constructor(nv: NodeVisitor?, valueModifications: Map<String?, String?>?) : super(nv) {
        mValueModifications = valueModifications
    }

    override fun attr(ns: String?, name: String?, resourceId: Int, type: Int, obj: Any?) {
        if (ns != null) {
            val fullName = getPrefix(ns) + ":" + name
            val newValue = mValueModifications!![fullName]
            if (newValue != null) {
                super.attr(ns, name, -1, NodeVisitor.TYPE_STRING, newValue)
                return
            }
        } else {
            val newValue = mValueModifications!![name]
            if (newValue != null) {
                super.attr(ns, name, resourceId, NodeVisitor.TYPE_STRING, newValue)
                return
            }
        }
        super.attr(ns, name, resourceId, type, obj)
    }

    override fun child(ns: String?, name: String?): NodeVisitor? {
        val child = super.child(ns, name)
        if (child !is DumpEditor) {
            return DumpEditor(child, mValueModifications)
        }
        return child
    }
}
