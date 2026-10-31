package dev.nacho.wibox.externalapi.aidl

import android.os.Parcel
import android.os.Parcelable

data class EventEnvelope(
    val providerEventId: String,
    val type: Int,
    val device: DeviceRef,
    val rawTime: String?,
    val parsedTimeEpochMs: Long?,
    val receivedAtEpochMs: Long,
    val origin: Int,
    val duplicate: Boolean,
    val cursor: EventCursor,
    val simulated: Boolean,
    val extensions: List<ProviderExtension>,
    val attachmentMetadata: List<AttachmentMetadata>,
) : BoundedParcelable(RESPONSE_PARCEL_BYTES) {
    init {
        validateUtf8("providerEventId", providerEventId, 128, required = true)
        validateUtf8("rawTime", rawTime, 128, required = false)
        validateItems("extensions", extensions, maxItems = 20)
        validateItems("attachmentMetadata", attachmentMetadata, maxItems = 4)
    }

    private constructor(source: Parcel) : this(
        providerEventId = source.readRequiredString(128),
        type = source.readInt(),
        device = DeviceRef.CREATOR.createFromParcel(source),
        rawTime = source.readOptionalString(128),
        parsedTimeEpochMs = source.readNullableLong(),
        receivedAtEpochMs = source.readLong(),
        origin = source.readInt(),
        duplicate = source.readBooleanValue(),
        cursor = EventCursor.CREATOR.createFromParcel(source),
        simulated = source.readBooleanValue(),
        extensions = source.readParcelableList(ProviderExtension.CREATOR, maxItems = 20),
        attachmentMetadata = source.readParcelableList(AttachmentMetadata.CREATOR, maxItems = 4),
    )

    override fun writeFields(dest: Parcel, flags: Int) {
        dest.writeString(providerEventId)
        dest.writeInt(type)
        device.writeToParcel(dest, flags)
        dest.writeString(rawTime)
        dest.writeNullableLong(parsedTimeEpochMs)
        dest.writeLong(receivedAtEpochMs)
        dest.writeInt(origin)
        dest.writeBooleanValue(duplicate)
        cursor.writeToParcel(dest, flags)
        dest.writeBooleanValue(simulated)
        dest.writeParcelableList(extensions, flags)
        dest.writeParcelableList(attachmentMetadata, flags)
    }

    companion object {
        @JvmField val CREATOR: Parcelable.Creator<EventEnvelope> = boundedCreator("EventEnvelope") { EventEnvelope(it) }
    }
}
