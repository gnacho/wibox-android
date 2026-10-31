package dev.nacho.wibox.externalapi.aidl

import android.os.Parcel
import android.os.Parcelable

data class UpgradeCheckRequest(
    val requestId: String,
    val languageTag: String,
) : BoundedParcelable(REQUEST_PARCEL_BYTES) {
    init {
        validateUtf8("requestId", requestId, 64, required = true)
        validateUtf8("languageTag", languageTag, 35, required = true)
        require(languageTag.matches(Regex("[A-Za-z]{1,8}(?:-[A-Za-z0-9]{1,8})*"))) { "languageTag must be BCP 47" }
    }

    private constructor(source: Parcel) : this(
        requestId = source.readRequiredString(64),
        languageTag = source.readRequiredString(35),
    )

    override fun writeFields(dest: Parcel, flags: Int) {
        dest.writeString(requestId)
        dest.writeString(languageTag)
    }

    companion object {
        @JvmField val CREATOR: Parcelable.Creator<UpgradeCheckRequest> = boundedCreator("UpgradeCheckRequest") { UpgradeCheckRequest(it) }
    }
}
