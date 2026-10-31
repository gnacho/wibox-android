package dev.nacho.wibox.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.nacho.wibox.R
import dev.nacho.wibox.core.cloud.ActionType
import dev.nacho.wibox.core.cloud.DevicePresence
import dev.nacho.wibox.core.cloud.DevicePermission
import dev.nacho.wibox.core.cloud.DeviceRole
import kotlinx.coroutines.delay

@Composable
fun PreviewScreen(
    deviceId: String,
    viewModel: PreviewViewModel,
    navigator: AppNavigator,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(deviceId) { viewModel.load(deviceId) }
    WiboxScreen("screen_preview") {
        WiboxTopBar(
            title = state.device?.name ?: stringResource(R.string.preview),
            onBack = { navigator.back(); Unit },
            actions = {
                SmallIconButton(
                    painter = R.drawable.ic_lock_open,
                    contentDescription = stringResource(R.string.modify_unlock_password),
                    onClick = { navigator.navigateInventory(AppDestinationRequest("password_modify", deviceId = deviceId, mode = "unlock")) },
                    tag = "preview_password",
                    tint = WiboxColors.onPrimary,
                )
                SmallIconButton(
                    painter = R.drawable.ic_settings,
                    contentDescription = stringResource(R.string.device_settings),
                    onClick = { navigator.navigateInventory(AppDestinationRequest("config", deviceId = deviceId)) },
                    tag = "preview_config",
                    tint = WiboxColors.onPrimary,
                )
            },
        )
        LocalDemoBanner()
        BoxWithConstraints(modifier = Modifier.weight(1f)) {
            val landscape = maxWidth > maxHeight
            if (landscape) {
                Row(modifier = Modifier.fillMaxSize()) {
                    PreviewArea(state, modifier = Modifier.weight(1.5f).fillMaxSize())
                    Column(
                        modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    ) {
                        PreviewControlBar(state, viewModel)
                        PreviewOperators(state, viewModel, modifier = Modifier.height(260.dp))
                        PreviewResult(state)
                    }
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    PreviewArea(state, modifier = Modifier.fillMaxWidth().height(240.dp))
                    PreviewControlBar(state, viewModel)
                    PreviewOperators(state, viewModel, modifier = Modifier.weight(1f))
                    PreviewResult(state)
                }
            }
        }
    }
}

@Composable
private fun PreviewArea(state: PreviewUiState, modifier: Modifier = Modifier) {
    Box(modifier = modifier.background(Color.Black).testTag("preview_area")) {
        if (state.fourPanels) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(modifier = Modifier.weight(1f)) {
                    PreviewPanel(state, 1, Modifier.weight(1f))
                    PreviewPanel(state, 2, Modifier.weight(1f))
                }
                Row(modifier = Modifier.weight(1f)) {
                    PreviewPanel(state, 3, Modifier.weight(1f))
                    PreviewPanel(state, 4, Modifier.weight(1f))
                }
            }
        } else {
            PreviewPanel(state, 1, Modifier.fillMaxSize())
        }
        if (state.recording) {
            Row(
                modifier = Modifier.align(Alignment.TopCenter).padding(20.dp).background(Color(0x88000000)).padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_record),
                    contentDescription = stringResource(R.string.recording),
                    tint = WiboxColors.error,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.size(6.dp))
                Text(formatDuration(state.recordingSeconds), color = Color.White, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun PreviewPanel(state: PreviewUiState, channel: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(Color.Black).padding(1.dp),
        contentAlignment = Alignment.Center,
    ) {
        when (state.status) {
            PreviewStatus.IDLE, PreviewStatus.CONNECTING -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color.White)
                    Text(stringResource(R.string.connecting), color = Color.White, modifier = Modifier.padding(top = 8.dp))
                }
            }
            PreviewStatus.PLAYING_SIMULATED -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        painter = painterResource(R.drawable.ic_video),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(50.dp),
                    )
                    Text(
                        text = stringResource(R.string.simulated_video),
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(8.dp),
                    )
                }
            }
            PreviewStatus.STOPPED -> {
                Icon(
                    painter = painterResource(R.drawable.ic_play),
                    contentDescription = stringResource(R.string.stopped),
                    tint = Color.White,
                    modifier = Modifier.size(50.dp),
                )
            }
            PreviewStatus.OFFLINE -> {
                Text(
                    text = stringResource(R.string.device_offline),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            }
            PreviewStatus.UNSUPPORTED -> {
                Text(
                    text = stringResource(R.string.preview_unsupported),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(12.dp),
                )
            }
            PreviewStatus.ERROR -> {
                Text(stringResource(R.string.device_not_found), color = WiboxColors.error)
            }
            else -> {
                Text(stringResource(R.string.loading), color = Color.White)
            }
        }
        Text(
            text = stringResource(R.string.channel_number, channel),
            modifier = Modifier.align(Alignment.BottomStart).padding(5.dp),
            color = Color.White,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun PreviewControlBar(state: PreviewUiState, viewModel: PreviewViewModel) {
    val playing = state.status == PreviewStatus.PLAYING_SIMULATED
    Row(
        modifier = Modifier.fillMaxWidth().height(60.dp).background(Color(0xFF424242)),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SmallIconButton(
            painter = if (playing) R.drawable.ic_stop else R.drawable.ic_play,
            contentDescription = stringResource(if (playing) R.string.stop else R.string.play),
            onClick = viewModel::togglePlayback,
            enabled = playing || state.status == PreviewStatus.STOPPED,
            tag = "preview_play",
            tint = Color.White,
        )
        SmallIconButton(
            painter = R.drawable.ic_mic,
            contentDescription = stringResource(R.string.talk),
            onClick = viewModel::toggleTalking,
            enabled = playing,
            tag = "preview_talk",
            tint = if (state.talking) WiboxColors.primary else Color.White,
        )
        SmallIconButton(
            painter = if (state.listening) R.drawable.ic_volume else R.drawable.ic_volume_off,
            contentDescription = stringResource(R.string.listen),
            onClick = viewModel::toggleListening,
            enabled = playing,
            tag = "preview_listen",
            tint = Color.White,
        )
        SmallIconButton(
            painter = R.drawable.ic_record,
            contentDescription = stringResource(if (state.recording) R.string.stop_recording else R.string.record),
            onClick = viewModel::toggleRecording,
            enabled = playing,
            tag = "preview_record",
            tint = if (state.recording) WiboxColors.error else Color.White,
        )
        SmallIconButton(
            painter = R.drawable.ic_camera,
            contentDescription = stringResource(R.string.snapshot),
            onClick = viewModel::snapshot,
            enabled = playing,
            tag = "preview_snapshot",
            tint = Color.White,
        )
        SmallIconButton(
            painter = R.drawable.ic_grid,
            contentDescription = stringResource(R.string.toggle_panels),
            onClick = viewModel::togglePanels,
            tag = "preview_panels",
            tint = Color.White,
        )
        SmallIconButton(
            painter = R.drawable.ic_f1,
            contentDescription = stringResource(R.string.f1_activation),
            onClick = { viewModel.performAction(ActionType.F1) },
            enabled = playing && state.device?.permissions?.contains(DevicePermission.F1) == true && state.action.pendingCommandId == null,
            tag = "preview_f1",
            tint = Color.White,
        )
    }
}

@Composable
private fun PreviewOperators(state: PreviewUiState, viewModel: PreviewViewModel, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().background(WiboxColors.background),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LargePreviewButton(
            icon = R.drawable.ic_lock_open,
            description = stringResource(R.string.unlock),
            onClick = { viewModel.performAction(ActionType.UNLOCK) },
            enabled = state.status == PreviewStatus.PLAYING_SIMULATED &&
                state.device?.permissions?.contains(DevicePermission.UNLOCK) == true && state.action.pendingCommandId == null,
            tag = "preview_unlock",
        )
        LargePreviewButton(
            icon = R.drawable.ic_call_end,
            description = stringResource(R.string.stop),
            onClick = {
                if (state.status == PreviewStatus.PLAYING_SIMULATED) viewModel.togglePlayback()
            },
            enabled = state.status == PreviewStatus.PLAYING_SIMULATED,
            tag = "preview_stop_large",
        )
        LargePreviewButton(
            icon = R.drawable.ic_mic,
            description = stringResource(R.string.talk),
            onClick = viewModel::toggleTalking,
            enabled = state.status == PreviewStatus.PLAYING_SIMULATED,
            tag = "preview_talk_large",
        )
    }
}

@Composable
private fun LargePreviewButton(
    icon: Int,
    description: String,
    onClick: () -> Unit,
    enabled: Boolean,
    tag: String,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(100.dp).testTag(tag),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = description,
            modifier = Modifier.size(72.dp),
            tint = if (enabled) WiboxColors.text else WiboxColors.disabled,
        )
    }
}

@Composable
private fun PreviewResult(state: PreviewUiState) {
    Box(modifier = Modifier.testTag("preview_action_result")) {
        Column {
            ActionOutcomeText(state.action)
            if (state.mediaSaved) {
                Text(
                    text = stringResource(R.string.local_media_saved),
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    color = WiboxColors.selected,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
fun DeviceConfigScreen(
    deviceId: String,
    viewModel: DeviceDetailViewModel,
    navigator: AppNavigator,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val device = state.device
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(deviceId) { viewModel.load(deviceId) }
    LaunchedEffect(state.deletedDeviceId) {
        if (state.deletedDeviceId == deviceId) {
            viewModel.consumeDeletedDevice()
            navigator.back()
        }
    }
    WiboxScreen("screen_config") {
        WiboxTopBar(stringResource(R.string.settings), onBack = { navigator.back() })
        LocalDemoBanner()
        if (device == null) {
            EmptyContent(stringResource(R.string.device_not_found))
        } else {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .testTag("config_scroll"),
            ) {
                WiboxOptionRow(
                    device.name,
                    { navigator.navigateInventory(AppDestinationRequest("device_info", deviceId = deviceId)) },
                    tag = "config_info",
                    topMargin = 15.dp,
                )
                if (device.role == DeviceRole.OWNER) {
                    WiboxOptionRow(
                        stringResource(R.string.modify_device_password),
                        { navigator.navigateInventory(AppDestinationRequest("password_modify", deviceId = deviceId, mode = "device")) },
                        tag = "config_password",
                        topMargin = 15.dp,
                    )
                    WiboxOptionRow(
                        stringResource(R.string.modify_unlock_password),
                        { navigator.navigateInventory(AppDestinationRequest("password_modify", deviceId = deviceId, mode = "unlock")) },
                        tag = "config_unlock_password",
                        topMargin = 15.dp,
                    )
                }
                WiboxOptionRow(
                    stringResource(R.string.unlock_setting),
                    { navigator.navigateInventory(AppDestinationRequest("unlock_setting", deviceId = deviceId)) },
                    tag = "config_unlock_setting",
                    topMargin = 15.dp,
                )
                WiboxOptionRow(
                    text = stringResource(R.string.intercom_way),
                    onClick = {
                        viewModel.updateDevice { snapshot ->
                            val current = snapshot.metadata["fullDuplex"]?.toBoolean() ?: true
                            snapshot.copy(metadata = snapshot.metadata + ("fullDuplex" to (!current).toString()))
                        }
                    },
                    topMargin = 15.dp,
                    trailing = { Text(stringResource(if (device.fullDuplex) R.string.full_duplex else R.string.half_duplex)) },
                )
                WiboxOptionRow(
                    text = stringResource(R.string.preview_mode),
                    onClick = {
                        viewModel.updateDevice { snapshot ->
                            val current = snapshot.metadata["highQuality"]?.toBoolean() ?: true
                            snapshot.copy(metadata = snapshot.metadata + ("highQuality" to (!current).toString()))
                        }
                    },
                    topMargin = 15.dp,
                    trailing = { Text(stringResource(if (device.highQuality) R.string.high_quality else R.string.low_quality)) },
                )
                if (device.role == DeviceRole.OWNER) {
                    WiboxOptionRow(
                        stringResource(R.string.wifi_setup),
                        { navigator.navigateInventory(AppDestinationRequest("handle_wifi", deviceId = deviceId)) },
                        tag = "config_wifi",
                        topMargin = 15.dp,
                    )
                }
                WiboxOptionRow(
                    text = stringResource(R.string.f1_enable_time),
                    onClick = {
                        viewModel.updateDevice { snapshot ->
                            val current = snapshot.metadata["f1Seconds"]?.toIntOrNull() ?: 3
                            snapshot.copy(metadata = snapshot.metadata + ("f1Seconds" to (if (current == 3) 5 else 3).toString()))
                        }
                    },
                    topMargin = 15.dp,
                    trailing = { Text(stringResource(R.string.seconds_value, device.f1Seconds)) },
                )
                if (device.role == DeviceRole.OWNER) {
                    WiboxSwitchRow(
                        text = stringResource(R.string.call_reception),
                        checked = device.callAlerts,
                        onCheckedChange = { enabled ->
                            viewModel.updateDevice { it.copy(metadata = it.metadata + ("callAlerts" to enabled.toString())) }
                        },
                        tag = "config_call_alerts",
                        topMargin = 15.dp,
                    )
                    WiboxSwitchRow(
                        text = stringResource(R.string.motion_alerts),
                        checked = device.motionAlerts,
                        onCheckedChange = { enabled ->
                            viewModel.updateDevice { it.copy(metadata = it.metadata + ("motionAlerts" to enabled.toString())) }
                        },
                        tag = "config_motion_alerts",
                        topMargin = 15.dp,
                    )
                    WiboxOptionRow(
                        stringResource(R.string.firmware_version),
                        { navigator.navigateInventory(AppDestinationRequest("upgrade", deviceId = deviceId)) },
                        tag = "config_upgrade",
                        topMargin = 15.dp,
                    )
                    WiboxOptionRow(
                        stringResource(R.string.share_manage_title),
                        { navigator.navigateInventory(AppDestinationRequest("share_manage", deviceId = deviceId)) },
                        tag = "config_share",
                        topMargin = 15.dp,
                    )
                }
                WiboxOptionRow(
                    text = stringResource(R.string.time_sync),
                    onClick = {},
                    enabled = false,
                    tag = "config_time_sync",
                    topMargin = 15.dp,
                    trailing = {},
                )
                Spacer(Modifier.height(20.dp))
                WiboxButton(stringResource(R.string.delete), { confirmDelete = true }, tag = "delete_device_button")
                Spacer(Modifier.height(20.dp))
            }
        }
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.delete_device),
            text = stringResource(R.string.delete_device_confirmation),
            confirmText = stringResource(R.string.delete),
            onConfirm = {
                confirmDelete = false
                viewModel.deleteDevice()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
fun PasswordModifyScreen(
    deviceId: String,
    mode: String,
    viewModel: DeviceDetailViewModel,
    navigator: AppNavigator,
) {
    var oldPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var newUnlock by remember { mutableStateOf("") }
    var unlockConfirmation by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf(false) }
    val admin = mode == "unlock"
    val mismatch = newPassword != confirmation || (admin && newUnlock != unlockConfirmation)
    val empty = newPassword.isBlank() || confirmation.isBlank() || oldPassword.isBlank() ||
        (admin && (newUnlock.isBlank() || unlockConfirmation.isBlank()))
    WiboxScreen("screen_password_modify") {
        WiboxTopBar(stringResource(R.string.modify_password), onBack = { navigator.back() })
        LocalDemoBanner()
        Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(vertical = 12.dp)) {
            Text(
                text = stringResource(R.string.password_demo_notice),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
            WiboxTextField(
                value = oldPassword,
                onValueChange = { oldPassword = it },
                label = stringResource(R.string.old_password),
                password = true,
                tag = "old_password",
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Spacer(Modifier.height(12.dp))
            WiboxTextField(
                value = newPassword,
                onValueChange = { newPassword = it.filter(Char::isDigit).take(6) },
                label = stringResource(R.string.new_password),
                password = true,
                tag = "new_password",
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Spacer(Modifier.height(12.dp))
            WiboxTextField(
                value = confirmation,
                onValueChange = { confirmation = it.filter(Char::isDigit).take(6) },
                label = stringResource(R.string.confirm_password),
                password = true,
                tag = "confirm_password",
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            if (admin) {
                Spacer(Modifier.height(12.dp))
                WiboxTextField(
                    value = newUnlock,
                    onValueChange = { newUnlock = it.filter(Char::isDigit).take(6) },
                    label = stringResource(R.string.new_unlock_password),
                    password = true,
                    tag = "new_unlock_password",
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                Spacer(Modifier.height(12.dp))
                WiboxTextField(
                    value = unlockConfirmation,
                    onValueChange = { unlockConfirmation = it.filter(Char::isDigit).take(6) },
                    label = stringResource(R.string.confirm_unlock_password),
                    password = true,
                    tag = "confirm_unlock_password",
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
            if (mismatch && confirmation.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.passwords_do_not_match),
                    modifier = Modifier.padding(20.dp).testTag("password_mismatch"),
                    color = WiboxColors.error,
                )
            }
            if (empty && saved) {
                Text(stringResource(R.string.empty_required_fields), modifier = Modifier.padding(20.dp), color = WiboxColors.error)
            }
            if (saved && !empty && !mismatch) {
                Text(
                    text = stringResource(R.string.local_password_validated),
                    modifier = Modifier.padding(20.dp).testTag("password_validated"),
                    color = WiboxColors.selected,
                )
            }
            Spacer(Modifier.height(20.dp))
            WiboxButton(stringResource(R.string.confirm), { saved = true }, tag = "password_confirm")
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
fun UnlockSettingScreen(
    deviceId: String,
    viewModel: DeviceDetailViewModel,
    navigator: AppNavigator,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(deviceId) { viewModel.load(deviceId) }
    WiboxScreen("screen_unlock_setting") {
        WiboxTopBar(stringResource(R.string.unlock_setting), onBack = { navigator.back() })
        LocalDemoBanner()
        WiboxSwitchRow(
            text = stringResource(R.string.quick_unlock),
            checked = state.preferences.quickUnlock,
            onCheckedChange = viewModel::setQuickUnlock,
            tag = "quick_unlock_switch",
            topMargin = 15.dp,
        )
        WiboxSwitchRow(
            text = stringResource(R.string.biometric_unlock),
            checked = false,
            onCheckedChange = {},
            enabled = false,
            tag = "biometric_unlock_switch",
            topMargin = 15.dp,
        )
        Text(
            text = stringResource(R.string.biometric_unavailable_demo),
            modifier = Modifier.padding(20.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(20.dp))
        WiboxButton(stringResource(R.string.save), { navigator.back() }, tag = "unlock_settings_save")
    }
}

@Composable
fun DeviceInfoScreen(
    deviceId: String,
    viewModel: DeviceDetailViewModel,
    navigator: AppNavigator,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val device = state.device
    var name by remember(device?.name) { mutableStateOf(device?.name.orEmpty()) }
    LaunchedEffect(deviceId) { viewModel.load(deviceId) }
    LaunchedEffect(state.deviceInfoSaved) {
        if (state.deviceInfoSaved) {
            viewModel.consumeDeviceInfoSaved()
            navigator.back()
        }
    }
    WiboxScreen("screen_device_info") {
        WiboxTopBar(stringResource(R.string.device_info), onBack = { navigator.back() })
        LocalDemoBanner()
        if (device == null) {
            EmptyContent(stringResource(R.string.device_not_found))
        } else {
            Column(modifier = Modifier.padding(20.dp)) {
                WiboxTextField(name, { name = it }, stringResource(R.string.device_name), tag = "device_info_name")
                Spacer(Modifier.height(12.dp))
                WiboxTextField(device.id, {}, stringResource(R.string.uid), enabled = false, readOnly = true, tag = "device_info_uid")
                Spacer(Modifier.height(20.dp))
                WiboxButton(
                    text = stringResource(R.string.save),
                    onClick = { viewModel.renameDevice(name) },
                    enabled = name.isNotBlank() && name != device.name,
                    tag = "device_info_save",
                )
            }
        }
    }
}

@Composable
fun UpgradeScreen(
    deviceId: String,
    viewModel: DeviceDetailViewModel,
    navigator: AppNavigator,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val device = state.device
    LaunchedEffect(deviceId) { viewModel.load(deviceId) }
    WiboxScreen("screen_upgrade") {
        WiboxTopBar(stringResource(R.string.firmware_version), onBack = { navigator.back() })
        LocalDemoBanner()
        if (device == null) {
            EmptyContent(stringResource(R.string.device_not_found))
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                WiboxOptionRow(
                    text = stringResource(R.string.current_version_value, device.firmwareCurrent),
                    onClick = {},
                    enabled = false,
                    topMargin = 5.dp,
                    trailing = {},
                )
                WiboxOptionRow(
                    text = stringResource(R.string.latest_version_value, device.firmwareLatest),
                    onClick = {},
                    enabled = false,
                    topMargin = 10.dp,
                    trailing = {},
                )
                Spacer(Modifier.weight(1f))
                if (state.queryingFirmware) {
                    CircularProgressIndicator(color = WiboxColors.selected, modifier = Modifier.align(Alignment.CenterHorizontally))
                } else {
                    Text(
                        text = stringResource(R.string.firmware_up_to_date),
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                        color = WiboxColors.selected,
                    )
                }
                Spacer(Modifier.height(20.dp))
                WiboxButton(stringResource(R.string.check_again), viewModel::checkFirmware, tag = "firmware_check")
                Spacer(Modifier.height(10.dp))
                WiboxButton(
                    text = stringResource(R.string.upgrade_unsupported),
                    onClick = {},
                    enabled = false,
                    tag = "firmware_upgrade_disabled",
                )
                Spacer(Modifier.height(30.dp))
            }
        }
    }
}

@Composable
fun PushCallScreen(
    callId: String,
    viewModel: CallViewModel,
    navigator: AppNavigator,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(callId) { viewModel.start(callId) }
    LaunchedEffect(state.status) {
        if (state.status in setOf(
                CallStatus.ACCEPTED,
                CallStatus.REJECTED,
                CallStatus.AUTO_REFUSED,
                CallStatus.ANSWERED_ELSEWHERE,
                CallStatus.FAILED,
            )
        ) {
            delay(900)
            navigator.back()
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .testTag("screen_push_call"),
    ) {
        Image(
            painter = painterResource(R.drawable.backgroud_calling),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))
            Image(
                painter = painterResource(R.drawable.ico_logo),
                contentDescription = stringResource(R.string.app_name),
                modifier = Modifier.size(80.dp),
            )
            Text(
                text = state.device?.name ?: stringResource(R.string.unknown_device),
                modifier = Modifier.padding(top = 20.dp),
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
            if (state.status == CallStatus.RINGING) {
                Text(
                    text = stringResource(R.string.local_call_ringing, state.remainingSeconds),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            Spacer(Modifier.weight(3f))
            when (state.status) {
                CallStatus.RINGING -> {
                    Row(
                        modifier = Modifier.fillMaxWidth().height(80.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = viewModel::refuse, modifier = Modifier.size(72.dp).testTag("call_refuse")) {
                            Image(painterResource(R.drawable.btn_refuse), contentDescription = stringResource(R.string.refuse_call))
                        }
                        IconButton(onClick = viewModel::accept, modifier = Modifier.size(72.dp).testTag("call_accept")) {
                            Image(painterResource(R.drawable.btn_accept), contentDescription = stringResource(R.string.accept_call))
                        }
                    }
                }
                CallStatus.ACCEPT_PENDING, CallStatus.REJECT_PENDING -> {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(60.dp).testTag("call_pending"))
                }
                else -> {}
            }
            Spacer(Modifier.weight(1f))
        }
    }
}
