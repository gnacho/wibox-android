package dev.nacho.wibox.externalapi.aidl

import android.os.Parcel
import android.os.Parcelable

data class DeviceSnapshot(
    val device: DeviceRef,
    val displayName: String?,
    val declaredModel: String?,
    val role: Int,
    val presence: Int,
    val lastObservedAtEpochMs: Long?,
    val source: Int,
    val stale: Boolean,
    val simulated: Boolean,
    val capabilityKeys: List<String>,
) : BoundedParcelable(RESPONSE_PARCEL_BYTES) {
    init {
        validateUtf8("displayName", displayName, 128, required = false)
        validateUtf8("declaredModel", declaredModel, 128, required = false)
        validateItems("capabilityKeys", capabilityKeys, maxItems = 100)
        capabilityKeys.forEachIndexed { index, value -> validateUtf8("capabilityKeys[$index]", value, 128) }
    }

    private constructor(source: Parcel) : this(
        device = DeviceRef.CREATOR.createFromParcel(source),
        displayName = source.readOptionalString(128),
        declaredModel = source.readOptionalString(128),
        role = source.readInt(),
        presence = source.readInt(),
        lastObservedAtEpochMs = source.readNullableLong(),
        source = source.readInt(),
        stale = source.readBooleanValue(),
        simulated = source.readBooleanValue(),
        capabilityKeys = source.readStringList(maxItems = 100, maxUtf8BytesEach = 128),
    )

    override fun writeFields(dest: Parcel, flags: Int) {
        device.writeToParcel(dest, flags)
        dest.writeString(displayName)
        dest.writeString(declaredModel)
        dest.writeInt(role)
        dest.writeInt(presence)
        dest.writeNullableLong(lastObservedAtEpochMs)
        dest.writeInt(source)
        dest.writeBooleanValue(stale)
        dest.writeBooleanValue(simulated)
        dest.writeStringList(capabilityKeys)
    }

    companion object {
        @JvmField val CREATOR: Parcelable.Creator<DeviceSnapshot> = boundedCreator("DeviceSnapshot") { DeviceSnapshot(it) }
    }
}
