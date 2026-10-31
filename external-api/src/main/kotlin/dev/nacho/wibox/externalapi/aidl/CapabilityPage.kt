package dev.nacho.wibox.externalapi.aidl

import android.os.Parcel
import android.os.Parcelable

data class CapabilityPage(
    val items: List<CapabilitySnapshot>,
    val nextCursor: PageCursor?,
    val observedAtEpochMs: Long,
) : BoundedParcelable(RESPONSE_PARCEL_BYTES) {
    init {
        validateItems("items", items, maxItems = 50)
    }

    private constructor(source: Parcel) : this(
        items = source.readParcelableList(CapabilitySnapshot.CREATOR, maxItems = 50),
        nextCursor = source.readNullableParcelable(PageCursor.CREATOR),
        observedAtEpochMs = source.readLong(),
    )

    override fun writeFields(dest: Parcel, flags: Int) {
        dest.writeParcelableList(items, flags)
        dest.writeNullableParcelable(nextCursor, flags)
        dest.writeLong(observedAtEpochMs)
    }

    companion object {
        @JvmField val CREATOR: Parcelable.Creator<CapabilityPage> = boundedCreator("CapabilityPage") { CapabilityPage(it) }
    }
}
