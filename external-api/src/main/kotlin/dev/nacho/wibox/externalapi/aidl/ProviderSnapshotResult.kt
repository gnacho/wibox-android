package dev.nacho.wibox.externalapi.aidl

import android.os.Parcel
import android.os.Parcelable

data class ProviderSnapshotResult(
    val snapshot: ProviderSnapshot?,
    val error: ApiError?,
) : BoundedParcelable(RESPONSE_PARCEL_BYTES) {
    init {
        require((snapshot != null) xor (error != null)) { "Exactly one payload or error is required" }
    }

    private constructor(source: Parcel) : this(
        snapshot = source.readNullableParcelable(ProviderSnapshot.CREATOR),
        error = source.readNullableParcelable(ApiError.CREATOR),
    )

    override fun writeFields(dest: Parcel, flags: Int) {
        dest.writeNullableParcelable(snapshot, flags)
        dest.writeNullableParcelable(error, flags)
    }

    companion object {
        @JvmField val CREATOR: Parcelable.Creator<ProviderSnapshotResult> = boundedCreator("ProviderSnapshotResult") { ProviderSnapshotResult(it) }
    }
}
