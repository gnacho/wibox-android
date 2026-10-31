package dev.nacho.wibox.externalapi.aidl

import android.os.Parcel
import android.os.Parcelable

data class PageCursor(
    val generation: Long,
    val offset: Int,
) : BoundedParcelable(RESPONSE_PARCEL_BYTES) {
    init {
        require(generation >= 0) { "generation must be nonnegative" }
        require(offset >= 0) { "offset must be nonnegative" }
    }

    private constructor(source: Parcel) : this(
        generation = source.readLong(),
        offset = source.readInt(),
    )

    override fun writeFields(dest: Parcel, flags: Int) {
        dest.writeLong(generation)
        dest.writeInt(offset)
    }

    companion object {
        @JvmField val CREATOR: Parcelable.Creator<PageCursor> = boundedCreator("PageCursor") { PageCursor(it) }
    }
}
