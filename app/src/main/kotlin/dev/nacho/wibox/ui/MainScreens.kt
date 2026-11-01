package dev.nacho.wibox.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.nacho.wibox.R
import dev.nacho.wibox.core.cloud.ActionType
import dev.nacho.wibox.core.cloud.DevicePresence
import dev.nacho.wibox.core.cloud.DevicePermission
import dev.nacho.wibox.core.cloud.DeviceRole
import dev.nacho.wibox.core.cloud.ProviderEventType
import dev.nacho.wibox.data.AppScenario
import dev.nacho.wibox.data.MediaKind
import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext

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
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding(),
    ) {
        val expanded = maxWidth >= 600.dp
        Row(modifier = Modifier.fillMaxSize()) {
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
                                )
                                SmallIconButton(
                                    painter = R.drawable.ic_add,
                                    contentDescription = stringResource(R.string.add_device),
                                    onClick = { navigator.navigateInventory("add_type") },
                                    tag = "add_device_button",
                                )
                            }
                            MainTab.EVENTS -> {
                                SmallIconButton(
                                    painter = if (eventsState.editing) R.drawable.ic_close else R.drawable.ic_edit,
                                    contentDescription = stringResource(R.string.edit),
                                    onClick = eventsViewModel::toggleEditing,
                                    tag = "edit_events",
                                )
                            }
                            MainTab.GALLERY -> {
                                SmallIconButton(
                                    painter = if (galleryState.editing) R.drawable.ic_close else R.drawable.ic_edit,
                                    contentDescription = stringResource(R.string.edit),
                                    onClick = galleryViewModel::toggleEditing,
                                    tag = "edit_gallery",
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
                    eventsState.editing -> SelectionBar(
                        onSelectAll = eventsViewModel::selectAll,
                        onDelete = eventsViewModel::deleteSelected,
                        deleteEnabled = eventsState.selected.isNotEmpty(),
                    )
                    galleryState.editing -> SelectionBar(
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
    val context = LocalContext.current
    val keyguard = remember(context) { context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager }
    var pendingAction by remember { mutableStateOf<PendingAction?>(null) }
    val credentialLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val pending = pendingAction
        pendingAction = null
        if (pending != null && result.resultCode == Activity.RESULT_OK) {
            viewModel.performAction(pending.deviceId, pending.type)
        }
    }
    val requestAction: (String, ActionType) -> Unit = { deviceId, action ->
        val gated = action == ActionType.UNLOCK || action == ActionType.F1
        @Suppress("DEPRECATION")
        val intent = if (state.biometricUnlock && gated) {
            keyguard?.createConfirmDeviceCredentialIntent(null, null)
        } else {
            null
        }
        if (intent != null) {
            pendingAction = PendingAction(deviceId, action)
            credentialLauncher.launch(intent)
        } else {
            viewModel.performAction(deviceId, action)
        }
    }
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
                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_add_circle),
                            contentDescription = stringResource(R.string.add_device),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(48.dp),
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        stringResource(R.string.add_device),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth().testTag("device_list"),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(devices.value, key = { it.id }) { device ->
                            DeviceCard(
                                device = device,
                                actionInProgress = state.action.pendingCommandId,
                                onPreview = { navigator.navigateInventory(AppDestinationRequest("preview", deviceId = device.id)) },
                                onConfig = { navigator.navigateInventory(AppDestinationRequest("config", deviceId = device.id)) },
                                onAction = { action -> requestAction(device.id, action) },
                            )
                        }
                    }
                    ActionOutcomeText(
                        action = state.action,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
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
    val online = device.presence == DevicePresence.ONLINE
    ElevatedCard(
        modifier = Modifier.fillMaxWidth().testTag("device_card_${device.id}"),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    painter = painterResource(if (online) R.drawable.device_status_online else R.drawable.device_status_offline),
                    contentDescription = stringResource(if (online) R.string.online else R.string.offline),
                    modifier = Modifier.size(26.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = device.name,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (device.role == DeviceRole.SHARED) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                    ) {
                        Text(
                            text = stringResource(R.string.shared_device),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                }
            }
            val darkSurface = MaterialTheme.colorScheme.surface.luminance() < 0.5f
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .padding(horizontal = 12.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .clickable(role = Role.Button, onClick = onPreview)
                    .testTag("device_preview_${device.id}"),
            ) {
                Image(
                    painter = painterResource(R.drawable.device_cover_default),
                    contentDescription = stringResource(R.string.cd_device_preview, device.name),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    alpha = if (darkSurface) 0.32f else 1f,
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                WiboxCircleAction(
                    painter = R.drawable.ic_settings,
                    contentDescription = stringResource(R.string.device_settings),
                    onClick = onConfig,
                    tag = "device_config_${device.id}",
                )
                WiboxCircleAction(
                    painter = R.drawable.ic_call,
                    contentDescription = stringResource(R.string.concierge_call),
                    onClick = { onAction(ActionType.CALL_GUARD) },
                    enabled = online,
                    tag = "device_guard_${device.id}",
                )
                WiboxCircleAction(
                    painter = R.drawable.ic_f1,
                    contentDescription = stringResource(R.string.f1_activation),
                    onClick = { onAction(ActionType.F1) },
                    enabled = online && DevicePermission.F1 in device.permissions && actionInProgress == null,
                    tag = "device_f1_${device.id}",
                )
                if (actionInProgress?.startsWith("${device.id}:${ActionType.UNLOCK.name}") == true) {
                    Box(modifier = Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp))
                    }
                } else {
                    WiboxCircleAction(
                        painter = R.drawable.ic_lock_open,
                        contentDescription = stringResource(R.string.unlock),
                        onClick = { onAction(ActionType.UNLOCK) },
                        enabled = online && DevicePermission.UNLOCK in device.permissions && actionInProgress == null,
                        tag = "action_unlock_${device.id}",
                        primary = true,
                    )
                }
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
        Text(
            text = stringResource(R.string.all_activity),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        when (val events = state.events) {
            is LoadState.Loading -> LoadingContent()
            is LoadState.Error -> ErrorContent(events.error, viewModel::refresh)
            is LoadState.Content -> {
                if (events.value.isEmpty()) {
                    EmptyContent(stringResource(R.string.empty_events))
                } else {
                    val visible = events.value.take(state.visibleCount)
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().testTag("event_list"),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item {
                            Text(
                                text = stringResource(R.string.events_photo_notice),
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clickable(role = Role.Button, onClick = onClick)
                .testTag("event_${event.id}")
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (editing) {
                Checkbox(checked = selected, onCheckedChange = { onSelect() })
            } else {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(
                            when (event.type) {
                                ProviderEventType.INCOMING_CALL, ProviderEventType.CALL_ANSWERED -> R.drawable.ic_call
                                ProviderEventType.MOTION -> R.drawable.ic_motion
                                ProviderEventType.UNKNOWN -> R.drawable.ic_photo
                            },
                        ),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "$deviceName (${event.channel})",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = formatTimestamp(event.timestampMillis),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            if (!editing) {
                Icon(
                    painter = painterResource(if (event.type == ProviderEventType.INCOMING_CALL) R.drawable.ic_call else R.drawable.ic_photo),
                    contentDescription = stringResource(if (event.type == ProviderEventType.INCOMING_CALL) R.string.answer_call else R.string.open_media),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp).testTag("event_open_${event.id}"),
                )
            }
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
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    groups.forEach { (day, itemsForDay) ->
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                text = day,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        items(itemsForDay, key = { it.id }) { item ->
                            Box(
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .clip(MaterialTheme.shapes.medium)
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
                                        modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp).size(22.dp),
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
private fun SettingsSectionHeader(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 4.dp),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
    )
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
        SettingsSectionHeader(stringResource(R.string.call_reception))
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
        WiboxOptionRow(stringResource(R.string.about), { navigator.navigateInventory("about") }, tag = "about_button", topMargin = 10.dp)
        SettingsSectionHeader(stringResource(R.string.local_demo_title))
        Text(
            text = stringResource(R.string.local_demo_explanation),
            modifier = Modifier.padding(horizontal = 20.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        WiboxOptionRow(
            text = stringResource(R.string.demo_scenario, scenarioLabel(state.preferences.scenario)),
            onClick = { showScenario = true },
            tag = "scenario_selector",
            topMargin = 10.dp,
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
                Icon(painterResource(R.drawable.ic_refresh), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
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

private data class PendingAction(val deviceId: String, val type: ActionType)
