package dev.nacho.wibox.externalapi.sdk

sealed interface WiboxEventUpdate {
    data class Subscription(val snapshot: EventSubscriptionSnapshot) : WiboxEventUpdate
    data class Events(val batch: EventBatch) : WiboxEventUpdate
    data class ConnectionChanged(val state: EventConnectionState) : WiboxEventUpdate
    data class Gap(val gap: EventGap) : WiboxEventUpdate
    data class AuthorizationChanged(val state: AuthorizationSnapshot) : WiboxEventUpdate
}
