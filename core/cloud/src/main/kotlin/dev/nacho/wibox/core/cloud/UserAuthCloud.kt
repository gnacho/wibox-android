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
import okhttp3.OkHttpClient

/**
 * Fachada del canal userauth duplex (registro / login / compartición de dispositivos).
 *
 * Distinto del canal OAuth/Shadow de [WiboxCloud]: este es el canal XML tdkcloud
 * que usa el app oficial para el alta y la compartición sin credenciales.
 */
class UserAuthCloud internal constructor(
    private val wire: UserAuthWire,
) {

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
        val certificate = CertificateFactory.getInstance("X509").generateCertificate(caPem)
        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null, null)
            setCertificateEntry("qualvision", certificate)
        }
        val trustManagerFactory = TrustManagerFactory.getInstance("X509").apply { init(keyStore) }
        val trustManager = trustManagerFactory.trustManagers[0] as X509TrustManager
        val sslContext = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trustManager), null) }
        val okHttpClient = OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustManager)
            .hostnameVerifier { _, _ -> true }
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
