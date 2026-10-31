package dev.nacho.wibox.core.cloud

import dev.nacho.wibox.core.cloud.internal.RealWiboxCloud
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal object Fixtures {
    fun text(name: String): String = requireNotNull(javaClass.getResource("/fixtures/$name")) {
        "Missing fixture $name"
    }.readText()
}

internal class TestClock(var now: Long = 1_800_000_000_000L) : CloudClock {
    override fun nowMillis(): Long = now
}

internal class RecordingTokenStore(initial: CloudSession? = null) : TokenStore {
    private val mutex = Mutex()
    private var stored: CloudSession? = initial
    val saves = mutableListOf<CloudSession>()
    var loads: Int = 0
        private set

    override suspend fun load(): CloudSession? = mutex.withLock {
        loads += 1
        stored
    }

    override suspend fun save(session: CloudSession) = mutex.withLock {
        stored = session
        saves += session
    }

    override suspend fun clear() = mutex.withLock {
        stored = null
    }
}

internal fun testConfig(): WiboxCloudConfig = WiboxCloudConfig(
    baseUrl = "https://unit.test/",
    clientId = "fixture-client",
    regionId = "fixture-region",
)

internal fun testCloud(
    engine: MockEngine,
    store: TokenStore,
    clock: CloudClock,
): RealWiboxCloud = RealWiboxCloud(testConfig(), store, clock, HttpClient(engine))

internal fun fixtureSession(): CloudSession = CloudSession(
    accessToken = "eyJhbGciOiJub25lIiwidHlwIjoiSldUIn0.eyJleHAiOjE4MDAwMDM2MDAsImZpeHR1cmUiOnRydWUsIm5hbWUiOiJhY2Nlc3MtcGFzc3dvcmQifQ.fixture-signature",
    refreshToken = "eyJhbGciOiJub25lIiwidHlwIjoiSldUIn0.eyJleHAiOjE4MDAwMDcyMDAsImZpeHR1cmUiOnRydWUsIm5hbWUiOiJyZWZyZXNoLXBhc3N3b3JkIn0.fixture-signature",
    accessTokenExpiresAtMillis = 1_800_003_600_000L,
    refreshTokenExpiresAtMillis = 1_800_007_200_000L,
)
