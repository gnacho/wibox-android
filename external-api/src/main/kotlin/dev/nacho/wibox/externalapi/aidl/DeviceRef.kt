package dev.nacho.wibox.externalapi.aidl

import android.os.Parcel
import android.os.Parcelable

data class DeviceRef(
    val providerId: String,
    val deviceId: String,
    val channel: Int?,
) : BoundedParcelable(RESPONSE_PARCEL_BYTES) {
    init {
        validateUtf8("providerId", providerId, 64, required = true)
        validateUtf8("deviceId", deviceId, 128, required = true)
    }

    private constructor(source: Parcel) : this(
        providerId = source.readRequiredString(64),
        deviceId = source.readRequiredString(128),
        channel = source.readNullableInt(),
    )

    override fun writeFields(dest: Parcel, flags: Int) {
        dest.writeString(providerId)
        dest.writeString(deviceId)
        dest.writeNullableInt(channel)
    }

    companion object {
        @JvmField val CREATOR: Parcelable.Creator<DeviceRef> = boundedCreator("DeviceRef") { DeviceRef(it) }
    }
}
