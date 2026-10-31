package dev.nacho.wibox.externalapi.aidl

import android.os.Parcel
import android.os.Parcelable

data class ShadowSnapshot(
    val device: DeviceRef,
    val stockFields: StockShadowFields?,
    val logicalPath: String?,
    val timestampEpochMs: Long?,
    val sequence: Long?,
    val source: Int,
    val stale: Boolean,
    val simulated: Boolean,
    val extensions: List<ProviderExtension>,
    val unknownFields: List<UnknownFieldDescriptor>,
) : BoundedParcelable(RESPONSE_PARCEL_BYTES) {
    init {
        validateUtf8("logicalPath", logicalPath, 128, required = false)
        validateItems("extensions", extensions, maxItems = 20)
        validateItems("unknownFields", unknownFields, maxItems = 50)
    }

    private constructor(source: Parcel) : this(
        device = DeviceRef.CREATOR.createFromParcel(source),
        stockFields = source.readNullableParcelable(StockShadowFields.CREATOR),
        logicalPath = source.readOptionalString(128),
        timestampEpochMs = source.readNullableLong(),
        sequence = source.readNullableLong(),
        source = source.readInt(),
        stale = source.readBooleanValue(),
        simulated = source.readBooleanValue(),
        extensions = source.readParcelableList(ProviderExtension.CREATOR, maxItems = 20),
        unknownFields = source.readParcelableList(UnknownFieldDescriptor.CREATOR, maxItems = 50),
    )

    override fun writeFields(dest: Parcel, flags: Int) {
        device.writeToParcel(dest, flags)
        dest.writeNullableParcelable(stockFields, flags)
        dest.writeString(logicalPath)
        dest.writeNullableLong(timestampEpochMs)
        dest.writeNullableLong(sequence)
        dest.writeInt(source)
        dest.writeBooleanValue(stale)
        dest.writeBooleanValue(simulated)
        dest.writeParcelableList(extensions, flags)
        dest.writeParcelableList(unknownFields, flags)
    }

    companion object {
        @JvmField val CREATOR: Parcelable.Creator<ShadowSnapshot> = boundedCreator("ShadowSnapshot") { ShadowSnapshot(it) }
    }
}
