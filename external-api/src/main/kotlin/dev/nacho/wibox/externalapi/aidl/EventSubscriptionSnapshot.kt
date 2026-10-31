package dev.nacho.wibox.externalapi.aidl

import android.os.Parcel
import android.os.Parcelable

data class EventSubscriptionSnapshot(
    val state: EventConnectionState,
    val resumeCursor: EventCursor?,
    val replayFromCache: Boolean,
    val liveSupported: Boolean,
) : BoundedParcelable(RESPONSE_PARCEL_BYTES) {
    private constructor(source: Parcel) : this(
        state = EventConnectionState.CREATOR.createFromParcel(source),
        resumeCursor = source.readNullableParcelable(EventCursor.CREATOR),
        replayFromCache = source.readBooleanValue(),
        liveSupported = source.readBooleanValue(),
    )

    override fun writeFields(dest: Parcel, flags: Int) {
        state.writeToParcel(dest, flags)
        dest.writeNullableParcelable(resumeCursor, flags)
        dest.writeBooleanValue(replayFromCache)
        dest.writeBooleanValue(liveSupported)
    }

    companion object {
        @JvmField val CREATOR: Parcelable.Creator<EventSubscriptionSnapshot> = boundedCreator("EventSubscriptionSnapshot") { EventSubscriptionSnapshot(it) }
    }
}
