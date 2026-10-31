package dev.nacho.wibox.externalapi.aidl

import android.os.Parcel
import android.os.Parcelable

data class ActionOutcomeResult(
    val outcome: ActionOutcome?,
    val error: ApiError?,
) : BoundedParcelable(RESPONSE_PARCEL_BYTES) {
    init {
        require((outcome != null) xor (error != null)) { "Exactly one payload or error is required" }
    }

    private constructor(source: Parcel) : this(
        outcome = source.readNullableParcelable(ActionOutcome.CREATOR),
        error = source.readNullableParcelable(ApiError.CREATOR),
    )

    override fun writeFields(dest: Parcel, flags: Int) {
        dest.writeNullableParcelable(outcome, flags)
        dest.writeNullableParcelable(error, flags)
    }

    companion object {
        @JvmField val CREATOR: Parcelable.Creator<ActionOutcomeResult> = boundedCreator("ActionOutcomeResult") { ActionOutcomeResult(it) }
    }
}
