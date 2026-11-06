package dev.nacho.wibox.core.cloud

import kotlinx.coroutines.flow.Flow

/**
 * Source of the devices linked to the signed in account.
 *
 * The stock cloud answers this on the userauth duplex channel (`get-device-list`),
 * which runs on its own session with its own cookie, different from the OAuth and
 * Shadow client. The owner of that session injects the call here, so
 * [WiboxCloud.listDevices] stays a single contract and no second wire is built
 * inside this client.
 */
fun interface CloudDeviceDirectory {
    suspend fun listDevices(): List<CloudDevice>
}

/**
 * Transport for the physical actions of a device.
 *
 * The stock cloud carries unlock and F1 as a transparent QUII command inside a
 * live session, not over HTTP. The owner of that session (the app, which holds
 * the vendor P2P binding) injects the verified transport here instead of this
 * client duplicating the doorbell protocol.
 */
fun interface CloudActionTransport {
    suspend fun performAction(deviceId: String, action: CloudAction): CloudActionAcknowledgement
}

/**
 * Push payloads that carry alarm events, already decoded from their transport
 * (the stock path is FCM) and keyed as the raw fields [WiboxCloud.parseAlarmEvent]
 * expects.
 */
typealias CloudAlarmPayloads = Flow<Map<String, String>>
