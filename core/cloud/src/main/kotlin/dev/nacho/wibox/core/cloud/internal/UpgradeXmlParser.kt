package dev.nacho.wibox.core.cloud.internal

import dev.nacho.wibox.core.cloud.AppUpgradeInfo
import dev.nacho.wibox.core.cloud.CloudError
import java.io.StringReader
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.ErrorHandler
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import org.xml.sax.SAXParseException

internal object UpgradeXmlParser {
    private const val OPERATION = "checkAppUpgrade"
    private const val MAX_XML_BYTES = 64 * 1024
    private val forbiddenDeclarations = Regex("<!\\s*(?:DOCTYPE|ENTITY)\\b", RegexOption.IGNORE_CASE)
    private val xInclude = Regex(
        "(?:<\\s*xi:include\\b|http://www\\.w3\\.org/2001/XInclude)",
        RegexOption.IGNORE_CASE,
    )

    fun parse(xml: String): AppUpgradeInfo {
        try {
            if (xml.toByteArray(Charsets.UTF_8).size > MAX_XML_BYTES) throw CloudError.Parse(OPERATION)
            if (forbiddenDeclarations.containsMatchIn(xml) || xInclude.containsMatchIn(xml)) {
                throw CloudError.Parse(OPERATION)
            }
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = false
                isXIncludeAware = false
                isExpandEntityReferences = false
                setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                setFeature("http://xml.org/sax/features/external-general-entities", false)
                setFeature("http://xml.org/sax/features/external-parameter-entities", false)
                setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
                setAttribute("http://javax.xml.XMLConstants/property/accessExternalDTD", "")
                setAttribute("http://javax.xml.XMLConstants/property/accessExternalSchema", "")
            }
            val builder = factory.newDocumentBuilder()
            builder.setEntityResolver { _, _ -> throw SAXException("External entities are disabled") }
            builder.setErrorHandler(object : ErrorHandler {
                override fun warning(exception: SAXParseException) = throw exception
                override fun error(exception: SAXParseException) = throw exception
                override fun fatalError(exception: SAXParseException) = throw exception
            })
            val document = builder.parse(InputSource(StringReader(xml)))
            val root = document.documentElement
            if (root.nodeName != "envelope" || !root.namespaceURI.isNullOrEmpty()) {
                throw CloudError.Parse(OPERATION)
            }
            val header = requiredElement(root, "header")
            val content = requiredElement(root, "content")
            val config = requiredElement(content, "config")
            val flag = requiredText(header, "flag")
            val result = requiredInt(header, "result")
            val priority = requiredInt(content, "priority")
            val version = requiredText(content, "version")
            val url = requiredText(content, "url")
            val description = requiredText(content, "description")
            val configVersion = requiredInt(config, "version")
            val configUrl = requiredText(config, "url")
            val configPicture = requiredText(config, "picture")
            if (flag != "tdkcloud" || version.isBlank()) throw CloudError.Parse(OPERATION)
            if (result != 0) {
                throw CloudError.Unsupported(OPERATION, "Only provider result 0 has been observed")
            }
            return AppUpgradeInfo(
                flag = flag,
                result = result,
                priority = priority,
                version = version,
                url = url,
                description = description,
                configVersion = configVersion,
                configUrl = configUrl,
                configPicture = configPicture,
            )
        } catch (error: CloudError) {
            throw error
        } catch (_: Exception) {
            throw CloudError.Parse(OPERATION)
        }
    }

    private fun requiredElement(parent: Element, name: String): Element {
        val matches = mutableListOf<Element>()
        val children = parent.childNodes
        for (index in 0 until children.length) {
            val child = children.item(index)
            if (child.nodeType == Node.ELEMENT_NODE && child.nodeName == name) {
                matches += child as Element
            }
        }
        return matches.singleOrNull() ?: throw CloudError.Parse(OPERATION)
    }

    private fun requiredText(parent: Element, name: String): String {
        val element = requiredElement(parent, name)
        val children = element.childNodes
        for (index in 0 until children.length) {
            if (children.item(index).nodeType == Node.ELEMENT_NODE) throw CloudError.Parse(OPERATION)
        }
        return element.textContent
    }

    private fun requiredInt(parent: Element, name: String): Int =
        requiredText(parent, name).trim().toIntOrNull() ?: throw CloudError.Parse(OPERATION)
}
