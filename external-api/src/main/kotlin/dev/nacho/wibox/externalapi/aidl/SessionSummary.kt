package dev.nacho.wibox.externalapi.aidl

import android.os.Parcel
import android.os.Parcelable

data class SessionSummary(
    val state: Int,
    val sessionGeneration: Long,
    val expiresAtEpochMs: Long?,
) : BoundedParcelable(RESPONSE_PARCEL_BYTES) {
    init {
        require(sessionGeneration >= 0) { "sessionGeneration must be nonnegative" }
    }

    private constructor(source: Parcel) : this(
        state = source.readInt(),
        sessionGeneration = source.readLong(),
        expiresAtEpochMs = source.readNullableLong(),
    )

    override fun writeFields(dest: Parcel, flags: Int) {
        dest.writeInt(state)
        dest.writeLong(sessionGeneration)
        dest.writeNullableLong(expiresAtEpochMs)
    }

    companion object {
        @JvmField val CREATOR: Parcelable.Creator<SessionSummary> = boundedCreator("SessionSummary") { SessionSummary(it) }
    }
}
