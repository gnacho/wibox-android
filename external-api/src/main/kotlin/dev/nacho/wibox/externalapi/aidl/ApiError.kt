package dev.nacho.wibox.externalapi.aidl

import android.os.Parcel
import android.os.Parcelable

data class ApiError(
    val code: Int,
    val retryPolicy: Int,
    val capabilityKey: String?,
    val outcome: Int?,
    val safeMessage: String?,
    val retryAfterMs: Long?,
    val providerCode: String?,
) : BoundedParcelable(RESPONSE_PARCEL_BYTES) {
    init {
        validateUtf8("capabilityKey", capabilityKey, 128, required = false)
        validateUtf8("safeMessage", safeMessage, 256, required = false)
        validateUtf8("providerCode", providerCode, 64, required = false)
        require(retryAfterMs == null || retryAfterMs >= 0) { "retryAfterMs must be nonnegative" }
    }

    private constructor(source: Parcel) : this(
        code = source.readInt(),
        retryPolicy = source.readInt(),
        capabilityKey = source.readOptionalString(128),
        outcome = source.readNullableInt(),
        safeMessage = source.readOptionalString(256),
        retryAfterMs = source.readNullableLong(),
        providerCode = source.readOptionalString(64),
    )

    override fun writeFields(dest: Parcel, flags: Int) {
        dest.writeInt(code)
        dest.writeInt(retryPolicy)
        dest.writeString(capabilityKey)
        dest.writeNullableInt(outcome)
        dest.writeString(safeMessage)
        dest.writeNullableLong(retryAfterMs)
        dest.writeString(providerCode)
    }

    companion object {
        @JvmField val CREATOR: Parcelable.Creator<ApiError> = boundedCreator("ApiError") { ApiError(it) }
    }
}
