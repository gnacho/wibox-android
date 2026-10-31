package dev.nacho.wibox.ui

import android.content.Intent
import dev.nacho.wibox.data.AppScenario

object PushRelay {
    private val allowedQuery = setOf("deviceId", "mediaId", "invitationId", "callId", "modify", "mode", "scenario")
    private val allowedModes = setOf("device", "admin", "unlock", "init")

    fun fromIntent(intent: Intent?): AppDestinationRequest? {
        val data = intent?.data
        return if (data != null) {
            val query = buildMap {
                data.queryParameterNames.forEach { key ->
                    data.getQueryParameter(key)?.let { put(key, it) }
                }
            }
            parseValues(data.scheme, data.host, data.pathSegments, query)
        } else {
            val screen = intent?.getStringExtra("screen") ?: return null
            parseValues("wibox", "app", listOf(screen), emptyMap())
        }
    }

    fun parseValues(
        scheme: String?,
        host: String?,
        pathSegments: List<String>,
        query: Map<String, String>,
    ): AppDestinationRequest? {
        if (scheme != "wibox" || host != "app") return null
        val inventoryId = pathSegments.firstOrNull() ?: return null
        if (inventoryId !in inventoryDestinations || inventoryId in omittedLegacyScreens) return null
        if (query.keys.any { it !in allowedQuery }) return null
        val mode = query["mode"] ?: "device"
        if (mode !in allowedModes) return null
        val identifiers = listOfNotNull(
            query["deviceId"],
            query["mediaId"],
            query["invitationId"],
            query["callId"],
        )
        if (identifiers.any { !validIdentifier(it) }) return null
        val scenario = query["scenario"]?.let { value ->
            runCatching { AppScenario.valueOf(value.uppercase()) }.getOrNull() ?: return null
        }
        return AppDestinationRequest(
            inventoryId = inventoryId,
            deviceId = query["deviceId"],
            mediaId = query["mediaId"],
            invitationId = query["invitationId"],
            callId = query["callId"],
            modify = query["modify"]?.toBoolean() == true,
            mode = mode,
            demoScenario = scenario,
        )
    }

    private fun validIdentifier(value: String): Boolean =
        value.isNotBlank() && value.length <= 128 && !value.contains('/') && !value.contains(' ')
}
