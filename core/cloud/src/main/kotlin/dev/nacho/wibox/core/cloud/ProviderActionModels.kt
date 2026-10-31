package dev.nacho.wibox.core.cloud

enum class ActionType {
    UNLOCK,
    F1,
    CALL_GUARD,
    CALL_ACCEPT,
    CALL_REJECT,
    START_CALL,
    STOP_CALL,
}

data class ActionCommand(
    val commandId: String,
    val device: DeviceRef,
    val channel: Int,
    val type: ActionType,
    val parameters: Map<String, String> = emptyMap(),
    val retryPolicy: ProviderRetryPolicy = ProviderRetryPolicy.DO_NOT_RETRY,
) {
    override fun toString(): String =
        "ActionCommand(commandId=$commandId, device=$device, channel=$channel, type=$type, " +
            "parameters=<redacted>, retryPolicy=$retryPolicy)"
}

enum class ActionAcknowledgementLevel {
    LOCAL_ACCEPTED,
    PROVIDER_ACCEPTED,
    DEVICE_ACKNOWLEDGED,
    PHYSICAL_OBSERVED,
    REJECTED,
    UNKNOWN_OUTCOME,
}

data class ActionOutcome(
    val level: ActionAcknowledgementLevel,
    val commandId: String,
    val correlationId: String,
    val providerCode: Int? = null,
    val observation: EventEnvelope? = null,
    val outcomeUnknown: Boolean = false,
) {
    fun canClaimPhysicalEffect(): Boolean =
        level == ActionAcknowledgementLevel.PHYSICAL_OBSERVED && observation != null && !outcomeUnknown

    companion object {
        fun localAccepted(commandId: String, correlationId: String = commandId): ActionOutcome =
            ActionOutcome(ActionAcknowledgementLevel.LOCAL_ACCEPTED, commandId, correlationId)

        fun providerAccepted(
            commandId: String,
            providerCode: Int? = null,
            correlationId: String = commandId,
        ): ActionOutcome = ActionOutcome(
            ActionAcknowledgementLevel.PROVIDER_ACCEPTED,
            commandId,
            correlationId,
            providerCode,
        )

        fun deviceAcknowledged(
            commandId: String,
            providerCode: Int? = null,
            correlationId: String = commandId,
        ): ActionOutcome = ActionOutcome(
            ActionAcknowledgementLevel.DEVICE_ACKNOWLEDGED,
            commandId,
            correlationId,
            providerCode,
        )

        fun physicalObserved(
            commandId: String,
            observation: EventEnvelope?,
            correlationId: String = commandId,
        ): ActionOutcome = ActionOutcome(
            ActionAcknowledgementLevel.PHYSICAL_OBSERVED,
            commandId,
            correlationId,
            observation = observation,
        )

        fun rejected(
            commandId: String,
            providerCode: Int? = null,
            correlationId: String = commandId,
        ): ActionOutcome = ActionOutcome(
            ActionAcknowledgementLevel.REJECTED,
            commandId,
            correlationId,
            providerCode,
        )

        fun unknown(commandId: String, correlationId: String = commandId): ActionOutcome =
            ActionOutcome(
                ActionAcknowledgementLevel.UNKNOWN_OUTCOME,
                commandId,
                correlationId,
                outcomeUnknown = true,
            )
    }
}
