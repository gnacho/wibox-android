package dev.nacho.wibox.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.nacho.wibox.R
import dev.nacho.wibox.data.CodeParseError
import dev.nacho.wibox.data.ParsedCode
import dev.nacho.wibox.ui.scan.CameraScanCard

@Composable
fun AddTypeScreen(
    viewModel: PairingViewModel,
    navigator: AppNavigator,
) {
    val onBack: () -> Unit = { navigator.back(); Unit }
    val onNew = { navigator.navigateInventory("product_intro") }
    val onOnline = { navigator.navigateInventory("online_type") }
    LaunchedEffect(Unit) { viewModel.beginNewFlow() }
    WiboxScreen("screen_add_type") {
        WiboxTopBar(stringResource(R.string.add_device), onBack = onBack)
        LocalDemoBanner()
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_device),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(44.dp),
                )
            }
            Spacer(Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.add_type_choice_title),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.add_type_choice_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(32.dp))
            WiboxButton(stringResource(R.string.new_device), onNew, tag = "add_new_device", primary = true)
            Spacer(Modifier.height(12.dp))
            WiboxButton(stringResource(R.string.device_online), onOnline, tag = "add_online_device")
        }
    }
}

@Composable
fun ProductIntroScreen(
    viewModel: PairingViewModel,
    navigator: AppNavigator,
) {
    val onBack: () -> Unit = { navigator.back(); Unit }
    val onScan = { navigator.navigateInventory("scan") }
    val onManualWifi = { navigator.navigateInventory("handle_wifi") }
    val state by viewModel.state.collectAsStateWithLifecycle()
    WiboxScreen("screen_product_intro") {
        WiboxTopBar(stringResource(R.string.wifi_setup), onBack = onBack)
        LocalDemoBanner()
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.reset_instruction),
                modifier = Modifier.padding(horizontal = 20.dp),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Image(
                painter = painterResource(R.drawable.reset_device_img),
                contentDescription = stringResource(R.string.cd_reset_device),
                modifier = Modifier.fillMaxWidth().height(300.dp).padding(24.dp),
            )
            Text(
                text = stringResource(R.string.reset_wait),
                modifier = Modifier.padding(horizontal = 20.dp),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            WiboxButton(stringResource(R.string.next), onScan, tag = "intro_scan_button", primary = true)
            TextButton(onClick = onManualWifi, modifier = Modifier.padding(16.dp)) {
                Text(stringResource(R.string.manual_wifi_instructions))
            }
            TextButton(onClick = { navigator.navigateInventory("router") }, modifier = Modifier.padding(bottom = 16.dp)) {
                Text(stringResource(R.string.qr_already_available))
            }
        }
        state.permission?.let { permission ->
            PermissionRationale(permission, viewModel::onPermissionResult) { viewModel.cancel() }
        }
    }
}

@Composable
fun ScanCodeScreen(
    viewModel: PairingViewModel,
    navigator: AppNavigator,
) {
    val onBack: () -> Unit = { navigator.back(); Unit }
    val onDevice = { navigator.navigateInventory("router") }
    val onInvitation = { navigator.navigateInventory("share_bind") }
    val state by viewModel.state.collectAsStateWithLifecycle()
    WiboxScreen("screen_scan") {
        WiboxTopBar(stringResource(R.string.scan_qr), onBack = onBack)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(16.dp))
            CameraScanCard(
                onDecoded = { payload ->
                    viewModel.updateCodeInput(payload)
                    when (viewModel.validateCode()) {
                        is ParsedCode.Device -> onDevice()
                        is ParsedCode.Invitation -> onInvitation()
                        null -> Unit
                    }
                },
                modifier = Modifier.testTag("camera_scan_card"),
            )
            Spacer(Modifier.height(20.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    WiboxTextField(
                        value = state.codeInput,
                        onValueChange = viewModel::updateCodeInput,
                        label = stringResource(R.string.paste_qr_or_invitation),
                        tag = "code_input",
                    )
                }
            }
            state.codeError?.let { error ->
                Text(
                    text = codeErrorText(error),
                    modifier = Modifier.padding(top = 10.dp).testTag("code_error"),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(20.dp))
            WiboxButton(
                text = stringResource(R.string.validate_code),
                onClick = {
                    when (viewModel.validateCode()) {
                        is ParsedCode.Device -> onDevice()
                        is ParsedCode.Invitation -> onInvitation()
                        null -> Unit
                    }
                },
                tag = "validate_code_button",
                primary = true,
            )
            TextButton(
                onClick = { navigator.navigateInventory("device_bind") },
                modifier = Modifier.testTag("add_manually_button"),
            ) {
                Text(stringResource(R.string.add_manually))
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
fun OnlineTypeScreen(
    viewModel: PairingViewModel,
    navigator: AppNavigator,
) {
    val onBack: () -> Unit = { navigator.back(); Unit }
    val onScanOnline = { navigator.navigateInventory("online_devices") }
    val onManual = {
        viewModel.startManual()
        navigator.navigateInventory("device_bind")
    }
    WiboxScreen("screen_online_type") {
        WiboxTopBar(stringResource(R.string.add_online), onBack = onBack)
        LocalDemoBanner()
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_wifi),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(44.dp),
                )
            }
            Spacer(Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.add_online_choice_title),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.add_online_choice_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(32.dp))
            WiboxButton(stringResource(R.string.add_online), onScanOnline, tag = "scan_online_button", primary = true)
            Spacer(Modifier.height(12.dp))
            WiboxButton(stringResource(R.string.add_manually), onManual, tag = "manual_add_button")
        }
    }
}

@Composable
fun OnlineDevicesScreen(
    viewModel: PairingViewModel,
    navigator: AppNavigator,
) {
    val onBack: () -> Unit = { navigator.back(); Unit }
    val onSelected = { navigator.navigateInventory("device_bind") }
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.scanOnlineDevices() }
    WiboxScreen("screen_online_devices") {
        WiboxTopBar(stringResource(R.string.add_online), onBack = onBack)
        LocalDemoBanner()
        Text(
            text = stringResource(R.string.select_online_device),
            modifier = Modifier.padding(20.dp),
            style = MaterialTheme.typography.titleMedium,
        )
        when (val result = state.onlineDevices) {
            is LoadState.Loading -> LoadingContent(stringResource(R.string.searching_devices))
            is LoadState.Error -> ErrorContent(result.error, viewModel::scanOnlineDevices)
            is LoadState.Content -> {
                if (result.value.isEmpty()) {
                    EmptyContent(stringResource(R.string.no_device_found))
                } else {
                    Column {
                        result.value.forEach { device ->
                            WiboxOptionRow(
                                text = device.id,
                                onClick = {
                                    viewModel.selectOnlineDevice(device)
                                    onSelected()
                                },
                                tag = "online_${device.id}",
                                trailing = {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_device),
                                        contentDescription = null,
                                        tint = WiboxColors.selected,
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DeviceBindScreen(
    viewModel: PairingViewModel,
    shared: Boolean,
    navigator: AppNavigator,
) {
    val onBack: () -> Unit = { navigator.back(); Unit }
    val onBound: (String) -> Unit = { navigator.goMain() }
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.boundDeviceId) {
        state.boundDeviceId?.let {
            viewModel.consumeBoundDevice()
            onBound(it)
        }
    }
    WiboxScreen(if (shared) "screen_share_bind" else "screen_device_bind") {
        WiboxTopBar(stringResource(R.string.add_device), onBack = onBack)
        LocalDemoBanner()
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(if (shared) R.string.share_accept_hint else R.string.device_bind_hint),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            WiboxTextField(
                value = state.name,
                onValueChange = viewModel::updateName,
                label = stringResource(R.string.device_name),
                enabled = !state.binding,
                tag = "bind_name",
            )
            Spacer(Modifier.height(12.dp))
            WiboxTextField(
                value = state.uid,
                onValueChange = viewModel::updateUid,
                label = stringResource(R.string.uid),
                enabled = !state.binding && !state.qrLocked && !shared,
                readOnly = state.qrLocked || shared,
                tag = "bind_uid",
                ltr = true,
            )
            if (!shared) {
                TextButton(onClick = { navigator.navigateInventory("scan") }, modifier = Modifier.align(Alignment.End)) {
                    Text(stringResource(R.string.scan_qr))
                }
            }
            Spacer(Modifier.height(12.dp))
            WiboxTextField(
                value = state.authCode,
                onValueChange = viewModel::updateAuthCode,
                label = stringResource(if (shared) R.string.invitation_code else R.string.password),
                enabled = !state.binding && !shared && state.invitation == null,
                readOnly = shared || state.invitation != null,
                password = !shared,
                tag = "bind_auth_code",
            )
            state.bindError?.let { error ->
                Text(
                    text = addDeviceErrorText(error),
                    modifier = Modifier.padding(top = 12.dp).testTag("bind_error"),
                    color = WiboxColors.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Spacer(Modifier.height(24.dp))
            if (state.binding) {
                CircularProgressIndicator(color = WiboxColors.selected, modifier = Modifier.testTag("binding_progress"))
                Text(stringResource(R.string.binding_local), modifier = Modifier.padding(10.dp))
            } else {
                WiboxButton(
                    text = stringResource(if (shared) R.string.accept else R.string.save),
                    onClick = viewModel::bindDevice,
                    tag = "bind_submit",
                    primary = true,
                )
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
fun HandleWifiScreen(
    viewModel: PairingViewModel,
    navigator: AppNavigator,
) {
    val onBack: () -> Unit = { navigator.back(); Unit }
    val onNext = { navigator.navigateInventory("router") }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    WiboxScreen("screen_handle_wifi") {
        WiboxTopBar(stringResource(R.string.wifi_setup), onBack = onBack)
        LocalDemoBanner()
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            InstructionStep("1", stringResource(R.string.open_wifi_settings))
            Spacer(Modifier.height(3.dp).fillMaxWidth().background(WiboxColors.divider))
            InstructionStep("2", stringResource(R.string.connect_to_ap, state.apName))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.ap_password, state.authCode.ifBlank { "WIBOX" }),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                )
                SmallIconButton(
                    painter = R.drawable.ic_copy,
                    contentDescription = stringResource(R.string.copy),
                    onClick = {
                        clipboard.setText(AnnotatedString(state.authCode.ifBlank { "WIBOX" }))
                        copied = true
                    },
                    tag = "copy_ap_password",
                )
            }
            if (copied) {
                Text(stringResource(R.string.copied), modifier = Modifier.padding(horizontal = 20.dp), color = WiboxColors.selected)
            }
            Spacer(Modifier.height(20.dp))
            WiboxButton(stringResource(R.string.next), onNext, tag = "handle_wifi_next", primary = true)
            TextButton(onClick = { navigator.navigateInventory("wifi_progress") }) {
                Text(stringResource(R.string.continue_to_pairing))
            }
        }
    }
}

@Composable
private fun InstructionStep(number: String, text: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(number, fontSize = 35.sp, fontWeight = FontWeight.Bold, color = WiboxColors.text)
        Spacer(Modifier.size(20.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun RouterScreen(
    viewModel: PairingViewModel,
    modify: Boolean,
    navigator: AppNavigator,
) {
    val onBack: () -> Unit = { navigator.back(); Unit }
    val onAp = { navigator.navigateInventory("wifi_progress") }
    val onVoice = { navigator.navigateInventory("voice") }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val networks = listOf("Wibox 2.4 GHz", "Wibox 2.4 GHz Alt", "Wibox test LAN")
    WiboxScreen("screen_router") {
        WiboxTopBar(stringResource(R.string.wifi_setup), onBack = onBack)
        LocalDemoBanner()
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(10.dp),
        ) {
            Text(
                text = stringResource(if (modify) R.string.modify_wifi_hint else R.string.router_choose_hint),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(16.dp))
            networks.forEach { network ->
                WiboxOptionRow(
                    text = network,
                    onClick = { viewModel.updateSelectedSsid(network) },
                    tag = "wifi_${network.replace(" ", "_")}",
                    trailing = {
                        if (network == state.selectedSsid) {
                            Icon(painterResource(R.drawable.ic_check), contentDescription = stringResource(R.string.selected), tint = WiboxColors.selected)
                        } else {
                            Icon(painterResource(R.drawable.ic_wifi), contentDescription = null, tint = WiboxColors.secondaryText)
                        }
                    },
                )
            }
            Spacer(Modifier.height(16.dp))
            WiboxTextField(
                value = state.wifiPassword,
                onValueChange = viewModel::updateWifiPassword,
                label = stringResource(R.string.wifi_password),
                password = !state.showWifiPassword,
                tag = "router_password",
                supportingText = stringResource(R.string.password_not_persisted),
            )
            Row(
                modifier = Modifier.fillMaxWidth().clickable { viewModel.toggleShowWifiPassword() }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = state.showWifiPassword, onCheckedChange = { viewModel.toggleShowWifiPassword() })
                Text(stringResource(R.string.show_password))
            }
            Row(
                modifier = Modifier.fillMaxWidth().clickable { viewModel.toggleSaveWifiPassword() }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = state.saveWifiPassword, onCheckedChange = { viewModel.toggleSaveWifiPassword() })
                Text(stringResource(R.string.save_password))
            }
            Spacer(Modifier.height(16.dp))
            WiboxButton(stringResource(R.string.continue_with_ap), onAp, tag = "router_ap_next", primary = true)
            Spacer(Modifier.height(10.dp))
            WiboxButton(stringResource(R.string.sound_waves_add), onVoice, tag = "router_voice_next")
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
fun VoiceProvisioningScreen(
    viewModel: PairingViewModel,
    navigator: AppNavigator,
) {
    val onBack: () -> Unit = { navigator.back(); Unit }
    val onReady = { navigator.navigateInventory("device_bind") }
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.startProvisioning() }
    WiboxScreen("screen_voice") {
        WiboxTopBar(stringResource(R.string.sound_waves_add), onBack = onBack)
        LocalDemoBanner()
        Column(
            modifier = Modifier.weight(1f).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.sound_waves_simulated),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            Image(
                painter = painterResource(R.drawable.device_add_sound_waver),
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().padding(vertical = 30.dp),
            )
            Text(stringResource(R.string.volume_hint), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.weight(1f))
            ProvisionStatus(state.provisionStage)
            Spacer(Modifier.weight(1f))
            WiboxButton(
                text = stringResource(R.string.next),
                onClick = onReady,
                enabled = state.provisionStage == ProvisionStage.READY,
                tag = "voice_next",
            )
            TextButton(onClick = { navigator.goMain() }) {
                Text(stringResource(R.string.finish_later))
            }
            Spacer(Modifier.height(50.dp))
        }
    }
}

@Composable
fun WifiProvisioningScreen(
    viewModel: PairingViewModel,
    navigator: AppNavigator,
) {
    val onBack: () -> Unit = { navigator.back(); Unit }
    val onReady = { navigator.navigateInventory("device_bind") }
    val onFailure = { navigator.navigateInventory("pairing_fail") }
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.startProvisioning() }
    WiboxScreen("screen_wifi_progress") {
        WiboxTopBar(stringResource(R.string.wifi_setup), onBack = onBack)
        LocalDemoBanner()
        Column(
            modifier = Modifier.weight(1f).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(R.drawable.device_add_wifi_set),
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().padding(30.dp),
            )
            Text(stringResource(R.string.pairing_simulated), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            Spacer(Modifier.weight(1f))
            ProvisionStatus(state.provisionStage)
            Spacer(Modifier.weight(1f))
            WiboxButton(
                text = stringResource(R.string.next),
                onClick = onReady,
                enabled = state.provisionStage == ProvisionStage.READY,
                tag = "wifi_next",
            )
            TextButton(
                onClick = {
                    viewModel.failProvisioning()
                    onFailure()
                },
                modifier = Modifier.testTag("simulate_pairing_failure"),
            ) {
                Text(stringResource(R.string.simulate_pairing_failure))
            }
            Row(horizontalArrangement = Arrangement.Center) {
                TextButton(onClick = { navigator.goMain() }) { Text(stringResource(R.string.finish_later)) }
                TextButton(
                    onClick = { navigator.navigateInventory("handle_wifi") },
                    modifier = Modifier.testTag("wifi_manual_button"),
                ) { Text(stringResource(R.string.manual_wifi_instructions)) }
            }
            Spacer(Modifier.height(30.dp))
        }
    }
}

@Composable
private fun ProvisionStatus(stage: ProvisionStage) {
    Box(modifier = Modifier.testTag("provision_status")) {
        PairingProgress(stage)
    }
}

@Composable
fun PairingFailScreen(viewModel: PairingViewModel, navigator: AppNavigator) {
    val onMain = { navigator.goMain() }
    androidx.activity.compose.BackHandler { viewModel.cancel() }
    WiboxScreen("screen_pairing_fail") {
        WiboxTopBar(stringResource(R.string.error))
        LocalDemoBanner()
        Column(
            modifier = Modifier.weight(1f).padding(20.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(R.string.pairing_error_hint),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(40.dp))
            WiboxButton(stringResource(R.string.next), onMain, tag = "pairing_fail_main")
        }
    }
}

@Composable
private fun codeErrorText(error: CodeParseError): String = when (error) {
    CodeParseError.EMPTY -> stringResource(R.string.code_empty)
    CodeParseError.DEVICE_FIELD_COUNT -> stringResource(R.string.qr_field_count)
    CodeParseError.INVALID_AP_NAME,
    CodeParseError.INVALID_UID,
    CodeParseError.INVALID_AUTH_CODE,
    CodeParseError.INVALID_MODEL,
    -> stringResource(R.string.qr_invalid_fields)
    CodeParseError.UNSUPPORTED_OEM,
    CodeParseError.UNSUPPORTED_APP,
    -> stringResource(R.string.invitation_unsupported)
    else -> stringResource(R.string.invitation_invalid)
}

@Composable
private fun addDeviceErrorText(error: PairingError): String = when (error) {
    PairingError.EMPTY_FIELDS -> stringResource(R.string.empty_required_fields)
    PairingError.DUPLICATE -> stringResource(R.string.duplicate_device)
    PairingError.INVITATION_USED -> stringResource(R.string.invitation_used)
    PairingError.INVITATION_EXPIRED -> stringResource(R.string.invitation_expired)
    PairingError.UNSUPPORTED -> stringResource(R.string.unsupported_local)
    else -> stringResource(R.string.add_device_failed)
}
