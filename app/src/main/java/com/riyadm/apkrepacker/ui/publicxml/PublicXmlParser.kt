package com.riyadm.apkrepacker.ui.publicxml

import com.riyadm.apkrepacker.utils.ProjectUtils
import com.riyadm.patchengine.resource.ResourceItem
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

/** Reads and writes `res/values/public.xml` (the type/name/id table of an apktool project). */
class PublicXmlParser(content: File) {

    private val resourceItems: List<ResourceItem> = ResourceItem.parseFrom(content).orEmpty().toList()

    fun getNameById(id: String?): String? =
        resourceItems.firstOrNull { ResourceItem.id2String(it.id) == id }?.name

    fun getIdByName(name: String?): String? {
        if (name == null) return null
        return resourceItems.firstOrNull { it.name?.contains(name) == true }?.let { ResourceItem.id2String(it.id) }
    }

    fun save(list: List<ResourceItem>) {
        try {
            val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument()
            val root = document.createElement("resources")
            document.appendChild(root)
            list.toList().forEach { item ->
                val id = ResourceItem.id2String(item.id)
                val name = item.name
                if (id != null && name != null) {
                    root.appendChild(
                        document.createElement("public").apply {
                            setAttribute("type", item.type)
                            setAttribute("name", name)
                            setAttribute("id", id)
                        },
                    )
                }
            }
            val transformer = TransformerFactory.newInstance().newTransformer().apply {
                setOutputProperty(OutputKeys.INDENT, "yes")
                setOutputProperty(OutputKeys.ENCODING, "utf-8")
                setOutputProperty(OutputKeys.METHOD, "xml")
            }
            File(ProjectUtils.getProjectPath() + "/res/values/public.xml").outputStream().use {
                transformer.transform(DOMSource(document), StreamResult(it))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
