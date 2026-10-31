package dev.nacho.wibox.externalapi.aidl

import android.os.Parcel
import android.os.Parcelable

data class ShadowBatchQuery(
    val requestId: String,
    val queries: List<ShadowQuery>,
) : BoundedParcelable(REQUEST_PARCEL_BYTES) {
    init {
        validateUtf8("requestId", requestId, 64, required = true)
        validateItems("queries", queries, maxItems = 20, minItems = 1)
    }

    private constructor(source: Parcel) : this(
        requestId = source.readRequiredString(64),
        queries = source.readParcelableList(ShadowQuery.CREATOR, maxItems = 20, minItems = 1),
    )

    override fun writeFields(dest: Parcel, flags: Int) {
        dest.writeString(requestId)
        dest.writeParcelableList(queries, flags)
    }

    companion object {
        @JvmField val CREATOR: Parcelable.Creator<ShadowBatchQuery> = boundedCreator("ShadowBatchQuery") { ShadowBatchQuery(it) }
    }
}
