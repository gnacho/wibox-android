package dev.nacho.wibox.externalapi.aidl

import android.os.Parcel
import android.os.Parcelable

data class AuthorizationResult(
    val authorization: AuthorizationSnapshot?,
    val error: ApiError?,
) : BoundedParcelable(RESPONSE_PARCEL_BYTES) {
    init {
        require((authorization != null) xor (error != null)) { "Exactly one payload or error is required" }
    }

    private constructor(source: Parcel) : this(
        authorization = source.readNullableParcelable(AuthorizationSnapshot.CREATOR),
        error = source.readNullableParcelable(ApiError.CREATOR),
    )

    override fun writeFields(dest: Parcel, flags: Int) {
        dest.writeNullableParcelable(authorization, flags)
        dest.writeNullableParcelable(error, flags)
    }

    companion object {
        @JvmField val CREATOR: Parcelable.Creator<AuthorizationResult> = boundedCreator("AuthorizationResult") { AuthorizationResult(it) }
    }
}
