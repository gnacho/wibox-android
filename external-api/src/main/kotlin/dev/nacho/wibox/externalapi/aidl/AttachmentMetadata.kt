package dev.nacho.wibox.externalapi.aidl

import android.os.Parcel
import android.os.Parcelable

data class AttachmentMetadata(
    val kind: String,
    val available: Boolean,
    val expiresAtEpochMs: Long?,
) : BoundedParcelable(RESPONSE_PARCEL_BYTES) {
    init {
        validateUtf8("kind", kind, 32, required = true)
    }

    private constructor(source: Parcel) : this(
        kind = source.readRequiredString(32),
        available = source.readBooleanValue(),
        expiresAtEpochMs = source.readNullableLong(),
    )

    override fun writeFields(dest: Parcel, flags: Int) {
        dest.writeString(kind)
        dest.writeBooleanValue(available)
        dest.writeNullableLong(expiresAtEpochMs)
    }

    companion object {
        @JvmField val CREATOR: Parcelable.Creator<AttachmentMetadata> = boundedCreator("AttachmentMetadata") { AttachmentMetadata(it) }
    }
}
