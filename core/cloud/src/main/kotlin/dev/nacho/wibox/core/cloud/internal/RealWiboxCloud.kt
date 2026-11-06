package dev.nacho.wibox.core.cloud.internal

import dev.nacho.wibox.core.cloud.AppUpgradeInfo
import dev.nacho.wibox.core.cloud.AppUpgradeRequest
import dev.nacho.wibox.core.cloud.CloudAction
import dev.nacho.wibox.core.cloud.CloudActionAcknowledgement
import dev.nacho.wibox.core.cloud.CloudActionTransport
import dev.nacho.wibox.core.cloud.CloudAlarmPayloads
import dev.nacho.wibox.core.cloud.CloudCapabilities
import dev.nacho.wibox.core.cloud.CloudCapability
import dev.nacho.wibox.core.cloud.CloudClock
import dev.nacho.wibox.core.cloud.CloudCredentials
import dev.nacho.wibox.core.cloud.CloudDevice
import dev.nacho.wibox.core.cloud.CloudDeviceDirectory
import dev.nacho.wibox.core.cloud.CloudError
import dev.nacho.wibox.core.cloud.CloudEvent
import dev.nacho.wibox.core.cloud.CloudService
import dev.nacho.wibox.core.cloud.CloudSession
import dev.nacho.wibox.core.cloud.DeviceShadow
import dev.nacho.wibox.core.cloud.ShadowAcknowledgement
import dev.nacho.wibox.core.cloud.ShadowUnsubscribeReason
import dev.nacho.wibox.core.cloud.TokenStore
import dev.nacho.wibox.core.cloud.UserAuthException
import dev.nacho.wibox.core.cloud.UserAuthTimeoutException
import dev.nacho.wibox.core.cloud.WiboxCloud
import dev.nacho.wibox.core.cloud.WiboxCloudConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.content.TextContent
import io.ktor.http.encodedPath
import io.ktor.http.takeFrom
import java.net.SocketTimeoutException
import java.net.URI
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

internal class RealWiboxCloud(
    private val config: WiboxCloudConfig,
    tokenStore: TokenStore,
    clock: CloudClock,
    private val httpClient: HttpClient,
    private val deviceDirectory: CloudDeviceDirectory? = null,
    private val actionTransport: CloudActionTransport? = null,
    private val alarmPayloads: CloudAlarmPayloads? = null,
) : WiboxCloud {
    private val baseUrl: String = validateAndNormalizeBaseUrl(config)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val closed = AtomicBoolean(false)
    private val sessions = SessionManager(tokenStore, clock, scope, ::passwordGrant, ::refreshGrant)

    override val capabilities = CloudCapabilities(
        supported = buildSet {
            add(CloudCapability.OAUTH_PASSWORD)
            add(CloudCapability.OAUTH_REFRESH)
            add(CloudCapability.SHADOW_HTTP)
            add(CloudCapability.OFFLINE_ALARM_PARSING)
            add(CloudCapability.APP_UPGRADE_CHECK)
            if (deviceDirectory != null) add(CloudCapability.DEVICE_LIST)
            if (actionTransport != null) add(CloudCapability.PHYSICAL_ACTIONS)
        },
    )

    override suspend fun authenticate(credentials: CloudCredentials): CloudSession {
        ensureOpen()
        if (credentials.username.isBlank() || credentials.password.isBlank()) {
            throw CloudError.InvalidInput("authenticate")
        }
        return sessions.authenticate(credentials)
    }

    override suspend fun restoreSession(): CloudSession? {
        ensureOpen()
        return sessions.restore()
    }

    override suspend fun refreshSession(): CloudSession {
        ensureOpen()
        return sessions.refresh()
    }

    override suspend fun getDeviceShadow(deviceId: String): DeviceShadow {
        val id = validateDeviceIds(listOf(deviceId), "getDeviceShadow").single()
        val session = sessions.validSession()
        val body = ShadowWire.json.encodeToString(DeviceFieldDto.serializer(), deviceField(id))
        val response = request("getDeviceShadow", ProtocolRoutes.SHADOW_STATUS, session.accessToken, HttpMethod.Post, body)
        return ShadowWire.status(response, "getDeviceShadow")
    }

    override suspend fun getDeviceShadows(deviceIds: List<String>): List<DeviceShadow> {
        val ids = validateDeviceIds(deviceIds, "getDeviceShadows")
        val session = sessions.validSession()
        val body = ShadowWire.json.encodeToString(
            kotlinx.serialization.builtins.ListSerializer(DeviceFieldDto.serializer()),
            ids.map(::deviceField),
        )
        val response = request("getDeviceShadows", ProtocolRoutes.SHADOW_STATUS_MULTI, session.accessToken, HttpMethod.Post, body)
        return ShadowWire.multiStatus(response, "getDeviceShadows")
    }

    override suspend fun subscribeDeviceShadows(deviceIds: List<String>): ShadowAcknowledgement {
        val ids = validateDeviceIds(deviceIds, "subscribeDeviceShadows")
        val session = sessions.validSession()
        val dto = ShadowSubscribeRequestDto(
            appId = config.appId.toString(),
            clientId = config.clientId,
            subData = ids.map(::deviceField),
        )
        val body = ShadowWire.json.encodeToString(ShadowSubscribeRequestDto.serializer(), dto)
        val response = request("subscribeDeviceShadows", ProtocolRoutes.SHADOW_SUBSCRIBE, session.accessToken, HttpMethod.Post, body)
        return ShadowWire.acknowledgement(response, "subscribeDeviceShadows")
    }

    override suspend fun unsubscribeDeviceShadows(
        deviceIds: List<String>,
        reason: ShadowUnsubscribeReason,
    ): ShadowAcknowledgement {
        val ids = validateDeviceIds(deviceIds, "unsubscribeDeviceShadows")
        val session = sessions.validSession()
        val dto = ShadowUnsubscribeRequestDto(
            appId = config.appId.toString(),
            clientId = config.clientId,
            subData = ids.map { DeviceUnsubscribeFieldDto(it, reason.wireValue, emptyList()) },
        )
        val body = ShadowWire.json.encodeToString(ShadowUnsubscribeRequestDto.serializer(), dto)
        val response = request("unsubscribeDeviceShadows", ProtocolRoutes.SHADOW_UNSUBSCRIBE, session.accessToken, HttpMethod.Post, body)
        return ShadowWire.acknowledgement(response, "unsubscribeDeviceShadows")
    }

    override suspend fun checkAppUpgrade(request: AppUpgradeRequest): AppUpgradeInfo {
        val upgradeBaseUrl = validateUpgradeRequest(request)
        val body = request(
            operation = "checkAppUpgrade",
            path = ProtocolRoutes.APP_UPGRADE_PATH,
            tokenHeader = null,
            method = HttpMethod.Get,
            requestBaseUrl = upgradeBaseUrl,
            expectedStatus = 200,
            requiredContentType = "text/xml;charset=UTF-8",
        ) {
            url.parameters.append("os", "android")
            url.parameters.append("appid", config.appId.toString())
            url.parameters.append("version", request.currentVersion.toString())
            url.parameters.append("clientid", request.clientId)
            header("Accept-Language", request.acceptLanguage)
        }
        return UpgradeXmlParser.parse(body)
    }

    private fun validateUpgradeRequest(request: AppUpgradeRequest): String {
        ensureOpen()
        if (
            request.currentVersion <= 0 || request.clientId.isBlank() ||
            request.acceptLanguage.isBlank() || request.acceptLanguage.any { it == '\r' || it == '\n' }
        ) {
            throw CloudError.InvalidInput("checkAppUpgrade")
        }
        return validateBaseUrl(request.baseUrl, "checkAppUpgrade")
    }

    override fun parseAlarmEvent(payload: Map<String, String>): CloudEvent = AlarmEventParser.parse(payload)

    /**
     * Alarm events travel as push payloads (FCM on the stock app), not as a socket
     * this client can open: the stock MQTT shadow is disabled in the vendor SDK and
     * its runtime was never proven. The owner of the push transport injects the
     * decoded payloads, and each one is parsed with the same contract as
     * [parseAlarmEvent]. Without an injected source the stream stays empty and no
     * event is invented.
     */
    override fun observeEvents(): Flow<CloudEvent> = flow {
        ensureOpen()
        val source = alarmPayloads ?: return@flow
        source.collect { payload ->
            ensureOpen()
            val event = try {
                parseAlarmEvent(payload)
            } catch (error: CloudError) {
                if (error is CloudError.Closed) throw error
                return@collect
            }
            emit(event)
        }
    }

    /**
     * Devices of the signed in account, as answered by the injected account
     * directory (userauth `get-device-list`). This client does not own that
     * session, so an unwired directory is reported as Unsupported instead of
     * returning an empty catalogue that reads like "no devices".
     */
    override suspend fun listDevices(): List<CloudDevice> {
        ensureOpen()
        val directory = deviceDirectory ?: throw CloudError.Unsupported(
            "listDevices",
            "The account channel that answers get-device-list is not wired in this client",
        )
        val devices = try {
            directory.listDevices()
        } catch (error: Throwable) {
            throw mapTransportError("listDevices", error)
        }
        return devices.filter { it.id.isNotBlank() }.distinctBy { it.id }
    }

    override suspend fun discoverServices(): Map<CloudService, String> {
        throw CloudError.Unsupported(
            "discoverServices",
            "Dynamic service discovery is unresolved",
        )
    }

    /**
     * Physical actions run over the device live session, not over this HTTP
     * client. The injected transport performs them and reports the provider code;
     * without a transport the operation stays Unsupported instead of faking an
     * acknowledgement.
     */
    override suspend fun performAction(deviceId: String, action: CloudAction): CloudActionAcknowledgement {
        ensureOpen()
        if (deviceId.isBlank()) throw CloudError.InvalidInput("performAction")
        val transport = actionTransport ?: throw CloudError.Unsupported(
            "performAction",
            "The physical action transport is not wired in this client",
        )
        return try {
            transport.performAction(deviceId, action)
        } catch (error: Throwable) {
            throw mapTransportError("performAction", error)
        }
    }

    private fun deviceField(id: String): DeviceFieldDto =
        DeviceFieldDto(id, listOf(ProtocolRoutes.DEVICE_STATE_FIELD))

    private fun validateDeviceIds(deviceIds: List<String>, operation: String): List<String> {
        ensureOpen()
        if (deviceIds.isEmpty() || deviceIds.any { it.isBlank() }) throw CloudError.InvalidInput(operation)
        return deviceIds
    }

    private suspend fun passwordGrant(credentials: CloudCredentials, tokenHeader: String): CloudSession {
        val body = request("authenticate", ProtocolRoutes.OAUTH_TOKEN, tokenHeader, HttpMethod.Get) {
            url.parameters.append("grant_type", "password")
            url.parameters.append("client_id", config.clientId)
            url.parameters.append("client_type", config.clientType.toString())
            url.parameters.append("oemid", config.oemId)
            url.parameters.append("appid", config.appId.toString())
            url.parameters.append("usr", credentials.username)
            url.parameters.append("pwd", credentials.password)
            url.parameters.append("region_id", config.regionId)
            url.parameters.append("client_flag", config.clientFlag.toString())
        }
        return OAuthWire.session(body, "authenticate")
    }

    private suspend fun refreshGrant(session: CloudSession): CloudSession {
        val body = request("refreshSession", ProtocolRoutes.OAUTH_TOKEN, session.accessToken, HttpMethod.Get) {
            url.parameters.append("grant_type", "refresh_token")
            url.parameters.append("refresh_token", session.refreshToken)
        }
        return OAuthWire.session(body, "refreshSession")
    }

    private suspend fun request(
        operation: String,
        path: String,
        tokenHeader: String?,
        method: HttpMethod,
        jsonBody: String? = null,
        requestBaseUrl: String = baseUrl,
        expectedStatus: Int? = null,
        requiredContentType: String? = null,
        query: io.ktor.client.request.HttpRequestBuilder.() -> Unit = {},
    ): String {
        ensureOpen()
        val response = try {
            httpClient.request {
                this.method = method
                url {
                    takeFrom(requestBaseUrl)
                    encodedPath = encodedPath.trimEnd('/') + path
                }
                if (tokenHeader != null) header("token", tokenHeader)
                if (jsonBody != null) setBody(TextContent(jsonBody, ContentType.Application.Json))
                query()
            }
        } catch (error: Throwable) {
            throw mapTransportError(operation, error)
        }
        val body = try {
            response.bodyAsText()
        } catch (error: Throwable) {
            throw mapTransportError(operation, error)
        }
        if (expectedStatus != null) {
            if (response.status.value != expectedStatus) throw httpError(operation, response.status.value)
        } else if (response.status.value !in 200..299) {
            throw httpError(operation, response.status.value)
        }
        if (requiredContentType != null) {
            val actual = response.headers["Content-Type"]?.replace(" ", "")?.lowercase()
            val expected = requiredContentType.replace(" ", "").lowercase()
            if (actual != expected) throw CloudError.Parse(operation)
        }
        return body
    }

    private fun httpError(operation: String, status: Int): CloudError = when (status) {
        401 -> CloudError.Authentication(operation)
        403 -> CloudError.Authorization(operation)
        else -> CloudError.Http(operation, status)
    }

    private fun mapTransportError(operation: String, error: Throwable): Throwable = when (error) {
        is CloudError -> error
        is HttpRequestTimeoutException, is SocketTimeoutException -> CloudError.Timeout(operation)
        is UserAuthTimeoutException -> CloudError.Timeout(operation)
        is UserAuthException -> CloudError.Provider(operation, error.result)
        is CancellationException -> error
        else -> CloudError.Network(operation)
    }

    private fun ensureOpen() {
        if (closed.get()) throw CloudError.Closed()
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            scope.cancel()
            httpClient.close()
        }
    }

    companion object {
        fun createDefault(
            config: WiboxCloudConfig,
            tokenStore: TokenStore,
            clock: CloudClock,
            deviceDirectory: CloudDeviceDirectory? = null,
            actionTransport: CloudActionTransport? = null,
            alarmPayloads: CloudAlarmPayloads? = null,
        ): RealWiboxCloud {
            validateAndNormalizeBaseUrl(config)
            val client = HttpClient(OkHttp) {
                install(HttpTimeout) {
                    requestTimeoutMillis = config.requestTimeoutMillis
                    connectTimeoutMillis = config.connectTimeoutMillis
                    socketTimeoutMillis = config.socketTimeoutMillis
                }
                followRedirects = false
                engine {
                    config {
                        retryOnConnectionFailure(false)
                    }
                }
            }
            return RealWiboxCloud(
                config = config,
                tokenStore = tokenStore,
                clock = clock,
                httpClient = client,
                deviceDirectory = deviceDirectory,
                actionTransport = actionTransport,
                alarmPayloads = alarmPayloads,
            )
        }

        private fun validateAndNormalizeBaseUrl(config: WiboxCloudConfig): String {
            val normalized = validateBaseUrl(config.baseUrl, "baseUrl")
            if (
                config.clientId.isBlank() || config.regionId.isBlank() || config.oemId.isBlank() ||
                config.appId <= 0 || config.clientType <= 0 || config.requestTimeoutMillis <= 0 ||
                config.connectTimeoutMillis <= 0 || config.socketTimeoutMillis <= 0
            ) {
                throw CloudError.InvalidInput("configuration")
            }
            return normalized
        }

        private fun validateBaseUrl(value: String, operation: String): String {
            val uri = try {
                URI(value)
            } catch (_: Exception) {
                throw CloudError.InvalidInput(operation)
            }
            if (
                uri.scheme != "https" || uri.host.isNullOrBlank() || uri.userInfo != null ||
                uri.query != null || uri.fragment != null
            ) {
                throw CloudError.InvalidInput(operation)
            }
            return value.trimEnd('/')
        }
    }
}
