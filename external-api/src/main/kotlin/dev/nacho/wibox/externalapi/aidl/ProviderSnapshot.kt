package dev.nacho.wibox.externalapi.aidl

import android.os.Parcel
import android.os.Parcelable

data class ProviderSnapshot(
    val providerId: String,
    val profileVersion: String,
    val connectionKind: Int,
    val preparationState: Int,
    val session: SessionSummary,
    val simulated: Boolean,
    val observedAtEpochMs: Long,
) : BoundedParcelable(RESPONSE_PARCEL_BYTES) {
    init {
        validateUtf8("providerId", providerId, 64, required = true)
        validateUtf8("profileVersion", profileVersion, 32, required = true)
    }

    private constructor(source: Parcel) : this(
        providerId = source.readRequiredString(64),
        profileVersion = source.readRequiredString(32),
        connectionKind = source.readInt(),
        preparationState = source.readInt(),
        session = SessionSummary.CREATOR.createFromParcel(source),
        simulated = source.readBooleanValue(),
        observedAtEpochMs = source.readLong(),
    )

    override fun writeFields(dest: Parcel, flags: Int) {
        dest.writeString(providerId)
        dest.writeString(profileVersion)
        dest.writeInt(connectionKind)
        dest.writeInt(preparationState)
        session.writeToParcel(dest, flags)
        dest.writeBooleanValue(simulated)
        dest.writeLong(observedAtEpochMs)
    }

    companion object {
        @JvmField val CREATOR: Parcelable.Creator<ProviderSnapshot> = boundedCreator("ProviderSnapshot") { ProviderSnapshot(it) }
    }
}
