package dev.nacho.wibox.externalapi.aidl

import android.os.BadParcelableException
import android.os.Parcel
import android.os.Parcelable

internal const val REQUEST_PARCEL_BYTES = 32 * 1024
internal const val RESPONSE_PARCEL_BYTES = 64 * 1024
internal const val EVENT_BATCH_PARCEL_BYTES = 48 * 1024

abstract class BoundedParcelable internal constructor(
    private val serializedSizeLimit: Int,
) : Parcelable {
    final override fun describeContents(): Int = 0

    final override fun writeToParcel(dest: Parcel, flags: Int) {
        val start = dest.dataPosition()
        writeFields(dest, flags)
        val written = dest.dataPosition() - start
        if (written > serializedSizeLimit) {
            throw BadParcelableException("Parcelable exceeds its $serializedSizeLimit byte budget")
        }
    }

    protected abstract fun writeFields(dest: Parcel, flags: Int)
}

internal inline fun <reified T> boundedCreator(
    modelName: String,
    crossinline reader: (Parcel) -> T,
): Parcelable.Creator<T> = object : Parcelable.Creator<T> {
    override fun createFromParcel(source: Parcel): T = try {
        reader(source)
    } catch (error: BadParcelableException) {
        throw error
    } catch (error: RuntimeException) {
        throw BadParcelableException("Invalid $modelName payload")
    }

    override fun newArray(size: Int): Array<T?> = arrayOfNulls(size)
}

internal fun validateUtf8(field: String, value: String?, maxBytes: Int, required: Boolean = true) {
    if (value == null) {
        require(!required) { "$field is required" }
        return
    }
    require(value.toByteArray(Charsets.UTF_8).size <= maxBytes) {
        "$field exceeds $maxBytes UTF-8 bytes"
    }
}

internal fun validateItems(field: String, values: List<*>, maxItems: Int, minItems: Int = 0) {
    require(values.size in minItems..maxItems) {
        "$field must contain between $minItems and $maxItems items"
    }
}

internal fun Parcel.readRequiredString(maxUtf8Bytes: Int? = null): String {
    val value = readString() ?: throw BadParcelableException("Missing required string")
    if (maxUtf8Bytes != null) validateUtf8("string", value, maxUtf8Bytes)
    return value
}

internal fun Parcel.readOptionalString(maxUtf8Bytes: Int? = null): String? {
    val value = readString() ?: return null
    if (maxUtf8Bytes != null) validateUtf8("string", value, maxUtf8Bytes, required = false)
    return value
}

internal fun Parcel.writeNullableInt(value: Int?) {
    writeInt(if (value == null) 0 else 1)
    if (value != null) writeInt(value)
}

internal fun Parcel.readNullableInt(): Int? = if (readPresence()) readInt() else null

internal fun Parcel.writeNullableLong(value: Long?) {
    writeInt(if (value == null) 0 else 1)
    if (value != null) writeLong(value)
}

internal fun Parcel.readNullableLong(): Long? = if (readPresence()) readLong() else null

internal fun Parcel.writeNullableDouble(value: Double?) {
    writeInt(if (value == null) 0 else 1)
    if (value != null) writeDouble(value)
}

internal fun Parcel.readNullableDouble(): Double? = if (readPresence()) readDouble() else null

internal fun Parcel.writeNullableBoolean(value: Boolean?) {
    writeInt(if (value == null) 0 else 1)
    if (value != null) writeBooleanValue(value)
}

internal fun Parcel.readNullableBoolean(): Boolean? = if (readPresence()) readBooleanValue() else null

internal fun Parcel.writeBooleanValue(value: Boolean) {
    writeInt(if (value) 1 else 0)
}

internal fun Parcel.readBooleanValue(): Boolean = when (val value = readInt()) {
    0 -> false
    1 -> true
    else -> throw BadParcelableException("Invalid boolean value")
}

private fun Parcel.readPresence(): Boolean = when (val value = readInt()) {
    0 -> false
    1 -> true
    else -> throw BadParcelableException("Invalid presence marker")
}

internal fun <T : Parcelable> Parcel.writeNullableParcelable(value: T?, flags: Int) {
    writeInt(if (value == null) 0 else 1)
    value?.writeToParcel(this, flags)
}

internal fun <T> Parcel.readNullableParcelable(creator: Parcelable.Creator<T>): T? =
    if (readPresence()) creator.createFromParcel(this) else null

internal fun Parcel.writeStringList(values: List<String>) {
    writeInt(values.size)
    values.forEach(::writeString)
}

internal fun Parcel.readStringList(
    maxItems: Int,
    maxUtf8BytesEach: Int? = null,
    minItems: Int = 0,
): List<String> {
    val size = readListSize(maxItems, minItems)
    return List(size) { readRequiredString(maxUtf8BytesEach) }
}

internal fun Parcel.writeIntList(values: List<Int>) {
    writeInt(values.size)
    values.forEach(::writeInt)
}

internal fun Parcel.readIntList(maxItems: Int, minItems: Int = 0): List<Int> {
    val size = readListSize(maxItems, minItems)
    return List(size) { readInt() }
}

internal fun <T : Parcelable> Parcel.writeParcelableList(values: List<T>, flags: Int) {
    writeInt(values.size)
    values.forEach { it.writeToParcel(this, flags) }
}

internal fun <T> Parcel.readParcelableList(
    creator: Parcelable.Creator<T>,
    maxItems: Int,
    minItems: Int = 0,
): List<T> {
    val size = readListSize(maxItems, minItems)
    return List(size) { creator.createFromParcel(this) }
}

private fun Parcel.readListSize(maxItems: Int, minItems: Int): Int {
    val size = readInt()
    if (size !in minItems..maxItems) {
        throw BadParcelableException("List size outside $minItems..$maxItems")
    }
    return size
}
