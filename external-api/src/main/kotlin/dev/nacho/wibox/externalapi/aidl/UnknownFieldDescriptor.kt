package dev.nacho.wibox.externalapi.aidl

import android.os.Parcel
import android.os.Parcelable

data class UnknownFieldDescriptor(
    val name: String,
    val observedType: String,
) : BoundedParcelable(RESPONSE_PARCEL_BYTES) {
    init {
        validateUtf8("name", name, 128, required = true)
        validateUtf8("observedType", observedType, 32, required = true)
    }

    private constructor(source: Parcel) : this(
        name = source.readRequiredString(128),
        observedType = source.readRequiredString(32),
    )

    override fun writeFields(dest: Parcel, flags: Int) {
        dest.writeString(name)
        dest.writeString(observedType)
    }

    companion object {
        @JvmField val CREATOR: Parcelable.Creator<UnknownFieldDescriptor> = boundedCreator("UnknownFieldDescriptor") { UnknownFieldDescriptor(it) }
    }
}
