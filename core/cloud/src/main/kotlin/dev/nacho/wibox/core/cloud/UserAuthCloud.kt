package dev.nacho.wibox.core.cloud

import dev.nacho.wibox.core.cloud.internal.UserAuthWire
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import java.io.InputStream
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient

private const val LOGIN_CACHE_TTL_MS = 10 * 60 * 1000L

/**
 * Fachada del canal userauth duplex (registro / login / compartición de dispositivos).
 *
 * Distinto del canal OAuth/Shadow de [WiboxCloud]: este es el canal XML tdkcloud
 * que usa el app oficial para el alta y la compartición sin credenciales.
 */
class UserAuthCloud internal constructor(
    private val wire: UserAuthWire,
) : UserAuthGateway {

    private data class CachedLogin(
        val account: String,
        val password: String,
        val login: UserAuthLogin,
        val completedAtMs: Long,
    )

    private val loginMutex = Mutex()

    @Volatile
    private var cachedLogin: CachedLogin? = null

    /**
     * Acepta una invitación de compartición (accept-nologin-share-invitation).
     *
     * @param invitation datos del QR de invitación.
     * @param name alias con el que se registra el dispositivo compartido.
     * @param createAccount true si el app no tiene cuenta aún (needCreate=1): el servidor
     *        crea una cuenta nologin nueva protegida con [newAccountPassword].
     * @param newAccountPassword contraseña de la cuenta nueva (UUID si no se indica).
     */
    suspend fun acceptShareInvitation(
        invitation: ShareInvitation,
        name: String,
        createAccount: Boolean = true,
        newAccountPassword: String = UUID.randomUUID().toString(),
    ): ShareAcceptance {
        // Campos en el orden y alias kebab-case EXACTOS del app oficial (XStream).
        val content = linkedMapOf(
            "share-code" to invitation.inviteCode,
            "device-id" to invitation.deviceId,
            "share-device-memo" to name,
            "need-create" to if (createAccount) "1" else "0",
            "password" to newAccountPassword,
            "way-from" to invitation.from,
        )
        val response = wire.command(
            "accept-nologin-share-invitation",
            content,
            contentClass = "com.quvii.qvweb.userauth.bean.request.NoLoginShareAcceptReqContent",
        )
        if (!response.isSuccess) {
            throw UserAuthException(
                command = "accept-nologin-share-invitation",
                result = response.result ?: -1,
                responseContent = response.content,
            )
        }
        return ShareAcceptance(
            account = response.content["account"],
            accountId = response.content["id"],
            name = response.content["name"],
            dataEncodeKey = response.content["data-encode-key"],
            dynamicPassword = response.content["dynamic-password"],
            passwordExpired = response.content["password-expired"],
            powers = response.content["powers"],
        )
    }

    /**
     * Login con la cuenta nologin creada en el accept: devuelve el token de sesion
     * que el wire captura automaticamente ( SESSION CAPTURED ) para los comandos
     * autenticados posteriores (get-device-list). La password es LA MISMA que se
     * envio en el accept con need-create=1.
     */
    override suspend fun login(account: String, password: String): UserAuthLogin =
        loginMutex.withLock {
            val now = System.currentTimeMillis()
            cachedLogin
                ?.takeIf {
                    it.account == account &&
                        it.password == password &&
                        now - it.completedAtMs < LOGIN_CACHE_TTL_MS
                }
                ?.let { return@withLock it.login }

            val content = linkedMapOf(
                "account" to account,
                "auth-code" to "",
                "ip-region-id" to "1",
                "password" to password,
                "auth-type" to "0",
            )
            val contentClass = "com.quvii.qvweb.userauth.bean.request.LoginReqContent"
            val response = try {
                wire.command("login", content, contentClass = contentClass, timeoutMs = 20_000)
            } catch (timeout: UserAuthTimeoutException) {
                // La cookie cacheada puede estar muerta: sesion nueva y un unico reintento.
                println("WIBOX-ACCEPT LOGIN RETRY after timeout")
                wire.resetSession()
                wire.command("login", content, contentClass = contentClass, timeoutMs = 20_000)
            }
            if (!response.isSuccess) {
                throw UserAuthException(
                    command = "login",
                    result = response.result ?: -1,
                    responseContent = response.content,
                )
            }
            val login = UserAuthLogin(
                accountId = response.content["account-id"] ?: response.content["id"],
                session = response.session,
            )
            cachedLogin = CachedLogin(account, password, login, System.currentTimeMillis())
            login
        }

    override suspend fun getShareLink(): String {
        val response = wire.command("get-share-link", linkedMapOf(), authenticated = true)
        if (!response.isSuccess) throw UserAuthException("get-share-link", response.result ?: -1, response.content)
        return response.content["invite-page"].orEmpty().ifBlank {
            throw UserAuthException("get-share-link", -1, response.content, "get-share-link returned no invite-page")
        }
    }

    override suspend fun listShareGroups(deviceId: String): List<ShareGroup> {
        val response = wire.command(
            "get-share-group-list",
            linkedMapOf("device-id" to deviceId),
            authenticated = true,
            contentClass = "com.quvii.qvweb.userauth.bean.request.NoLoginShareListReqContent",
        )
        if (!response.isSuccess) throw UserAuthException("get-share-group-list", response.result ?: -1, response.content)
        return response.shareGroups.mapNotNull { fields ->
            val id = fields["share-group-id"] ?: return@mapNotNull null
            shareGroup(fields, id)
        }
    }

    override suspend fun createShareGroup(
        deviceId: String,
        displayName: String,
        permissions: Set<DevicePermission>,
    ): ShareGroupCreation {
        val response = wire.command(
            "generate-nologin-share-invitation",
            linkedMapOf(
                "device-id" to deviceId,
                "share-group-memo" to displayName,
                "powers" to DevicePermissionCodec.encode(permissions),
            ),
            authenticated = true,
            contentClass = "com.quvii.qvweb.userauth.bean.request.NoLoginShareCreateReqContent",
        )
        if (!response.isSuccess) {
            throw UserAuthException("generate-nologin-share-invitation", response.result ?: -1, response.content)
        }
        val fields = response.shareGroups.firstOrNull() ?: response.content
        val id = fields["share-group-id"].orEmpty()
        return ShareGroupCreation(
            group = shareGroup(fields, id),
            invitePage = response.content["invite-page"].orEmpty(),
            expireMinutes = response.content["expire-minutes"]?.toIntOrNull(),
        )
    }

    override suspend fun updateShareGroup(
        id: String,
        displayName: String,
        permissions: Set<DevicePermission>,
    ) {
        val response = wire.command(
            "update-share-group-config",
            linkedMapOf(
                "share-group-id" to id,
                "share-group-memo" to displayName,
                "powers" to DevicePermissionCodec.encode(permissions),
            ),
            authenticated = true,
            contentClass = "com.quvii.qvweb.userauth.bean.request.NoLoginShareUpdateReqContent",
        )
        if (!response.isSuccess) throw UserAuthException("update-share-group-config", response.result ?: -1, response.content)
    }

    override suspend fun deleteShareGroup(id: String) {
        val response = wire.command(
            "delete-share-group",
            linkedMapOf("share-group-id" to id),
            authenticated = true,
            contentClass = "com.quvii.qvweb.userauth.bean.request.NoLoginShareDeleteReqContent",
        )
        if (!response.isSuccess) throw UserAuthException("delete-share-group", response.result ?: -1, response.content)
    }

    private fun shareGroup(fields: Map<String, String>, id: String): ShareGroup = ShareGroup(
        id = id,
        name = fields["share-group-memo"] ?: fields["name"].orEmpty(),
        powers = fields["powers"].orEmpty(),
        shareCode = fields["share-code"].orEmpty(),
        expireTime = fields["expire-time"].orEmpty(),
    )

    /**
     * Lista los dispositivos vinculados a la cuenta de la sesion (get-device-list).
     *
     * Debe llamarse sobre la MISMA sesion (mismo [UserAuthWire]) que acepto la invitacion:
     * es la sesion la que lleva la cuenta vinculada. Contrato capturado del app oficial
     * (DevListReqContent con count=0/page=0 devuelve todas).
     */
    suspend fun getDeviceList(): List<UserAuthDevice> {
        val content = linkedMapOf(
            "count" to "0",
            "filter" to "",
            "order" to "0",
            "owner" to "",
            "page" to "0",
        )
        val response = wire.command(
            "get-device-list",
            content,
            contentClass = "com.quvii.qvweb.userauth.bean.request.DevListReqContent",
        )
        if (!response.isSuccess) {
            throw UserAuthException(
                command = "get-device-list",
                result = response.result ?: -1,
                responseContent = response.content,
            )
        }
        return response.devices.mapNotNull { d ->
            val id = d["id"] ?: return@mapNotNull null
            UserAuthDevice(
                id = id,
                name = d["name"].orEmpty(),
                model = d["model"].orEmpty(),
                type = d["type"].orEmpty(),
                channelNum = d["channel-num"]?.toIntOrNull() ?: 1,
                fromShare = d["from-share"] == "1",
                powers = d["powers"].orEmpty(),
            )
        }
    }

    /**
     * Credenciales dinamicas del device (get-device-token, DevDynamicPwdGetReqContent).
     *
     * Debe llamarse tras [login] en la MISMA sesion: el header lleva el session token
     * que el wire capturo del login. Emite auth-code/data-encode-key/dynamic-password
     * (los mismos campos que el accept), usados para el canal device y el stream.
     */
    suspend fun getDeviceToken(deviceId: String): DeviceTokenData {
        val content = linkedMapOf("device-id" to deviceId)
        val response = wire.command(
            "get-device-token",
            content,
            contentClass = "com.quvii.qvweb.userauth.bean.request.DevDynamicPwdGetReqContent",
        )
        if (!response.isSuccess) {
            throw UserAuthException(
                command = "get-device-token",
                result = response.result ?: -1,
                responseContent = response.content,
            )
        }
        println("TOKEN RAW=" + response.content.entries.joinToString { it.key + "=" + it.value })
        return DeviceTokenData(
            authCode = response.content["auth-code"]
                ?: response.content["out-auth-code"].orEmpty(),
            dataEncodeKey = response.content["data-encode-key"].orEmpty(),
            dynamicPassword = response.content["dynamic-password"].orEmpty(),
            passwordExpired = response.content["password-expired"].orEmpty(),
            transparentBasedata = response.content["transparent-basedata"].orEmpty(),
        )
    }
}

object UserAuthCloudFactory {

    /** Crea la fachada con un HttpClient OkHttp con timeouts acordes al long-poll. */
    fun create(
        baseUrl: String,
        clientId: String,
        httpClient: HttpClient = defaultClient(),
    ): UserAuthCloud = UserAuthCloud(UserAuthWire(httpClient, baseUrl, clientId))

    /**
     * Crea la fachada confiando en la CA privada Qualvision (cert autofirmado del server
     * userauth, sin CN en el subject): mismo comportamiento que el app oficial, que hace
     * pinning con su truststore BKS y usa AllowAllHostnameVerifier.
     *
     * @param caPem PEM de la CA raíz Qualvision (assets/ca.pem del app oficial).
     */
    fun createWithQualvisionCa(
        baseUrl: String,
        clientId: String,
        caPem: InputStream,
    ): UserAuthCloud {
        val started = System.currentTimeMillis()
        android.util.Log.d("WIBOX-LIVE", "auth factory BEGIN")
        val certificate = CertificateFactory.getInstance("X509").generateCertificate(caPem)
        android.util.Log.d("WIBOX-LIVE", "auth factory CA ${System.currentTimeMillis() - started}ms")
        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null, null)
            setCertificateEntry("qualvision", certificate)
        }
        android.util.Log.d("WIBOX-LIVE", "auth factory KS ${System.currentTimeMillis() - started}ms")
        val trustManagerFactory = TrustManagerFactory.getInstance("X509").apply { init(keyStore) }
        android.util.Log.d("WIBOX-LIVE", "auth factory TMF ${System.currentTimeMillis() - started}ms")
        val trustManager = trustManagerFactory.trustManagers[0] as X509TrustManager
        val sslContext = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trustManager), null) }
        android.util.Log.d("WIBOX-LIVE", "auth factory SSL ${System.currentTimeMillis() - started}ms")
        val okHttpClient = OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustManager)
            .hostnameVerifier { _, _ -> true }
            .protocols(listOf(okhttp3.Protocol.HTTP_1_1))
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
        val httpClient = HttpClient(OkHttp) {
            engine { preconfigured = okHttpClient }
            expectSuccess = false
        }
        return UserAuthCloud(UserAuthWire(httpClient, baseUrl, clientId))
    }

    private fun defaultClient(): HttpClient = HttpClient(OkHttp) {
        engine {
            config {
                connectTimeout(20, TimeUnit.SECONDS)
                readTimeout(45, TimeUnit.SECONDS)
                writeTimeout(30, TimeUnit.SECONDS)
            }
        }
        expectSuccess = false
    }
}
