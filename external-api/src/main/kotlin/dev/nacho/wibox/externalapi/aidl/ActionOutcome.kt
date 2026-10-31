package dev.nacho.wibox.externalapi.aidl

import android.os.Parcel
import android.os.Parcelable

data class ActionOutcome(
    val requestId: String,
    val actionType: Int,
    val device: DeviceRef,
    val ackLevel: Int,
    val origin: Int,
    val simulated: Boolean,
    val occurredAtEpochMs: Long,
    val providerCode: String?,
    val observationEventId: String?,
) : BoundedParcelable(RESPONSE_PARCEL_BYTES) {
    init {
        validateUtf8("requestId", requestId, 64, required = true)
        validateUtf8("providerCode", providerCode, 64, required = false)
        validateUtf8("observationEventId", observationEventId, 128, required = false)
    }

    private constructor(source: Parcel) : this(
        requestId = source.readRequiredString(64),
        actionType = source.readInt(),
        device = DeviceRef.CREATOR.createFromParcel(source),
        ackLevel = source.readInt(),
        origin = source.readInt(),
        simulated = source.readBooleanValue(),
        occurredAtEpochMs = source.readLong(),
        providerCode = source.readOptionalString(64),
        observationEventId = source.readOptionalString(128),
    )

    override fun writeFields(dest: Parcel, flags: Int) {
        dest.writeString(requestId)
        dest.writeInt(actionType)
        device.writeToParcel(dest, flags)
        dest.writeInt(ackLevel)
        dest.writeInt(origin)
        dest.writeBooleanValue(simulated)
        dest.writeLong(occurredAtEpochMs)
        dest.writeString(providerCode)
        dest.writeString(observationEventId)
    }

    companion object {
        @JvmField val CREATOR: Parcelable.Creator<ActionOutcome> = boundedCreator("ActionOutcome") { ActionOutcome(it) }
    }
}
