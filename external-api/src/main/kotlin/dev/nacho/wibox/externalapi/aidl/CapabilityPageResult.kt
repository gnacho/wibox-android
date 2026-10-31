package dev.nacho.wibox.externalapi.aidl

import android.os.Parcel
import android.os.Parcelable

data class CapabilityPageResult(
    val page: CapabilityPage?,
    val error: ApiError?,
) : BoundedParcelable(RESPONSE_PARCEL_BYTES) {
    init {
        require((page != null) xor (error != null)) { "Exactly one payload or error is required" }
    }

    private constructor(source: Parcel) : this(
        page = source.readNullableParcelable(CapabilityPage.CREATOR),
        error = source.readNullableParcelable(ApiError.CREATOR),
    )

    override fun writeFields(dest: Parcel, flags: Int) {
        dest.writeNullableParcelable(page, flags)
        dest.writeNullableParcelable(error, flags)
    }

    companion object {
        @JvmField val CREATOR: Parcelable.Creator<CapabilityPageResult> = boundedCreator("CapabilityPageResult") { CapabilityPageResult(it) }
    }
}
