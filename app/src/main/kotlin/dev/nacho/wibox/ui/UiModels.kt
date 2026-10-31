package dev.nacho.wibox.ui

import dev.nacho.wibox.core.cloud.ActionOutcome
import dev.nacho.wibox.core.cloud.DevicePermission
import dev.nacho.wibox.core.cloud.DevicePresence
import dev.nacho.wibox.core.cloud.DeviceRole
import dev.nacho.wibox.core.cloud.PreparationState
import dev.nacho.wibox.core.cloud.ProviderError
import dev.nacho.wibox.core.cloud.ProviderEventType
import dev.nacho.wibox.data.MediaKind

sealed interface LoadState<out T> {
    val data: T?
    val refreshing: Boolean
    val stale: Boolean
    val lastUpdated: Long?
    val error: ProviderError?

    data class Loading<T>(
        override val data: T? = null,
        override val refreshing: Boolean = false,
        override val stale: Boolean = false,
        override val lastUpdated: Long? = null,
        override val error: ProviderError? = null,
    ) : LoadState<T>

    data class Content<T>(
        val value: T,
        override val refreshing: Boolean = false,
        override val stale: Boolean = false,
        override val lastUpdated: Long? = null,
        override val error: ProviderError? = null,
    ) : LoadState<T> {
        override val data: T get() = value
    }

    data class Error<T>(
        override val error: ProviderError,
        override val data: T? = null,
        override val refreshing: Boolean = false,
        override val stale: Boolean = true,
        override val lastUpdated: Long? = null,
    ) : LoadState<T>
}

enum class MainTab {
    DEVICES,
    EVENTS,
    GALLERY,
    SETTINGS,
}

enum class UserNotice {
    LOCAL_ACTION_ACKNOWLEDGED,
    ACTION_FAILED,
    ACTION_UNKNOWN,
    UNSUPPORTED,
    DELETED,
    SAVED_LOCAL,
    MEDIA_EXPORT_UNSUPPORTED,
    INVITATION_CREATED,
    DEMO_RESET,
}

data class DeviceUi(
    val id: String,
    val name: String,
    val model: String,
    val role: DeviceRole,
    val presence: DevicePresence,
    val permissions: Set<DevicePermission>,
    val lastObservedAtMillis: Long?,
    val fresh: Boolean,
    val firmwareCurrent: String,
    val firmwareLatest: String,
    val callAlerts: Boolean,
    val motionAlerts: Boolean,
    val f1Seconds: Int,
    val highQuality: Boolean,
    val fullDuplex: Boolean,
)

data class EventUi(
    val id: String,
    val deviceId: String,
    val type: ProviderEventType,
    val channel: Int,
    val timestampMillis: Long,
    val mediaId: String? = null,
)

data class MediaUi(
    val id: String,
    val deviceId: String,
    val kind: MediaKind,
    val timestampMillis: Long,
    val durationSeconds: Int,
    val synthetic: Boolean,
)

data class InvitationUi(
    val id: String,
    val deviceId: String,
    val displayName: String,
    val code: String,
    val createdAtMillis: Long,
    val expiresAtMillis: Long,
    val link: String,
)

data class ProviderStatusUi(
    val label: String,
    val profileVersion: String,
    val simulated: Boolean,
    val preparation: PreparationState = PreparationState.IDLE,
    val error: ProviderError? = null,
)

data class ShadowUi(
    val returnedFields: Set<String> = emptySet(),
    val path: String? = null,
    val timestampMillis: Long? = null,
    val fresh: Boolean = false,
)

data class ActionUi(
    val pendingCommandId: String? = null,
    val outcome: ActionOutcome? = null,
    val error: ProviderError? = null,
)

