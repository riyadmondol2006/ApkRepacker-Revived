package com.riyadm.apkrepacker.ui.dimenslist

import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** Reads every `<dimen>` of a values file. */
object DimensLoader {

    /** Parses [file]; throws if it is missing or not well-formed XML. */
    fun load(file: File): List<DimensMeta> {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        document.documentElement.normalize()
        val nodes = document.getElementsByTagName("dimen")
        return (0 until nodes.length)
            .map { nodes.item(it) }
            .filter { it.nodeType == Node.ELEMENT_NODE }
            .map { node ->
                val element = node as Element
                DimensMeta.Builder(element.getAttribute("name")).setValue(element.textContent.trim()).build()
            }
    }
}
