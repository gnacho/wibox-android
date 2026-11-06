package dev.nacho.wibox.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeSdkAssetsTest {

    @Test
    fun aPublicBuildMissesTheWholeVendorMaterial() {
        val missing = NativeSdkAssets.missing(hasAsset = { false }, hasNativeLibrary = false)

        assertEquals(
            listOf(
                NativeSdkAssets.Missing.NATIVE_LIBRARY,
                NativeSdkAssets.Missing.CLIENT_CERT,
                NativeSdkAssets.Missing.CLIENT_KEY,
            ),
            missing,
        )
    }

    @Test
    fun aLocalBuildWithTheVendorMaterialSatisfiesEveryRequirement() {
        val missing = NativeSdkAssets.missing(hasAsset = { true }, hasNativeLibrary = true)

        assertTrue(missing.isEmpty())
    }

    @Test
    fun theBundledCaIsNotRequestedAsASeparateAsset() {
        val packed = setOf(NativeSdkAssets.CA)
        val missing = NativeSdkAssets.missing(hasAsset = packed::contains, hasNativeLibrary = true)

        assertEquals(
            listOf(NativeSdkAssets.Missing.CLIENT_CERT, NativeSdkAssets.Missing.CLIENT_KEY),
            missing,
        )
        assertFalse(NativeSdkAssets.describe(missing).contains("qv_ca"))
    }

    @Test
    fun theReportSaysWhereEachMissingPieceGoes() {
        val report = NativeSdkAssets.describe(
            NativeSdkAssets.missing(hasAsset = { false }, hasNativeLibrary = false),
        )

        assertTrue(report.contains("jniLibs/arm64-v8a"))
        assertTrue(report.contains("assets/client.pem"))
        assertTrue(report.contains("assets/client.txt"))
    }

    @Test
    fun theCaAndTheLocalAssetsUseTheNamesTheSdkExpects() {
        assertEquals("ca.pem", NativeSdkAssets.CA)
        assertEquals(listOf("client.pem", "client.txt"), NativeSdkAssets.localAssets)
        assertEquals("qv-p2p-v2", NativeSdkAssets.NATIVE_LIBRARY)
    }
}
