package dev.nacho.wibox.externalapi.aidl

import android.os.Parcel
import android.os.Parcelable

data class ProviderExtension(
    val namespacedKey: String,
    val value: ProviderExtensionValue,
) : BoundedParcelable(RESPONSE_PARCEL_BYTES) {
    init {
        validateUtf8("namespacedKey", namespacedKey, 128, required = true)
    }

    private constructor(source: Parcel) : this(
        namespacedKey = source.readRequiredString(128),
        value = ProviderExtensionValue.CREATOR.createFromParcel(source),
    )

    override fun writeFields(dest: Parcel, flags: Int) {
        dest.writeString(namespacedKey)
        value.writeToParcel(dest, flags)
    }

    companion object {
        @JvmField val CREATOR: Parcelable.Creator<ProviderExtension> = boundedCreator("ProviderExtension") { ProviderExtension(it) }
    }
}
