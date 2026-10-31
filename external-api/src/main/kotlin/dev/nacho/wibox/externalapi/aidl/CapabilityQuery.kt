package dev.nacho.wibox.externalapi.aidl

import android.os.Parcel
import android.os.Parcelable

data class CapabilityQuery(
    val page: PageRequest,
    val deviceId: String?,
    val namespace: String?,
) : BoundedParcelable(REQUEST_PARCEL_BYTES) {
    init {
        validateUtf8("deviceId", deviceId, 128, required = false)
        validateUtf8("namespace", namespace, 128, required = false)
    }

    private constructor(source: Parcel) : this(
        page = PageRequest.CREATOR.createFromParcel(source),
        deviceId = source.readOptionalString(128),
        namespace = source.readOptionalString(128),
    )

    override fun writeFields(dest: Parcel, flags: Int) {
        page.writeToParcel(dest, flags)
        dest.writeString(deviceId)
        dest.writeString(namespace)
    }

    companion object {
        @JvmField val CREATOR: Parcelable.Creator<CapabilityQuery> = boundedCreator("CapabilityQuery") { CapabilityQuery(it) }
    }
}
