package dev.nacho.wibox.externalapi.aidl

import android.os.Parcel
import android.os.Parcelable

data class PageRequest(
    val requestId: String,
    val cursor: PageCursor?,
    val limit: Int,
) : BoundedParcelable(REQUEST_PARCEL_BYTES) {
    init {
        validateUtf8("requestId", requestId, 64, required = true)
        require(limit >= 1) { "limit is below minimum" }
        require(limit <= 20) { "limit is above maximum" }
    }

    private constructor(source: Parcel) : this(
        requestId = source.readRequiredString(64),
        cursor = source.readNullableParcelable(PageCursor.CREATOR),
        limit = source.readInt(),
    )

    override fun writeFields(dest: Parcel, flags: Int) {
        dest.writeString(requestId)
        dest.writeNullableParcelable(cursor, flags)
        dest.writeInt(limit)
    }

    companion object {
        @JvmField val CREATOR: Parcelable.Creator<PageRequest> = boundedCreator("PageRequest") { PageRequest(it) }
    }
}
