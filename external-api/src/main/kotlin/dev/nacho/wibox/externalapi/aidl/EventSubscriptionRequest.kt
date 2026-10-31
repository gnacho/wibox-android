package dev.nacho.wibox.externalapi.aidl

import android.os.Parcel
import android.os.Parcelable

data class EventSubscriptionRequest(
    val after: EventCursor?,
    val filter: EventFilter,
    val maximumBatchSize: Int,
) : BoundedParcelable(REQUEST_PARCEL_BYTES) {
    init {
        require(maximumBatchSize >= 1) { "maximumBatchSize is below minimum" }
        require(maximumBatchSize <= 10) { "maximumBatchSize is above maximum" }
    }

    private constructor(source: Parcel) : this(
        after = source.readNullableParcelable(EventCursor.CREATOR),
        filter = EventFilter.CREATOR.createFromParcel(source),
        maximumBatchSize = source.readInt(),
    )

    override fun writeFields(dest: Parcel, flags: Int) {
        dest.writeNullableParcelable(after, flags)
        filter.writeToParcel(dest, flags)
        dest.writeInt(maximumBatchSize)
    }

    companion object {
        @JvmField val CREATOR: Parcelable.Creator<EventSubscriptionRequest> = boundedCreator("EventSubscriptionRequest") { EventSubscriptionRequest(it) }
    }
}
