package dev.nacho.wibox.externalapi.aidl

import android.os.Parcel
import android.os.Parcelable

data class AuthorizationRequest(
    val displayName: String,
    val requestedScopes: List<String>,
    val requestedDeviceIds: List<String>,
) : BoundedParcelable(REQUEST_PARCEL_BYTES) {
    init {
        validateUtf8("displayName", displayName, 128, required = true)
        validateItems("requestedScopes", requestedScopes, maxItems = 10)
        validateItems("requestedDeviceIds", requestedDeviceIds, maxItems = 20)
        requestedDeviceIds.forEachIndexed { index, value -> validateUtf8("requestedDeviceIds[$index]", value, 128) }
    }

    private constructor(source: Parcel) : this(
        displayName = source.readRequiredString(128),
        requestedScopes = source.readStringList(maxItems = 10),
        requestedDeviceIds = source.readStringList(maxItems = 20, maxUtf8BytesEach = 128),
    )

    override fun writeFields(dest: Parcel, flags: Int) {
        dest.writeString(displayName)
        dest.writeStringList(requestedScopes)
        dest.writeStringList(requestedDeviceIds)
    }

    companion object {
        @JvmField val CREATOR: Parcelable.Creator<AuthorizationRequest> = boundedCreator("AuthorizationRequest") { AuthorizationRequest(it) }
    }
}
