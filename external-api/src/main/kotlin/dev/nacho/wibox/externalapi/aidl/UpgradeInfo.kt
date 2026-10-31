package dev.nacho.wibox.externalapi.aidl

import android.os.Parcel
import android.os.Parcelable

data class UpgradeInfo(
    val state: Int,
    val currentVersion: String,
    val availableVersion: String?,
    val priority: Int?,
    val description: String?,
    val checkedAtEpochMs: Long,
) : BoundedParcelable(RESPONSE_PARCEL_BYTES) {
    init {
        validateUtf8("currentVersion", currentVersion, 64, required = true)
        validateUtf8("availableVersion", availableVersion, 64, required = false)
        validateUtf8("description", description, 1024, required = false)
    }

    private constructor(source: Parcel) : this(
        state = source.readInt(),
        currentVersion = source.readRequiredString(64),
        availableVersion = source.readOptionalString(64),
        priority = source.readNullableInt(),
        description = source.readOptionalString(1024),
        checkedAtEpochMs = source.readLong(),
    )

    override fun writeFields(dest: Parcel, flags: Int) {
        dest.writeInt(state)
        dest.writeString(currentVersion)
        dest.writeString(availableVersion)
        dest.writeNullableInt(priority)
        dest.writeString(description)
        dest.writeLong(checkedAtEpochMs)
    }

    companion object {
        @JvmField val CREATOR: Parcelable.Creator<UpgradeInfo> = boundedCreator("UpgradeInfo") { UpgradeInfo(it) }
    }
}
