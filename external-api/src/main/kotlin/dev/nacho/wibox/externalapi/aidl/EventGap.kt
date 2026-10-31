package dev.nacho.wibox.externalapi.aidl

import android.os.Parcel
import android.os.Parcelable

data class EventGap(
    val lastValidCursor: EventCursor?,
    val resumeCursor: EventCursor?,
    val reason: Int,
    val detectedAtEpochMs: Long,
    val resyncRequired: Boolean,
    val recoverability: Int,
) : BoundedParcelable(RESPONSE_PARCEL_BYTES) {
    private constructor(source: Parcel) : this(
        lastValidCursor = source.readNullableParcelable(EventCursor.CREATOR),
        resumeCursor = source.readNullableParcelable(EventCursor.CREATOR),
        reason = source.readInt(),
        detectedAtEpochMs = source.readLong(),
        resyncRequired = source.readBooleanValue(),
        recoverability = source.readInt(),
    )

    override fun writeFields(dest: Parcel, flags: Int) {
        dest.writeNullableParcelable(lastValidCursor, flags)
        dest.writeNullableParcelable(resumeCursor, flags)
        dest.writeInt(reason)
        dest.writeLong(detectedAtEpochMs)
        dest.writeBooleanValue(resyncRequired)
        dest.writeInt(recoverability)
    }

    companion object {
        @JvmField val CREATOR: Parcelable.Creator<EventGap> = boundedCreator("EventGap") { EventGap(it) }
    }
}
