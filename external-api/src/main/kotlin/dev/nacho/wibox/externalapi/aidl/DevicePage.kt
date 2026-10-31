package dev.nacho.wibox.externalapi.aidl

import android.os.Parcel
import android.os.Parcelable

data class DevicePage(
    val items: List<DeviceSnapshot>,
    val nextCursor: PageCursor?,
    val source: Int,
    val observedAtEpochMs: Long,
) : BoundedParcelable(RESPONSE_PARCEL_BYTES) {
    init {
        validateItems("items", items, maxItems = 20)
    }

    private constructor(source: Parcel) : this(
        items = source.readParcelableList(DeviceSnapshot.CREATOR, maxItems = 20),
        nextCursor = source.readNullableParcelable(PageCursor.CREATOR),
        source = source.readInt(),
        observedAtEpochMs = source.readLong(),
    )

    override fun writeFields(dest: Parcel, flags: Int) {
        dest.writeParcelableList(items, flags)
        dest.writeNullableParcelable(nextCursor, flags)
        dest.writeInt(source)
        dest.writeLong(observedAtEpochMs)
    }

    companion object {
        @JvmField val CREATOR: Parcelable.Creator<DevicePage> = boundedCreator("DevicePage") { DevicePage(it) }
    }
}
