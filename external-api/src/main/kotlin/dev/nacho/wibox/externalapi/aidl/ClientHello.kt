package dev.nacho.wibox.externalapi.aidl

import android.os.Parcel
import android.os.Parcelable

data class ClientHello(
    val packageName: String,
    val clientApiMajor: Int,
    val clientApiMinor: Int,
    val clientSdkVersion: String,
) : BoundedParcelable(REQUEST_PARCEL_BYTES) {
    init {
        validateUtf8("packageName", packageName, 128, required = true)
        validateUtf8("clientSdkVersion", clientSdkVersion, 32, required = true)
        require(clientApiMajor >= 1) { "clientApiMajor must be positive" }
        require(clientApiMinor >= 0) { "clientApiMinor must be nonnegative" }
    }

    private constructor(source: Parcel) : this(
        packageName = source.readRequiredString(128),
        clientApiMajor = source.readInt(),
        clientApiMinor = source.readInt(),
        clientSdkVersion = source.readRequiredString(32),
    )

    override fun writeFields(dest: Parcel, flags: Int) {
        dest.writeString(packageName)
        dest.writeInt(clientApiMajor)
        dest.writeInt(clientApiMinor)
        dest.writeString(clientSdkVersion)
    }

    companion object {
        @JvmField val CREATOR: Parcelable.Creator<ClientHello> = boundedCreator("ClientHello") { ClientHello(it) }
    }
}
