package dev.nacho.wibox.externalapi.aidl

import android.os.Parcel
import android.os.Parcelable

data class AuthorizationSnapshot(
    val state: Int,
    val grantedScopes: List<String>,
    val grantedDeviceIds: List<String>,
    val grantGeneration: Long,
) : BoundedParcelable(RESPONSE_PARCEL_BYTES) {
    init {
        validateItems("grantedScopes", grantedScopes, maxItems = 10)
        validateItems("grantedDeviceIds", grantedDeviceIds, maxItems = 100)
        grantedDeviceIds.forEachIndexed { index, value -> validateUtf8("grantedDeviceIds[$index]", value, 128) }
        require(grantGeneration >= 0) { "grantGeneration must be nonnegative" }
    }

    private constructor(source: Parcel) : this(
        state = source.readInt(),
        grantedScopes = source.readStringList(maxItems = 10),
        grantedDeviceIds = source.readStringList(maxItems = 100, maxUtf8BytesEach = 128),
        grantGeneration = source.readLong(),
    )

    override fun writeFields(dest: Parcel, flags: Int) {
        dest.writeInt(state)
        dest.writeStringList(grantedScopes)
        dest.writeStringList(grantedDeviceIds)
        dest.writeLong(grantGeneration)
    }

    companion object {
        @JvmField val CREATOR: Parcelable.Creator<AuthorizationSnapshot> = boundedCreator("AuthorizationSnapshot") { AuthorizationSnapshot(it) }
    }
}
