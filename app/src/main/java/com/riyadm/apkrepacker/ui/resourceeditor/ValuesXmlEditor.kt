package com.riyadm.apkrepacker.ui.resourceeditor

import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

/**
 * Edits a values XML file in place: the file is re-read, one change is applied to its DOM and it is
 * written back, so comments, other resource kinds and attributes the editors don't show survive a save.
 */
object ValuesXmlEditor {

    fun edit(file: File, change: (document: Document, resources: Element) -> Unit) {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val resources = document.documentElement
        dropBlankText(resources)
        change(document, resources)
        val transformer = TransformerFactory.newInstance().newTransformer().apply {
            setOutputProperty(OutputKeys.INDENT, "yes")
            setOutputProperty(OutputKeys.ENCODING, "utf-8")
            setOutputProperty(OutputKeys.METHOD, "xml")
        }
        file.outputStream().use { transformer.transform(DOMSource(document), StreamResult(it)) }
    }

    /** First direct child `<[tag] name="[name]">` of [resources]. */
    fun find(resources: Element, tag: String, name: String): Element? {
        var child = resources.firstChild
        while (child != null) {
            if (child is Element && child.tagName == tag && child.getAttribute("name") == name) return child
            child = child.nextSibling
        }
        return null
    }

    /** Whitespace between elements would be doubled by the indenting transformer. */
    private fun dropBlankText(parent: Element) {
        var child = parent.firstChild
        while (child != null) {
            val next = child.nextSibling
            if (child.nodeType == Node.TEXT_NODE && child.textContent.isBlank()) parent.removeChild(child)
            child = next
        }
    }
}
