package dev.nacho.wibox.externalapi.aidl

import android.os.Parcel
import android.os.Parcelable

data class StockShadowFields(
    val deviceState: Int?,
    val f1State: Int?,
    val switchState: Int?,
    val privateModeState: Int?,
    val fisheyeChannelState: Int?,
    val lockState: Int?,
    val alarmZoneMode: Int?,
    val smartRelay: Int?,
) : BoundedParcelable(RESPONSE_PARCEL_BYTES) {
    private constructor(source: Parcel) : this(
        deviceState = source.readNullableInt(),
        f1State = source.readNullableInt(),
        switchState = source.readNullableInt(),
        privateModeState = source.readNullableInt(),
        fisheyeChannelState = source.readNullableInt(),
        lockState = source.readNullableInt(),
        alarmZoneMode = source.readNullableInt(),
        smartRelay = source.readNullableInt(),
    )

    override fun writeFields(dest: Parcel, flags: Int) {
        dest.writeNullableInt(deviceState)
        dest.writeNullableInt(f1State)
        dest.writeNullableInt(switchState)
        dest.writeNullableInt(privateModeState)
        dest.writeNullableInt(fisheyeChannelState)
        dest.writeNullableInt(lockState)
        dest.writeNullableInt(alarmZoneMode)
        dest.writeNullableInt(smartRelay)
    }

    companion object {
        @JvmField val CREATOR: Parcelable.Creator<StockShadowFields> = boundedCreator("StockShadowFields") { StockShadowFields(it) }
    }
}
