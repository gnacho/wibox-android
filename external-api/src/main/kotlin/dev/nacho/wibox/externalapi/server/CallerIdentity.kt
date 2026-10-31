package dev.nacho.wibox.externalapi.server

/**
 * Caller identity derived inside the Binder transaction. The claimed package
 * name sent by the client is only an assertion to contrast, never evidence.
 */
sealed interface CallerIdentity {
    val uid: Int

    /** Single package under a uid whose current signer set could be read. */
    data class Verified(
        override val uid: Int,
        val packageName: String,
        val signerSetSha256: String,
        val signerSha256s: List<String>,
    ) : CallerIdentity

    /** More than one package shares this uid: denied in v1. */
    data class SharedUid(override val uid: Int, val packages: List<String>) : CallerIdentity

    /** The claimed package name does not belong to the calling uid. */
    data class PackageMismatch(
        override val uid: Int,
        val claimedPackageName: String,
        val uidPackages: List<String>,
    ) : CallerIdentity

    /** The package has no readable current signing certificates. */
    data class NoSigningInfo(override val uid: Int, val packageName: String) : CallerIdentity

    /** System, isolated or otherwise non-application uid. */
    data class NonApplicationUid(override val uid: Int) : CallerIdentity
}

/** Resolves the packages owned by a Linux uid (production: PackageManager.getPackagesForUid). */
fun interface UidPackageSource {
    fun packagesForUid(uid: Int): List<String>
}

/** Reads the current signer certificates of an installed package as raw DER bytes. */
fun interface SigningCertificateSource {
    fun currentSignerCertificates(packageName: String): List<ByteArray>
}

object CallerIdentityResolver {
    const val FIRST_APPLICATION_UID = 10000
    const val FIRST_ISOLATED_UID = 99000
    const val LAST_ISOLATED_UID = 99999

    /**
     * @param claimedPackageName assertion sent by the client, or null when the
     *   caller did not claim a package (methods without ClientHello).
     */
    fun resolve(
        uid: Int,
        claimedPackageName: String?,
        uidPackages: UidPackageSource,
        signingCertificates: SigningCertificateSource,
    ): CallerIdentity {
        if (uid < FIRST_APPLICATION_UID || uid in FIRST_ISOLATED_UID..LAST_ISOLATED_UID) {
            return CallerIdentity.NonApplicationUid(uid)
        }
        val packages = uidPackages.packagesForUid(uid)
        if (packages.isEmpty()) {
            return CallerIdentity.NonApplicationUid(uid)
        }
        if (packages.size > 1) {
            return CallerIdentity.SharedUid(uid, packages.sorted())
        }
        val packageName = packages.single()
        if (claimedPackageName != null && claimedPackageName != packageName) {
            return CallerIdentity.PackageMismatch(uid, claimedPackageName, packages)
        }
        val certificates = signingCertificates.currentSignerCertificates(packageName)
        if (certificates.isEmpty()) {
            return CallerIdentity.NoSigningInfo(uid, packageName)
        }
        return CallerIdentity.Verified(
            uid = uid,
            packageName = packageName,
            signerSetSha256 = SignerSetHash.signerSetSha256(certificates),
            signerSha256s = SignerSetHash.signerSha256s(certificates),
        )
    }
}
