/*
 *  Copyright (C) 2010 Ryszard Wiśniewski <brut.alll@gmail.com>
 *  Copyright (C) 2010 Connor Tumbleson <connor.tumbleson@gmail.com>
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package brut.xml

import org.w3c.dom.Document
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import java.io.File
import java.io.IOException
import java.io.StringReader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.Collections
import javax.xml.XMLConstants
import javax.xml.namespace.NamespaceContext
import javax.xml.namespace.QName
import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.ParserConfigurationException
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerException
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import javax.xml.xpath.XPathConstants
import javax.xml.xpath.XPathExpressionException
import javax.xml.xpath.XPathFactory

/**
 * Android port of brut.j.xml 3.0.3's XmlUtils (same API).
 *
 * Upstream enables JAXP security features that Android's DocumentBuilderFactory doesn't know: it
 * throws ParserConfigurationException for any feature other than namespaces/validation, which made
 * every apktool manifest tweak (--debuggable, --net-sec-conf, provider fixes, version cleanup)
 * silently do nothing. The app's build strips the upstream class from brut.j.xml (see
 * app/build.gradle) and ships this one, which sets those features only where supported. Android's
 * parser never loads external DTDs anyway.
 */
object XmlUtils {
    const val XML_PROLOG = "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
    const val XML_PREFIX = "xml"
    const val XML_URI = "http://www.w3.org/XML/1998/namespace"
    const val XMLNS_PREFIX = "xmlns"
    const val XMLNS_URI = "http://www.w3.org/2000/xmlns/"

    private const val FEATURE_DISALLOW_DOCTYPE_DECL = "http://apache.org/xml/features/disallow-doctype-decl"
    // javax.xml.XMLConstants.ACCESS_EXTERNAL_DTD/SCHEMA (JAXP 1.5), missing from Android's copy.
    private const val ACCESS_EXTERNAL_DTD = "http://javax.xml.XMLConstants/property/accessExternalDTD"
    private const val ACCESS_EXTERNAL_SCHEMA = "http://javax.xml.XMLConstants/property/accessExternalSchema"
    private const val FEATURE_LOAD_EXTERNAL_DTD = "http://apache.org/xml/features/nonvalidating/load-external-dtd"

    @Throws(SAXException::class, ParserConfigurationException::class)
    private fun newDocumentBuilder(nsAware: Boolean): DocumentBuilder {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = nsAware
        trySetFeature(factory, XMLConstants.FEATURE_SECURE_PROCESSING, true)
        trySetFeature(factory, FEATURE_DISALLOW_DOCTYPE_DECL, true)
        trySetFeature(factory, FEATURE_LOAD_EXTERNAL_DTD, false)

        try {
            factory.setAttribute(ACCESS_EXTERNAL_DTD, "")
            factory.setAttribute(ACCESS_EXTERNAL_SCHEMA, "")
        } catch (ignored: IllegalArgumentException) {
            // Not supported on Android.
        }

        return factory.newDocumentBuilder()
    }

    private fun trySetFeature(factory: DocumentBuilderFactory, name: String, value: Boolean) {
        try {
            factory.setFeature(name, value)
        } catch (ignored: ParserConfigurationException) {
            // Not supported on Android.
        }
    }

    @JvmStatic
    @Throws(SAXException::class, ParserConfigurationException::class)
    fun newDocument(): Document = newDocument(false)

    @JvmStatic
    @Throws(SAXException::class, ParserConfigurationException::class)
    fun newDocument(nsAware: Boolean): Document = newDocumentBuilder(nsAware).newDocument()

    @JvmStatic
    @Throws(IOException::class, SAXException::class, ParserConfigurationException::class)
    fun parseDocument(xml: String): Document = parseDocument(xml, false)

    @JvmStatic
    @Throws(IOException::class, SAXException::class, ParserConfigurationException::class)
    fun parseDocument(xml: String, nsAware: Boolean): Document {
        val builder = newDocumentBuilder(nsAware)
        return builder.parse(InputSource(StringReader(xml)))
    }

    @JvmStatic
    @Throws(IOException::class, SAXException::class, ParserConfigurationException::class)
    fun loadDocument(file: File): Document = loadDocument(file, false)

    @JvmStatic
    @Throws(IOException::class, SAXException::class, ParserConfigurationException::class)
    fun loadDocument(file: File, nsAware: Boolean): Document {
        val builder = newDocumentBuilder(nsAware)
        // Not using the parse(File) method on purpose, so that we can control when to close it.
        // Somehow parse(File) does not seem to close the file in all cases.
        Files.newInputStream(file.toPath()).use { input ->
            return builder.parse(InputSource(input))
        }
    }

    @JvmStatic
    @Throws(IOException::class, SAXException::class, ParserConfigurationException::class, TransformerException::class)
    fun saveDocument(doc: Document, file: File) {
        val factory = TransformerFactory.newInstance()
        val transformer = factory.newTransformer()
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes")

        val xmlDecl = XML_PROLOG.toByteArray(StandardCharsets.US_ASCII)
        val newLine = System.lineSeparator().toByteArray(StandardCharsets.US_ASCII)

        Files.newOutputStream(file.toPath()).use { out ->
            out.write(xmlDecl)
            out.write(newLine)
            transformer.transform(DOMSource(doc), StreamResult(out))
            out.write(newLine)
        }
    }

    @JvmStatic
    @Suppress("UNCHECKED_CAST")
    @Throws(XPathExpressionException::class)
    fun <T> evaluateXPath(doc: Document, expression: String, returnType: Class<T>): T? {
        val type: QName = when (returnType) {
            Node::class.java -> XPathConstants.NODE
            NodeList::class.java -> XPathConstants.NODESET
            String::class.java -> XPathConstants.STRING
            java.lang.Double::class.java -> XPathConstants.NUMBER
            java.lang.Boolean::class.java -> XPathConstants.BOOLEAN
            else -> throw IllegalArgumentException("Unexpected return type: " + returnType.name)
        }

        val xPath = XPathFactory.newInstance().newXPath()
        xPath.namespaceContext = object : NamespaceContext {
            override fun getNamespaceURI(prefix: String?): String? = doc.lookupNamespaceURI(prefix)

            override fun getPrefix(namespaceURI: String?): String? = doc.lookupPrefix(namespaceURI)

            override fun getPrefixes(namespaceURI: String?): MutableIterator<String> {
                val prefix = getPrefix(namespaceURI)
                return if (prefix != null) {
                    Collections.singleton(prefix).iterator()
                } else {
                    Collections.emptyIterator()
                }
            }
        }

        return xPath.evaluate(expression, doc, type) as T?
    }
}
