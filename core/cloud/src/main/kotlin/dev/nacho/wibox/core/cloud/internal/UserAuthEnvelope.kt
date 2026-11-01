package dev.nacho.wibox.core.cloud.internal

import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.Node

/** Datos del header del envelope userauth (XStream, kebab-case en el wire). */
internal data class UserAuthHeader(
    val command: String,
    val clientId: String,
    val flag: String = "tdkcloud",
    val version: String = "",
    val seq: Int = 0,
    val session: String = "",
    val userData: String = "",
    val clientType: Int = 3,
    val oem: String = "G0021",
    val app: Int = 2021,
)

/** Respuesta parseada del canal down (result=0 = exito). */
data class UserAuthResponse(
    val flag: String?,
    val version: String?,
    val command: String?,
    val seq: Int?,
    val result: Int?,
    val content: Map<String, String>,
) {
    val isSuccess: Boolean get() = result == 0
}

/**
 * Serializacion del envelope userauth en el formato XStream real observado en el app oficial:
 * content primero (con atributo class del bean), header despues, version vacio por defecto,
 * campos kebab-case. Parse de la respuesta (header + content) con DocumentBuilder endurecido.
 */
internal object UserAuthEnvelope {

    fun build(
        header: UserAuthHeader,
        contentFields: LinkedHashMap<String, String>,
        contentClass: String? = null,
    ): String {
        // Replicación byte a byte del pretty-print XStream del app oficial (golden request):
        // indentación de 3 espacios por nivel (content/header a 6, hijos de client a 9) y
        // etiquetas vacías expandidas (<user-data></user-data>).
        val sb = StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><envelope>")
        sb.append("\n   <content")
        if (contentClass != null) sb.append(" class=\"").append(escape(contentClass)).append("\"")
        sb.append(">")
        for ((k, v) in contentFields) {
            sb.append("\n      <").append(k).append(">").append(escape(v)).append("</").append(k).append(">")
        }
        sb.append("\n   </content>")
        sb.append("\n   <header>")
        sb.append("\n      <client>")
        sb.append("\n         <app>").append(header.app).append("</app>")
        sb.append("\n         <id>").append(escape(header.clientId)).append("</id>")
        sb.append("\n         <oem>").append(escape(header.oem)).append("</oem>")
        sb.append("\n         <type>").append(header.clientType).append("</type>")
        sb.append("\n      </client>")
        sb.append("\n      <command>").append(escape(header.command)).append("</command>")
        sb.append("\n      <flag>").append(escape(header.flag)).append("</flag>")
        sb.append("\n      <seq>").append(header.seq).append("</seq>")
        if (header.session.isNotEmpty()) {
            sb.append("\n      <session>").append(escape(header.session)).append("</session>")
        }
        sb.append("\n      <user-data>").append(escape(header.userData)).append("</user-data>")
        sb.append("\n      <version>").append(escape(header.version)).append("</version>")
        sb.append("\n   </header>")
        sb.append("\n</envelope>")
        return sb.toString()
    }

    fun parse(xml: String): UserAuthResponse {
        val factory = DocumentBuilderFactory.newInstance()
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false)
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        factory.isXIncludeAware = false
        factory.isExpandEntityReferences = false
        val doc = factory.newDocumentBuilder().parse(xml.byteInputStream(Charsets.UTF_8))
        val root = doc.documentElement
        val headerEl = firstChild(root, "header")
        val contentEl = firstChild(root, "content")
        val headerMap = headerEl?.let { childTextMap(it) } ?: emptyMap()
        val contentMap = contentEl?.let { childTextMap(it) } ?: emptyMap()
        return UserAuthResponse(
            flag = headerMap["flag"],
            version = headerMap["version"],
            command = headerMap["command"],
            seq = headerMap["seq"]?.toIntOrNull(),
            result = headerMap["result"]?.toIntOrNull(),
            content = contentMap,
        )
    }

    private fun firstChild(parent: Element, tag: String): Element? {
        val nodes = parent.childNodes
        for (i in 0 until nodes.length) {
            val n = nodes.item(i)
            if (n is Element && n.tagName == tag) return n
        }
        return null
    }

    private fun childTextMap(parent: Element): Map<String, String> {
        val map = LinkedHashMap<String, String>()
        val nodes = parent.childNodes
        for (i in 0 until nodes.length) {
            val n = nodes.item(i)
            if (n is Element) map[n.tagName] = n.textContent ?: ""
        }
        return map
    }

    private fun escape(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}
