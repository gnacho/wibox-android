package dev.nacho.wibox.externalapi.aidl

import android.os.Parcel
import android.os.Parcelable

data class CapabilitySnapshot(
    val key: String,
    val scope: Int,
    val support: Int,
    val availability: Int,
    val evidence: String,
    val origin: String,
    val profileVersion: String,
    val observedAtEpochMs: Long,
    val revalidateAfterEpochMs: Long?,
    val limitations: List<String>,
    val extensions: List<ProviderExtension>,
) : BoundedParcelable(RESPONSE_PARCEL_BYTES) {
    init {
        validateUtf8("key", key, 128, required = true)
        validateUtf8("evidence", evidence, 64, required = true)
        validateUtf8("origin", origin, 64, required = true)
        validateUtf8("profileVersion", profileVersion, 32, required = true)
        validateItems("limitations", limitations, maxItems = 10)
        limitations.forEachIndexed { index, value -> validateUtf8("limitations[$index]", value, 256) }
        validateItems("extensions", extensions, maxItems = 20)
    }

    private constructor(source: Parcel) : this(
        key = source.readRequiredString(128),
        scope = source.readInt(),
        support = source.readInt(),
        availability = source.readInt(),
        evidence = source.readRequiredString(64),
        origin = source.readRequiredString(64),
        profileVersion = source.readRequiredString(32),
        observedAtEpochMs = source.readLong(),
        revalidateAfterEpochMs = source.readNullableLong(),
        limitations = source.readStringList(maxItems = 10, maxUtf8BytesEach = 256),
        extensions = source.readParcelableList(ProviderExtension.CREATOR, maxItems = 20),
    )

    override fun writeFields(dest: Parcel, flags: Int) {
        dest.writeString(key)
        dest.writeInt(scope)
        dest.writeInt(support)
        dest.writeInt(availability)
        dest.writeString(evidence)
        dest.writeString(origin)
        dest.writeString(profileVersion)
        dest.writeLong(observedAtEpochMs)
        dest.writeNullableLong(revalidateAfterEpochMs)
        dest.writeStringList(limitations)
        dest.writeParcelableList(extensions, flags)
    }

    companion object {
        @JvmField val CREATOR: Parcelable.Creator<CapabilitySnapshot> = boundedCreator("CapabilitySnapshot") { CapabilitySnapshot(it) }
    }
}
