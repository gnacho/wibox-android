package dev.nacho.wibox.externalapi.aidl

import android.os.Parcel
import android.os.Parcelable

data class HandshakeResult(
    val serverApiMajor: Int,
    val serverApiMinor: Int,
    val negotiatedApiMinor: Int,
    val authorization: AuthorizationSnapshot?,
    val error: ApiError?,
) : BoundedParcelable(RESPONSE_PARCEL_BYTES) {
    init {
        require(serverApiMajor >= 1) { "serverApiMajor must be positive" }
        require(serverApiMinor >= 0 && negotiatedApiMinor >= 0) { "API minors must be nonnegative" }
        require(error == null || authorization == null) { "Error and authorization are mutually exclusive" }
    }

    private constructor(source: Parcel) : this(
        serverApiMajor = source.readInt(),
        serverApiMinor = source.readInt(),
        negotiatedApiMinor = source.readInt(),
        authorization = source.readNullableParcelable(AuthorizationSnapshot.CREATOR),
        error = source.readNullableParcelable(ApiError.CREATOR),
    )

    override fun writeFields(dest: Parcel, flags: Int) {
        dest.writeInt(serverApiMajor)
        dest.writeInt(serverApiMinor)
        dest.writeInt(negotiatedApiMinor)
        dest.writeNullableParcelable(authorization, flags)
        dest.writeNullableParcelable(error, flags)
    }

    companion object {
        @JvmField val CREATOR: Parcelable.Creator<HandshakeResult> = boundedCreator("HandshakeResult") { HandshakeResult(it) }
    }
}
