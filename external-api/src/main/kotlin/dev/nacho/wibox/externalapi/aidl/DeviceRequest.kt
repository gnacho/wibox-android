package dev.nacho.wibox.externalapi.aidl

import android.os.Parcel
import android.os.Parcelable

data class DeviceRequest(
    val requestId: String,
    val device: DeviceRef,
) : BoundedParcelable(REQUEST_PARCEL_BYTES) {
    init {
        validateUtf8("requestId", requestId, 64, required = true)
    }

    private constructor(source: Parcel) : this(
        requestId = source.readRequiredString(64),
        device = DeviceRef.CREATOR.createFromParcel(source),
    )

    override fun writeFields(dest: Parcel, flags: Int) {
        dest.writeString(requestId)
        device.writeToParcel(dest, flags)
    }

    companion object {
        @JvmField val CREATOR: Parcelable.Creator<DeviceRequest> = boundedCreator("DeviceRequest") { DeviceRequest(it) }
    }
}
