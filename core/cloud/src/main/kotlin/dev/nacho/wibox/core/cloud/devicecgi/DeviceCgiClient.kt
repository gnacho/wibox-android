package dev.nacho.wibox.core.cloud.devicecgi

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import java.io.IOException

/** Datos de firmware leidos del CGI local del timbre. */
data class DeviceFirmwareInfo(
    val version: String?,
    val releaseDate: String?,
    val model: String?,
    val mac: String?,
    val latestVersion: String?,
)

/** Error del CGI del dispositivo (error != 0, XML invalido o red). */
class DeviceCgiException(message: String, cause: Throwable? = null) : IOException(message, cause)

/**
 * Cliente del CGI local del timbre (`POST http://host:80/tdkcgi`).
 *
 * Solo funciona en la misma red local: el firmware no esta expuesto por la nube.
 * Autenticacion: usuario `adminapp2` y password = encodeDevicePassword(authCode).
 */
class DeviceCgiClient(
    private val httpClient: HttpClient,
    private val port: Int = 80,
) {

    suspend fun productInfo(host: String, password: String): DeviceFirmwareInfo {
        val content = post(host, "get.product.info", password)
        val info = element(content, "info")
        return DeviceFirmwareInfo(
            version = tag(info, "version"),
            releaseDate = tag(info, "releasedate"),
            model = tag(info, "model"),
            mac = tag(info, "mac"),
            latestVersion = null,
        )
    }

    suspend fun deviceStatus(host: String, password: String): DeviceFirmwareInfo {
        val content = post(host, "get.device.status", password)
        val info = element(content, "info")
        val afterInfo = content.substringAfter("</info>", "")
        return DeviceFirmwareInfo(
            version = tag(info, "version"),
            releaseDate = tag(info, "releasedate"),
            model = tag(info, "model"),
            mac = tag(info, "mac") ?: tag(element(afterInfo, "network"), "mac"),
            latestVersion = tag(afterInfo, "version"),
        )
    }

    suspend fun upgradeVersion(host: String, password: String): String? =
        tag(post(host, "get.system.upgradeversion", password), "version")

    private suspend fun post(host: String, command: String, password: String): String {
        val xml = envelope(command, password)
        val response = try {
            httpClient.post("http://$host:$port/tdkcgi") {
                contentType(ContentType.Application.Xml)
                header("Charset", "utf-8")
                setBody(xml)
            }
        } catch (error: Exception) {
            throw DeviceCgiException("no se pudo contactar con $host:$port", error)
        }
        val text = try {
            response.bodyAsText()
        } catch (error: Exception) {
            throw DeviceCgiException("respuesta ilegible de $host:$port", error)
        }
        val error = tag(text, "error")
        if (error != null && error != "0") {
            throw DeviceCgiException("CGI $command devolvio error $error")
        }
        return element(text, "content")
    }

    internal fun envelope(command: String, password: String): String = buildString {
        append("<envelope><body><command>").append(command).append("</command><content /></body>")
        append("<header><password>").append(password).append("</password>")
        append("<passwordencode>1</passwordencode><security>username</security>")
        append("<username>adminapp2</username></header></envelope>")
    }

    private fun element(xml: String, name: String): String =
        Regex("<$name>(.*?)</$name>", RegexOption.DOT_MATCHES_ALL)
            .find(xml)?.groupValues?.get(1) ?: ""

    private fun tag(xml: String, name: String): String? =
        Regex("<$name>([^<]*)</$name>").find(xml)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }

    companion object {
        /** Cliente con el motor HTTP por defecto del modulo (OkHttp). */
        fun default(port: Int = 80): DeviceCgiClient = DeviceCgiClient(HttpClient(), port)
    }
}
