package dev.nacho.wibox.data

/**
 * What the vendor P2P SDK needs before it can start and what this project ships.
 *
 * The trust anchor is the Qualvision root that already lives in `assets/ca.pem`,
 * the same one the cloud channels use, so it is written out at runtime and never
 * duplicated. The client certificate and its private key belong to the
 * manufacturer: they stay out of version control and are dropped locally by
 * whoever builds with the native SDK.
 */
object NativeSdkAssets {

    /** Qualvision root CA bundled with the app. Public certificate, not a secret. */
    const val CA = "ca.pem"

    /** Manufacturer client certificate. Not redistributable. */
    const val CLIENT_CERT = "client.pem"

    /** Manufacturer private key. Not redistributable. */
    const val CLIENT_KEY = "client.txt"

    /** Native library behind [com.quvii.p2pv2.QvP2PV2Api]. Not redistributable. */
    const val NATIVE_LIBRARY = "qv-p2p-v2"

    /** Assets a local build has to place in `app/src/main/assets/`. */
    val localAssets: List<String> = listOf(CLIENT_CERT, CLIENT_KEY)

    /** One requirement the running build does not satisfy. */
    enum class Missing(val detail: String) {
        NATIVE_LIBRARY("libqv-p2p-v2.so in app/src/main/jniLibs/arm64-v8a/"),
        CLIENT_CERT("app/src/main/assets/" + NativeSdkAssets.CLIENT_CERT),
        CLIENT_KEY("app/src/main/assets/" + NativeSdkAssets.CLIENT_KEY),
    }

    /**
     * Requirements this build cannot satisfy, in the order they are checked.
     *
     * @param hasAsset whether the asset is packed in the APK.
     * @param hasNativeLibrary whether the vendor library could be loaded.
     */
    fun missing(hasAsset: (String) -> Boolean, hasNativeLibrary: Boolean): List<Missing> = buildList {
        if (!hasNativeLibrary) add(Missing.NATIVE_LIBRARY)
        if (!hasAsset(CLIENT_CERT)) add(Missing.CLIENT_CERT)
        if (!hasAsset(CLIENT_KEY)) add(Missing.CLIENT_KEY)
    }

    /**
     * Single log line explaining why the vendor path is off and where each piece
     * goes. The CA is never listed: it is derived from [CA].
     */
    fun describe(missing: List<Missing>): String =
        "vendor P2P SDK off, missing " + missing.joinToString("; ") { it.detail }
}
