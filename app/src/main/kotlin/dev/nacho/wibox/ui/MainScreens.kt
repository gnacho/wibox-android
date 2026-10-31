package dev.nacho.wibox.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.nacho.wibox.R
import dev.nacho.wibox.core.cloud.ActionType
import dev.nacho.wibox.core.cloud.DevicePresence
import dev.nacho.wibox.core.cloud.DevicePermission
import dev.nacho.wibox.core.cloud.DeviceRole
import dev.nacho.wibox.core.cloud.ProviderEventType
import dev.nacho.wibox.data.AppScenario
import dev.nacho.wibox.data.MediaKind

@Composable
fun MainScreen(
    sessionViewModel: SessionViewModel,
    devicesViewModel: DevicesViewModel,
    eventsViewModel: EventsViewModel,
    galleryViewModel: GalleryViewModel,
    settingsViewModel: SettingsViewModel,
    navigator: AppNavigator,
    onExit: () -> Unit,
) {
    val sessionState by sessionViewModel.state.collectAsStateWithLifecycle()
    val devicesState by devicesViewModel.state.collectAsStateWithLifecycle()
    val eventsState by eventsViewModel.state.collectAsStateWithLifecycle()
    val galleryState by galleryViewModel.state.collectAsStateWithLifecycle()
    val settingsState by settingsViewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(sessionViewModel) { navigator.setMainTabHandler(sessionViewModel::selectTab) }
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().background(WiboxColors.primary).safeDrawingPadding(),
    ) {
        val expanded = maxWidth >= 600.dp
        Row(modifier = Modifier.fillMaxSize().background(WiboxColors.background)) {
            if (expanded) {
                WiboxNavigationRail(sessionState.selectedTab, sessionViewModel::selectTab)
            }
            Column(modifier = Modifier.weight(1f)) {
                val title = when {
                    galleryState.editing -> stringResource(R.string.selected_count, galleryState.selected.size)
                    eventsState.editing -> stringResource(R.string.selected_count, eventsState.selected.size)
                    else -> tabLabel(sessionState.selectedTab)
                }
                WiboxTopBar(
                    title = title,
                    actions = {
                        when (sessionState.selectedTab) {
                            MainTab.DEVICES -> {
                                SmallIconButton(
                                    painter = R.drawable.ic_refresh,
                                    contentDescription = stringResource(R.string.refresh),
                                    onClick = devicesViewModel::refresh,
                                    enabled = !devicesState.devices.refreshing,
                                    tag = "refresh_devices",
                                    tint = Color.White,
                                )
                                SmallIconButton(
                                    painter = R.drawable.ic_add,
                                    contentDescription = stringResource(R.string.add_device),
                                    onClick = { navigator.navigateInventory("add_type") },
                                    tag = "add_device_button",
                                    tint = Color.White,
                                )
                            }
                            MainTab.EVENTS -> {
                                SmallIconButton(
                                    painter = if (eventsState.editing) R.drawable.ic_close else R.drawable.ic_edit,
                                    contentDescription = stringResource(R.string.edit),
                                    onClick = eventsViewModel::toggleEditing,
                                    tag = "edit_events",
                                    tint = Color.White,
                                )
                            }
                            MainTab.GALLERY -> {
                                SmallIconButton(
                                    painter = if (galleryState.editing) R.drawable.ic_close else R.drawable.ic_edit,
                                    contentDescription = stringResource(R.string.edit),
                                    onClick = galleryViewModel::toggleEditing,
                                    tag = "edit_gallery",
                                    tint = Color.White,
                                )
                            }
                            MainTab.SETTINGS -> Unit
                        }
                    },
                )
                LocalDemoBanner()
                Box(modifier = Modifier.weight(1f)) {
                    when (sessionState.selectedTab) {
                        MainTab.DEVICES -> DevicesScreen(devicesState, devicesViewModel, navigator)
                        MainTab.EVENTS -> EventsScreen(eventsState, devicesState, eventsViewModel, navigator)
                        MainTab.GALLERY -> GalleryScreen(galleryState, galleryViewModel, navigator)
                        MainTab.SETTINGS -> SettingsScreen(settingsState, devicesState, settingsViewModel, navigator, onExit)
                    }
                }
                when {
                    eventsState.editing -> SelectionBottomBar(
                        onSelectAll = eventsViewModel::selectAll,
                        onDelete = eventsViewModel::deleteSelected,
                        deleteEnabled = eventsState.selected.isNotEmpty(),
                        showSave = false,
                        showShare = false,
                    )
                    galleryState.editing -> SelectionBottomBar(
                        onSelectAll = galleryViewModel::selectAll,
                        onDelete = galleryViewModel::deleteSelected,
                        deleteEnabled = galleryState.selected.isNotEmpty(),
                        onSave = { settingsViewModel.showNotice(UserNotice.SAVED_LOCAL) },
                        onShare = { settingsViewModel.showNotice(UserNotice.MEDIA_EXPORT_UNSUPPORTED) },
                    )
                    !expanded -> WiboxBottomNavigation(sessionState.selectedTab, sessionViewModel::selectTab)
                }
            }
        }
    }
}

@Composable
private fun DevicesScreen(
    state: DevicesUiState,
    viewModel: DevicesViewModel,
    navigator: AppNavigator,
) {
    when (val devices = state.devices) {
        is LoadState.Loading -> LoadingContent()
        is LoadState.Error -> ErrorContent(devices.error, viewModel::refresh)
        is LoadState.Content -> {
            if (devices.value.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(role = Role.Button, onClick = { navigator.navigateInventory("add_type") })
                        .testTag("empty_add_device"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_add_circle),
                        contentDescription = stringResource(R.string.add_device),
                        tint = WiboxColors.primary,
                        modifier = Modifier.size(88.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.add_device), style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().testTag("device_list"),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 5.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(devices.value, key = { it.id }) { device ->
                        DeviceCard(
                            device = device,
                            actionInProgress = state.action.pendingCommandId,
                            onPreview = { navigator.navigateInventory(AppDestinationRequest("preview", deviceId = device.id)) },
                            onConfig = { navigator.navigateInventory(AppDestinationRequest("config", deviceId = device.id)) },
                            onAction = { action -> viewModel.performAction(device.id, action) },
                        )
                    }
                    item { ActionOutcomeText(state.action) }
                }
            }
        }
    }
}

@Composable
private fun DeviceCard(
    device: DeviceUi,
    actionInProgress: String?,
    onPreview: () -> Unit,
    onConfig: () -> Unit,
    onAction: (ActionType) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .testTag("device_card_${device.id}"),
    ) {
        Row(
            modifier = Modifier.height(40.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(if (device.presence == DevicePresence.ONLINE) R.drawable.device_status_online else R.drawable.device_status_offline),
                contentDescription = stringResource(if (device.presence == DevicePresence.ONLINE) R.string.online else R.string.offline),
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = device.name,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                color = Color.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (device.role == DeviceRole.SHARED) {
                Text(stringResource(R.string.shared_device), style = MaterialTheme.typography.bodySmall)
            }
        }
        Image(
            painter = painterResource(R.drawable.device_cover_default),
            contentDescription = stringResource(R.string.cd_device_preview, device.name),
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clickable(role = Role.Button, onClick = onPreview)
                .testTag("device_preview_${device.id}"),
            contentScale = ContentScale.Crop,
        )
        Row(
            modifier = Modifier.fillMaxWidth().height(56.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SmallIconButton(
                painter = R.drawable.ic_settings,
                contentDescription = stringResource(R.string.device_settings),
                onClick = onConfig,
                tag = "device_config_${device.id}",
            )
            SmallIconButton(
                painter = R.drawable.ic_call,
                contentDescription = stringResource(R.string.concierge_call),
                onClick = { onAction(ActionType.CALL_GUARD) },
                enabled = device.presence == DevicePresence.ONLINE,
                tag = "device_guard_${device.id}",
            )
            SmallIconButton(
                painter = R.drawable.ic_f1,
                contentDescription = stringResource(R.string.f1_activation),
                onClick = { onAction(ActionType.F1) },
                enabled = device.presence == DevicePresence.ONLINE && DevicePermission.F1 in device.permissions && actionInProgress == null,
                tag = "device_f1_${device.id}",
            )
            if (actionInProgress?.startsWith("${device.id}:${ActionType.UNLOCK.name}") == true) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp), color = WiboxColors.selected)
                Spacer(Modifier.size(20.dp))
            } else {
                SmallIconButton(
                    painter = R.drawable.ic_lock_open,
                    contentDescription = stringResource(R.string.unlock),
                    onClick = { onAction(ActionType.UNLOCK) },
                    enabled = device.presence == DevicePresence.ONLINE && DevicePermission.UNLOCK in device.permissions && actionInProgress == null,
                    tag = "action_unlock_${device.id}",
                )
            }
        }
    }
}

@Composable
private fun EventsScreen(
    state: EventsUiState,
    devices: DevicesUiState,
    viewModel: EventsViewModel,
    navigator: AppNavigator,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.background(Color.White)) {
            Text(
                text = stringResource(R.string.all_activity),
                modifier = Modifier.fillMaxWidth().height(25.dp),
                color = WiboxColors.selected,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )
            Box(modifier = Modifier.fillMaxWidth().height(3.dp).background(WiboxColors.selected))
        }
        when (val events = state.events) {
            is LoadState.Loading -> LoadingContent()
            is LoadState.Error -> ErrorContent(events.error, viewModel::refresh)
            is LoadState.Content -> {
                if (events.value.isEmpty()) {
                    EmptyContent(stringResource(R.string.empty_events))
                } else {
                    val visible = events.value.take(state.visibleCount)
                    LazyColumn(modifier = Modifier.fillMaxSize().testTag("event_list")) {
                        item {
                            Text(
                                text = stringResource(R.string.events_photo_notice),
                                modifier = Modifier.fillMaxWidth().background(Color.White).padding(10.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = WiboxColors.secondaryText,
                            )
                        }
                        items(visible, key = { it.id }) { event ->
                            EventRow(
                                event = event,
                                deviceName = devices.devices.data.orEmpty().firstOrNull { it.id == event.deviceId }?.name
                                    ?: stringResource(R.string.unknown_device),
                                editing = state.editing,
                                selected = event.id in state.selected,
                                onSelect = { viewModel.toggleSelection(event.id) },
                                onClick = {
                                    when {
                                        state.editing -> viewModel.toggleSelection(event.id)
                                        event.type == ProviderEventType.INCOMING_CALL -> navigator.navigateInventory(
                                            AppDestinationRequest("push_call", deviceId = event.deviceId),
                                        )
                                        event.mediaId != null -> navigator.navigateInventory(
                                            AppDestinationRequest("image_pager", mediaId = event.mediaId),
                                        )
                                    }
                                },
                            )
                        }
                        if (events.value.size > visible.size) {
                            item {
                                WiboxButton(
                                    text = stringResource(R.string.load_more),
                                    onClick = viewModel::loadMore,
                                    modifier = Modifier.padding(vertical = 12.dp),
                                    tag = "load_more_events",
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EventRow(
    event: EventUi,
    deviceName: String,
    editing: Boolean,
    selected: Boolean,
    onSelect: () -> Unit,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(55.dp)
            .background(if (selected) WiboxColors.pressed else Color.White)
            .clickable(role = Role.Button, onClick = onClick)
            .testTag("event_${event.id}")
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (editing) {
            Checkbox(checked = selected, onCheckedChange = { onSelect() })
        } else {
            Icon(
                painter = painterResource(
                    when (event.type) {
                        ProviderEventType.INCOMING_CALL, ProviderEventType.CALL_ANSWERED -> R.drawable.ic_call
                        ProviderEventType.MOTION -> R.drawable.ic_motion
                        ProviderEventType.UNKNOWN -> R.drawable.ic_photo
                    },
                ),
                contentDescription = null,
                tint = WiboxColors.selected,
                modifier = Modifier.size(25.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "$deviceName (${event.channel})",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = formatTimestamp(event.timestampMillis),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
            )
        }
        if (!editing) {
            Icon(
                painter = painterResource(if (event.type == ProviderEventType.INCOMING_CALL) R.drawable.ic_call else R.drawable.ic_photo),
                contentDescription = stringResource(if (event.type == ProviderEventType.INCOMING_CALL) R.string.answer_call else R.string.open_media),
                tint = WiboxColors.text,
                modifier = Modifier.size(26.dp).testTag("event_open_${event.id}"),
            )
        }
    }
}

@Composable
private fun GalleryScreen(
    state: GalleryUiState,
    viewModel: GalleryViewModel,
    navigator: AppNavigator,
) {
    when (val media = state.media) {
        is LoadState.Loading -> LoadingContent()
        is LoadState.Error -> ErrorContent(media.error, viewModel::refresh)
        is LoadState.Content -> {
            if (media.value.isEmpty()) {
                EmptyContent(stringResource(R.string.empty_gallery))
            } else {
                val groups = media.value.sortedByDescending { it.timestampMillis }.groupBy { formatDay(it.timestampMillis) }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize().testTag("gallery_grid"),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    groups.forEach { (day, itemsForDay) ->
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                text = day,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                        items(itemsForDay, key = { it.id }) { item ->
                            Box(
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .clickable {
                                        if (state.editing) viewModel.toggleSelection(item.id) else navigator.navigateInventory(AppDestinationRequest("image_pager", mediaId = item.id))
                                    }
                                    .testTag("media_${item.id}"),
                            ) {
                                DemoMediaArtwork(item, modifier = Modifier.fillMaxSize())
                                if (item.kind == MediaKind.VIDEO) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_play),
                                        contentDescription = stringResource(R.string.video),
                                        tint = Color.White,
                                        modifier = Modifier.align(Alignment.BottomEnd).padding(5.dp).size(22.dp),
                                    )
                                }
                                if (state.editing) {
                                    Checkbox(
                                        checked = item.id in state.selected,
                                        onCheckedChange = { viewModel.toggleSelection(item.id) },
                                        modifier = Modifier.align(Alignment.TopEnd),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    state: SettingsUiState,
    devices: DevicesUiState,
    viewModel: SettingsViewModel,
    navigator: AppNavigator,
    onExit: () -> Unit,
) {
    var showScenario by remember { mutableStateOf(false) }
    var showReset by remember { mutableStateOf(false) }
    var showExit by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .testTag("settings_content"),
    ) {
        Text(
            text = stringResource(R.string.call_reception),
            modifier = Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 20.dp, vertical = 14.dp),
            style = MaterialTheme.typography.bodyLarge,
        )
        WiboxSwitchRow(
            text = stringResource(R.string.sound),
            checked = state.preferences.sound,
            onCheckedChange = viewModel::setSound,
            tag = "settings_sound",
        )
        WiboxSwitchRow(
            text = stringResource(R.string.vibration),
            checked = state.preferences.vibration,
            onCheckedChange = viewModel::setVibration,
            tag = "settings_vibration",
        )
        WiboxOptionRow(stringResource(R.string.about), { navigator.navigateInventory("about") }, tag = "about_button", topMargin = 15.dp)
        Text(
            text = stringResource(R.string.local_demo_title),
            modifier = Modifier.padding(start = 20.dp, top = 24.dp, bottom = 8.dp),
            style = MaterialTheme.typography.titleMedium,
            color = WiboxColors.selected,
        )
        Text(
            text = stringResource(R.string.local_demo_explanation),
            modifier = Modifier.padding(horizontal = 20.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
        WiboxOptionRow(
            text = stringResource(R.string.demo_scenario, scenarioLabel(state.preferences.scenario)),
            onClick = { showScenario = true },
            tag = "scenario_selector",
            topMargin = 15.dp,
        )
        WiboxOptionRow(
            text = stringResource(R.string.simulate_incoming_call),
            onClick = {
                devices.devices.data.orEmpty().firstOrNull { it.presence == DevicePresence.ONLINE }?.let { viewModel.simulateIncomingCall(it.id) }
            },
            enabled = devices.devices.data.orEmpty().any { it.presence == DevicePresence.ONLINE },
            tag = "simulate_call",
        )
        WiboxOptionRow(
            text = stringResource(R.string.reset_demo),
            onClick = { showReset = true },
            tag = "reset_demo_button",
            trailing = {
                Icon(painterResource(R.drawable.ic_refresh), contentDescription = null, tint = WiboxColors.text)
            },
        )
        Spacer(Modifier.height(24.dp))
        WiboxButton(stringResource(R.string.exit), { showExit = true }, tag = "exit_button")
        Spacer(Modifier.height(24.dp))
    }
    if (showScenario) {
        AlertDialog(
            onDismissRequest = { showScenario = false },
            title = { Text(stringResource(R.string.select_demo_scenario)) },
            text = {
                Column {
                    AppScenario.entries.forEach { scenario ->
                        TextButton(
                            onClick = {
                                viewModel.setScenario(scenario)
                                showScenario = false
                            },
                            modifier = Modifier.fillMaxWidth().testTag("scenario_${scenario.name}"),
                        ) {
                            Text(scenarioLabel(scenario), modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showScenario = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
    if (showReset) {
        ConfirmDialog(
            title = stringResource(R.string.reset_demo),
            text = stringResource(R.string.reset_demo_confirmation),
            confirmText = stringResource(R.string.reset),
            onConfirm = {
                viewModel.resetDemo()
                showReset = false
            },
            onDismiss = { showReset = false },
        )
    }
    if (showExit) {
        ConfirmDialog(
            title = stringResource(R.string.exit),
            text = stringResource(R.string.exit_confirmation),
            confirmText = stringResource(R.string.exit),
            onConfirm = onExit,
            onDismiss = { showExit = false },
        )
    }
}

@Composable
private fun SelectionBottomBar(
    onSelectAll: () -> Unit,
    onDelete: () -> Unit,
    deleteEnabled: Boolean,
    showSave: Boolean = true,
    showShare: Boolean = true,
    onSave: () -> Unit = {},
    onShare: () -> Unit = {},
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(55.dp).background(WiboxColors.primary),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SmallIconButton(
            painter = R.drawable.ic_select_all,
            contentDescription = stringResource(R.string.select_all),
            onClick = onSelectAll,
            tag = "select_all",
            tint = Color.White,
        )
        if (showSave) {
            SmallIconButton(
                painter = R.drawable.ic_save,
                contentDescription = stringResource(R.string.save),
                onClick = onSave,
                tag = "save_selection",
                tint = Color.White,
            )
        }
        if (showShare) {
            SmallIconButton(
                painter = R.drawable.ic_share,
                contentDescription = stringResource(R.string.share),
                onClick = onShare,
                tag = "share_selection",
                tint = Color.White,
            )
        }
        SmallIconButton(
            painter = R.drawable.ic_delete,
            contentDescription = stringResource(R.string.delete),
            onClick = onDelete,
            enabled = deleteEnabled,
            tag = "delete_selection",
            tint = Color.White,
        )
    }
}

@Composable
fun scenarioLabel(scenario: AppScenario): String = when (scenario) {
    AppScenario.NORMAL -> stringResource(R.string.scenario_normal)
    AppScenario.LOADING -> stringResource(R.string.scenario_loading)
    AppScenario.EMPTY -> stringResource(R.string.scenario_empty)
    AppScenario.ERROR -> stringResource(R.string.scenario_error)
    AppScenario.UNSUPPORTED -> stringResource(R.string.scenario_unsupported)
    AppScenario.OFFLINE -> stringResource(R.string.offline)
    AppScenario.PERMISSION -> stringResource(R.string.permission_required)
    AppScenario.ACTION_PENDING -> stringResource(R.string.action_pending)
    AppScenario.ACTION_FAILURE -> stringResource(R.string.action_failed)
}
