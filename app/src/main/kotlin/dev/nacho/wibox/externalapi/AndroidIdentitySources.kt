package dev.nacho.wibox.externalapi

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import dev.nacho.wibox.externalapi.server.SigningCertificateSource
import dev.nacho.wibox.externalapi.server.UidPackageSource

/**
 * Production identity sources backed by the Android PackageManager.
 * The caller uid and its packages are read here; the certificate never
 * travels from the client, it is always read from the platform.
 */
internal object AndroidIdentitySources {
    fun uidPackageSource(pm: PackageManager): UidPackageSource = UidPackageSource { uid ->
        runCatching { pm.getPackagesForUid(uid)?.toList() ?: emptyList() }.getOrDefault(emptyList())
    }

    fun signingCertificateSource(pm: PackageManager): SigningCertificateSource = SigningCertificateSource { packageName ->
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            // SigningInfo requires API 28; the handshake rejects older builds with UNSUPPORTED.
            return@SigningCertificateSource emptyList()
        }
        runCatching {
            val info = pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            val signingInfo = info.signingInfo ?: return@runCatching emptyList()
            signingInfo.apkContentsSigners.orEmpty().map { it.toByteArray() }
        }.getOrDefault(emptyList())
    }

    fun create(context: Context): Pair<UidPackageSource, SigningCertificateSource> {
        val pm = context.applicationContext.packageManager
        return uidPackageSource(pm) to signingCertificateSource(pm)
    }
}
