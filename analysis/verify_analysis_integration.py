#!/usr/bin/env python3
from copy import deepcopy
import json
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]


def load(path: str):
    return json.loads((ROOT / path).read_text(encoding="utf-8"))


def verify(rea, apk, ui, contract) -> list[str]:
    errors: list[str] = []

    def require(condition: bool, message: str) -> None:
        if not condition:
            errors.append(message)

    require(rea["reference_assessment"]["is_wibox_client"] is False, "REA must not be classified as a Wibox client")
    require(rea["reference_assessment"]["is_fermax_protocol_reference"] is False, "REA must not be classified as a Fermax protocol reference")
    require(rea["wibox_cloud"]["reference_status"] == "absent-in-this-commit", "Unexpected Wibox protocol claim in REA")
    require(rea["coverage"]["real_services_contacted"] is False, "REA analysis contacted real services")
    execution = rea.get("rea_execution", {})
    require(execution.get("status") == "executed-static-analysis", "REA was inspected but not executed against Wibox")
    require(execution.get("target_apk_sha256") == "08d78731fe79481b60b4250e736f653cd7af91932844a612b0b5c5a989aac7ce", "REA execution used a different APK")
    require(execution.get("real_services_contacted") is False, "REA execution contacted real services")
    require(execution.get("devices_used") is False, "REA execution used a device")
    for artifact in (
        "wibox-inspect-android-package.json",
        "wibox-method-oauth-manager-getToken.json",
        "wibox-method-shadow-manager-getDeviceShadowInfo.json",
        "wibox-method-location-startQueryTargetService-1.json",
    ):
        require((ROOT / "analysis/rea" / artifact).is_file(), f"Missing REA execution evidence: {artifact}")
    require(apk["scope"]["cloud_contacted_by_analysis"] is False, "APK analysis contacted the cloud")
    require(apk["scope"]["dynamic_behavior_claimed"] is False, "Static APK evidence was promoted to runtime behavior")
    require(ui["captures"]["real_runtime_screenshots"] is False, "Resource images were promoted to runtime captures")

    expected_hash = "08d78731fe79481b60b4250e736f653cd7af91932844a612b0b5c5a989aac7ce"
    require(apk["apk"]["sha256"] == expected_hash, "APK hash differs from the verified source")
    require(ui["source"]["sha256"] == expected_hash, "UI and APK analyses use different sources")
    require(apk["apk"]["package"] == ui["source"]["package"] == "com.fermax.wibox", "Unexpected source package")
    require(contract["source_of_truth"]["rea_role"] == "reverse_engineering_tool_only", "REA role drifted from analysis")
    require(contract["source_of_truth"]["runtime_stock_behavior_confirmed"] is False, "Unverified runtime stock behavior entered the contract")

    oauth = apk["cloud_areas"]["oauth"]
    contract_oauth = contract["stock_cloud"]["oauth"]
    for key in ("path", "method", "token_header"):
        require(contract_oauth[key] == oauth[key], f"OAuth {key} contradicts APK evidence")
    require(contract_oauth["grant_types"] == oauth["grant_types"], "OAuth grant types contradict APK evidence")
    require(contract["stock_cloud"]["shadow_http_paths"] == apk["cloud_areas"]["shadow_http"]["paths"], "Shadow HTTP paths contradict APK evidence")

    mqtt_status = apk["protocols"]["mqtt"]["status"]
    contract_mqtt = contract["stock_cloud"]["mqtt"]
    require(contract_mqtt["status"] == mqtt_status, "MQTT status contradicts APK evidence")
    require(mqtt_status == "implemented_but_disabled_by_default", "Unexpected MQTT evidence status")
    require(contract_mqtt["default_enabled"] is False, "MQTT must remain disabled by default")
    require(contract_mqtt["runtime_active_claimed"] is False, "MQTT runtime activity is not proven")

    media = contract["stock_cloud"]["media"]
    require(media["transport"] == apk["protocols"]["p2p"]["status"], "Media transport contradicts APK evidence")
    require(media["native_p2p_required_by_stock"] is True, "Stock native P2P dependency was hidden")
    require(media["sip_client_available"] is False and apk["protocols"]["sip"]["status"] == "no_call_client_found", "SIP client claim lacks evidence")
    require(media["webrtc_peerconnection_available"] is False and apk["protocols"]["webrtc"]["status"] == "no_peerconnection_found", "WebRTC PeerConnection claim lacks evidence")
    require(contract["stock_cloud"]["service_discovery"] == "dynamic_unresolved", "Dynamic service discovery was presented as resolved")
    require(contract["stock_cloud"]["visible_username_password_login"] is False, "A visible login flow was invented")
    require(apk["authentication"]["user_visible_login_screen"] is False, "APK login assessment changed unexpectedly")

    rules = contract["implementation_rules"]
    for key in ("strict_tls_validation", "redact_secrets_from_logs", "require_evidence_for_new_wire_operations", "supported_features_must_be_explicit"):
        require(rules[key] is True, f"Required safety rule disabled: {key}")
    for key in ("embed_original_certificates_or_credentials", "reuse_original_native_binaries", "retry_ambiguous_physical_actions", "report_action_success_without_acknowledgement"):
        require(rules[key] is False, f"Forbidden implementation behavior enabled: {key}")

    alternatives = contract["alternative_firmware_profiles"]
    require(alternatives["separate_from_stock"] is True, "Alternative firmware protocols were mixed with stock")
    require(alternatives["enabled_by_default"] is False, "Alternative firmware profiles must be opt-in")
    require(set(alternatives["duhow_transports"]) == {"local-http-cgi", "mqtt"}, "Unexpected duhow transport mapping")
    require(set(alternatives["cmos486_transports"]) == {"webrtc", "rtsp", "mqtt"}, "Unexpected cmos486 transport mapping")
    return errors


def self_test(rea, apk, ui, contract) -> None:
    baseline = verify(rea, apk, ui, contract)
    if baseline:
        raise AssertionError(f"Positive integration control failed: {baseline}")
    mqtt_enabled = deepcopy(contract)
    mqtt_enabled["stock_cloud"]["mqtt"]["default_enabled"] = True
    if not any("MQTT" in error for error in verify(rea, apk, ui, mqtt_enabled)):
        raise AssertionError("Negative MQTT control failed")
    mixed_profiles = deepcopy(contract)
    mixed_profiles["alternative_firmware_profiles"]["separate_from_stock"] = False
    if not any("mixed" in error for error in verify(rea, apk, ui, mixed_profiles)):
        raise AssertionError("Negative firmware separation control failed")
    insecure = deepcopy(contract)
    insecure["implementation_rules"]["strict_tls_validation"] = False
    if not any("strict_tls_validation" in error for error in verify(rea, apk, ui, insecure)):
        raise AssertionError("Negative TLS control failed")
    missing_execution = deepcopy(rea)
    missing_execution.pop("rea_execution", None)
    if not any("not executed" in error for error in verify(missing_execution, apk, ui, contract)):
        raise AssertionError("Negative REA execution control failed")


def main() -> int:
    rea = load("analysis/rea/inventory.json")
    apk = load("analysis/apk/inventory.json")
    ui = load("analysis/apk/ui-inventory.json")
    contract = load("analysis/integration-contract.json")
    self_test(rea, apk, ui, contract)
    errors = verify(rea, apk, ui, contract)
    if errors:
        for error in errors:
            print(f"ERROR: {error}")
        return 1
    print("WIBOX_ANALYSIS_INTEGRATION_VERIFIED")
    return 0


if __name__ == "__main__":
    sys.exit(main())
