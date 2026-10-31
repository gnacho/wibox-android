package dev.nacho.wibox.externalapi.aidl

import android.os.Parcel
import android.os.Parcelable

data class ActionRequest(
    val requestId: String,
    val device: DeviceRef,
    val type: Int,
    val parameters: List<ProviderExtension>,
    val expiresAtEpochMs: Long,
) : BoundedParcelable(REQUEST_PARCEL_BYTES) {
    init {
        validateUtf8("requestId", requestId, 64, required = true)
        validateItems("parameters", parameters, maxItems = 10)
    }

    private constructor(source: Parcel) : this(
        requestId = source.readRequiredString(64),
        device = DeviceRef.CREATOR.createFromParcel(source),
        type = source.readInt(),
        parameters = source.readParcelableList(ProviderExtension.CREATOR, maxItems = 10),
        expiresAtEpochMs = source.readLong(),
    )

    override fun writeFields(dest: Parcel, flags: Int) {
        dest.writeString(requestId)
        device.writeToParcel(dest, flags)
        dest.writeInt(type)
        dest.writeParcelableList(parameters, flags)
        dest.writeLong(expiresAtEpochMs)
    }

    companion object {
        @JvmField val CREATOR: Parcelable.Creator<ActionRequest> = boundedCreator("ActionRequest") { ActionRequest(it) }
    }
}
