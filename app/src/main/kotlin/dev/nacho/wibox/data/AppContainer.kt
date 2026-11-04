package dev.nacho.wibox.data

import android.content.Context
import dev.nacho.wibox.data.demo.DataStoreDemoStore
import dev.nacho.wibox.data.demo.DemoDevice
import dev.nacho.wibox.data.demo.DemoFixtures
import dev.nacho.wibox.data.demo.DemoWiboxProvider
import dev.nacho.wibox.data.demo.LocalDemoStore
import dev.nacho.wibox.core.cloud.DeviceDataSource
import dev.nacho.wibox.core.cloud.DeviceRef
import dev.nacho.wibox.core.cloud.DeviceRole
import dev.nacho.wibox.core.cloud.DeviceSnapshot
import dev.nacho.wibox.core.cloud.ProviderId
import dev.nacho.wibox.core.cloud.ProviderError
import dev.nacho.wibox.core.cloud.ShareInvitation
import dev.nacho.wibox.core.cloud.UserAuthCloud
import dev.nacho.wibox.core.cloud.UserAuthCloudFactory
import dev.nacho.wibox.core.cloud.UserAuthException
import dev.nacho.wibox.core.cloud.diag.LiveDiag
import dev.nacho.wibox.core.cloud.UserAuthTimeoutException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AppContainer(
    private val demoStore: LocalDemoStore,
    actionStore: ActionJournalStore,
    private val userAuthCloudFactory: () -> UserAuthCloud = {
        UserAuthCloudFactory.create(
            baseUrl = REAL_CLOUD_BASE_URL,
            clientId = REAL_CLOUD_CLIENT_ID,
        )
    },
    private val caPem: ByteArray? = null,
    private val ustTables: dev.nacho.wibox.core.cloud.quii.UstCrypto.Tables? = null,
    private val appContext: Context? = null,
) {
    private val provider = DemoWiboxProvider(persist = demoStore::save)

    private val userAuthCloud by lazy(userAuthCloudFactory)

    private val invitationOperations by lazy {
        UserAuthInvitationOperations(userAuthCloudFactory) {
            provider.cloudCredentials() ?: (REAL_CLOUD_ACCOUNT to REAL_CLOUD_PASSWORD)
        }
    }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Ultima sesion viva por SDK, para poder cerrarla aunque muera la Activity. */
    @Volatile
    private var activeSdkSession: dev.nacho.wibox.core.cloud.quii.SdkTunnelLiveSession? = null

    @Volatile
    private var activeSdkDeviceId: String? = null

    /**
     * Abre una sesion de video remoto (canal P2P+RB-UDP+QUII) con el timbre.
     * Requiere credenciales cloud guardadas (accept previo) y los assets
     * ca.pem + quii/p2p_table_*.bin cargados en local().
     */
    private data class CloudAccess(
        val authCode: String,
        val dataEncodeKey: String,
        val dynamicPassword: String,
        val sessionToken: String,
        val unlockPassword: String,
    )

    private suspend fun resolveAccess(deviceId: String): CloudAccess {
        val auth = userAuthCloud
        if (provider.cloudCredentials() == null && REAL_CLOUD_ACCOUNT.isEmpty()) {
            throw IllegalStateException(
                "sin credenciales del timbre: define wibox.account y wibox.password en local.properties " +
                    "o acepta una invitacion desde la app",
            )
        }
        val login = loginWithFallback(auth, provider.cloudCredentials())
        val token = userAuthCloud.getDeviceToken(deviceId)
        LiveDiag.add("token ok key=" + token.dataEncodeKey.length + "B")
        return CloudAccess(
            authCode = token.authCode,
            dataEncodeKey = token.dataEncodeKey,
            dynamicPassword = token.dynamicPassword,
            sessionToken = login.session ?: "",
            unlockPassword = provider.unlockPassword(deviceId)
                ?: REAL_DEVICE_UNLOCK_PASSWORD,
        )
    }

    /**
     * Login con las credenciales guardadas y, si el proveedor las rechaza por
     * contrasena o cuenta, reintento con la cuenta vinculada al timbre. Las
     * invitaciones caducan y dejan credenciales muertas en el snapshot, que
     * sombreaban a las buenas y daban result=100100003.
     */
    private suspend fun loginWithFallback(
        auth: UserAuthCloud,
        stored: Pair<String, String>?,
    ): dev.nacho.wibox.core.cloud.UserAuthLogin {
        if (stored != null) {
            try {
                val login = auth.login(stored.first, stored.second)
                android.util.Log.d(
                    "WIBOX-LIVE",
                    "cloud login OK (guardadas) session=" + (login.session?.isNotBlank() == true),
                )
                LiveDiag.add("login guardadas ok")
                return login
            } catch (e: UserAuthException) {
                val retry = e.result == RESULT_PASSWORD_ERROR || e.result == RESULT_ACCOUNT_MISSING
                android.util.Log.d(
                    "WIBOX-LIVE",
                    "login guardadas rechazado result=" + e.result + " retry=" + retry,
                )
                LiveDiag.add("login guardadas rechazado r=" + e.result)
                if (!retry) throw e
            }
        }
        val login = auth.login(REAL_CLOUD_ACCOUNT, REAL_CLOUD_PASSWORD)
        android.util.Log.d(
            "WIBOX-LIVE",
            "cloud login OK (respaldo) session=" + (login.session?.isNotBlank() == true),
        )
        LiveDiag.add("login respaldo ok")
        return login
    }

    /**
     * Abre el live usando el SDK propietario como transporte: el SDK mapea el
     * puerto de media del timbre a 127.0.0.1 y nosotros hablamos QUII por TCP,
     * igual que el reproductor de la app oficial.
     */
    suspend fun openSdkTunnelLive(
        deviceId: String,
        channel: Int = 1,
        stream: Int = 1,
    ): dev.nacho.wibox.core.cloud.quii.SdkTunnelLiveSession {
        val context = appContext ?: throw IllegalStateException("sin contexto (AppContainer no inicializado via local)")
        val pem = caPem ?: throw IllegalStateException("sin ca.pem (AppContainer no inicializado via local)")
        val fixed = dev.nacho.wibox.core.cloud.quii.LiveTuning.fixedPort
        // El enlace P2P no necesita credenciales, asi que se abre en paralelo con
        // el login y el token. En serie se sumaban varios segundos antes del PLAY.
        val portJob = if (fixed > 0) {
            null
        } else {
            appScope.async { com.quvii.p2pv2.NativeP2PWake.openStreamPort(context, pem, deviceId) }
        }
        val access = try {
            resolveAccess(deviceId)
        } catch (error: Throwable) {
            portJob?.cancel()
            throw error
        }
        val port: Int
        if (fixed > 0) {
            port = fixed
            android.util.Log.d("WIBOX-LIVE", "puerto fijo port=$port (sin crear enlace)")
        } else {
            val mapped = portJob!!.await()
            if (!mapped.ok) throw IllegalStateException("tunel SDK no disponible: " + mapped.describe())
            port = mapped.port
            android.util.Log.d("WIBOX-LIVE", "SDK tunnel port=$port")
        }
        val key = access.dataEncodeKey.toByteArray(Charsets.UTF_8).copyOf(32)
        val transport = dev.nacho.wibox.core.cloud.quii.TcpQuiiTransport("127.0.0.1", port, key)
        transport.connect()
        val playPassword = if (dev.nacho.wibox.core.cloud.quii.LiveTuning.passwordMode == 1) {
            access.dynamicPassword.ifEmpty {
                dev.nacho.wibox.core.cloud.quii.QuiiCrypto.encodeDevicePassword(access.authCode)
            }
        } else {
            dev.nacho.wibox.core.cloud.quii.QuiiCrypto.encodeDevicePassword(access.authCode)
        }
        android.util.Log.d(
            "WIBOX-LIVE",
            "play pwd mode=${dev.nacho.wibox.core.cloud.quii.LiveTuning.passwordMode} len=${playPassword.length} dynLen=${access.dynamicPassword.length}",
        )
        val live = dev.nacho.wibox.core.cloud.quii.QuiiLiveSession(
            transport = transport,
            config = dev.nacho.wibox.core.cloud.quii.QuiiLiveConfig(
                username = "adminapp2",
                password = playPassword,
                channel = channel,
                stream = stream,
                cryptoKey = key,
            ),
            scope = appScope,
        )
        val result = runCatching { live.open() }.getOrElse { error ->
            runCatching { live.close() }
            runCatching { transport.close() }
            throw error
        }
        if (result is dev.nacho.wibox.core.cloud.quii.QuiiLiveSession.OpenResult.Rejected) {
            runCatching { live.close() }
            runCatching { transport.close() }
            throw java.net.ConnectException(
                "QUII rechazado status=${result.status} crypto=${result.cryptoMode} sha=${result.shaProfile}",
            )
        }
        return dev.nacho.wibox.core.cloud.quii.SdkTunnelLiveSession(
            transport = transport,
            live = live,
            unlockPassword = access.unlockPassword,
            scope = appScope,
        ).also {
            activeSdkSession = it
            activeSdkDeviceId = deviceId
        }
    }

    /**
     * Cierra cualquier sesion viva y suelta el puerto P2P. Lo llama el servicio en
     * primer plano al morir: si el sistema mata la Activity sin onDestroy, el socket
     * seguiria abierto y el timbre responderia BUSY a todos.
     */
    fun closeAllLive() {
        runCatching { activeSdkSession?.closeNow() }
        activeSdkSession = null
        activeSdkDeviceId?.let { runCatching { closeSdkPort(it) } }
        activeSdkDeviceId = null
    }

    /** Releases the SDK port mapping so the doorbell does not keep it open. */
    fun closeSdkPort(deviceId: String) {
        runCatching { com.quvii.p2pv2.NativeP2PWake.closePort(deviceId) }
    }

    /** Consulta de diagnostico al CGI local del timbre a traves del puerto P2P. */
    suspend fun probeCgi(deviceId: String, commands: String): String = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val context = appContext ?: return@withContext "sin contexto"
        val pem = caPem ?: return@withContext "sin ca.pem"
        val access = resolveAccess(deviceId)
        val mapped = com.quvii.p2pv2.NativeP2PWake.openCgiPort(context, pem, deviceId)
        if (!mapped.ok) return@withContext "cgi no disponible: " + mapped.describe()
        android.util.Log.d("WIBOX-LIVE", "CGI port=${mapped.port}")
        val password = dev.nacho.wibox.core.cloud.quii.QuiiCrypto.encodeDevicePassword(access.authCode)
        val results = StringBuilder()
        for (command in commands.split(";").map { it.trim() }.filter { it.isNotEmpty() }) {
            val envelope = "<envelope><body><command>" + command + "</command><content /></body>" +
                "<header><password>" + password + "</password>" +
                "<passwordencode>1</passwordencode><security>username</security>" +
                "<username>adminapp2</username></header></envelope>"
            val text = runCatching { cgiPost(mapped.port, "/tdkcgi", envelope, "adminapp2", password) }
                .getOrElse { "fallo: " + it }
            val body = text.substringAfter("?>", text)
                .replace("\n", " ").replace("\r", "").replace(Regex(" +"), " ").trim()
            android.util.Log.d("WIBOX-LIVE", "CGI $command => ${body.take(500)}")
            results.append("[").append(command).append("] ").append(body.take(500)).append("\n")
        }
        results.toString()
    }

    private fun cgiPost(port: Int, path: String, envelope: String, user: String, password: String): String {
            val body = envelope.toByteArray(Charsets.UTF_8)
            val auth = android.util.Base64.encodeToString(
                (user + ":" + password).toByteArray(Charsets.UTF_8),
                android.util.Base64.NO_WRAP,
            )
            val request = "POST " + path + " HTTP/1.1\r\n" +
                "Host: 127.0.0.1:" + port + "\r\n" +
                "Authorization: Basic " + auth + "\r\n" +
                "Content-Type: text/xml\r\n" +
                "Charset: utf-8\r\n" +
                "Content-Length: " + body.size + "\r\n" +
                "Connection: close\r\n\r\n"
            val trustAll = arrayOf<javax.net.ssl.TrustManager>(object : javax.net.ssl.X509TrustManager {
                override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) = Unit
                override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) = Unit
                override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = emptyArray()
            })
            val sslContext = javax.net.ssl.SSLContext.getInstance("TLS")
            sslContext.init(null, trustAll, java.security.SecureRandom())
            val raw = java.net.Socket()
            raw.connect(java.net.InetSocketAddress("127.0.0.1", port), 8_000)
            raw.soTimeout = 8_000
            val ssl = sslContext.socketFactory.createSocket(raw, "127.0.0.1", port, true) as javax.net.ssl.SSLSocket
            return ssl.use {
                it.startHandshake()
                it.getOutputStream().apply {
                    write(request.toByteArray(Charsets.UTF_8))
                    write(body)
                    flush()
                }
                val input = it.getInputStream()
                val buffer = java.io.ByteArrayOutputStream()
                val chunk = ByteArray(4096)
                it.soTimeout = 3_000
                var idle = 0
                while (idle < 2) {
                    val read = runCatching { input.read(chunk) }.getOrElse { -1 }
                    if (read > 0) {
                        buffer.write(chunk, 0, read)
                        idle = 0
                    } else {
                        idle++
                    }
                }
                buffer.toString("UTF-8")
            }
    }

    suspend fun openRemoteLive(
        deviceId: String,
        channel: Int = 1,
        stream: Int = 2,
        p2pAttempts: Int = 20,
    ): dev.nacho.wibox.core.cloud.quii.RemoteLiveSession {
        val pem = caPem ?: throw IllegalStateException("sin ca.pem (AppContainer no inicializado via local)")
        val tables = ustTables ?: throw IllegalStateException("sin tablas UST (AppContainer no inicializado via local)")
        val access = resolveAccess(deviceId)
        val session = dev.nacho.wibox.core.cloud.quii.RemoteLiveSession(
            serviceUrl = REAL_CLOUD_BASE_URL,
            clientId = REAL_CLOUD_CLIENT_ID,
            deviceId = deviceId,
            channel = channel,
            stream = stream,
            authCode = access.authCode,
            dataEncodeKey = access.dataEncodeKey,
            caPem = pem,
            tables = tables,
            dynamicPassword = access.dynamicPassword,
            unlockPassword = access.unlockPassword,
            sessionToken = access.sessionToken,
            p2pAttempts = p2pAttempts,
        )
        val remote = session
        remote.open(appScope)
        return remote
    }

    /** Login ya hecho + get-device-list + merge al provider (el caller registra logs de fallo). */
    private suspend fun syncCloudAndMerge(): List<dev.nacho.wibox.core.cloud.UserAuthDevice> {
        val devices = userAuthCloud.getDeviceList()
        android.util.Log.d(
            "WIBOX-ACCEPT",
            "GET-DEVICE-LIST OK n=" + devices.size + " " +
                devices.joinToString(separator = ", ") { it.id + "/" + it.model + "/" + it.name + if (it.fromShare) " (share)" else "" },
        )
        provider.mergeCloudDevices(
            devices.map { d ->
                DemoDevice(
                    id = d.id,
                    name = d.name.ifBlank { "Wi-Box" },
                    model = d.model.ifBlank { "Wi-Box" },
                    online = true,
                    shared = d.fromShare,
                )
            },
        )
        return devices
    }

    val repository = WiboxRepository(
        provider = provider,
        operations = provider,
        actionCoordinator = ActionCoordinator(provider, actionStore),
        cloudInvitations = invitationOperations,
        firmwareInspector = LanFirmwareInspector(
            userAuthCloudFactory = userAuthCloudFactory,
            credentials = { provider.cloudCredentials() ?: (REAL_CLOUD_ACCOUNT to REAL_CLOUD_PASSWORD) },
        ),
        restore = {
            provider.initializeDemo(demoStore.load() ?: DemoFixtures.initial())
            // Sesion cloud persistente: si hay credenciales de un accept anterior,
            // login + get-device-list en segundo plano (el timbre aparece al abrir).
            // Sin credenciales aceptadas en este movil se usan las de local.properties,
            // si no la lista se queda con los dispositivos de demo y el timbre real no aparece.
            val startup = provider.cloudCredentials() ?: (REAL_CLOUD_ACCOUNT to REAL_CLOUD_PASSWORD)
            if (startup.first.isNotEmpty() && startup.second.isNotEmpty()) {
                appScope.launch {
                    try {
                        userAuthCloud.login(startup.first, startup.second)
                        syncCloudAndMerge()
                        android.util.Log.d("WIBOX-ACCEPT", "STARTUP SYNC OK account=" + startup.first)
                    } catch (error: Exception) {
                        android.util.Log.d(
                            "WIBOX-ACCEPT",
                            "STARTUP SYNC FAIL " + error::class.java.simpleName + ": " + error.message,
                        )
                    }
                }
            }
        },
        realInvitationAcceptor = { invitation, displayName ->
            android.util.Log.d("WIBOX-ACCEPT", "ACCEPTOR: launching acceptShareInvitation deviceId=" + invitation.deviceId + " code=" + invitation.inviteCode)
            val acceptancePassword = java.util.UUID.randomUUID().toString()
            val acceptance: dev.nacho.wibox.core.cloud.ShareAcceptance
            try {
                acceptance = userAuthCloud.acceptShareInvitation(
                    ShareInvitation(
                        deviceId = invitation.deviceId,
                        inviteCode = invitation.inviteCode,
                        ownerId = invitation.ownerId,
                        oemId = invitation.oemId,
                        appId = invitation.appId,
                        from = invitation.from,
                    ),
                    name = displayName,
                    newAccountPassword = acceptancePassword,
                )
            } catch (timeout: UserAuthTimeoutException) {
                throw ProviderError.Timeout("acceptShareInvitation")
            } catch (rejected: UserAuthException) {
                throw when (rejected.result) {
                    100151007 -> ProviderError.InvitationUsed("acceptShareInvitation")
                    100100003 -> ProviderError.Conflict("acceptShareInvitation")
                    else -> ProviderError.Network("acceptShareInvitation")
                }
            }
            // Credenciales de la cuenta nologin creada/vinculada: se registran para poder
            // reutilizarlas (login + get-device-list en sesiones futuras).
            android.util.Log.d(
                "WIBOX-ACCEPT",
                "ACCEPTANCE account=" + acceptance.account + " id=" + acceptance.accountId +
                    " powers=" + acceptance.powers,
            )
            // Login con la cuenta creada/vinculada (emite el token de sesion) y persistencia
            // de credenciales: los arranques futuros restauran la lista sin nueva invitacion.
            val account = acceptance.account
            val cloudDevices = if (account.isNullOrBlank()) {
                android.util.Log.d("WIBOX-ACCEPT", "ACCEPTANCE sin account: sin login ni lista")
                emptyList()
            } else {
                try {
                    userAuthCloud.login(account, acceptancePassword)
                    provider.saveCloudCredentials(account, acceptancePassword)
                    android.util.Log.d("WIBOX-ACCEPT", "LOGIN OK account=" + account)
                    try {
                        syncCloudAndMerge()
                    } catch (error: Exception) {
                        android.util.Log.d(
                            "WIBOX-ACCEPT",
                            "GET-DEVICE-LIST FAIL " + error::class.java.simpleName + ": " + error.message,
                        )
                        emptyList()
                    }
                } catch (loginError: Exception) {
                    android.util.Log.d(
                        "WIBOX-ACCEPT",
                        "LOGIN FAIL " + loginError::class.java.simpleName + ": " + loginError.message,
                    )
                    emptyList()
                }
            }
            provider.mergeCloudDevices(
                cloudDevices.map { d ->
                    DemoDevice(
                        id = d.id,
                        name = d.name.ifBlank { displayName },
                        model = d.model.ifBlank { "Wi-Box" },
                        online = true,
                        shared = d.fromShare,
                    )
                },
            )
            val primary = cloudDevices.firstOrNull { it.id == invitation.deviceId } ?: cloudDevices.firstOrNull()
            DeviceSnapshot(
                device = DeviceRef(ProviderId.STOCK_QVCLOUD, primary?.id ?: invitation.deviceId),
                displayName = primary?.name?.ifBlank { null } ?: displayName,
                declaredModel = primary?.model?.ifBlank { null },
                role = DeviceRole.SHARED,
                source = DeviceDataSource.CLOUD,
                fresh = true,
            )
        },
    )

    suspend fun initialize() = repository.prepare()

    fun close() = repository.close()

    companion object {
        private const val REAL_CLOUD_BASE_URL = "https://r1-8.qvcloud.net"
        // Cuenta vinculada al timbre. Nunca en el repo: sale de local.properties
        // (wibox.account / wibox.password) o de variables de entorno. Vacio = sin
        // credenciales de respaldo, la app usa solo las guardadas al aceptar la invitacion.
        private val REAL_CLOUD_ACCOUNT: String = dev.nacho.wibox.BuildConfig.WIBOX_ACCOUNT
        private val REAL_CLOUD_PASSWORD: String = dev.nacho.wibox.BuildConfig.WIBOX_PASSWORD
        private val REAL_CLOUD_CLIENT_ID: String = dev.nacho.wibox.BuildConfig.WIBOX_CLIENT_ID
        private val REAL_DEVICE_UNLOCK_PASSWORD: String = dev.nacho.wibox.BuildConfig.WIBOX_UNLOCK_PASSWORD

        /** SDKStatus.EmErrFailedPasswordErr: el proveedor rechaza la contrasena. */
        private const val RESULT_PASSWORD_ERROR = 100100003

        /** SDKStatus.EmErrFailedAccountNotExist: la cuenta ya no existe. */
        private const val RESULT_ACCOUNT_MISSING = 100100002

        fun local(context: Context): AppContainer {
            val appContext = context.applicationContext
            val caPem = appContext.assets.open("ca.pem").use { it.readBytes() }
            val tables = dev.nacho.wibox.core.cloud.quii.UstCrypto.loadTables(
                appContext.assets.open("quii/p2p_table_t1.bin").use { it.readBytes() },
                appContext.assets.open("quii/p2p_table_t2.bin").use { it.readBytes() },
                appContext.assets.open("quii/p2p_table_t3.bin").use { it.readBytes() },
            )
            return AppContainer(
                demoStore = DataStoreDemoStore(appContext),
                actionStore = DataStoreActionJournalStore(appContext),
                userAuthCloudFactory = {
                    UserAuthCloudFactory.createWithQualvisionCa(
                        baseUrl = REAL_CLOUD_BASE_URL,
                        clientId = REAL_CLOUD_CLIENT_ID,
                        caPem = appContext.assets.open("ca.pem"),
                    )
                },
                caPem = caPem,
                ustTables = tables,
                appContext = appContext,
            )
        }
    }
}
