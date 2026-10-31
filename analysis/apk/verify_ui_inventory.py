#!/usr/bin/env python3
"""Verify UI screen coverage and exercise a negative control."""
from __future__ import annotations

import copy
import hashlib
import json
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
APK_DIR = ROOT / 'analysis/apk'
ANDROID = '{http://schemas.android.com/apk/res/android}'
EXPECTED_SCREENS = {
    'loading', 'cache', 'main_tab', 'add_type', 'preview', 'online_type',
    'product_choose', 'product_intro', 'handle_wifi', 'wifi_list_legacy',
    'wifi_set_legacy', 'device_bind', 'online_devices', 'scan', 'config',
    'password_modify', 'unlock_setting', 'device_info', 'about', 'image_pager',
    'video_player', 'push_call', 'debug', 'upgrade', 'guide', 'add_method_legacy',
    'router', 'voice', 'wifi_progress', 'share_device', 'pairing_fail',
    'share_manage', 'share_bind', 'tab_devices', 'tab_events', 'tab_event_list',
    'tab_gallery', 'tab_settings',
}
EXPECTED_FRAGMENTS = {'tab_devices', 'tab_events', 'tab_event_list', 'tab_gallery', 'tab_settings'}
REQUIRED_COLORS = {
    'colorPrimary', 'appTitleColor', 'main_ui_color_white', 'textColor',
    'titleText', 'bg_red', 'color_bottom_tab_text',
    'color_bottom_tab_text_normal', 'fm_fragment_text_selected', 'preview_item_bg',
}
REQUIRED_DIMENS = {
    'title_height', 'title_text_size', 'main_bottom_height', 'bottom_text_size',
    'btn_height', 'btn_width', 'standard_width', 'text_normal_size',
    'text_small_size', 'text_nomal_size', 'text_large_size', 'text_mini_size',
    'margin_large', 'margin_normal', 'margin_small', 'padding_normal',
    'devlist_height', 'preview_item_icon_small',
}
REQUIRED_STATES = {
    'empty', 'loading', 'error', 'offline', 'connecting', 'playing', 'refresh',
    'success', 'failure', 'password_hidden', 'password_visible', 'black', 'white',
    'scanning', 'edit', 'delete_confirm', 'auto_refuse', 'progress',
}
REQUIRED_DOC_MARKERS = [
    '## 1. Alcance y ausencia de capturas',
    '## 2. Tecnología y estructura',
    '### 3.1 Colores',
    '### 3.2 Tipografía',
    '### 3.3 Dimensiones',
    '### 3.4 Recursos por densidad',
    '## 4. Navegación global',
    '## 5. Pantallas, estados y recursos',
    '## 6. Componentes y estados transversales',
    '## 7. Cadenas ES/EN',
    '## 8. Recursos y trazabilidad',
]


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open('rb') as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b''):
            digest.update(block)
    return digest.hexdigest()


def validate(ui: dict, doc: str) -> list[str]:
    errors = []

    def need(condition: bool, message: str):
        if not condition:
            errors.append(message)

    need(ui.get('schema_version') == 1, 'ui schema_version must be 1')
    source = ui.get('source', {})
    need(source.get('package') == 'com.fermax.wibox', 'UI package mismatch')
    need(source.get('version_name') == 'v2.2' and source.get('version_code') == 18, 'UI version mismatch')
    need(source.get('sha256') == '08d78731fe79481b60b4250e736f653cd7af91932844a612b0b5c5a989aac7ce', 'UI source hash mismatch')
    need(ui.get('captures', {}).get('real_runtime_screenshots') is False, 'UI must not claim real runtime screenshots')
    need(ui.get('technology', {}).get('views_xml') is True and ui.get('technology', {}).get('compose') is False, 'UI technology mismatch')

    screens = ui.get('screens', [])
    ids = [item.get('id') for item in screens]
    need(len(ids) == len(set(ids)), 'duplicate screen IDs')
    need(set(ids) == EXPECTED_SCREENS, f'screen coverage mismatch: expected {len(EXPECTED_SCREENS)} screens')
    need(ui.get('screen_count') == len(screens) == 38, 'screen_count must be 38')

    by_id = {item['id']: item for item in screens if isinstance(item, dict) and item.get('id')}
    fragments = {item['id'] for item in screens if item.get('kind') == 'fragment'}
    need(fragments == EXPECTED_FRAGMENTS, 'fragment coverage mismatch')
    all_states = set()
    ui_code_path = APK_DIR / 'evidence/ui-code-evidence.md'
    ui_code = ui_code_path.read_text() if ui_code_path.is_file() else ''
    for item in screens:
        screen_id = item['id']
        need(item.get('class', '').startswith('com.quvii.ubell.'), f'invalid screen class: {screen_id}')
        states = item.get('states', [])
        need(bool(states), f'screen has no states: {screen_id}')
        state_ids = [state.get('id') for state in states]
        need(len(state_ids) == len(set(state_ids)), f'duplicate states: {screen_id}')
        need(all(isinstance(state.get('description'), str) and state.get('description') for state in states), f'empty state description: {screen_id}')
        all_states.update(state_ids)
        need(isinstance(item.get('navigation'), list), f'navigation is not a list: {screen_id}')
        for target in item.get('navigation', []):
            need(target in by_id, f'unknown navigation target {target} from {screen_id}')
        need(f'## {screen_id}\n' in ui_code, f'UI code evidence missing: {screen_id}')
        if screen_id == 'cache':
            need(item.get('layout') is None, 'cache must not invent a layout')
        else:
            layout = item.get('layout')
            need(isinstance(layout, str) and layout.startswith('analysis/apk/resources/layout/'), f'layout missing: {screen_id}')
            if isinstance(layout, str):
                need((ROOT / layout).is_file(), f'layout file missing: {screen_id}')
        need(isinstance(item.get('widgets'), list), f'widgets missing: {screen_id}')
        need(isinstance(item.get('resources'), list), f'resources missing: {screen_id}')
        if screen_id != 'cache':
            need(bool(item.get('resource_evidence')), f'resource hash evidence missing: {screen_id}')

    need(REQUIRED_STATES.issubset(all_states), f'required UI states missing: {sorted(REQUIRED_STATES - all_states)}')
    expected_edges = [{'from': item['id'], 'to': target} for item in screens for target in item['navigation']]
    need(ui.get('navigation_edges') == expected_edges, 'navigation_edges do not match screen navigation')
    need(len(expected_edges) == 71, 'expected 71 navigation edges')
    for legacy in ('guide', 'product_choose', 'add_method_legacy', 'wifi_list_legacy', 'wifi_set_legacy'):
        need(by_id[legacy].get('reachability') == 'legacy_no_inbound', f'legacy reachability mismatch: {legacy}')
        need(bool(by_id[legacy].get('note')), f'legacy note missing: {legacy}')
    product_states = {state['id']: state['description'] for state in by_id['product_choose']['states']}
    need('7410' in product_states.get('doorbell', ''), 'product mapping: Doorbell must be type 7410')
    need('9440' in product_states.get('monitor', ''), 'product mapping: Monitor must be type 9440')

    own_activity_classes = {
        item['class'] for item in screens if item['kind'] == 'activity'
    }
    manifest = ET.parse(APK_DIR / 'evidence/AndroidManifest.xml').getroot()
    app = manifest.find('application')
    manifest_classes = {
        node.get(ANDROID + 'name') for node in app.findall('activity')
        if node.get(ANDROID + 'name', '').startswith('com.quvii.ubell.')
    }
    need(own_activity_classes == manifest_classes, 'activity classes do not cover all 33 Wibox activities')
    need(len(own_activity_classes) == 33, 'expected 33 Wibox activity screens')

    visual = ui.get('visual_system', {})
    colors = visual.get('colors', {})
    need(REQUIRED_COLORS.issubset(colors), f'color coverage missing: {sorted(REQUIRED_COLORS - set(colors))}')
    need(colors.get('colorPrimary') == '#78c9e7', 'primary color mismatch')
    need(colors.get('main_ui_color_white') == '#f4f4f4', 'main background mismatch')
    need(colors.get('textColor') == '#ff4d4d4d', 'text color mismatch')
    typography = visual.get('typography', {})
    need('sans-serif' in typography.get('default_family', ''), 'default typography missing')
    need('FontHelper.Init(false)' in typography.get('default_family', ''), 'disabled bundled font not documented')
    need(len(typography.get('styles', [])) >= 5, 'typography styles coverage missing')
    dimensions = visual.get('dimensions', {})
    need(REQUIRED_DIMENS.issubset(dimensions), f'dimension coverage missing: {sorted(REQUIRED_DIMENS - set(dimensions))}')
    expected_dimens = {'title_height': '60dp', 'main_bottom_height': '55dp', 'btn_height': '50dp', 'bottom_text_size': '12sp', 'title_text_size': '20sp', 'text_normal_size': '18sp', 'standard_width': '45dp'}
    for key, value in expected_dimens.items():
        need(dimensions.get(key) == value, f'dimension mismatch: {key}')
    need('60x60 px xhdpi' in visual.get('key_dimensions', {}).get('tab_icons', ''), 'tab icon dimensions missing')
    need('120x120 dp' in visual.get('key_dimensions', {}).get('qr_code', ''), 'QR dimensions missing')
    need('values-night/colors.xml está vacío' in visual.get('themes', {}).get('dark', ''), 'dark theme limitation missing')
    night_colors = ET.parse(APK_DIR / 'resources/values-night/colors.xml').getroot()
    need(len(night_colors.findall('color')) == 0, 'night colors evidence must be empty')

    localization = ui.get('localization', {})
    need(localization.get('default_english_app_strings') == 282, 'English app string count mismatch')
    need(localization.get('spanish_app_strings') == 281, 'Spanish app string count mismatch')
    need(localization.get('identical_in_spanish') == 59, 'untranslated Spanish string count mismatch')
    need(localization.get('missing_in_spanish') == ['key_is_phone_calling'], 'missing Spanish key mismatch')
    en = ET.parse(APK_DIR / 'resources/values/strings-en.xml').getroot().findall('string')
    es = ET.parse(APK_DIR / 'resources/values/strings-es.xml').getroot().findall('string')
    need(len(en) == 286 and len(es) == 285, 'filtered string resource counts mismatch')
    need(len(localization.get('examples', [])) >= 6, 'localization examples missing')

    densities = ui.get('resource_densities', {})
    need('drawable-xhdpi' in densities, 'xhdpi resources missing')
    need(densities['drawable-xhdpi'].get('files', 0) >= 50, 'xhdpi resource count too low')
    for data in densities.values():
        for example in data.get('examples', []):
            need(example.get('width', 0) > 0 and example.get('height', 0) > 0, 'invalid image dimensions')

    resource_manifest = json.loads((APK_DIR / 'resources/manifest.json').read_text())
    files = resource_manifest.get('files', [])
    need(resource_manifest.get('file_count') == len(files) == 189, 'resource count mismatch')
    layout_count = sum(1 for entry in files if entry['path'].startswith('layout/') and entry['path'].endswith('.xml'))
    image_count = sum(1 for entry in files if 'width' in entry)
    need(layout_count == 74, 'expected 74 app layouts')
    need(image_count == 53, 'expected 53 representative images')
    for entry in files:
        path = APK_DIR / 'resources' / entry['path']
        need(path.is_file(), f'resource missing: {entry["path"]}')
        if path.is_file():
            need(sha256(path) == entry['sha256'], f'resource hash mismatch: {entry["path"]}')

    for marker in REQUIRED_DOC_MARKERS:
        need(marker in doc, f'UI document section missing: {marker}')
    for screen_id in EXPECTED_SCREENS:
        need(f'`{screen_id}`' in doc, f'UI document screen missing: {screen_id}')
    need('<!--' not in doc, 'UI document contains unresolved template markers')
    need('189 archivos' in doc and '74 layouts' in doc and '53 imágenes' in doc, 'UI document resource totals missing')
    need('No hay capturas reales' in doc, 'UI document must state no real screenshots')
    return errors


def main() -> int:
    try:
        ui = json.loads((APK_DIR / 'ui-inventory.json').read_text())
        doc = (ROOT / 'docs/ui-inventory.md').read_text()
    except (OSError, json.JSONDecodeError) as exc:
        print(f'FAIL: unable to load UI inputs: {exc}')
        return 1

    errors = validate(ui, doc)
    if errors:
        for error in errors:
            print(f'FAIL: {error}')
        return 1

    negative = copy.deepcopy(ui)
    negative['screens'] = [item for item in negative['screens'] if item['id'] != 'preview']
    negative_errors = validate(negative, doc)
    if not any('screen coverage mismatch' in error for error in negative_errors):
        print('FAIL: negative control was not detected')
        return 1

    print('WIBOX_UI_INVENTORY_VERIFIED')
    return 0


if __name__ == '__main__':
    sys.exit(main())
