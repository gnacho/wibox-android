package dev.nacho.wibox.externalapi.server

import dev.nacho.wibox.externalapi.aidl.WireApiErrorCode
import dev.nacho.wibox.externalapi.aidl.WireAuthorizationState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Authorization tests covering the uid + packageName + signer-set allowlist:
 * deny by default, approval, revocation, signature and uid rotation, shared
 * uid, package spoofing, pending request limits and the two-signature case
 * (signatures=2) where only the correct signer set is accepted.
 */
class AuthorizationEngineTest {
    private val signerA = "3082a".toByteArray()
    private val signerB = "3082b".toByteArray()
    private val signerC = "3082c".toByteArray()
    private val twoSignatureSet = listOf(signerA, signerB)

    private var now = 1_000_000L
    private val store = InMemoryAuthorizationStore()
    private val engine = AuthorizationEngine(store) { now }

    private fun identity(
        uid: Int = 10_123,
        packageName: String = "com.thirdparty.app",
        certificates: List<ByteArray> = twoSignatureSet,
    ): CallerIdentity.Verified = CallerIdentity.Verified(
        uid = uid,
        packageName = packageName,
        signerSetSha256 = SignerSetHash.signerSetSha256(certificates),
        signerSha256s = SignerSetHash.signerSha256s(certificates),
    )

    private fun approveTestGrant(identityArg: CallerIdentity.Verified, scopes: List<String>, devices: List<String> = listOf("device-1")) {
        store.upsert(
            GrantRecord(
                uid = identityArg.uid,
                packageName = identityArg.packageName,
                signerSetSha256 = identityArg.signerSetSha256,
                state = WireAuthorizationState.PENDING,
                scopes = scopes,
                deviceIds = devices,
                grantGeneration = 0,
                displayName = "test",
                updatedAtEpochMs = now,
            ),
        )
        now += 1
        assertTrue(engine.approve(identityArg.uid, identityArg.packageName, identityArg.signerSetSha256))
    }

    @Test
    fun `deny by default when no allowlist entry exists`() {
        val decision = engine.authorize(identity())
        assertFalse(decision.authorized)
        assertEquals(WireApiErrorCode.NOT_AUTHORIZED, decision.errorCode)
        assertEquals(WireAuthorizationState.NOT_REQUESTED, engine.snapshot(identity()).state)
    }

    @Test
    fun `approved grant authorizes requested scope and device only`() {
        val caller = identity()
        approveTestGrant(caller, scopes = listOf(ApiScopes.DEVICES_READ, ApiScopes.SHADOW_READ))
        val decision = engine.checkAccess(caller, ApiScopes.DEVICES_READ, "device-1")
        assertTrue(decision.authorized)
        assertEquals(WireAuthorizationState.AUTHORIZED, engine.snapshot(caller).state)
        assertEquals(listOf(ApiScopes.DEVICES_READ, ApiScopes.SHADOW_READ), engine.snapshot(caller).grantedScopes)

        val wrongScope = engine.checkAccess(caller, ApiScopes.ACTIONS_UNLOCK, "device-1")
        assertFalse(wrongScope.authorized)
        assertEquals(WireApiErrorCode.NOT_AUTHORIZED, wrongScope.errorCode)

        val wrongDevice = engine.checkAccess(caller, ApiScopes.DEVICES_READ, "device-other")
        assertFalse(wrongDevice.authorized)

        val unknownDevice = engine.checkAccess(caller, ApiScopes.DEVICES_READ, null)
        assertTrue(unknownDevice.authorized)
    }

    @Test
    fun `empty device grant means access to zero devices`() {
        val caller = identity()
        approveTestGrant(caller, scopes = listOf(ApiScopes.DEVICES_READ), devices = emptyList())
        val decision = engine.checkAccess(caller, ApiScopes.DEVICES_READ, "device-1")
        assertFalse(decision.authorized)
    }

    @Test
    fun `revocation denies later calls and increments the grant generation`() {
        val caller = identity()
        approveTestGrant(caller, scopes = listOf(ApiScopes.DEVICES_READ))
        val generation = engine.snapshot(caller).grantGeneration
        assertTrue(engine.revoke(caller.uid, caller.packageName, caller.signerSetSha256))
        val decision = engine.authorize(caller)
        assertFalse(decision.authorized)
        assertEquals(WireApiErrorCode.AUTHORIZATION_REVOKED, decision.errorCode)
        assertEquals(generation + 1, engine.snapshot(caller).grantGeneration)
        // A revoked app can request again but stays pending without access.
        val outcome = engine.requestAuthorization(caller, "renamed", listOf(ApiScopes.DEVICES_READ), listOf("device-1"))
        assertEquals(WireAuthorizationState.PENDING, outcome.snapshot?.state)
        assertEquals(WireApiErrorCode.AUTHORIZATION_PENDING, engine.authorize(caller).errorCode)
    }

    @Test
    fun `two signature app is accepted with the correct set and rejected with a different set`() {
        val caller = identity(certificates = twoSignatureSet)
        approveTestGrant(caller, scopes = listOf(ApiScopes.DEVICES_READ))

        val sameSetDifferentOrder = identity(certificates = listOf(signerB, signerA))
        assertTrue(engine.checkAccess(sameSetDifferentOrder, ApiScopes.DEVICES_READ, "device-1").authorized)

        val wrongSignerSet = identity(certificates = listOf(signerA, signerC))
        assertEquals(
            WireApiErrorCode.SIGNATURE_CHANGED,
            engine.authorize(wrongSignerSet).errorCode,
        )

        val singleSigner = identity(certificates = listOf(signerA))
        assertEquals(
            WireApiErrorCode.SIGNATURE_CHANGED,
            engine.authorize(singleSigner).errorCode,
        )
    }

    @Test
    fun `signature rotation suspends the grant and requires re-approval`() {
        val caller = identity(certificates = twoSignatureSet)
        approveTestGrant(caller, scopes = listOf(ApiScopes.DEVICES_READ))
        val rotated = identity(certificates = listOf(signerC))
        assertEquals(WireApiErrorCode.SIGNATURE_CHANGED, engine.authorize(rotated).errorCode)
        assertEquals(WireAuthorizationState.SIGNATURE_CHANGED, engine.snapshot(rotated).state)
        // The previous signer set keeps access until the user revokes it.
        assertTrue(engine.authorize(caller).authorized)
    }

    @Test
    fun `uid change requires re-approval`() {
        val caller = identity(uid = 10_123)
        approveTestGrant(caller, scopes = listOf(ApiScopes.DEVICES_READ))
        val reinstalled = identity(uid = 10_999)
        assertEquals(WireApiErrorCode.UID_CHANGED, engine.authorize(reinstalled).errorCode)
    }

    @Test
    fun `shared uid is denied with SHARED_UID_UNSUPPORTED`() {
        val decision = engine.authorize(CallerIdentity.SharedUid(uid = 10_123, packages = listOf("com.one", "com.two")))
        assertEquals(WireApiErrorCode.SHARED_UID_UNSUPPORTED, decision.errorCode)
    }

    @Test
    fun `package spoofing is denied with PACKAGE_UID_MISMATCH`() {
        val decision = engine.authorize(CallerIdentity.PackageMismatch(uid = 10_123, claimedPackageName = "com.victim", uidPackages = listOf("com.thirdparty.app")))
        assertEquals(WireApiErrorCode.PACKAGE_UID_MISMATCH, decision.errorCode)
    }

    @Test
    fun `non application uids are denied`() {
        assertEquals(WireApiErrorCode.NOT_AUTHORIZED, engine.authorize(CallerIdentity.NonApplicationUid(1000)).errorCode)
        assertEquals(WireApiErrorCode.NOT_AUTHORIZED, engine.authorize(CallerIdentity.NonApplicationUid(99_500)).errorCode)
    }

    @Test
    fun `request authorization creates a pending record without granting access`() {
        val caller = identity()
        val outcome = engine.requestAuthorization(caller, "Mi integracion", listOf(ApiScopes.DEVICES_READ), listOf("device-1"))
        assertNull(outcome.errorCode)
        assertEquals(WireAuthorizationState.PENDING, outcome.snapshot?.state)
        assertTrue(outcome.snapshot?.grantedScopes?.isEmpty() == true)
        assertEquals(WireApiErrorCode.AUTHORIZATION_PENDING, engine.authorize(caller).errorCode)
    }

    @Test
    fun `request with unknown scope is rejected as invalid input`() {
        val caller = identity()
        val outcome = engine.requestAuthorization(caller, "x", listOf("admin.all"), emptyList())
        assertEquals(WireApiErrorCode.INVALID_INPUT, outcome.errorCode)
    }

    @Test
    fun `request on an authorized grant does not elevate scopes`() {
        val caller = identity()
        approveTestGrant(caller, scopes = listOf(ApiScopes.DEVICES_READ))
        val outcome = engine.requestAuthorization(caller, "x", listOf(ApiScopes.ACTIONS_UNLOCK), listOf("device-1"))
        assertEquals(WireAuthorizationState.AUTHORIZED, outcome.snapshot?.state)
        assertEquals(listOf(ApiScopes.DEVICES_READ), outcome.snapshot?.grantedScopes)
    }

    @Test
    fun `pending request limits are enforced per package and globally`() {
        val base = identity()
        // Three distinct identity tuples pending for the same package.
        val secondSigner = identity(certificates = listOf(signerC))
        val thirdUid = identity(uid = 10_999, certificates = listOf(signerC))
        listOf(base, secondSigner, thirdUid).forEachIndexed { index, caller ->
            val outcome = engine.requestAuthorization(caller, "req-$index", listOf(ApiScopes.DEVICES_READ), emptyList())
            assertNull(outcome.errorCode, "request $index should be accepted")
        }
        val fourthTuple = identity(uid = 10_888, certificates = listOf(signerC))
        val fourth = engine.requestAuthorization(fourthTuple, "req-4", listOf(ApiScopes.DEVICES_READ), emptyList())
        assertEquals(WireApiErrorCode.RATE_LIMITED, fourth.errorCode)

        // A second package still fits the global budget.
        val other = identity(packageName = "com.other.app")
        val otherOutcome = engine.requestAuthorization(other, "other", listOf(ApiScopes.DEVICES_READ), emptyList())
        assertNull(otherOutcome.errorCode)
    }

    @Test
    fun `global pending budget is enforced across packages`() {
        val tinyStore = InMemoryAuthorizationStore()
        val tinyEngine = AuthorizationEngine(tinyStore) { now }
        var filler = 0
        while (filler < 50) {
            tinyStore.upsert(
                GrantRecord(
                    uid = 20_000 + filler,
                    packageName = "com.filler.$filler",
                    signerSetSha256 = "digest-$filler",
                    state = WireAuthorizationState.PENDING,
                    scopes = emptyList(),
                    deviceIds = emptyList(),
                    grantGeneration = 0,
                    displayName = "filler",
                    updatedAtEpochMs = now,
                ),
            )
            filler += 1
        }
        val outcome = tinyEngine.requestAuthorization(identity(), "overflow", listOf(ApiScopes.DEVICES_READ), emptyList())
        assertEquals(WireApiErrorCode.RATE_LIMITED, outcome.errorCode)
    }

    @Test
    fun `latest user decision wins over older records`() {
        val caller = identity()
        approveTestGrant(caller, scopes = listOf(ApiScopes.DEVICES_READ))
        now += 10
        assertTrue(engine.revoke(caller.uid, caller.packageName, caller.signerSetSha256))
        now += 10
        val outcome = engine.requestAuthorization(caller, "again", listOf(ApiScopes.DEVICES_READ), listOf("device-1"))
        assertNull(outcome.errorCode)
        assertTrue(engine.approve(caller.uid, caller.packageName, caller.signerSetSha256))
        assertTrue(engine.authorize(caller).authorized)
        assertNotEquals(WireApiErrorCode.AUTHORIZATION_REVOKED, engine.authorize(caller).errorCode)
    }

    @Test
    fun `identity resolver rejects isolated and system uids`() {
        val packages = UidPackageSource { listOf("com.x") }
        val certificates = SigningCertificateSource { listOf(signerA) }
        assertTrue(CallerIdentityResolver.resolve(999, null, packages, certificates) is CallerIdentity.NonApplicationUid)
        assertTrue(CallerIdentityResolver.resolve(99_500, null, packages, certificates) is CallerIdentity.NonApplicationUid)
        assertTrue(CallerIdentityResolver.resolve(10_123, null, packages, certificates) is CallerIdentity.Verified)
    }

    @Test
    fun `identity resolver flags shared uid and mismatch`() {
        val shared = CallerIdentityResolver.resolve(
            10_123,
            "com.claimed",
            UidPackageSource { listOf("com.one", "com.two") },
            SigningCertificateSource { listOf(signerA) },
        )
        assertTrue(shared is CallerIdentity.SharedUid)

        val mismatch = CallerIdentityResolver.resolve(
            10_123,
            "com.claimed",
            UidPackageSource { listOf("com.real") },
            SigningCertificateSource { listOf(signerA) },
        )
        assertTrue(mismatch is CallerIdentity.PackageMismatch)
    }

    @Test
    fun `token bucket bounds bursts and reports retry delay`() {
        val bucket = TokenBucket(capacity = 2, refillPerMinute = 60) { now }
        assertNull(bucket.tryAcquire())
        assertNull(bucket.tryAcquire())
        val retryAfter = bucket.tryAcquire()
        assertTrue(retryAfter != null && retryAfter in 1..60_000)
        now += 1_000
        assertNull(bucket.tryAcquire())
    }
}
