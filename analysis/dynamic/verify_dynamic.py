#!/usr/bin/env python3
import copy
import hashlib
import json
import re
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
INVENTORY_PATH = ROOT / "analysis/dynamic/inventory.json"
REPORT_PATH = ROOT / "docs/dynamic-analysis.md"
STATIC_INVENTORY_PATH = ROOT / "analysis/apk/inventory.json"
CODE_EVIDENCE_PATH = ROOT / "analysis/apk/evidence/code-evidence.md"
APK_REPORT_PATH = ROOT / "docs/apk-analysis.md"
REA_REPORT_PATH = ROOT / "docs/rea-analysis.md"
RAW_ROOT = Path.home() / "temp/opencode-work/wibox-dynamic-raw"
EXPECTED_APK_SHA256 = "08d78731fe79481b60b4250e736f653cd7af91932844a612b0b5c5a989aac7ce"
EXPECTED_ATTEMPT_1_ERROR = "Avd's CPU Architecture 'arm64' is not supported by the QEMU2 emulator on x86_64 host. System image must match the host architecture."
EXPECTED_UPGRADE_PATH = "/webs/version/app/version_check.jsp"
SUCCESS = "WIBOX_DYNAMIC_ANALYSIS_VERIFIED"


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def require(condition, message, errors):
    if not condition:
        errors.append(message)


def validate_request(request, prefix="network.requests[0]"):
    errors = []
    for key in ("id", "sequence", "method", "url", "query_keys", "headers", "body_schema", "response", "evidence", "redacted_fields"):
        require(key in request, f"{prefix}: falta {key}", errors)
    if errors:
        return errors
    require(isinstance(request["sequence"], int) and request["sequence"] > 0, f"{prefix}: secuencia inválida", errors)
    require(request["method"] in {"GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS"}, f"{prefix}: método inválido", errors)
    url = request["url"]
    require(isinstance(url, dict), f"{prefix}: URL inválida", errors)
    if isinstance(url, dict):
        for key in ("scheme", "host", "path"):
            require(isinstance(url.get(key), str) and bool(url[key]), f"{prefix}: falta URL {key}", errors)
        require(url.get("scheme") in {"http", "https"}, f"{prefix}: esquema inválido", errors)
        require(str(url.get("path", "")).startswith("/"), f"{prefix}: path inválido", errors)
    require(isinstance(request["query_keys"], list) and all(isinstance(k, str) for k in request["query_keys"]), f"{prefix}: query keys inválidas", errors)
    require(isinstance(request["headers"], list), f"{prefix}: headers inválidos", errors)
    for header in request["headers"] if isinstance(request["headers"], list) else []:
        require(isinstance(header, dict) and isinstance(header.get("name"), str) and isinstance(header.get("value"), str), f"{prefix}: header inválido", errors)
    response = request["response"]
    require(isinstance(response, dict) and isinstance(response.get("observed"), bool), f"{prefix}: response.observed ausente", errors)
    if isinstance(response, dict) and isinstance(response.get("observed"), bool):
        require("headers" in response and isinstance(response["headers"], list), f"{prefix}: headers response ausentes", errors)
        require("body_schema" in response, f"{prefix}: esquema response ausente", errors)
        if response["observed"]:
            require(isinstance(response.get("status"), int) and 100 <= response["status"] <= 599, f"{prefix}: código HTTP inválido", errors)
        else:
            require(response.get("status") is None, f"{prefix}: response no observada con código inventado", errors)
            require(isinstance(response.get("transport_error"), str) and bool(response["transport_error"]), f"{prefix}: response ausente sin error explícito", errors)
    require(isinstance(request["redacted_fields"], list), f"{prefix}: redacted_fields inválido", errors)
    return errors


def validate_evidence_files(observations, errors):
    for observation in observations:
        evidence = observation.get("evidence", {})
        for key in ("raw_path", "logcat_path", "console_path", "sockets_path", "mitm_path", "raw_manifest", "ui_path", "install_path", "final_path"):
            value = evidence.get(key)
            if not value:
                continue
            path = Path(value)
            require(path.is_absolute(), f"{observation.get('id')}: ruta raw no absoluta", errors)
            require(path.is_relative_to(RAW_ROOT), f"{observation.get('id')}: evidencia fuera de raw", errors)
            require(not path.is_relative_to(ROOT), f"{observation.get('id')}: raw dentro del repo", errors)
            require(path.exists(), f"{observation.get('id')}: evidencia ausente {path.name}", errors)
        for key in ("raw_sha256", "logcat_sha256", "console_sha256", "sockets_sha256", "mitm_sha256", "raw_manifest_sha256", "ui_sha256", "install_sha256", "final_sha256"):
            expected = evidence.get(key)
            if not expected:
                continue
            path_key = key.removesuffix("_sha256")
            if path_key == "raw":
                path_key = "raw_path"
            elif path_key == "raw_manifest":
                path_key = "raw_manifest"
            else:
                path_key += "_path"
            path = Path(evidence[path_key])
            require(path.is_file() and digest(path) == expected, f"{observation.get('id')}: hash incorrecto en {key}", errors)


def validate(inventory, report, external=False):
    errors = []
    for key in ("status", "result", "apk", "attempts", "isolation", "tools", "commands", "sequence", "observations", "network", "static_correspondence", "discrepancies", "partial_runtime_confirmations", "unsupported_updates", "unresolved_operations", "blockers", "raw_locations", "cleanup", "security_review"):
        require(key in inventory, f"inventory: falta {key}", errors)
    if errors:
        return errors

    require(inventory["leaf"] == "leaf-1.2.5", "leaf incorrecta", errors)
    require(inventory.get("leaves") == ["leaf-1.2.3", "leaf-1.2.5"], "hojas históricas incorrectas", errors)
    require(inventory["schema_version"] == 3, "schema_version incorrecta", errors)
    require(inventory["status"] == "partially_captured", "estado global incorrecto", errors)
    attempts = inventory["attempts"]
    require(isinstance(attempts, list) and len(attempts) == 3, "deben conservarse tres intentos", errors)
    if len(attempts) != 3:
        return errors
    first, second, third = attempts
    require(first["id"] == "attempt-1" and first["status"] == "blocked", "intento 1 alterado", errors)
    require(second["id"] == "attempt-2" and second["status"] == "partial_capture", "intento 2 incorrecto", errors)
    require(first["avd"]["name"] == "wibox-stock-capture" and first["avd"]["deleted"] is True, "AVD intento 1 incorrecto", errors)
    require(first["avd"]["android_started"] is False and first["avd"]["apk_installed"] is False, "intento 1 con ejecución inventada", errors)
    require(first["error"] == EXPECTED_ATTEMPT_1_ERROR and EXPECTED_ATTEMPT_1_ERROR in report, "error intento 1 ausente", errors)
    require(second["avd"]["name"] == "wibox-stock-arm" and second["avd"]["deleted"] is True, "AVD intento 2 incorrecto", errors)
    require(second["avd"]["selected_image"] == "system-images;android-30;google_apis;x86_64", "imagen Android 30 incorrecta", errors)
    require(second["avd"]["selected_image_revision"] == 16, "revisión de imagen incorrecta", errors)
    require(second["avd"]["requested_serial"] == "emulator-5558", "serial explícito incorrecto", errors)
    require(second["avd"]["android_started"] is True and second["avd"]["apk_installed"] is True, "arranque o instalación no demostrados", errors)
    abi = second["android_abi_verification"]
    require(abi.get("ro_product_cpu_abilist") == "x86_64,x86,arm64-v8a,armeabi-v7a,armeabi", "ABI ARM incompleta", errors)
    require(abi.get("ro_product_cpu_abilist64") == "x86_64,arm64-v8a", "ABI64 ARM ausente", errors)
    require(abi.get("ro_dalvik_vm_isa_arm64") == "x86_64", "traducción ARM64 no demostrada", errors)
    require("Initialized NDK translation (aarch64)" in abi.get("native_translation_log", ""), "log de traducción ausente", errors)
    require(second["official_translation"]["manual_translator_installed"] is False and second["official_translation"]["root_used"] is False, "traducción no autorizada", errors)
    require(second["installation"]["command_result"] == "Success", "instalación no confirmada", errors)
    require(second["startup"]["launch_state"] == "COLD" and second["startup"]["main_activity_reached"].endswith("MainTabActivity"), "arranque MainTab no confirmado", errors)
    require(second["startup"]["linked_devices"] == 0, "dispositivo vinculado registrado", errors)
    require(third["id"] == "attempt-3" and third["status"] == "qr_registration_not_reached_upgrade_captured", "intento 3 incorrecto", errors)
    require(third["avd"]["name"] == "wibox-stock-arm" and third["avd"]["deleted"] is True, "AVD intento 3 incorrecto", errors)
    require(third["avd"]["selected_image"] == "system-images;android-30;google_apis;x86_64", "imagen intento 3 incorrecta", errors)
    qr = third["fictitious_qr"]
    require(qr.get("generated_locally") is True and qr.get("reserved_nonexistent_uid") is True, "QR ficticio no demostrado", errors)
    require(qr.get("real_qr_scanned") is False and qr.get("real_camera_used") is False and qr.get("online_lan_scan_used") is False, "QR real o LAN utilizado", errors)
    require(qr.get("save_pressed_once") is True and qr.get("final_bind_confirmation_pressed") is False, "bind final confirmado", errors)
    require(qr.get("values_sanitized", {}).get("uid") == "<fictitious-uid>" and qr.get("values_sanitized", {}).get("authCode") == "<fictitious-auth-code>", "valores QR sin placeholder", errors)
    registration = third["registration"]
    require(registration.get("query_device_online_started") is True, "consulta online intento 3 no demostrada", errors)
    for key in ("user_free_register_observed", "user_login_observed", "session_generated", "oauth_tokens_generated", "bind_succeeded", "server_rejection_observed"):
        require(registration.get(key) is False, f"registro attempt3 incoherente: {key}", errors)
    require(registration.get("app_force_stopped_to_end_queries") is True, "consultas intento 3 no detenidas", errors)
    upgrade = third["upgrade"]
    require(upgrade.get("request_observed") is True and upgrade.get("response_observed") is True and upgrade.get("http_status") == 200, "response attempt3 ausente", errors)
    require(upgrade.get("set_cookie_present") is True and upgrade.get("set_cookie_value") == "<redacted>", "cookie attempt3 no redactada", errors)
    require(upgrade.get("response_xml_schema", {}).get("envelope.header.result") == 0, "result XML attempt3 incorrecto", errors)
    require(upgrade.get("response_xml_schema", {}).get("envelope.content.version") == "5.1.11", "version XML attempt3 incorrecta", errors)

    result = inventory["result"]
    expected_result = {
        "apk_installed": True,
        "app_started": True,
        "session_generated": False,
        "opaque_registration_observed": False,
        "login_observed": False,
        "discovery_observed": True,
        "discovery_decoded": False,
        "stock_http_observed": True,
        "stock_http_response_observed": True,
        "app_upgrade_response_observed": True,
        "stock_udp_observed": False,
        "oauth_observed": False,
        "runtime_cloud_compatibility_claimed": False,
    }
    for key, expected in expected_result.items():
        require(result.get(key) is expected, f"result.{key} incoherente", errors)

    apk = inventory["apk"]
    require(apk.get("sha256") == EXPECTED_APK_SHA256, "hash APK no coincide", errors)
    require(apk.get("package") == "com.fermax.wibox" and apk.get("version_code") == 18, "identidad APK incorrecta", errors)
    require(apk.get("size_bytes") == 45816991, "tamaño APK incorrecto", errors)
    require(apk.get("native_abis") == ["arm64-v8a", "armeabi-v7a"], "ABI APK incorrecta", errors)
    require(apk.get("native_library_counts", {}).get("x86_64") == 0, "x86_64 debe estar ausente", errors)
    require(EXPECTED_APK_SHA256 in report, "informe sin hash APK", errors)

    isolation = inventory["isolation"]
    for key in ("physical_device_used", "forbidden_physical_serial_used", "existing_wibox_test_used_for_execution", "real_qr_or_camera_used", "final_bind_confirmation_pressed", "real_user_accounts_used", "real_secrets_entered", "duhow_or_cmos486_contacted", "laboratory_endpoints_contacted", "apk_tls_modified", "client_kotlin_tls_modified", "mitm_upstream_verification_disabled_attempt2", "user_ca_installed", "frida_or_objection_used", "root_used", "manual_native_translation_installed"):
        require(isolation.get(key) is False, f"aislamiento incumplido: {key}", errors)
    require(isolation.get("linked_doorbells") == 0 and isolation.get("physical_actions_attempted") == 0, "dispositivo o acción física registrada", errors)
    require(isolation.get("official_process_translation_used") is True and isolation.get("mitm_started") is True, "método dinámico incompleto", errors)
    require(isolation.get("fictitious_qr_used") is True and isolation.get("mitm_ssl_insecure_used_only_in_attempt3_proxy") is True, "excepción attempt3 no declarada", errors)
    for command in inventory["commands"]:
        text = command.get("command", "")
        if "adb " in text:
            require("adb -s emulator-5558" in text, "adb sin serial explícito", errors)

    sequence = inventory["sequence"]
    require(isinstance(sequence, list) and len(sequence) >= 20, "secuencia incompleta", errors)
    orders = [item.get("order") for item in sequence]
    require(orders == sorted(set(orders)), "secuencia desordenada o duplicada", errors)
    require({item.get("attempt") for item in sequence} == {1, 2, 3}, "secuencia no conserva los tres intentos", errors)
    observation_ids = {item.get("id") for item in inventory["observations"]}
    for item in sequence:
        require(item.get("evidence") in observation_ids, f"secuencia sin evidencia: {item.get('stage')}", errors)
    for required_id in ("DYN-BLOCK-001", "CLEANUP-001", "DYN2-ABI-001", "DYN2-START-001", "DYN2-HTTP-UPGRADE-001", "DYN2-DISCOVERY-001", "DYN2-SESSION-001", "DYN2-CAPTURE-LIMIT-001", "CLEANUP-002", "DYN3-QR-001", "DYN3-START-001", "DYN3-REGISTER-001", "DYN3-HTTP-UPGRADE-001", "DYN3-DISCOVERY-001", "CLEANUP-003"):
        require(required_id in observation_ids, f"observación ausente: {required_id}", errors)
    if external:
        validate_evidence_files(inventory["observations"], errors)

    network = inventory["network"]
    require(network.get("status") == "partially_observed" and network.get("capture_started") is True, "estado de captura incorrecto", errors)
    require("wlan0" in network.get("pcap_scope", ""), "límite PCAP no declarado", errors)
    requests = network.get("requests", [])
    require(isinstance(requests, list) and len(requests) == 2, "deben existir las dos requests observadas", errors)
    for index, request in enumerate(requests):
        errors.extend(validate_request(request, f"network.requests[{index}]"))
    if len(requests) == 2:
        first_request, third_request = requests
        require(first_request.get("attempt") == 2 and third_request.get("attempt") == 3, "requests sin intento correcto", errors)
        for request in requests:
            require(request["method"] == "GET" and request["url"]["host"] == "matainde.qvcloud.net" and request["url"]["path"] == EXPECTED_UPGRADE_PATH, "request upgrade incorrecta", errors)
            require(request["query_keys"] == ["os", "appid", "version", "clientid"], "query upgrade incorrecta", errors)
            require(request["query_values_sanitized"]["clientid"] == "<redacted>", "clientid no redactado", errors)
        if "response" in first_request:
            require(first_request["response"]["observed"] is False and first_request["response"]["status"] is None, "response upgrade attempt2 inventada", errors)
            require(first_request["response"]["transport_error"] == "Certificate verify failed: certificate has expired", "error upstream ausente", errors)
        if "response" in third_request:
            require(third_request["response"]["observed"] is True and third_request["response"]["status"] == 200, "response attempt3 ausente o incorrecta", errors)
            require(third_request["response"]["body_schema"]["header"]["result"] == 0, "result response attempt3 incorrecto", errors)
            require(third_request["response"]["body_schema"]["content"]["version"] == "5.1.11", "version response attempt3 incorrecta", errors)
            require(third_request["response"]["size_bytes"] == 308, "tamaño response attempt3 incorrecto", errors)
            require(any(header["name"] == "Set-Cookie" and header["value"] == "<redacted>" for header in third_request["response"]["headers"]), "cookie response attempt3 no redactada", errors)
    require(network.get("responses") == [] and network.get("udp_datagrams") == [], "responses adicionales o UDP inventados", errors)
    sockets = network.get("discovery_sockets", [])
    require(len(sockets) >= 2 and all(item.get("transport") == "TCP" for item in sockets), "discovery TCP incompleto", errors)
    require(any(item.get("port") == 1884 and item.get("uid") == 10167 and item.get("state") == "ESTABLISHED" for item in sockets), "socket UID 1884 no confirmado", errors)
    require(any(item.get("attempt") == 3 and item.get("destination") == "47.254.141.132" and item.get("port") == 1884 for item in sockets), "socket attempt3 no confirmado", errors)
    require(all(item.get("payload") == "not_decoded" for item in sockets), "payload P2P inventado", errors)
    for field in ("Método", "URL host/path", "Query keys", "Headers", "Código HTTP", "Esquema request", "Esquema response"):
        require(field in report, f"informe sin campo {field}", errors)

    correspondence = inventory["static_correspondence"]
    required_operations = {"bootstrap", "opaque_register_and_login", "oauth_password_and_refresh", "service_discovery", "shadow_http", "auth_routes", "p2p_native", "app_upgrade_check", "physical_actions"}
    require(required_operations == {item.get("operation") for item in correspondence}, "correspondencia estática incorrecta", errors)
    if external:
        code_evidence = CODE_EVIDENCE_PATH.read_text(encoding="utf-8")
        apk_report = APK_REPORT_PATH.read_text(encoding="utf-8")
        rea_report = REA_REPORT_PATH.read_text(encoding="utf-8")
        for item in correspondence:
            for evidence_id in item.get("static_evidence", []):
                if evidence_id.startswith("E"):
                    require(re.search(rf"^## {re.escape(evidence_id)}\b", code_evidence, re.MULTILINE) is not None, f"evidencia estática ausente: {evidence_id}", errors)
                elif evidence_id == "APK-ANALYSIS-5":
                    require("/auth/nologin" in apk_report and "HttpDeviceConst.java:29-44" in apk_report, "rutas auth ausentes en APK §5", errors)
                elif evidence_id == "REA-20.2":
                    require("### 20.2" in rea_report, "REA 20.2 ausente", errors)

    updates = inventory["unsupported_updates"]
    require(isinstance(updates, list) and len(updates) == 1, "solo app_upgrade_check puede promoverse", errors)
    if updates:
        require(updates[0].get("operation") == "app_upgrade_check" and updates[0].get("request_response_observed") is True, "promover Unsupported sin request+response", errors)
        require(updates[0].get("evidence_id") == "DYN3-HTTP-UPGRADE-001" and bool(updates[0].get("remaining_limits")), "alcance de app_upgrade_check incompleto", errors)
    confirmations = {item.get("operation"): item for item in inventory["partial_runtime_confirmations"]}
    require("app_upgrade_check" in confirmations and "service_discovery" in confirmations, "confirmaciones parciales ausentes", errors)
    for item in confirmations.values():
        require(bool(item.get("demonstrated")) and bool(item.get("not_demonstrated")), "confirmación parcial incompleta", errors)
        decision = item.get("implementation_decision", "").lower()
        require("not ready" in decision or "unsupported" in decision or "ready for implementation" in decision, "promoción indebida de operación parcial", errors)
    blocker_ids = {item.get("id") for item in inventory["blockers"]}
    require({"ABI_HOST_MISMATCH", "UPSTREAM_CERT_EXPIRED", "WIFI_CAPTURE_INCOMPLETE", "OPAQUE_SESSION_NOT_TRIGGERED", "ATTEMPT3_REGISTRATION_NOT_REACHED"}.issubset(blocker_ids), "bloqueos incompletos", errors)

    raw_entries = inventory["raw_locations"]
    require(len(raw_entries) >= 8, "raw_locations insuficiente", errors)
    for entry in raw_entries:
        path = Path(entry["path"])
        require(path.is_absolute() and path.is_relative_to(RAW_ROOT), "ruta raw fuera del directorio autorizado", errors)
        require(not path.is_relative_to(ROOT), "ruta raw dentro del repo", errors)
        require(path.exists() is entry.get("exists", True), f"existencia raw incoherente: {path.name}", errors)
        if path.exists() and entry.get("sha256"):
            require(path.is_file() and digest(path) == entry["sha256"], f"hash raw incorrecto: {path.name}", errors)
        if path.exists() and entry.get("size_bytes") is not None:
            require(path.is_file() and path.stat().st_size == entry["size_bytes"], f"tamaño raw incorrecto: {path.name}", errors)

    cleanup = inventory["cleanup"]
    for key in ("attempt_1_avd_deleted", "attempt_2_avd_stopped", "attempt_2_avd_deleted", "attempt_2_avd_config_absent", "attempt_2_process_absent", "attempt_3_queries_stopped", "attempt_3_avd_stopped", "attempt_3_avd_deleted", "attempt_3_avd_config_absent", "attempt_3_proxy_cleared", "attempt_3_mitm_stopped", "attempt_3_sensitive_raw_paths_identified", "proxy_cleared", "mitm_stopped", "existing_wibox_test_untouched", "raw_retained_outside_repo", "sensitive_raw_paths_identified"):
        require(cleanup.get(key) is True, f"limpieza no demostrada: {key}", errors)
    if external:
        avd_root = Path.home() / ".android/avd"
        for name in ("wibox-stock-capture", "wibox-stock-arm"):
            require(not (avd_root / f"{name}.avd").exists(), f"directorio AVD presente: {name}", errors)
            require(not (avd_root / f"{name}.ini").exists(), f"INI AVD presente: {name}", errors)
        raw_log = RAW_ROOT / "emulator-console.log"
        require(raw_log.is_file() and EXPECTED_ATTEMPT_1_ERROR in raw_log.read_text(encoding="utf-8"), "raw intento 1 sin error exacto", errors)
        static_inventory = json.loads(STATIC_INVENTORY_PATH.read_text(encoding="utf-8"))
        require(static_inventory["apk"]["sha256"] == apk["sha256"], "hash distinto al inventario estático", errors)
        source_path = Path(apk["source_path"])
        require(source_path.is_file(), "APK original ausente", errors)
        if source_path.is_file():
            require(digest(source_path) == apk["sha256"], "APK actual cambió de hash", errors)
            with zipfile.ZipFile(source_path) as archive:
                for abi_name, expected_count in apk["native_library_counts"].items():
                    actual_count = len([name for name in archive.namelist() if name.startswith(f"lib/{abi_name}/") and name.endswith(".so")])
                    require(actual_count == expected_count, f"conteo nativo incorrecto para {abi_name}", errors)
        for attempt_name, expected_count in (("attempt2", 66), ("attempt3", 55)):
            manifest_path = RAW_ROOT / attempt_name / "raw-manifest.json"
            manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
            require(manifest.get("file_count") == expected_count, f"recuento raw incorrecto: {attempt_name}", errors)
            for entry in manifest.get("files", []):
                path = manifest_path.parent / entry["path"]
                require(path.is_file(), f"archivo del manifiesto ausente: {attempt_name}/{entry['path']}", errors)
                if path.is_file():
                    require(path.stat().st_size == entry["size_bytes"], f"tamaño manifiesto incorrecto: {attempt_name}/{entry['path']}", errors)
                    require(digest(path) == entry["sha256"], f"hash manifiesto incorrecto: {attempt_name}/{entry['path']}", errors)
        require(cleanup.get("raw_file_count_excluding_manifest") == 66 and cleanup.get("attempt_3_raw_file_count_excluding_manifest") == 55, "recuentos raw de limpieza incorrectos", errors)

    for snippet in ("Intento 1", "Intento 2", "Intento 3", "Initialized NDK translation", "HTTP-UPGRADE-001", "HTTP-UPGRADE-002", "Certificate verify failed: certificate has expired", "CAP_NET_RAW", "unsupported_updates", "userFreeRegister", "47.254.151.218", "47.254.141.132", "66 archivos", "55 archivos", "5.1.11", "<fictitious-uid>", "ssl_insecure", "wibox-stock-arm", "wibox-stock-capture"):
        require(snippet in report, f"informe sin evidencia obligatoria: {snippet}", errors)
    require(chr(0x2014) not in report and chr(0x2013) not in report, "informe contiene guiones tipográficos prohibidos", errors)
    return errors


def self_tests(inventory, report):
    errors = []
    require(validate(inventory, report, external=False) == [], "control positivo del inventario rechazado", errors)
    valid_request = copy.deepcopy(inventory["network"]["requests"][0])
    require(validate_request(valid_request) == [], "control positivo request rechazado", errors)
    cases = []

    def add_case(name, mutate, expected):
        candidate = copy.deepcopy(inventory)
        mutate(candidate)
        cases.append((name, candidate, expected))

    add_case("hash", lambda item: item["apk"].update(sha256="0" * 64), "hash APK")
    add_case("attempts", lambda item: item["attempts"].pop(0), "tres intentos")
    add_case("startup", lambda item: item["result"].update(app_started=False), "app_started")
    add_case("abi", lambda item: item["attempts"][1]["android_abi_verification"].update(ro_product_cpu_abilist="x86_64,x86"), "ABI ARM incompleta")
    add_case("sequence", lambda item: item["sequence"][1].update(order=item["sequence"][0]["order"]), "secuencia desordenada")
    add_case("unsupported", lambda item: item["unsupported_updates"].append({"operation": "discoverServices"}), "solo app_upgrade_check")
    add_case("registration", lambda item: item["attempts"][2]["registration"].update(user_free_register_observed=True), "registro attempt3 incoherente")
    add_case("upgrade_response", lambda item: item["attempts"][2]["upgrade"].update(response_observed=False), "response attempt3 ausente")
    add_case("cleanup", lambda item: item["cleanup"].update(attempt_2_avd_deleted=False), "limpieza no demostrada")
    add_case("physical", lambda item: item["isolation"].update(physical_device_used=True), "aislamiento incumplido")
    add_case("static", lambda item: item["static_correspondence"].pop(), "correspondencia estática incorrecta")
    add_case("capture", lambda item: item["observations"].pop(), "secuencia sin evidencia")

    def invalid_request(item):
        item["network"]["requests"][0]["response"].update(status=200)
    add_case("response", invalid_request, "response no observada con código inventado")

    def missing_response(item):
        del item["network"]["requests"][0]["response"]
    add_case("missing_response", missing_response, "falta response")

    for name, candidate, expected in cases:
        candidate_errors = validate(candidate, report, external=False)
        require(any(expected in message for message in candidate_errors), f"control negativo no detectado: {name}", errors)
    broken_report = report.replace("Certificate verify failed: certificate has expired", "error eliminado")
    require(any("error upstream ausente" in message or "evidencia obligatoria" in message for message in validate(inventory, broken_report, external=False)), "control negativo del informe no detectado", errors)
    return errors


def main():
    try:
        inventory = json.loads(INVENTORY_PATH.read_text(encoding="utf-8"))
        report = REPORT_PATH.read_text(encoding="utf-8")
    except (OSError, json.JSONDecodeError) as exc:
        print(f"Error de lectura: {exc}", file=sys.stderr)
        return 1
    errors = validate(inventory, report, external=True)
    errors.extend(self_tests(inventory, report))
    if errors:
        for error in errors:
            print(error, file=sys.stderr)
        return 1
    print(SUCCESS)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
