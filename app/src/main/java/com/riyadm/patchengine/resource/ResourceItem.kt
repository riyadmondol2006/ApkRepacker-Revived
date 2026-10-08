package com.riyadm.patchengine.resource

import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.SAXException
import java.io.File
import java.io.IOException
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.ParserConfigurationException

class ResourceItem(val type: String?, val name: String?, _id: Int) {

    var id: Int = _id

    override fun toString(): String {
        return String.format(
            "<public type=\"%s\" name=\"%s\" id=\"0x%s\" />",
            this.type,
            this.name,
            Integer.toHexString(this.id)
        )
    }

    companion object {

        @JvmStatic
        fun parseFrom(line: File?): List<ResourceItem>? {
            /*int startPos = 0;
            int endPos;
            int startPos2 = 0;
            int endPos2;
            int startPos3 = 0;
            int endPos3;
            String type2 = null;
            String name2 = null;
            int id2 = -1;
            int startPos4 = line.indexOf("type=\"");
            if (!(startPos4 == -1 || (endPos = line.indexOf("\" ", startPos)) == -1)) {
                type2 = line.substring(startPos, endPos);
                int startPos5 = line.indexOf("name=\"");
                if (!(startPos5 == -1 || (endPos2 = line.indexOf("\" ", startPos2)) == -1)) {
                    name2 = line.substring(startPos2, endPos2);
                    int startPos6 = line.indexOf("id=\"");
                    if (!(startPos6 == -1 || (endPos3 = line.indexOf("\" ", startPos3)) == -1)) {
                        id2 = string2Id(line.substring(startPos3, endPos3));
                    }
                }
            }
            if (type2 == null || name2 == null || id2 == -1) {
                return null;
            }*/
            try {
                val result: MutableList<ResourceItem> = ArrayList()
                val builderFactory = DocumentBuilderFactory.newInstance()
                val documentBuilder = builderFactory.newDocumentBuilder()
                val doc = documentBuilder.parse(line)
                doc.documentElement.normalize()
                val nodeList = doc.getElementsByTagName("public")
                for (i in 0 until nodeList.length) {
                    val node = nodeList.item(i)
                    if (node.nodeType == Node.ELEMENT_NODE) {
                        val element = node as Element
                        val type = element.getAttribute("type")
                        val name = element.getAttribute("name")
                        val id = element.getAttribute("id")
                        result.add(ResourceItem(type, name, string2Id(id)))
                    }
                }
                return result
            } catch (e: IOException) {
                e.printStackTrace()
            } catch (e: SAXException) {
                e.printStackTrace()
            } catch (e: ParserConfigurationException) {
                e.printStackTrace()
            }
            return null
        }

        @JvmStatic
        fun string2Id(str: String): Int {
            var value = 0
            if (str.length == 10) {
                for (i in 2 until 10) {
                    value = (value shl 4) or getVal(str[i])
                }
            }
            return value
        }

        @JvmStatic
        fun id2String(id2: Int): String {
            return "0x" + Integer.toHexString(id2)
        }

        private fun getVal(c: Char): Int {
            if (c >= '0' && c <= '9') {
                return c - '0'
            }
            if (c >= 'a' && c <= 'f') {
                return (c - 'a') + 10
            }
            if (c < 'A' || c > 'F') {
                return 0
            }
            return (c - 'A') + 10
        }
    }
}
