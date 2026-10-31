package dev.nacho.wibox.externalapi

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.test.platform.app.InstrumentationRegistry
import dev.nacho.wibox.externalapi.aidl.ActionOutcome
import dev.nacho.wibox.externalapi.aidl.ActionRequest
import dev.nacho.wibox.externalapi.aidl.ApiError
import dev.nacho.wibox.externalapi.aidl.AuthorizationRequest
import dev.nacho.wibox.externalapi.aidl.ClientHello
import dev.nacho.wibox.externalapi.aidl.DeviceRef
import dev.nacho.wibox.externalapi.aidl.DeviceRequest
import dev.nacho.wibox.externalapi.aidl.DeviceSnapshot
import dev.nacho.wibox.externalapi.aidl.IActionCallback
import dev.nacho.wibox.externalapi.aidl.IDeviceCallback
import dev.nacho.wibox.externalapi.aidl.IShadowCallback
import dev.nacho.wibox.externalapi.aidl.IWiboxExternalService
import dev.nacho.wibox.externalapi.aidl.ShadowQuery
import dev.nacho.wibox.externalapi.aidl.ShadowSnapshot
import dev.nacho.wibox.externalapi.aidl.WireActionType
import dev.nacho.wibox.externalapi.aidl.WireApiErrorCode
import dev.nacho.wibox.externalapi.aidl.WireAuthorizationState
import dev.nacho.wibox.externalapi.aidl.WireFreshness
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Deny-by-default integration test over the real Binder service.
 *
 * The test process binds to the exported service with the app's own
 * signature and no allowlist entry, so every protected operation must come
 * back as NOT_AUTHORIZED without crashing and without leaking secrets.
 * A second connection checks that the handshake is required before anything
 * else.
 */
class ExternalApiDenyByDefaultTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext
    private val storeName = "wibox_external_api_allowlist"

    private val connections = mutableListOf<ServiceConnection>()
    private lateinit var service: IWiboxExternalService

    private class ConnectionHolder : ServiceConnection {
        val latch = CountDownLatch(1)
        var binder: IBinder? = null

        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            binder ?: return
            this.binder = binder
            latch.countDown()
        }

        override fun onServiceDisconnected(name: ComponentName?) = Unit
    }

    private fun bindAndAwait(): ConnectionHolder {
        val holder = ConnectionHolder()
        val intent = Intent().setClassName(context, "dev.nacho.wibox.externalapi.WiboxExternalApiService")
        val accepted = context.bindService(intent, holder, Context.BIND_AUTO_CREATE)
        assertTrue("bindService must accept the explicit intent", accepted)
        connections.add(holder)
        assertTrue("service must connect", holder.latch.await(20, TimeUnit.SECONDS))
        return holder
    }

    private fun clearAllowlist() {
        context.getSharedPreferences(storeName, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Before
    fun prepare() {
        clearAllowlist()
        val holder = bindAndAwait()
        service = IWiboxExternalService.Stub.asInterface(holder.binder)
    }

    @After
    fun release() {
        connections.forEach { context.unbindService(it) }
        connections.clear()
        clearAllowlist()
    }

    @Test
    fun handshakeSucceedsWithoutAGrantAndReportsNotRequested() {
        val result = service.handshake(hello())
        assertNull("handshake must not require an allowlist entry", result.error)
        assertEquals(1, result.serverApiMajor)
        assertEquals(0, result.negotiatedApiMinor)
        assertEquals(WireAuthorizationState.NOT_REQUESTED, result.authorization?.state)
    }

    @Test
    fun denyByDefaultForReadsActionsAndAuthorizationState() {
        // Handshake first: this connection is now known but NOT authorized.
        assertNull(service.handshake(hello()).error)

        // Provider snapshot: denied.
        assertEquals(WireApiErrorCode.NOT_AUTHORIZED, service.getProviderSnapshot().error?.code)

        // Authorization request: accepted as PENDING, which is not access.
        val request = service.requestAuthorization(
            AuthorizationRequest(
                displayName = "Instrumented deny test",
                requestedScopes = listOf("devices.read"),
                requestedDeviceIds = listOf("device-1"),
            ),
        )
        assertEquals(WireAuthorizationState.PENDING, request.authorization?.state)
        assertTrue(request.authorization?.grantedScopes?.isEmpty() == true)

        // Pending is still no access for every protected operation.
        assertEquals(WireApiErrorCode.NOT_AUTHORIZED, service.getProviderSnapshot().error?.code)
        assertEquals(WireApiErrorCode.AUTHORIZATION_PENDING, service.getAuthorizationState().authorization?.state)

        val deviceError = captureDeviceError { callback ->
            service.getDevice(DeviceRequest(requestId = "t-device", device = DeviceRef("demo", "cam-01", null)), callback)
        }
        assertNotNull(deviceError)
        assertEquals(WireApiErrorCode.NOT_AUTHORIZED, deviceError!!.code)

        val shadowError = captureShadowError { callback ->
            service.getDeviceShadow(
                ShadowQuery(
                    requestId = "t-shadow",
                    device = DeviceRef("demo", "cam-01", null),
                    fieldNames = listOf("Device_state"),
                    freshness = WireFreshness.ANY,
                ),
                callback,
            )
        }
        assertNotNull(shadowError)
        assertEquals(WireApiErrorCode.NOT_AUTHORIZED, shadowError!!.code)

        val actionError = captureActionError { callback ->
            service.performAction(
                ActionRequest(
                    requestId = "t-action",
                    device = DeviceRef("demo", "cam-01", null),
                    type = WireActionType.UNLOCK,
                    parameters = emptyList(),
                    expiresAtEpochMs = System.currentTimeMillis() + 60_000,
                ),
                callback,
            )
        }
        assertNotNull(actionError)
        assertEquals(WireApiErrorCode.NOT_AUTHORIZED, actionError!!.code)

        // Safe messages must never carry credential or transport details.
        listOfNotNull(deviceError, shadowError, actionError).forEach { error ->
            val message = error.safeMessage
            assertTrue(
                "error message must stay free of credential or transport details",
                message == null || (!message.contains("token", true) && !message.contains("http", true)),
            )
        }
    }

    @Test
    fun everyCallWithoutHandshakeReturnsHandshakeRequired() {
        // A separate connection gets a fresh stub that has not done the handshake.
        val fresh = IWiboxExternalService.Stub.asInterface(bindAndAwait().binder)
        assertEquals(WireApiErrorCode.HANDSHAKE_REQUIRED, fresh.getAuthorizationState().error?.code)
        assertEquals(WireApiErrorCode.HANDSHAKE_REQUIRED, fresh.getProviderSnapshot().error?.code)
    }

    private fun hello() = ClientHello(
        packageName = context.packageName,
        clientApiMajor = 1,
        clientApiMinor = 0,
        clientSdkVersion = "instrumented-test",
    )

    private fun captureDeviceError(call: (IDeviceCallback) -> Unit): ApiError? {
        val latch = CountDownLatch(1)
        var captured: ApiError? = null
        call(object : IDeviceCallback.Stub() {
            override fun onSuccess(result: DeviceSnapshot?) {
                latch.countDown()
            }

            override fun onError(error: ApiError?) {
                captured = error
                latch.countDown()
            }
        })
        latch.await(10, TimeUnit.SECONDS)
        return captured
    }

    private fun captureShadowError(call: (IShadowCallback) -> Unit): ApiError? {
        val latch = CountDownLatch(1)
        var captured: ApiError? = null
        call(object : IShadowCallback.Stub() {
            override fun onSuccess(result: ShadowSnapshot?) {
                latch.countDown()
            }

            override fun onError(error: ApiError?) {
                captured = error
                latch.countDown()
            }
        })
        latch.await(10, TimeUnit.SECONDS)
        return captured
    }

    private fun captureActionError(call: (IActionCallback) -> Unit): ApiError? {
        val latch = CountDownLatch(1)
        var captured: ApiError? = null
        call(object : IActionCallback.Stub() {
            override fun onOutcome(result: ActionOutcome?) {
                latch.countDown()
            }

            override fun onError(error: ApiError?) {
                captured = error
                latch.countDown()
            }
        })
        latch.await(10, TimeUnit.SECONDS)
        return captured
    }
}
