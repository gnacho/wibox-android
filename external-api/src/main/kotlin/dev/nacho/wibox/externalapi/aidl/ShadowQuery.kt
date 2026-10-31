package dev.nacho.wibox.externalapi.aidl

import android.os.Parcel
import android.os.Parcelable

data class ShadowQuery(
    val requestId: String,
    val device: DeviceRef,
    val fieldNames: List<String>,
    val freshness: Int,
) : BoundedParcelable(REQUEST_PARCEL_BYTES) {
    init {
        validateUtf8("requestId", requestId, 64, required = true)
        validateItems("fieldNames", fieldNames, maxItems = 32)
        fieldNames.forEachIndexed { index, value -> validateUtf8("fieldNames[$index]", value, 128) }
    }

    private constructor(source: Parcel) : this(
        requestId = source.readRequiredString(64),
        device = DeviceRef.CREATOR.createFromParcel(source),
        fieldNames = source.readStringList(maxItems = 32, maxUtf8BytesEach = 128),
        freshness = source.readInt(),
    )

    override fun writeFields(dest: Parcel, flags: Int) {
        dest.writeString(requestId)
        device.writeToParcel(dest, flags)
        dest.writeStringList(fieldNames)
        dest.writeInt(freshness)
    }

    companion object {
        @JvmField val CREATOR: Parcelable.Creator<ShadowQuery> = boundedCreator("ShadowQuery") { ShadowQuery(it) }
    }
}
