package dev.nacho.wibox.externalapi.aidl

import android.os.Parcel
import android.os.Parcelable

data class ShadowBatch(
    val items: List<ShadowSnapshot>,
    val missingDeviceIds: List<String>,
) : BoundedParcelable(RESPONSE_PARCEL_BYTES) {
    init {
        validateItems("items", items, maxItems = 20)
        validateItems("missingDeviceIds", missingDeviceIds, maxItems = 20)
        missingDeviceIds.forEachIndexed { index, value -> validateUtf8("missingDeviceIds[$index]", value, 128) }
    }

    private constructor(source: Parcel) : this(
        items = source.readParcelableList(ShadowSnapshot.CREATOR, maxItems = 20),
        missingDeviceIds = source.readStringList(maxItems = 20, maxUtf8BytesEach = 128),
    )

    override fun writeFields(dest: Parcel, flags: Int) {
        dest.writeParcelableList(items, flags)
        dest.writeStringList(missingDeviceIds)
    }

    companion object {
        @JvmField val CREATOR: Parcelable.Creator<ShadowBatch> = boundedCreator("ShadowBatch") { ShadowBatch(it) }
    }
}
