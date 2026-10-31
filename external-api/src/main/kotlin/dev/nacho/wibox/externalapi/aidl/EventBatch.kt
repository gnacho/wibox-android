package dev.nacho.wibox.externalapi.aidl

import android.os.Parcel
import android.os.Parcelable

data class EventBatch(
    val items: List<EventEnvelope>,
    val latestCursor: EventCursor?,
) : BoundedParcelable(EVENT_BATCH_PARCEL_BYTES) {
    init {
        validateItems("items", items, maxItems = 10)
    }

    private constructor(source: Parcel) : this(
        items = source.readParcelableList(EventEnvelope.CREATOR, maxItems = 10),
        latestCursor = source.readNullableParcelable(EventCursor.CREATOR),
    )

    override fun writeFields(dest: Parcel, flags: Int) {
        dest.writeParcelableList(items, flags)
        dest.writeNullableParcelable(latestCursor, flags)
    }

    companion object {
        @JvmField val CREATOR: Parcelable.Creator<EventBatch> = boundedCreator("EventBatch") { EventBatch(it) }
    }
}
