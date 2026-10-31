package dev.nacho.wibox.externalapi.aidl

import android.os.Parcel
import android.os.Parcelable

data class EventCursor(
    val streamGeneration: Long,
    val sequence: Long,
) : BoundedParcelable(RESPONSE_PARCEL_BYTES) {
    init {
        require(streamGeneration >= 0) { "streamGeneration must be nonnegative" }
        require(sequence >= 0) { "sequence must be nonnegative" }
    }

    private constructor(source: Parcel) : this(
        streamGeneration = source.readLong(),
        sequence = source.readLong(),
    )

    override fun writeFields(dest: Parcel, flags: Int) {
        dest.writeLong(streamGeneration)
        dest.writeLong(sequence)
    }

    companion object {
        @JvmField val CREATOR: Parcelable.Creator<EventCursor> = boundedCreator("EventCursor") { EventCursor(it) }
    }
}
