package dev.nacho.wibox.data

import android.content.Context
import dev.nacho.wibox.data.demo.DataStoreDemoStore
import dev.nacho.wibox.data.demo.DemoFixtures
import dev.nacho.wibox.data.demo.DemoWiboxProvider
import dev.nacho.wibox.data.demo.LocalDemoStore
import dev.nacho.wibox.core.cloud.DeviceDataSource
import dev.nacho.wibox.core.cloud.DeviceRef
import dev.nacho.wibox.core.cloud.DeviceRole
import dev.nacho.wibox.core.cloud.DeviceSnapshot
import dev.nacho.wibox.core.cloud.ProviderId
import dev.nacho.wibox.core.cloud.ShareInvitation
import dev.nacho.wibox.core.cloud.UserAuthCloud
import dev.nacho.wibox.core.cloud.UserAuthCloudFactory

class AppContainer(
    private val demoStore: LocalDemoStore,
    actionStore: ActionJournalStore,
    userAuthCloudFactory: () -> UserAuthCloud = {
        UserAuthCloudFactory.create(
            baseUrl = REAL_CLOUD_BASE_URL,
            clientId = REAL_CLOUD_CLIENT_ID,
        )
    },
) {
    private val provider = DemoWiboxProvider(persist = demoStore::save)

    private val userAuthCloud by lazy(userAuthCloudFactory)

    val repository = WiboxRepository(
        provider = provider,
        operations = provider,
        actionCoordinator = ActionCoordinator(provider, actionStore),
        restore = { provider.initializeDemo(demoStore.load() ?: DemoFixtures.initial()) },
        realInvitationAcceptor = { invitation, displayName ->
            android.util.Log.d("WIBOX-ACCEPT", "ACCEPTOR: launching acceptShareInvitation deviceId=" + invitation.deviceId + " code=" + invitation.inviteCode)
            userAuthCloud.acceptShareInvitation(
                ShareInvitation(
                    deviceId = invitation.deviceId,
                    inviteCode = invitation.inviteCode,
                    ownerId = invitation.ownerId,
                    oemId = invitation.oemId,
                    appId = invitation.appId,
                    from = invitation.from,
                ),
                name = displayName,
            )
            DeviceSnapshot(
                device = DeviceRef(ProviderId.STOCK_QVCLOUD, invitation.deviceId),
                displayName = displayName,
                declaredModel = null,
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
        private const val REAL_CLOUD_CLIENT_ID = "003-2021-a1b2c3d4e5f60718"

        fun local(context: Context): AppContainer = AppContainer(
            demoStore = DataStoreDemoStore(context.applicationContext),
            actionStore = DataStoreActionJournalStore(context.applicationContext),
            userAuthCloudFactory = {
                context.applicationContext.assets.open("ca.pem").use { caPem ->
                    UserAuthCloudFactory.createWithQualvisionCa(
                        baseUrl = REAL_CLOUD_BASE_URL,
                        clientId = REAL_CLOUD_CLIENT_ID,
                        caPem = caPem,
                    )
                }
            },
        )
    }
}
