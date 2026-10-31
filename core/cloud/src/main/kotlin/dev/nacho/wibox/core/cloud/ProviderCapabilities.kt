package dev.nacho.wibox.core.cloud

@JvmInline
value class CapabilityKey(val id: String)

enum class CapabilitySupport {
    REQUIRED,
    OPTIONAL,
    UNSUPPORTED,
    UNKNOWN,
}

enum class CapabilityEvidence {
    IMPLEMENTED_CONTRACT,
    IMPLEMENTED_OFFLINE,
    LOCAL_ONLY,
    STATIC_EVIDENCE_ONLY,
    UNSUPPORTED,
    UNKNOWN,
    DESIGN_REQUIRED,
    NOT_IMPLEMENTED,
}

enum class CapabilityScope {
    PROVIDER,
    ACCOUNT,
    DEVICE,
    CHANNEL,
    SESSION,
}

enum class EvidenceOrigin {
    DECLARATION_LOCAL,
    ACCOUNT_NEGOTIATION,
    SHADOW,
    INVITATION_PERMISSION,
    STATIC_APK,
    CONTRACT_TEST,
    DYNAMIC_CAPTURE,
}

enum class ProviderOperation(val capabilityId: String) {
    PREPARE_SESSION("core.lifecycle"),
    AUTHENTICATE("auth.oauth_password"),
    REFRESH_SESSION("auth.oauth_refresh"),
    RESTORE_SESSION("auth.session_restore"),
    GET_SHADOW("shadow.get"),
    GET_SHADOW_MULTI("shadow.multi"),
    SUBSCRIBE_SHADOW("shadow.subscribe"),
    UNSUBSCRIBE_SHADOW("shadow.unsubscribe"),
    PARSE_ALARM_EVENT("events.alarm_parser"),
    CHECK_APP_UPGRADE("app.update_check"),
}

data class CapabilitySnapshot(
    val key: CapabilityKey,
    val support: CapabilitySupport,
    val scope: CapabilityScope,
    val device: DeviceRef? = null,
    val channel: ChannelRef? = null,
    val revalidateAtMillis: Long? = null,
    val declaredOrigin: EvidenceOrigin,
    val declaredEvidence: CapabilityEvidence,
    val operation: ProviderOperation?,
    val operationEvidence: CapabilityEvidence,
    val operationOrigin: EvidenceOrigin,
    val profileVersion: String,
    val evidenceReferences: List<String>,
    val limitations: Set<String>,
)

object ProviderCapabilityCatalog {
    val stockQvCloud: List<CapabilitySnapshot> by lazy { STOCK_ROWS.map(::snapshot) }

    private fun snapshot(row: String): CapabilitySnapshot {
        val parts = row.split('|')
        require(parts.size == 5) { "Invalid capability catalog row" }
        val id = parts[0]
        val declaredEvidence = CapabilityEvidence.valueOf(parts[2].uppercase())
        val declaredOrigin = when (declaredEvidence) {
            CapabilityEvidence.IMPLEMENTED_CONTRACT, CapabilityEvidence.IMPLEMENTED_OFFLINE -> EvidenceOrigin.CONTRACT_TEST
            CapabilityEvidence.STATIC_EVIDENCE_ONLY -> EvidenceOrigin.STATIC_APK
            else -> EvidenceOrigin.DECLARATION_LOCAL
        }
        val operation = ProviderOperation.entries.firstOrNull { it.capabilityId == id }
        val isDynamicUpgrade = id == "app.update_check"
        return CapabilitySnapshot(
            key = CapabilityKey(id),
            support = CapabilitySupport.valueOf(parts[1].uppercase()),
            scope = scopeFor(id),
            declaredOrigin = declaredOrigin,
            declaredEvidence = declaredEvidence,
            operation = operation,
            operationEvidence = if (isDynamicUpgrade) CapabilityEvidence.IMPLEMENTED_CONTRACT else declaredEvidence,
            operationOrigin = if (isDynamicUpgrade) EvidenceOrigin.DYNAMIC_CAPTURE else declaredOrigin,
            profileVersion = "1",
            evidenceReferences = parts[3].split(','),
            limitations = parts[4].split(',').toSet(),
        )
    }

    private fun scopeFor(id: String): CapabilityScope = when {
        id == "core.capability_negotiation" -> CapabilityScope.DEVICE
        id.startsWith("core.") || id.startsWith("app.") || id == "events.notifications" -> CapabilityScope.PROVIDER
        id.startsWith("auth.") -> CapabilityScope.SESSION
        id == "devices.list" || id.startsWith("invitations.") || id == "events.realtime" ||
            id == "events.history" || id == "events.photo" || id == "events.delete" -> CapabilityScope.ACCOUNT
        id.startsWith("media.") || id == "events.filters" -> CapabilityScope.CHANNEL
        id == "security.persistence" -> CapabilityScope.SESSION
        else -> CapabilityScope.DEVICE
    }

    private val STOCK_ROWS = listOf(
        "core.lifecycle|required|implemented_contract|core/cloud/src/main/kotlin/dev/nacho/wibox/core/cloud/WiboxCloud.kt,docs/protocol.md#7|api_wire=contract_tested,behavior=offline_only,security=resource_isolation_offline,hardware=not_applicable_local",
        "core.capability_negotiation|required|local_only|core/cloud/src/main/kotlin/dev/nacho/wibox/core/cloud/EventModels.kt,E020|api_wire=static_capability_set,behavior=not_negotiated,security=not_reviewed_runtime,hardware=not_tested",
        "auth.opaque_registration|required|static_evidence_only|E004,E010,docs/rea-analysis.md#20|api_wire=static_only,behavior=not_demonstrated,security=not_reviewed_runtime,hardware=not_tested",
        "auth.user_login|required|static_evidence_only|E004,E010|api_wire=static_only,behavior=not_demonstrated,security=not_reviewed_runtime,hardware=not_tested",
        "auth.oauth_password|required|implemented_contract|E029,E033,P-OAUTH-PASSWORD|api_wire=contract_tested,behavior=offline_only,security=strict_tls_offline,hardware=not_applicable_protocol_only",
        "auth.oauth_refresh|required|implemented_contract|E033,E034,E035,P-OAUTH-REFRESH|api_wire=contract_tested,behavior=offline_only,security=strict_tls_offline,hardware=not_applicable_protocol_only",
        "auth.session_restore|required|implemented_contract|E034,E036,P-SESSION-RESTORE|api_wire=contract_tested,behavior=offline_only,security=store_injected_not_android_verified,hardware=not_applicable_local",
        "auth.logout|unknown|unknown|E036,E041|api_wire=unknown,behavior=unknown,security=unknown,hardware=not_tested",
        "discovery.regional_services|required|unsupported|E034,E040,U-SERVICE-DISCOVERY,docs/rea-analysis.md#20|api_wire=static_only,behavior=unsupported_current,security=strict_design_only,hardware=not_tested",
        "devices.list|required|unsupported|E004,E010,E020,U-DEVICE-LIST|api_wire=static_only,behavior=unsupported_current,security=strict_design_only,hardware=not_tested",
        "devices.dynamic_credentials|required|static_evidence_only|E010,E013,E022|api_wire=static_only,behavior=not_demonstrated,security=not_reviewed_runtime,hardware=not_tested",
        "devices.local_discovery|required|unsupported|E014,E017,E018|api_wire=static_only,behavior=local_demo_only,security=strict_design_only,hardware=not_tested",
        "devices.online_status|required|static_evidence_only|E020,E041,E063|api_wire=shadow_contract_partial,behavior=semantics_unknown,security=strict_tls_offline,hardware=not_tested",
        "pairing.qr|required|local_only|E007,E012|api_wire=parser_local_only,behavior=offline_parser_only,security=no_real_secret_fixtures,hardware=not_tested",
        "pairing.ap_wifi|required|local_only|E014,E015,E017,E018|api_wire=static_only,behavior=local_demo_only,security=no_real_wifi,hardware=not_tested",
        "pairing.audio|required|local_only|E016,E017,E018|api_wire=static_only,behavior=local_demo_only,security=no_audio_emitted,hardware=not_tested",
        "pairing.bind|required|local_only|E009,E010,E013|api_wire=static_only,behavior=local_demo_only,security=local_validation_only,hardware=not_tested",
        "invitations.accept|required|local_only|E008,E010|api_wire=parser_local_only,behavior=local_demo_only,security=synthetic_links_only,hardware=not_tested",
        "invitations.manage|required|local_only|E055,analysis/apk/ui-inventory.json#share_manage|api_wire=not_implemented,behavior=local_demo_only,security=local_roles_only,hardware=not_tested",
        "invitations.issue|required|local_only|E008,E011,analysis/apk/ui-inventory.json#share_device|api_wire=not_implemented,behavior=synthetic_link_only,security=example_invalid_only,hardware=not_tested",
        "invitations.permissions|required|local_only|E019,E055|api_wire=static_only,behavior=local_demo_only,security=local_roles_only,hardware=not_tested",
        "shadow.get|required|implemented_contract|E041,P-SHADOW-STATUS|api_wire=contract_tested,behavior=offline_only,security=strict_tls_offline,hardware=not_tested",
        "shadow.multi|required|implemented_contract|E041,P-SHADOW-MULTI|api_wire=contract_tested,behavior=offline_only,security=strict_tls_offline,hardware=not_tested",
        "shadow.subscribe|required|implemented_contract|E041,P-SHADOW-SUB|api_wire=contract_tested,behavior=ack_offline_only,security=strict_tls_offline,hardware=not_tested",
        "shadow.unsubscribe|required|implemented_contract|E041,P-SHADOW-UNSUB|api_wire=contract_tested,behavior=ack_offline_only,security=strict_tls_offline,hardware=not_tested",
        "events.alarm_parser|required|implemented_offline|E056,E057,P-ALARM-FCM|api_wire=offline_fixtures,behavior=parser_tested,security=synthetic_fixtures,hardware=not_applicable_parser",
        "events.push_registration|required|static_evidence_only|E048,E056,E057|api_wire=static_only,behavior=not_demonstrated,security=not_reviewed_runtime,hardware=not_tested",
        "events.realtime|required|unsupported|E037,E038,E039,E040,E063,E073,U-REALTIME|api_wire=mqtt_static_disabled,behavior=unsupported_current,security=strict_design_only,hardware=not_tested",
        "events.history|required|local_only|E051,E052|api_wire=static_only,behavior=local_demo_only,security=local_only,hardware=not_tested",
        "events.photo|required|local_only|E051,E052,analysis/apk/ui-inventory.json#tab_events|api_wire=not_implemented,behavior=synthetic_markers_only,security=no_real_media,hardware=not_tested",
        "events.delete|required|local_only|E051,analysis/apk/ui-inventory.json#tab_events|api_wire=not_implemented,behavior=local_demo_only,security=local_only,hardware=not_tested",
        "events.filters|optional|static_evidence_only|E051,analysis/apk/ui-inventory.json#tab_events|api_wire=static_only,behavior=hidden_in_reference,security=not_reviewed_runtime,hardware=not_tested",
        "media.preview_single|required|unsupported|E011,E021,E042,U-MEDIA|api_wire=native_static_only,behavior=unsupported_current,security=strict_design_only,hardware=not_tested",
        "media.preview_multi|required|unsupported|E021,E023,analysis/apk/ui-inventory.json#preview|api_wire=not_implemented,behavior=unsupported_current,security=strict_design_only,hardware=not_tested",
        "media.stream_controls|required|unsupported|E021,E024,E026|api_wire=static_only,behavior=unsupported_current,security=strict_design_only,hardware=not_tested",
        "media.listen|required|unsupported|E023|api_wire=native_static_only,behavior=unsupported_current,security=strict_design_only,hardware=not_tested",
        "media.talk|required|unsupported|E011,E023|api_wire=native_static_only,behavior=unsupported_current,security=strict_design_only,hardware=not_tested",
        "media.snapshot|required|unsupported|E021,analysis/apk/ui-inventory.json#preview|api_wire=not_implemented,behavior=metadata_demo_only,security=no_real_media,hardware=not_tested",
        "media.record|required|unsupported|E021,analysis/apk/ui-inventory.json#preview|api_wire=not_implemented,behavior=metadata_demo_only,security=no_real_media,hardware=not_tested",
        "media.local_gallery|optional|local_only|E062,analysis/apk/ui-inventory.json#tab_gallery|api_wire=local_metadata_only,behavior=local_demo_only,security=no_real_media,hardware=not_tested",
        "media.local_share_save|optional|local_only|E062,E075|api_wire=local_partial,behavior=share_export_unsupported,security=fileprovider_review_required,hardware=not_tested",
        "actions.unlock|required|unsupported|E019,E023,E025,U-ACTIONS|api_wire=static_only,behavior=unsupported_current,security=strict_design_only,hardware=not_tested",
        "actions.f1|required|unsupported|E019,E023,U-ACTIONS|api_wire=static_only,behavior=unsupported_current,security=strict_design_only,hardware=not_tested",
        "actions.call_guard|required|unsupported|E019,E054,U-ACTIONS|api_wire=static_only,behavior=unsupported_current,security=strict_design_only,hardware=not_tested",
        "actions.call_accept|required|unsupported|E057,E058,E059,U-ACTIONS|api_wire=static_only,behavior=local_demo_only,security=strict_design_only,hardware=not_tested",
        "actions.call_reject|required|unsupported|E057,E058,E059,U-ACTIONS|api_wire=static_only,behavior=local_demo_only,security=strict_design_only,hardware=not_tested",
        "calls.lifecycle|required|local_only|E056,E057,E059|api_wire=static_only,behavior=local_demo_only,security=strict_design_only,hardware=not_tested",
        "config.rename|required|local_only|analysis/apk/ui-inventory.json#device_info|api_wire=not_implemented,behavior=local_demo_only,security=local_validation_only,hardware=not_tested",
        "config.passwords|required|local_only|E031,E048,analysis/apk/ui-inventory.json#password_modify|api_wire=static_only,behavior=local_validation_only,security=not_reviewed_runtime,hardware=not_tested",
        "config.unlock_security|required|local_only|E019,analysis/apk/ui-inventory.json#unlock_setting|api_wire=local_settings_only,behavior=biometric_unavailable_demo,security=local_only,hardware=not_tested",
        "config.alarms|required|local_only|E051,E055,analysis/apk/ui-inventory.json#config|api_wire=static_only,behavior=local_demo_only,security=local_roles_only,hardware=not_tested",
        "config.wifi|required|local_only|E014,E015,analysis/apk/ui-inventory.json#router|api_wire=static_only,behavior=local_demo_only,security=no_real_wifi,hardware=not_tested",
        "config.time_sync|required|local_only|E055,analysis/apk/ui-inventory.json#config|api_wire=not_implemented,behavior=local_demo_only,security=local_roles_only,hardware=not_tested",
        "config.playback_intercom|required|local_only|analysis/apk/ui-inventory.json#config,E021,E023|api_wire=not_implemented,behavior=local_demo_only,security=local_only,hardware=not_tested",
        "config.delete_device|required|local_only|E041,E055,analysis/apk/ui-inventory.json#config|api_wire=not_implemented,behavior=local_demo_only,security=local_roles_only,hardware=not_tested",
        "firmware.check|required|local_only|analysis/apk/ui-inventory.json#upgrade|api_wire=not_implemented,behavior=up_to_date_stub,security=strict_design_only,hardware=not_tested",
        "firmware.apply|required|unsupported|analysis/apk/ui-inventory.json#upgrade|api_wire=not_implemented,behavior=unsupported_current,security=strict_design_only,hardware=not_tested",
        "app.update_check|optional|static_evidence_only|E049,E050|api_wire=static_only,behavior=not_demonstrated,security=strict_tls_required_not_original,hardware=not_tested",
        "app.privacy_terms|required|local_only|E064,analysis/apk/ui-inventory.json#about|api_wire=local_content,behavior=local_only,security=no_remote_policy,hardware=not_applicable_local",
        "app.deep_link|optional|static_evidence_only|E006,E057,analysis/apk/ui-inventory.json#loading,analysis/apk/ui-inventory.json#cache|api_wire=manifest_static_only,behavior=not_demonstrated,security=not_reviewed_runtime,hardware=not_tested",
        "app.diagnostics|optional|local_only|E061,E067|api_wire=local_only,behavior=local_demo_only,security=token_not_displayed_demo,hardware=not_tested",
        "app.sound_vibration|required|local_only|E058,E060|api_wire=local_preferences,behavior=local_demo_only,security=local_only,hardware=not_tested",
        "security.persistence|required|design_required|E036,E043,E044,E045,E046,E047,core/cloud/src/main/kotlin/dev/nacho/wibox/core/cloud/CloudModels.kt|api_wire=token_store_abstraction,behavior=android_store_not_verified,security=design_required,hardware=not_tested",
        "events.notifications|required|static_evidence_only|E056,E057,E058,E059|api_wire=static_only,behavior=not_demonstrated,security=strict_design_only,hardware=not_tested",
    )
}
