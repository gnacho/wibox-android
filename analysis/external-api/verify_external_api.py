#!/usr/bin/env python3
"""Verify the Wibox external API design without modifying the source repository."""

from __future__ import annotations

import argparse
import copy
import hashlib
import json
import os
import re
import sys
from pathlib import Path
from typing import Any

SUCCESS_MARKER = "WIBOX_EXTERNAL_API_DESIGN_VERIFIED"
REQUIRED_METHODS = {
    "handshake",
    "requestAuthorization",
    "getAuthorizationState",
    "getProviderSnapshot",
    "getCapabilities",
    "listDevices",
    "getDevice",
    "getDeviceShadow",
    "getDeviceShadows",
    "subscribeEvents",
    "unsubscribeEvents",
    "performAction",
    "cancelOperation",
    "getActionOutcome",
    "checkAppUpgrade",
}
REQUIRED_ACK_LEVELS = {
    "LOCAL_ACCEPTED",
    "PROVIDER_ACCEPTED",
    "DEVICE_ACKNOWLEDGED",
    "PHYSICAL_OBSERVED",
    "UNKNOWN_OUTCOME",
}
REQUIRED_MODELS = {
    "ClientHello",
    "HandshakeResult",
    "AuthorizationRequest",
    "AuthorizationSnapshot",
    "ProviderSnapshot",
    "SessionSummary",
    "CapabilitySnapshot",
    "DeviceSnapshot",
    "DevicePage",
    "ShadowSnapshot",
    "StockShadowFields",
    "UnknownFieldDescriptor",
    "EventCursor",
    "EventEnvelope",
    "EventBatch",
    "EventGap",
    "ActionRequest",
    "ActionOutcome",
    "CancellationResult",
    "UpgradeCheckRequest",
    "UpgradeInfo",
    "ApiError",
}
REQUIRED_ERRORS = {
    "PACKAGE_UID_MISMATCH",
    "SHARED_UID_UNSUPPORTED",
    "SIGNATURE_CHANGED",
    "AUTHORIZATION_REVOKED",
    "VERSION_UNSUPPORTED",
    "UNSUPPORTED",
    "CAPABILITY_UNKNOWN",
    "PAYLOAD_TOO_LARGE",
    "RATE_LIMITED",
    "SERVICE_DIED",
    "OUTCOME_UNKNOWN",
}
FORBIDDEN_MODEL_FIELDS = {
    "token",
    "accesstoken",
    "refreshtoken",
    "cookie",
    "setcookie",
    "password",
    "secret",
    "credential",
    "credentials",
    "clientid",
    "dataencodekey",
    "authcode",
    "wifipassword",
    "invitecode",
    "downloadurl",
    "url",
}
ALLOWED_API_EXPOSURE = {
    "contract_local",
    "contract_runtime_gated",
    "contract_demo_only",
    "unsupported_now",
    "not_exposed",
}
DOC_REQUIRED_TEXT = [
    "Binder/AIDL",
    "Binder.getCallingUid()",
    "PackageManager.getPackagesForUid(uid)",
    "SHA-256",
    "Denegar por defecto",
    "UID compartido",
    "`normal`",
    "`signature`",
    "WiboxProvider",
    "FakeWiboxCloud",
    "UNKNOWN_OUTCOME",
    "PHYSICAL_OBSERVED",
    "LOCAL_ACCEPTED",
    "UNSUPPORTED",
    "design-only",
    "no se han implementado",
    "dos apps de firmas distintas",
]


def normalize_field(value: str) -> str:
    return re.sub(r"[^a-z0-9]", "", value.lower())


def load_json(path: Path) -> dict[str, Any]:
    value = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(value, dict):
        raise ValueError(f"Expected a JSON object in {path}")
    return value


def validate_design(
    contract: dict[str, Any],
    documentation: str,
    provider_matrix: dict[str, Any],
    dynamic_inventory: dict[str, Any],
    matrix_bytes: bytes,
    verifier_source: str,
) -> list[str]:
    errors: list[str] = []

    def check(condition: bool, message: str) -> None:
        if not condition:
            errors.append(message)

    check(contract.get("status") == "design-only", "status must be design-only")
    check(contract.get("implementation_claimed") is False, "implementation must not be claimed")
    check(contract.get("tests_executed_now") is False, "future tests must not be claimed as executed")

    service = contract.get("service", {})
    check(service.get("transport") == "android_binder_aidl", "Binder/AIDL transport is required")
    check(
        service.get("binding") == "explicit_bound_service",
        "service binding must be explicit_bound_service",
    )
    check(service.get("exported") is True, "third-party service must be explicitly exported")
    check(service.get("http_server") is False, "an open HTTP server is forbidden")
    check(service.get("manifest_permission") is None, "manifest permission is not the authorization boundary")
    check(service.get("provider_boundary") == "WiboxProvider", "API must target WiboxProvider")
    check(service.get("binder_instance_per_connection") is True, "each binding needs isolated session state")
    check(bool(service.get("component")), "an explicit service component is required")
    check(bool(service.get("intent_action")), "a stable binding intent action is required")

    auth = contract.get("auth", {})
    check(auth.get("default_policy") == "deny", "authorization must deny by default")
    factors = set(auth.get("factors", []))
    check(
        factors == {"real_uid", "package_name", "current_signing_certificate_set_sha256"},
        "authorization must combine real UID, package and current signer SHA-256",
    )
    check(auth.get("uid_source") == "Binder.getCallingUid()", "UID must come from Binder.getCallingUid")
    check(
        auth.get("package_source") == "PackageManager.getPackagesForUid(uid)",
        "packages must come from the real UID",
    )
    check(auth.get("package_name_only_sufficient") is False, "package_name_only must be rejected")
    check(auth.get("normal_permission_sufficient") is False, "a normal permission is not sufficient")
    check(
        auth.get("custom_signature_permission_for_third_parties") is False,
        "custom_signature permission must not be used for differently signed third parties",
    )
    check(auth.get("client_supplied_certificate_trusted") is False, "client certificate claims are untrusted")
    check(auth.get("per_call_reauthorization") is True, "every transaction must reauthorize")
    check(
        auth.get("background_identity") == "captured_at_binder_transaction_and_attached_to_work",
        "background work must retain the transaction identity",
    )
    check(auth.get("empty_device_grants_mean") == "no_devices", "empty device grants must deny devices")
    check(auth.get("device_id_wildcards_allowed") is False, "device grant wildcards are forbidden")
    check(
        auth.get("revocation_cleanup") == "unsubscribe_and_cancel_owned_operations_allowed",
        "revocation must still allow owned cleanup",
    )
    check(auth.get("shared_uid_policy") == "deny", "shared UID must be denied in v1")
    check(auth.get("isolated_uid_policy") == "deny", "isolated UID must be denied")
    check(
        auth.get("signature_rotation") == "suspend_and_require_user_reapproval",
        "signature rotation must suspend and require reapproval",
    )
    check(auth.get("signing_history_automatically_trusted") is False, "signing history is not automatic authorization")
    check(auth.get("uid_change") == "require_user_reapproval", "UID changes require reapproval")
    check(auth.get("third_party_self_approval") is False, "third parties must not approve themselves")
    check("revocable" in str(auth.get("allowlist", "")), "allowlist must be user revocable")

    versioning = contract.get("versioning", {})
    check(versioning.get("different_major_error") == "VERSION_UNSUPPORTED", "major mismatch must be explicit")
    check(versioning.get("additive_minor_changes_only") is True, "minor changes must be additive")
    check(versioning.get("append_only_aidl_methods") is True, "AIDL methods must be append only")
    check(
        versioning.get("parcelable_layout_changes_within_major") is False,
        "Parcelable layouts must not change within a major",
    )
    check(
        versioning.get("new_parcelable_fields_require_v2_type") is True,
        "new Parcelable fields require a V2 type",
    )
    check(versioning.get("server_side_version_enforcement") is True, "server_side_version_enforcement is required")
    check(versioning.get("unknown_enum_policy") == "preserve_as_UNKNOWN", "unknown enums must be tolerated")

    methods = contract.get("methods", [])
    method_names = {method.get("name") for method in methods if isinstance(method, dict)}
    missing_methods = sorted(REQUIRED_METHODS - method_names)
    check(not missing_methods, f"missing required methods: {missing_methods}")
    for method in methods:
        if not isinstance(method, dict):
            errors.append("every method must be an object")
            continue
        if method.get("kind") in {"asynchronous_one_shot", "synchronous_setup_asynchronous_delivery"}:
            check(bool(method.get("callback")), f"asynchronous method {method.get('name')} needs a callback")
    action_method = next((method for method in methods if method.get("name") == "performAction"), {})
    check(action_method.get("current_stock_behavior") == "UNSUPPORTED", "stock actions must remain UNSUPPORTED")
    check(action_method.get("automatic_physical_retry") is False, "performAction must forbid automatic physical retry")

    models = contract.get("models", {})
    check(REQUIRED_MODELS <= set(models), f"missing models: {sorted(REQUIRED_MODELS - set(models))}")
    for model_name, model in models.items():
        for field in model.get("fields", []):
            field_name = field.get("name", "")
            check(
                normalize_field(field_name) not in FORBIDDEN_MODEL_FIELDS,
                f"forbidden model field {model_name}.{field_name}",
            )
    session_absent = " ".join(models.get("SessionSummary", {}).get("explicitly_absent", []))
    for required_absence in ["password", "token", "cookie", "client identifier", "credential"]:
        check(required_absence in session_absent, f"SessionSummary must exclude {required_absence}")
    check(
        models.get("UnknownFieldDescriptor", {}).get("raw_value_exposed") is False,
        "unknown raw values must not cross AIDL",
    )
    check(
        models.get("AttachmentMetadata", {}).get("content_or_uri_exposed") is False,
        "media content and broad URIs must not cross AIDL",
    )
    check(models.get("UpgradeInfo", {}).get("query_only") is True, "upgrade check must be query only")

    error_codes = set(contract.get("errors", []))
    check(REQUIRED_ERRORS <= error_codes, f"missing errors: {sorted(REQUIRED_ERRORS - error_codes)}")
    check("DO_NOT_RETRY" in contract.get("retry_policies", []), "DO_NOT_RETRY is required")
    check("RESYNC" in contract.get("retry_policies", []), "RESYNC is required")

    ack_levels = {entry.get("id") for entry in contract.get("ack_levels", [])}
    check(REQUIRED_ACK_LEVELS <= ack_levels, f"missing ack levels including UNKNOWN_OUTCOME: {sorted(REQUIRED_ACK_LEVELS - ack_levels)}")
    action_policy = contract.get("action_policy", {})
    check(action_policy.get("authorization_and_capability_checked") is True, "actions need auth and capability checks")
    check(action_policy.get("success_without_ack") is False, "success without ack is forbidden")
    check(action_policy.get("automatic_physical_retries") == 0, "automatic physical retries must be zero")
    check(
        action_policy.get("duplicate_request_id") == "return_existing_operation_or_outcome_never_reexecute",
        "duplicate_request_id must never reexecute an action",
    )
    check(
        action_policy.get("request_id_reuse_with_different_payload") == "CONFLICT",
        "request ID reuse with another payload must conflict",
    )
    check(action_policy.get("unknown_outcome_is_not_failure_or_success") is True, "UNKNOWN_OUTCOME must remain ambiguous")
    check(action_policy.get("cancel_after_dispatch_guarantees_rollback") is False, "cancellation is not physical rollback")
    check(action_policy.get("demo_marks_simulation") is True, "demo_marks_simulation must be true")
    check(action_policy.get("demo_maximum_ack") == "LOCAL_ACCEPTED", "demo must not exceed LOCAL_ACCEPTED")
    check(action_policy.get("demo_presented_as_qvcloud") is False, "demo must not be presented as QVCloud")

    event_policy = contract.get("event_policy", {})
    check(event_policy.get("gaps_explicit") is True, "event gaps must be explicit")
    check(event_policy.get("resync_required_explicit") is True, "resync must be explicit")
    check(event_policy.get("empty_stream_means_connected") is False, "an empty stream is not a success")
    check(event_policy.get("exactly_once_claimed") is False, "exactly-once must not be claimed")
    check(event_policy.get("callback_death_unsubscribes") is True, "callback death must unsubscribe")

    limits = contract.get("limits", {})
    for key, value in limits.items():
        if key != "silent_truncation":
            check(isinstance(value, int) and value > 0, f"limit {key} must be a positive integer")
    check(limits.get("silent_truncation") is False, "silent truncation is forbidden")
    check(
        limits.get("event_batch_serialized_bytes", 65537) <= limits.get("response_serialized_bytes", 0),
        "event batch budget must fit the callback budget",
    )

    lifecycle = contract.get("lifecycle", {})
    check(lifecycle.get("main_thread_provider_io") is False, "provider I/O must not use the main thread")
    check(lifecycle.get("blocking_binder_physical_wait") is False, "Binder must not wait for physical actions")
    check(lifecycle.get("global_scope") is False, "GlobalScope is forbidden")
    check(lifecycle.get("one_shot_callback_removed_in_finally") is True, "one-shot callbacks must be removed")
    check(
        lifecycle.get("background_work_uses_captured_transaction_identity") is True,
        "background work must use captured transaction identity",
    )
    check(
        lifecycle.get("failed_subscription_callback_registered") is False,
        "failed subscriptions must not retain callbacks",
    )
    check(
        lifecycle.get("sdk_holds_callback_strong_until_terminal") is True,
        "SDK must retain callbacks until terminal completion",
    )
    check(lifecycle.get("link_to_death") is True, "Binder death recipients are required")
    check(lifecycle.get("automatic_action_replay_on_reconnect") is False, "action replay on reconnect is forbidden")
    check(lifecycle.get("media_over_binder") is False, "media_over_binder must remain false")
    check(lifecycle.get("file_descriptor_media_over_binder") is False, "media file descriptors are out of scope")

    provider_policy = contract.get("provider_policy", {})
    check(provider_policy.get("port") == "WiboxProvider", "provider policy must use WiboxProvider")
    check(provider_policy.get("typed_provider_extensions") is True, "typed provider extensions are required")
    check(provider_policy.get("arbitrary_raw_dto_exposure") is False, "raw DTO exposure is forbidden")
    check(provider_policy.get("unknown_field_values_exposed") is False, "unknown values need review before exposure")
    check(provider_policy.get("unknown_is_not_unsupported") is True, "unknown and unsupported must differ")
    check(provider_policy.get("demo_fallback_on_stock_failure") is False, "stock failure must not activate demo")

    matrix_capabilities = provider_matrix.get("capabilities", [])
    source_by_id = {entry.get("id"): entry for entry in matrix_capabilities}
    contract_capabilities = contract.get("capability_matrix", [])
    contract_by_id = {entry.get("id"): entry for entry in contract_capabilities}
    expected_count = provider_matrix.get("totals", {}).get("capabilities")
    check(len(matrix_capabilities) == expected_count, "provider matrix capability count mismatch")
    check(len(contract_capabilities) == len(matrix_capabilities), "contract must include every provider capability")
    check(len(contract_by_id) == len(contract_capabilities), "contract capability IDs must be unique")
    check(set(contract_by_id) == set(source_by_id), "contract and provider matrix capability IDs differ")

    matrix_source = contract.get("matrix_source", {})
    actual_matrix_hash = hashlib.sha256(matrix_bytes).hexdigest()
    check(matrix_source.get("sha256") == actual_matrix_hash, "provider matrix SHA-256 mismatch")
    check(matrix_source.get("evidence_levels_elevated") is False, "matrix evidence must not be elevated")

    for capability_id, source in source_by_id.items():
        designed = contract_by_id.get(capability_id, {})
        stock = source.get("providers", {}).get("stock-qvcloud", {})
        check(designed.get("current") == source.get("current"), f"matrix mismatch {capability_id}.current")
        check(designed.get("stock_support") == stock.get("support"), f"matrix mismatch {capability_id}.stock_support")
        check(designed.get("stock_status") == stock.get("status"), f"matrix mismatch {capability_id}.stock_status")
        check(designed.get("evidence_not_elevated") is True, f"evidence elevation flag missing for {capability_id}")
        exposure = designed.get("api_v1")
        check(exposure in ALLOWED_API_EXPOSURE, f"invalid api_v1 exposure for {capability_id}")
        if stock.get("status") == "unsupported":
            check(exposure == "unsupported_now", f"unsupported capability {capability_id} must remain unsupported_now")
        if stock.get("status") == "local_only" and capability_id not in {
            "core.lifecycle",
            "core.capability_negotiation",
        }:
            check(
                exposure in {"contract_demo_only", "not_exposed", "unsupported_now"},
                f"local_only capability {capability_id} cannot become runtime support",
            )
        if capability_id.startswith("media."):
            check(exposure == "unsupported_now", f"media capability {capability_id} must remain Unsupported")
        if exposure == "contract_runtime_gated":
            check(
                stock.get("status") in {"implemented_contract", "static_evidence_only"},
                f"runtime-gated capability {capability_id} has insufficient evidence basis",
            )
        for method_name in designed.get("methods", []):
            check(method_name in method_names, f"unknown method {method_name} in capability {capability_id}")

    dynamic_policy = contract.get("dynamic_evidence_source", {})
    check(dynamic_policy.get("used_to_elevate_provider_matrix") is False, "dynamic evidence must not silently elevate matrix")
    check(dynamic_inventory.get("result", {}).get("runtime_cloud_compatibility_claimed") is False, "dynamic source claims unexpected global compatibility")
    observation_ids = {entry.get("id") for entry in dynamic_inventory.get("observations", [])}
    check(
        dynamic_policy.get("app_upgrade_observation_id") in observation_ids,
        "app upgrade dynamic observation is absent",
    )
    upgrade_design = contract_by_id.get("app.update_check", {})
    dynamic_observation = upgrade_design.get("dynamic_observation", {})
    check(dynamic_observation.get("raises_evidence_level") is False, "app upgrade observation must not raise evidence")
    check(
        dynamic_observation.get("scope") == "request_and_http_200_xml_success_only",
        "app upgrade dynamic scope must remain limited",
    )

    sdk = contract.get("sdk", {})
    check(sdk.get("published_now") is False, "SDK publication must not be claimed")
    check(sdk.get("remote_repository_created") is False, "remote repository creation must not be claimed")
    check(sdk.get("explicit_component_binding") is True, "SDK must use explicit binding")
    check(sdk.get("coroutines") is True and sdk.get("flows") is True, "SDK must wrap coroutines and Flow")
    check(
        sdk.get("client_interface_consumer_implementation") is False,
        "WiboxClient must not be a consumer-implemented interface",
    )
    check(
        sdk.get("binary_compatibility_check_required") is True,
        "SDK binary compatibility checks are required",
    )

    test_ids = {entry.get("id") for entry in contract.get("future_tests", [])}
    check(test_ids == {f"T{index:02d}" for index in range(1, 13)}, "future test inventory T01-T12 is incomplete")
    non_goals = " ".join(contract.get("non_goals", []))
    for required_non_goal in ["HTTP", "opaque client IDs", "video audio P2P", "publishing the SDK"]:
        check(required_non_goal in non_goals, f"missing non-goal: {required_non_goal}")

    second_pass = contract.get("second_pass", {})
    check(second_pass.get("completed") is True, "second threat and compatibility pass is required")
    check(second_pass.get("kind") == "design_review_only_not_runtime_testing", "second pass must not imply runtime tests")

    for required_text in DOC_REQUIRED_TEXT:
        check(required_text in documentation, f"documentation missing {required_text}")
    combined = documentation + json.dumps(contract, ensure_ascii=False) + verifier_source
    check("\u2014" not in combined and "\u2013" not in combined, "em dash and en dash are forbidden")

    return errors


def run_negative_controls(
    contract: dict[str, Any],
    documentation: str,
    provider_matrix: dict[str, Any],
    dynamic_inventory: dict[str, Any],
    matrix_bytes: bytes,
    verifier_source: str,
) -> list[str]:
    failures: list[str] = []

    def mutate_contract(mutator: Any) -> dict[str, Any]:
        mutated = copy.deepcopy(contract)
        mutator(mutated)
        return mutated

    def package_only(value: dict[str, Any]) -> None:
        value["auth"]["factors"] = ["package_name"]
        value["auth"]["package_name_only_sufficient"] = True

    def signature_permission(value: dict[str, Any]) -> None:
        value["auth"]["custom_signature_permission_for_third_parties"] = True

    def elevate_device_list(value: dict[str, Any]) -> None:
        entry = next(item for item in value["capability_matrix"] if item["id"] == "devices.list")
        entry["stock_status"] = "implemented_contract"
        entry["api_v1"] = "contract_runtime_gated"

    def elevate_action(value: dict[str, Any]) -> None:
        entry = next(item for item in value["capability_matrix"] if item["id"] == "actions.unlock")
        entry["api_v1"] = "contract_runtime_gated"

    def add_secret(value: dict[str, Any]) -> None:
        value["models"]["DeviceSnapshot"]["fields"].append(
            {"name": "access_token", "type": "String", "required": False}
        )

    def remove_unknown_ack(value: dict[str, Any]) -> None:
        value["ack_levels"] = [
            entry for entry in value["ack_levels"] if entry["id"] != "UNKNOWN_OUTCOME"
        ]

    def hide_demo(value: dict[str, Any]) -> None:
        value["action_policy"]["demo_marks_simulation"] = False

    def implicit_binding(value: dict[str, Any]) -> None:
        value["service"]["binding"] = "implicit_service"

    def elevate_media(value: dict[str, Any]) -> None:
        entry = next(item for item in value["capability_matrix"] if item["id"] == "media.preview_single")
        entry["api_v1"] = "contract_runtime_gated"

    def disable_version_enforcement(value: dict[str, Any]) -> None:
        value["versioning"]["server_side_version_enforcement"] = False

    def enable_media_binder(value: dict[str, Any]) -> None:
        value["lifecycle"]["media_over_binder"] = True

    def replay_duplicate_action(value: dict[str, Any]) -> None:
        value["action_policy"]["duplicate_request_id"] = "execute_again"

    def lose_background_identity(value: dict[str, Any]) -> None:
        value["lifecycle"]["background_work_uses_captured_transaction_identity"] = False

    def change_parcelable_layout(value: dict[str, Any]) -> None:
        value["versioning"]["parcelable_layout_changes_within_major"] = True

    controls = [
        ("package_name_only", mutate_contract(package_only), documentation, "package_name_only"),
        ("custom_signature_permission", mutate_contract(signature_permission), documentation, "custom_signature"),
        ("elevated_devices_list", mutate_contract(elevate_device_list), documentation, "matrix mismatch devices.list"),
        ("elevated_action", mutate_contract(elevate_action), documentation, "unsupported capability actions.unlock"),
        ("secret_model_field", mutate_contract(add_secret), documentation, "forbidden model field"),
        ("missing_unknown_outcome", mutate_contract(remove_unknown_ack), documentation, "UNKNOWN_OUTCOME"),
        ("hidden_demo", mutate_contract(hide_demo), documentation, "demo_marks_simulation"),
        ("implicit_binding", mutate_contract(implicit_binding), documentation, "explicit_bound_service"),
        ("elevated_media", mutate_contract(elevate_media), documentation, "media capability"),
        ("disabled_version_enforcement", mutate_contract(disable_version_enforcement), documentation, "server_side_version_enforcement"),
        ("media_over_binder", mutate_contract(enable_media_binder), documentation, "media_over_binder"),
        ("duplicate_action_replay", mutate_contract(replay_duplicate_action), documentation, "duplicate_request_id"),
        ("lost_background_identity", mutate_contract(lose_background_identity), documentation, "background work must use captured transaction identity"),
        ("parcelable_layout_change", mutate_contract(change_parcelable_layout), documentation, "Parcelable layouts"),
        (
            "documentation_without_uid_check",
            copy.deepcopy(contract),
            documentation.replace("Binder.getCallingUid()", ""),
            "documentation missing Binder.getCallingUid()",
        ),
    ]

    for name, mutated_contract, mutated_docs, expected_error in controls:
        control_errors = validate_design(
            mutated_contract,
            mutated_docs,
            provider_matrix,
            dynamic_inventory,
            matrix_bytes,
            verifier_source,
        )
        if not control_errors:
            failures.append(f"negative control {name} was unexpectedly accepted")
        elif not any(expected_error in message for message in control_errors):
            failures.append(
                f"negative control {name} lacked expected error {expected_error}: {control_errors}"
            )
        else:
            print(f"NEGATIVE_CONTROL_REJECTED {name}")
    return failures


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--repo",
        type=Path,
        default=os.environ.get("WIBOX_REPO"),
        help="Wibox repository to read, or set WIBOX_REPO",
    )
    args = parser.parse_args()
    if not args.repo:
        parser.error("--repo or WIBOX_REPO is required")

    repo = Path(args.repo).resolve()
    base = Path(__file__).resolve().parents[2]
    contract_path = base / "analysis/external-api/api-contract.json"
    documentation_path = base / "docs/external-api.md"
    matrix_path = repo / "analysis/architecture/provider-matrix.json"
    dynamic_path = repo / "analysis/dynamic/inventory.json"

    try:
        contract = load_json(contract_path)
        documentation = documentation_path.read_text(encoding="utf-8")
        matrix_bytes = matrix_path.read_bytes()
        provider_matrix = json.loads(matrix_bytes)
        dynamic_inventory = load_json(dynamic_path)
        verifier_source = Path(__file__).read_text(encoding="utf-8")
    except (OSError, ValueError, json.JSONDecodeError) as error:
        print(f"INPUT_ERROR {error}", file=sys.stderr)
        return 2

    errors = validate_design(
        contract,
        documentation,
        provider_matrix,
        dynamic_inventory,
        matrix_bytes,
        verifier_source,
    )
    if errors:
        for error in errors:
            print(f"DESIGN_ERROR {error}", file=sys.stderr)
        return 1
    print("POSITIVE_CONTROL_OK docs contract provider-matrix dynamic-limits")

    negative_failures = run_negative_controls(
        contract,
        documentation,
        provider_matrix,
        dynamic_inventory,
        matrix_bytes,
        verifier_source,
    )
    if negative_failures:
        for error in negative_failures:
            print(f"NEGATIVE_CONTROL_ERROR {error}", file=sys.stderr)
        return 1

    print(SUCCESS_MARKER)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
