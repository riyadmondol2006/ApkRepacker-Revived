package com.riyadm.apkrepacker.ui.colorslist

import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** Reads every `<color>` of a values file. */
object ColorsLoader {

    /** Parses [file]; throws if it is missing or not well-formed XML. */
    fun load(file: File): List<ColorMeta> {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        document.documentElement.normalize()
        val nodes = document.getElementsByTagName("color")
        return (0 until nodes.length)
            .map { nodes.item(it) }
            .filter { it.nodeType == Node.ELEMENT_NODE }
            .map { node ->
                val element = node as Element
                val value = element.textContent.trim()
                ColorMeta.Builder(element.getAttribute("name")).setValue(value).setIcon(value).build()
            }
    }
}
