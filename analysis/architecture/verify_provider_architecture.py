#!/usr/bin/env python3
"""Verify provider architecture, UI coverage, and honest compatibility claims."""

from __future__ import annotations

import copy
import json
import re
import sys
from pathlib import Path

SUCCESS_TOKEN = "WIBOX_PROVIDER_ARCHITECTURE_VERIFIED"
PROVIDERS = ("stock-qvcloud", "mqtt", "self-hosted")
DIMENSIONS = ("api_wire", "behavior", "security", "hardware")
SUPPORT = {"required", "optional", "unsupported", "unknown"}
CURRENT = {
    "implemented_contract",
    "implemented_offline",
    "local_only",
    "static_evidence_only",
    "unsupported",
    "unknown",
    "design_required",
    "not_implemented",
}
REQUIRED_CAPABILITIES = {
    "core.lifecycle",
    "core.capability_negotiation",
    "auth.opaque_registration",
    "auth.user_login",
    "auth.oauth_password",
    "auth.oauth_refresh",
    "auth.session_restore",
    "auth.logout",
    "discovery.regional_services",
    "devices.list",
    "devices.dynamic_credentials",
    "devices.local_discovery",
    "devices.online_status",
    "pairing.qr",
    "pairing.ap_wifi",
    "pairing.audio",
    "pairing.bind",
    "invitations.accept",
    "invitations.manage",
    "invitations.issue",
    "invitations.permissions",
    "shadow.get",
    "shadow.multi",
    "shadow.subscribe",
    "shadow.unsubscribe",
    "events.alarm_parser",
    "events.push_registration",
    "events.realtime",
    "events.history",
    "events.photo",
    "events.delete",
    "events.filters",
    "media.preview_single",
    "media.preview_multi",
    "media.stream_controls",
    "media.listen",
    "media.talk",
    "media.snapshot",
    "media.record",
    "media.local_gallery",
    "media.local_share_save",
    "actions.unlock",
    "actions.f1",
    "actions.call_guard",
    "actions.call_accept",
    "actions.call_reject",
    "calls.lifecycle",
    "config.rename",
    "config.passwords",
    "config.unlock_security",
    "config.alarms",
    "config.wifi",
    "config.time_sync",
    "config.playback_intercom",
    "config.delete_device",
    "firmware.check",
    "firmware.apply",
    "app.update_check",
    "app.privacy_terms",
    "app.deep_link",
    "app.diagnostics",
    "app.sound_vibration",
    "security.persistence",
    "events.notifications",
}
POSITIVE_CONTROLS = (
    "source_inventory",
    "matrix_shape",
    "capability_coverage",
    "screen_coverage",
    "state_coverage",
    "navigation_coverage",
    "provider_rules",
    "migration_and_tests",
    "claim_guards",
)
NEGATIVE_CONTROLS = (
    "missing_provider",
    "missing_capability",
    "missing_screen_state",
    "missing_navigation_target",
    "false_100_percent_claim",
    "missing_migration_strategy",
    "future_provider_marked_implemented",
    "unverified_hardware_marked_verified",
    "legacy_screen_silently_activated",
    "invalid_evidence_reference",
)
PROVIDER_TOKENS = (
    "Diagnóstico de acoplamientos actuales",
    "Frontera provider-neutral",
    "Capacidades negociadas",
    "SessionHandle",
    "DeviceSnapshot",
    "ShadowSnapshot",
    "EventEnvelope",
    "PairingRequest",
    "ActionOutcome",
    "MediaSessionHandle",
    "InvitationPort",
    "ProviderError",
    "Discovery real sin filtrar endpoints ni DTOs",
    "stock-qvcloud",
    "mqtt",
    "self-hosted",
    "Lifecycle, threading, Flow, cancelación y persistencia",
    "callbackFlow",
    "awaitClose",
    "UNKNOWN_OUTCOME",
    "PHYSICAL_OBSERVED",
    "SAFE_READ_RETRY",
    "DO_NOT_RETRY",
    "RESYNC",
    "Compatibilidad por capacidad",
    "Migración incremental y compatibilidad",
    "Compatibilidad binaria y source",
    "Tests de contrato comunes",
    "Suites específicas",
    "Revisión final y APK",
    "Android Keystore",
    "single-flight",
)
UI_TOKENS = (
    "Arquitectura Compose propuesta",
    "NavHost",
    "ViewModels",
    "Inventario completo y destino modernizado",
    "Modelo de estados de UI",
    "loading",
    "empty",
    "error",
    "offline",
    "unsupported",
    "unknown",
    "permission",
    "action-pending",
    "ack",
    "failure",
    "Demo frente a conexión real",
    "Accesibilidad, tamaños, tema y navegación",
    "Tema claro y oscuro",
    "Pruebas que debe producir la implementación",
    "Capturas requeridas, todavía no producidas",
    "Puerta de revisión funcional y APK final",
    "32 superficies visuales",
    "33 pantallas",
)
FALSE_CLAIM = re.compile(
    r"100\s*%|100 por cien|compatibilidad total|totalmente compatible|"
    r"fully[ _]compatible|verified[ _]100|certified[ _]compatible",
    re.IGNORECASE,
)
NEGATIONS = (
    "no ",
    "nunca",
    "sin ",
    "falta",
    "pendiente",
    "not ",
    "not_",
    "requires",
    "requiere",
    "cannot",
    "can't",
    "unknown",
)


def validate(
    provider_text: str,
    ui_text: str,
    matrix: dict,
    source_inventory: dict,
) -> list[str]:
    errors: list[str] = []

    def check(condition: bool, message: str) -> None:
        if not condition:
            errors.append(message)

    screens = source_inventory.get("screens", [])
    source_ids = [screen.get("id") for screen in screens]
    check(len(screens) == 38, "source inventory must contain 38 screens")
    check(source_inventory.get("screen_count") == len(screens), "source screen_count mismatch")
    check(len(set(source_ids)) == len(source_ids), "source screen IDs must be unique")
    check(
        sum(screen.get("reachability") != "legacy_no_inbound" for screen in screens) == 33,
        "source inventory must contain 33 non-legacy screens",
    )
    check(
        sum(screen.get("reachability") == "legacy_no_inbound" for screen in screens) == 5,
        "source inventory must contain five legacy screens",
    )
    check(
        len(source_inventory.get("navigation_edges", [])) == 71,
        "source inventory must contain 71 navigation edges",
    )

    check(set(matrix.get("providers", {})) == set(PROVIDERS), "providers must be exactly stock-qvcloud, mqtt, self-hosted")
    for provider_id in ("mqtt", "self-hosted"):
        provider = matrix.get("providers", {}).get(provider_id, {})
        check(
            provider.get("phase") == "future_not_implemented",
            f"future provider {provider_id} must remain not implemented",
        )
        check(
            provider.get("implemented_as_complete_provider") is False,
            f"future provider {provider_id} cannot claim complete implementation",
        )

    capabilities = matrix.get("capabilities", [])
    capability_ids = [capability.get("id") for capability in capabilities]
    check(len(capabilities) == 64, "matrix must contain 64 capabilities")
    check(len(set(capability_ids)) == len(capability_ids), "capability IDs must be unique")
    check(set(capability_ids) == REQUIRED_CAPABILITIES, "capability coverage does not match the required inventory")
    check(
        tuple(matrix.get("compatibility_dimensions", [])) == DIMENSIONS,
        "matrix must separate api_wire, behavior, security and hardware",
    )

    for capability in capabilities:
        capability_id = capability.get("id", "<missing>")
        evidence = capability.get("evidence")
        check(isinstance(evidence, list) and bool(evidence), f"capability {capability_id} lacks evidence")
        check(
            all(isinstance(item, str) and item.strip() for item in evidence or []),
            f"capability {capability_id} has invalid evidence",
        )
        current = capability.get("current")
        check(current in CURRENT, f"capability {capability_id} has invalid current state {current}")
        provider_states = capability.get("providers", {})
        check(
            set(provider_states) == set(PROVIDERS),
            f"capability {capability_id} does not cover all providers",
        )
        for provider_id in PROVIDERS:
            state = provider_states.get(provider_id, {})
            check(
                state.get("support") in SUPPORT,
                f"capability {capability_id} has invalid support for {provider_id}",
            )
            check(
                state.get("status") in CURRENT,
                f"capability {capability_id} has invalid status for {provider_id}",
            )
        check(
            provider_states.get("stock-qvcloud", {}).get("status") == current,
            f"capability {capability_id} current state differs from stock status",
        )
        for provider_id in ("mqtt", "self-hosted"):
            check(
                provider_states.get(provider_id, {}).get("status") == "not_implemented",
                f"future provider {provider_id} is marked implemented for {capability_id}",
            )
        acceptance = capability.get("acceptance", {})
        compatibility_now = capability.get("compatibility_now", {})
        check(
            set(acceptance) == set(DIMENSIONS),
            f"capability {capability_id} lacks acceptance dimensions",
        )
        check(
            set(compatibility_now) == set(DIMENSIONS),
            f"capability {capability_id} lacks current compatibility dimensions",
        )
        for dimension in DIMENSIONS:
            check(
                isinstance(acceptance.get(dimension), str) and bool(acceptance.get(dimension, "").strip()),
                f"capability {capability_id} lacks {dimension} acceptance criterion",
            )
            value = compatibility_now.get(dimension)
            check(
                isinstance(value, str) and bool(value.strip()),
                f"capability {capability_id} lacks current {dimension} state",
            )
            check(
                value not in {"verified", "runtime_verified", "hardware_verified", "tested", "passed"},
                f"capability {capability_id} contains unverified {dimension} success claim",
            )
        check(
            f"`{capability_id}`" in provider_text,
            f"provider document omits capability {capability_id}",
        )

    ui_screens = matrix.get("ui_screens", [])
    matrix_screen_ids = [screen.get("id") for screen in ui_screens]
    check(len(ui_screens) == 38, "matrix must contain 38 UI screens")
    check(set(matrix_screen_ids) == set(source_ids), "matrix UI screens do not match source inventory")
    source_by_id = {screen.get("id"): screen for screen in screens}
    for screen in ui_screens:
        screen_id = screen.get("id", "<missing>")
        source = source_by_id.get(screen_id, {})
        check(
            screen.get("reachability") == source.get("reachability"),
            f"screen {screen_id} has changed reachability without evidence",
        )
        check(bool(screen.get("destination")), f"screen {screen_id} lacks modernization destination")
        check(
            isinstance(screen.get("required_evidence"), str) and bool(screen.get("required_evidence", "").strip()),
            f"screen {screen_id} lacks required evidence",
        )
        if source.get("reachability") == "legacy_no_inbound":
            check(
                screen.get("destination") == "legacy_documented",
                f"legacy screen {screen_id} was silently activated",
            )
        else:
            check(
                screen.get("destination") != "legacy_documented",
                f"non-legacy screen {screen_id} was dropped as legacy",
            )

    totals = matrix.get("totals", {})
    check(totals.get("capabilities") == len(capabilities), "matrix capability total mismatch")
    check(totals.get("screens") == len(ui_screens), "matrix screen total mismatch")
    check(
        totals.get("non_legacy_screens") == sum(
            screen.get("reachability") != "legacy_no_inbound" for screen in ui_screens
        ),
        "matrix non-legacy total mismatch",
    )
    check(
        totals.get("legacy_screens") == sum(
            screen.get("reachability") == "legacy_no_inbound" for screen in ui_screens
        ),
        "matrix legacy total mismatch",
    )

    inventory_section = ui_text.split("## 4. Inventario completo y destino modernizado", 1)
    check(len(inventory_section) == 2, "UI inventory section is missing")
    rows: dict[str, str] = {}
    if len(inventory_section) == 2:
        section = inventory_section[1].split("## 5. Modelo de estados de UI", 1)[0]
        for line in section.splitlines():
            match = re.match(r"^\| `([^`]+)`", line)
            if match and match.group(1) in source_ids:
                screen_id = match.group(1)
                check(screen_id not in rows, f"screen {screen_id} has duplicate UI inventory rows")
                rows[screen_id] = line
    check(set(rows) == set(source_ids), "UI document does not cover every source screen")
    for screen in screens:
        screen_id = screen.get("id")
        row = rows.get(screen_id, "")
        for state in screen.get("states", []):
            state_id = state.get("id")
            check(
                f"`{state_id}`" in row,
                f"screen {screen_id} omits state {state_id}",
            )
        for target in screen.get("navigation", []):
            check(
                f"`{target}`" in row,
                f"screen {screen_id} omits navigation target {target}",
            )

    for token in PROVIDER_TOKENS:
        check(token in provider_text, f"provider architecture or migration strategy lacks: {token}")
    for token in UI_TOKENS:
        check(token in ui_text, f"UI modernization or test strategy lacks: {token}")

    for document_name, text in (
        ("provider-architecture.md", provider_text),
        ("ui-modernization.md", ui_text),
        ("provider-matrix.json", json.dumps(matrix, ensure_ascii=False, sort_keys=True)),
    ):
        check("\u2014" not in text and "\u2013" not in text, f"{document_name} contains a forbidden dash")
        for line in text.splitlines():
            if FALSE_CLAIM.search(line):
                lowered = line.lower()
                check(
                    any(negation in lowered for negation in NEGATIONS),
                    f"{document_name} contains an unsupported absolute compatibility claim",
                )
    return errors


def validate_evidence_references(
    matrix: dict,
    root: Path,
    apk_inventory: dict,
    protocol_text: str,
) -> list[str]:
    errors: list[str] = []
    registered = {
        item.get("id")
        for item in apk_inventory.get("evidence_registry", [])
        if isinstance(item.get("id"), str)
    }
    for capability in matrix.get("capabilities", []):
        for reference in capability.get("evidence", []):
            if re.fullmatch(r"E\d{3}", reference):
                if reference not in registered:
                    errors.append(f"capability {capability['id']} has unknown evidence {reference}")
            elif reference.startswith(("P-", "U-")):
                if reference not in protocol_text:
                    errors.append(f"capability {capability['id']} has unknown protocol evidence {reference}")
            elif "/" in reference:
                path = reference.split("#", 1)[0]
                if not (root / path).is_file():
                    errors.append(f"capability {capability['id']} has missing evidence file {path}")
            else:
                errors.append(f"capability {capability['id']} has unrecognized evidence {reference}")
    return errors


def negative_controls(
    provider_text: str,
    ui_text: str,
    matrix: dict,
    source_inventory: dict,
) -> list[str]:
    errors: list[str] = []

    def expect_failure(name: str, mutated_matrix: dict, mutated_provider: str, mutated_ui: str, needle: str) -> None:
        result = validate(mutated_provider, mutated_ui, mutated_matrix, source_inventory)
        if not result:
            errors.append(f"negative control {name} did not fail")
        elif not any(needle in error for error in result):
            errors.append(f"negative control {name} failed for an unexpected reason: {result[0]}")

    mutated = copy.deepcopy(matrix)
    del mutated["providers"]["mqtt"]
    expect_failure("missing_provider", mutated, provider_text, ui_text, "providers")

    mutated = copy.deepcopy(matrix)
    mutated["capabilities"].pop()
    expect_failure("missing_capability", mutated, provider_text, ui_text, "capability")

    mutated_ui = ui_text.replace("`startup_wait`", "`removed_state`", 1)
    expect_failure("missing_screen_state", matrix, provider_text, mutated_ui, "startup_wait")

    mutated_ui = ui_text.replace(
        "Navega a `main_tab`, `push_call`, `preview`.",
        "Navega a `main_tab`, `preview`.",
        1,
    )
    expect_failure("missing_navigation_target", matrix, provider_text, mutated_ui, "navigation target")

    mutated_provider = provider_text + "\nCompatibilidad 100% certificada.\n"
    expect_failure("false_100_percent_claim", matrix, mutated_provider, ui_text, "absolute compatibility claim")

    mutated_provider = provider_text.replace(
        "## 11. Migración incremental y compatibilidad",
        "## 11. Sección retirada",
        1,
    )
    expect_failure("missing_migration_strategy", matrix, mutated_provider, ui_text, "migration strategy")

    mutated = copy.deepcopy(matrix)
    mutated["capabilities"][0]["providers"]["mqtt"]["status"] = "implemented_contract"
    expect_failure("future_provider_marked_implemented", mutated, provider_text, ui_text, "future provider")

    mutated = copy.deepcopy(matrix)
    unlock = next(capability for capability in mutated["capabilities"] if capability["id"] == "actions.unlock")
    unlock["compatibility_now"]["hardware"] = "verified"
    expect_failure("unverified_hardware_marked_verified", mutated, provider_text, ui_text, "unverified hardware")

    mutated = copy.deepcopy(matrix)
    legacy = next(screen for screen in mutated["ui_screens"] if screen["id"] == "product_choose")
    legacy["destination"] = "modernized"
    expect_failure("legacy_screen_silently_activated", mutated, provider_text, ui_text, "silently activated")
    return errors


def negative_evidence_control(
    matrix: dict,
    root: Path,
    apk_inventory: dict,
    protocol_text: str,
) -> list[str]:
    mutated = copy.deepcopy(matrix)
    mutated["capabilities"][0]["evidence"] = ["missing/evidence.kt"]
    errors = validate_evidence_references(mutated, root, apk_inventory, protocol_text)
    if not errors:
        return ["negative control invalid_evidence_reference did not fail"]
    if not any("missing evidence file" in error for error in errors):
        return [f"negative control invalid_evidence_reference failed unexpectedly: {errors[0]}"]
    return []


def main() -> int:
    root = Path(__file__).resolve().parents[2]
    try:
        provider_text = (root / "docs/provider-architecture.md").read_text(encoding="utf-8")
        ui_text = (root / "docs/ui-modernization.md").read_text(encoding="utf-8")
        matrix = json.loads((root / "analysis/architecture/provider-matrix.json").read_text(encoding="utf-8"))
        source_inventory = json.loads((root / "analysis/apk/ui-inventory.json").read_text(encoding="utf-8"))
        apk_inventory = json.loads((root / "analysis/apk/inventory.json").read_text(encoding="utf-8"))
        protocol_text = (root / "docs/protocol.md").read_text(encoding="utf-8")
    except (OSError, json.JSONDecodeError) as error:
        print(f"verification input error: {error}", file=sys.stderr)
        return 1

    errors = validate(provider_text, ui_text, matrix, source_inventory)
    if not errors:
        errors.extend(validate_evidence_references(matrix, root, apk_inventory, protocol_text))
    if not errors:
        errors.extend(negative_controls(provider_text, ui_text, matrix, source_inventory))
    if not errors:
        errors.extend(negative_evidence_control(matrix, root, apk_inventory, protocol_text))
    if errors:
        for error in errors:
            print(f"FAIL: {error}", file=sys.stderr)
        return 1

    if "--report" in sys.argv[1:]:
        report = {
            "capabilities": len(matrix["capabilities"]),
            "providers": sorted(matrix["providers"]),
            "screens": len(matrix["ui_screens"]),
            "non_legacy_screens": sum(
                screen["reachability"] != "legacy_no_inbound" for screen in matrix["ui_screens"]
            ),
            "legacy_screens": sum(
                screen["reachability"] == "legacy_no_inbound" for screen in matrix["ui_screens"]
            ),
            "source_states": sum(len(screen["states"]) for screen in source_inventory["screens"]),
            "source_navigation_edges": len(source_inventory["navigation_edges"]),
            "positive_controls": len(POSITIVE_CONTROLS),
            "negative_controls": len(NEGATIVE_CONTROLS),
        }
        print(json.dumps(report, sort_keys=True))
    print(SUCCESS_TOKEN)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
