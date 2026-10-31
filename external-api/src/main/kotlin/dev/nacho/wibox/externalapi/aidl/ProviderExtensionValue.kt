package dev.nacho.wibox.externalapi.aidl

import android.os.Parcel
import android.os.Parcelable

data class ProviderExtensionValue(
    val type: Int,
    val booleanValue: Boolean?,
    val integerValue: Long?,
    val decimalValue: Double?,
    val stringValue: String?,
) : BoundedParcelable(RESPONSE_PARCEL_BYTES) {
    init {
        validateUtf8("stringValue", stringValue, 1024, required = false)
        when (type) {
            WireExtensionValueType.BOOLEAN -> require(booleanValue != null && integerValue == null && decimalValue == null && stringValue == null) { "BOOLEAN requires only booleanValue" }
            WireExtensionValueType.INTEGER -> require(integerValue != null && booleanValue == null && decimalValue == null && stringValue == null) { "INTEGER requires only integerValue" }
            WireExtensionValueType.DECIMAL -> require(decimalValue != null && decimalValue.isFinite() && booleanValue == null && integerValue == null && stringValue == null) { "DECIMAL requires only a finite decimalValue" }
            WireExtensionValueType.STRING -> require(stringValue != null && booleanValue == null && integerValue == null && decimalValue == null) { "STRING requires only stringValue" }
        }
    }

    private constructor(source: Parcel) : this(
        type = source.readInt(),
        booleanValue = source.readNullableBoolean(),
        integerValue = source.readNullableLong(),
        decimalValue = source.readNullableDouble(),
        stringValue = source.readOptionalString(1024),
    )

    override fun writeFields(dest: Parcel, flags: Int) {
        dest.writeInt(type)
        dest.writeNullableBoolean(booleanValue)
        dest.writeNullableLong(integerValue)
        dest.writeNullableDouble(decimalValue)
        dest.writeString(stringValue)
    }

    companion object {
        @JvmField val CREATOR: Parcelable.Creator<ProviderExtensionValue> = boundedCreator("ProviderExtensionValue") { ProviderExtensionValue(it) }
    }
}
