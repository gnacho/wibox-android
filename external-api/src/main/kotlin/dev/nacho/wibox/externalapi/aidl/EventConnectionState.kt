package dev.nacho.wibox.externalapi.aidl

import android.os.Parcel
import android.os.Parcelable

data class EventConnectionState(
    val state: Int,
    val observedAtEpochMs: Long,
) : BoundedParcelable(RESPONSE_PARCEL_BYTES) {
    private constructor(source: Parcel) : this(
        state = source.readInt(),
        observedAtEpochMs = source.readLong(),
    )

    override fun writeFields(dest: Parcel, flags: Int) {
        dest.writeInt(state)
        dest.writeLong(observedAtEpochMs)
    }

    companion object {
        @JvmField val CREATOR: Parcelable.Creator<EventConnectionState> = boundedCreator("EventConnectionState") { EventConnectionState(it) }
    }
}
