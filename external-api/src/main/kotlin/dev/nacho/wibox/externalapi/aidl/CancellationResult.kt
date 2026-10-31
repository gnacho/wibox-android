package dev.nacho.wibox.externalapi.aidl

import android.os.Parcel
import android.os.Parcelable

data class CancellationResult(
    val requestId: String,
    val state: Int,
    val error: ApiError?,
) : BoundedParcelable(RESPONSE_PARCEL_BYTES) {
    init {
        validateUtf8("requestId", requestId, 64, required = true)
    }

    private constructor(source: Parcel) : this(
        requestId = source.readRequiredString(64),
        state = source.readInt(),
        error = source.readNullableParcelable(ApiError.CREATOR),
    )

    override fun writeFields(dest: Parcel, flags: Int) {
        dest.writeString(requestId)
        dest.writeInt(state)
        dest.writeNullableParcelable(error, flags)
    }

    companion object {
        @JvmField val CREATOR: Parcelable.Creator<CancellationResult> = boundedCreator("CancellationResult") { CancellationResult(it) }
    }
}
