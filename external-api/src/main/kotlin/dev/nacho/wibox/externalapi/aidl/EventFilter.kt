package dev.nacho.wibox.externalapi.aidl

import android.os.Parcel
import android.os.Parcelable

data class EventFilter(
    val deviceIds: List<String>,
    val channels: List<Int>,
    val eventTypes: List<Int>,
) : BoundedParcelable(RESPONSE_PARCEL_BYTES) {
    init {
        validateItems("deviceIds", deviceIds, maxItems = 20)
        deviceIds.forEachIndexed { index, value -> validateUtf8("deviceIds[$index]", value, 128) }
        validateItems("channels", channels, maxItems = 20)
        validateItems("eventTypes", eventTypes, maxItems = 20)
    }

    private constructor(source: Parcel) : this(
        deviceIds = source.readStringList(maxItems = 20, maxUtf8BytesEach = 128),
        channels = source.readIntList(maxItems = 20),
        eventTypes = source.readIntList(maxItems = 20),
    )

    override fun writeFields(dest: Parcel, flags: Int) {
        dest.writeStringList(deviceIds)
        dest.writeIntList(channels)
        dest.writeIntList(eventTypes)
    }

    companion object {
        @JvmField val CREATOR: Parcelable.Creator<EventFilter> = boundedCreator("EventFilter") { EventFilter(it) }
    }
}
