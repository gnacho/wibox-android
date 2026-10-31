package dev.nacho.wibox.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.nacho.wibox.R
import dev.nacho.wibox.core.cloud.ActionAcknowledgementLevel
import dev.nacho.wibox.core.cloud.ProviderError
import dev.nacho.wibox.data.MediaKind
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun WiboxScreen(
    tag: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(WiboxColors.primary)
            .safeDrawingPadding()
            .testTag(tag),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().background(WiboxColors.background),
            content = content,
        )
    }
}

@Composable
fun WiboxTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    backContentDescription: String = stringResource(R.string.cd_back),
    actions: @Composable RowScope.() -> Unit = {},
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(60.dp)
            .background(WiboxColors.primary),
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack, modifier = Modifier.size(60.dp).testTag("top_back")) {
                    Icon(
                        painter = painterResource(R.drawable.ic_back),
                        contentDescription = backContentDescription,
                        tint = WiboxColors.onPrimary,
                        modifier = Modifier.size(28.dp),
                    )
                }
            } else {
                Spacer(Modifier.width(60.dp))
            }
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                color = WiboxColors.onPrimary,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier.widthIn(min = 60.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
                content = actions,
            )
        }
    }
}

@Composable
fun WiboxBottomNavigation(
    selected: MainTab,
    onSelect: (MainTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(55.dp)
            .background(WiboxColors.primary),
    ) {
        MainTab.entries.forEach { tab ->
            val label = tabLabel(tab)
            val selectedTab = tab == selected
            Column(
                modifier = Modifier
                    .weight(1f)
                    .height(55.dp)
                    .clickable(
                        role = Role.Tab,
                        onClick = { onSelect(tab) },
                    )
                    .semantics {
                        this.selected = selectedTab
                        contentDescription = label
                    }
                    .testTag("tab_${tab.name.lowercase()}"),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Image(
                    painter = painterResource(tabIcon(tab, selectedTab)),
                    contentDescription = null,
                    modifier = Modifier.size(29.dp),
                )
                Text(
                    text = label,
                    color = if (selectedTab) WiboxColors.onPrimary else WiboxColors.inactiveTab,
                    fontSize = 12.sp,
                    lineHeight = 13.sp,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
fun WiboxNavigationRail(
    selected: MainTab,
    onSelect: (MainTab) -> Unit,
) {
    Column(
        modifier = Modifier
            .width(104.dp)
            .fillMaxSize()
            .background(WiboxColors.primary)
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        MainTab.entries.forEach { tab ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .clickable(role = Role.Tab) { onSelect(tab) }
                    .semantics { this.selected = tab == selected }
                    .testTag("rail_${tab.name.lowercase()}"),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Image(
                    painter = painterResource(tabIcon(tab, tab == selected)),
                    contentDescription = null,
                    modifier = Modifier.size(30.dp),
                )
                Text(tabLabel(tab), color = if (tab == selected) WiboxColors.onPrimary else WiboxColors.inactiveTab, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun tabLabel(tab: MainTab): String = when (tab) {
    MainTab.DEVICES -> stringResource(R.string.device)
    MainTab.EVENTS -> stringResource(R.string.events)
    MainTab.GALLERY -> stringResource(R.string.gallery)
    MainTab.SETTINGS -> stringResource(R.string.settings)
}

private fun tabIcon(tab: MainTab, selected: Boolean): Int = when (tab) {
    MainTab.DEVICES -> if (selected) R.drawable.main_btn_device_select else R.drawable.main_btn_device
    MainTab.EVENTS -> if (selected) R.drawable.main_btn_call_log_select else R.drawable.main_btn_call_log
    MainTab.GALLERY -> if (selected) R.drawable.main_btn_file_select else R.drawable.main_btn_file
    MainTab.SETTINGS -> if (selected) R.drawable.main_btn_setting_select else R.drawable.main_btn_setting
}

@Composable
fun ContentFrame(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 680.dp),
            content = content,
        )
    }
}

@Composable
fun WiboxButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tag: String? = null,
    primary: Boolean = false,
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .padding(horizontal = 30.dp)
            .fillMaxWidth()
            .heightIn(min = 50.dp)
            .then(if (tag == null) Modifier else Modifier.testTag(tag)),
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (primary) WiboxColors.primary else WiboxColors.surface,
            contentColor = if (primary) WiboxColors.onPrimary else WiboxColors.text,
            disabledContainerColor = WiboxColors.disabled,
            disabledContentColor = WiboxColors.secondaryText,
        ),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 2, textAlign = TextAlign.Center)
    }
}

@Composable
fun WiboxOptionRow(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tag: String? = null,
    topMargin: androidx.compose.ui.unit.Dp = 2.dp,
    trailing: @Composable RowScope.() -> Unit = {
        Icon(
            painter = painterResource(R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = WiboxColors.secondaryText,
        )
    },
) {
    Row(
        modifier = modifier
            .padding(top = topMargin)
            .fillMaxWidth()
            .heightIn(min = 50.dp)
            .background(if (enabled) WiboxColors.surface else WiboxColors.disabled)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .then(if (tag == null) Modifier else Modifier.testTag(tag))
            .padding(start = 20.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) WiboxColors.text else WiboxColors.secondaryText,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        trailing()
    }
}

@Composable
fun WiboxSwitchRow(
    text: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tag: String? = null,
    topMargin: androidx.compose.ui.unit.Dp = 2.dp,
) {
    val stateText = stringResource(if (checked) R.string.enabled_on else R.string.enabled_off)
    Row(
        modifier = modifier
            .padding(top = topMargin)
            .fillMaxWidth()
            .heightIn(min = 50.dp)
            .background(if (enabled) WiboxColors.surface else WiboxColors.disabled)
            .padding(start = 20.dp, end = 10.dp)
            .then(if (tag == null) Modifier else Modifier.testTag(tag))
            .semantics { this.stateDescription = stateText },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) WiboxColors.text else WiboxColors.secondaryText,
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedTrackColor = WiboxColors.selected,
                checkedThumbColor = Color.White,
                uncheckedTrackColor = WiboxColors.divider,
                uncheckedThumbColor = Color.White,
            ),
        )
    }
}

@Composable
fun WiboxTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    password: Boolean = false,
    tag: String? = null,
    supportingText: String? = null,
    ltr: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .then(if (tag == null) Modifier else Modifier.testTag(tag)),
        enabled = enabled,
        readOnly = readOnly,
        singleLine = true,
        label = { Text(label) },
        supportingText = supportingText?.let { { Text(it) } },
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = WiboxColors.surface,
            unfocusedContainerColor = WiboxColors.surface,
            disabledContainerColor = WiboxColors.disabled,
            focusedBorderColor = WiboxColors.selected,
            unfocusedBorderColor = WiboxColors.divider,
            focusedTextColor = WiboxColors.text,
            unfocusedTextColor = WiboxColors.text,
        ),
        textStyle = if (ltr) {
            MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Ltr)
        } else {
            MaterialTheme.typography.bodyLarge
        },
    )
}

val LocalProviderStatus = staticCompositionLocalOf {
    ProviderStatusUi(label = "LOCAL DEMO", profileVersion = "local-demo", simulated = true)
}

@Composable
fun ProviderStatusBanner(modifier: Modifier = Modifier) {
    val status = LocalProviderStatus.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(WiboxColors.banner)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .testTag("provider_status_banner"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_info),
            contentDescription = null,
            tint = WiboxColors.selected,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = if (status.simulated) {
                stringResource(R.string.provider_demo_banner, status.profileVersion)
            } else {
                stringResource(R.string.provider_stock_banner, status.profileVersion)
            },
            style = MaterialTheme.typography.bodySmall,
            color = WiboxColors.bannerText,
        )
    }
}

@Composable
fun LocalDemoBanner(modifier: Modifier = Modifier) = ProviderStatusBanner(modifier)

@Composable
fun LoadingContent(label: String = stringResource(R.string.loading)) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp)
            .testTag("loading_content"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(color = WiboxColors.selected)
        Spacer(Modifier.height(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
    }
}

@Composable
fun EmptyContent(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 45.dp)
            .testTag("empty_content"),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = WiboxColors.text,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun ErrorContent(
    error: ProviderError,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp)
            .testTag("error_content"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = cloudErrorText(error),
            style = MaterialTheme.typography.bodyLarge,
            color = WiboxColors.error,
            textAlign = TextAlign.Center,
        )
        if (onRetry != null) {
            Spacer(Modifier.height(20.dp))
            WiboxButton(stringResource(R.string.retry), onRetry, tag = "retry_button")
        }
    }
}

@Composable
fun cloudErrorText(error: ProviderError): String = when (error) {
    is ProviderError.Unsupported -> stringResource(R.string.unsupported_local)
    is ProviderError.Authorization -> stringResource(R.string.unauthorized)
    is ProviderError.Timeout -> stringResource(R.string.connection_timeout)
    else -> stringResource(R.string.loading_error)
}

@Composable
fun ErrorPanel(
    error: ProviderError?,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    if (error == null) return
    ErrorContent(error, onRetry, modifier)
}

@Composable
fun <T> LoadContent(
    state: LoadState<T>,
    onRetry: () -> Unit,
    emptyText: String,
    content: @Composable (T) -> Unit,
) {
    when {
        state is LoadState.Loading && state.data == null -> LoadingContent()
        state is LoadState.Error && state.data == null -> ErrorPanel(state.error, onRetry)
        state.data != null -> {
            val data = requireNotNull(state.data)
            Column(modifier = Modifier.fillMaxSize()) {
                if (state.stale || state.error != null) {
                    Text(
                        text = stringResource(R.string.stale_content),
                        modifier = Modifier.fillMaxWidth().background(WiboxColors.banner).padding(8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = WiboxColors.bannerText,
                    )
                }
                if (data is Collection<*> && data.isEmpty()) {
                    EmptyContent(emptyText)
                } else {
                    content(data)
                }
            }
        }
        else -> ErrorPanel(ProviderError.Unknown("loadContent"), onRetry)
    }
}

@Composable
fun CapabilityAction(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    pending: Boolean = false,
    tag: String? = null,
) {
    WiboxButton(
        text = if (pending) stringResource(R.string.action_pending) else text,
        onClick = onClick,
        enabled = enabled && !pending,
        tag = tag,
    )
}

@Composable
fun ActionOutcomeText(action: ActionUi, modifier: Modifier = Modifier) {
    val text = when {
        action.pendingCommandId != null -> stringResource(R.string.action_pending)
        action.error is ProviderError.Unsupported -> stringResource(R.string.unsupported_local)
        action.error != null -> stringResource(R.string.action_failed)
        action.outcome == null -> null
        action.outcome.level == ActionAcknowledgementLevel.LOCAL_ACCEPTED -> stringResource(R.string.local_action_ack)
        action.outcome.level == ActionAcknowledgementLevel.PROVIDER_ACCEPTED -> stringResource(R.string.provider_action_ack)
        action.outcome.level == ActionAcknowledgementLevel.DEVICE_ACKNOWLEDGED -> stringResource(R.string.device_action_ack)
        action.outcome.level == ActionAcknowledgementLevel.PHYSICAL_OBSERVED && action.outcome.canClaimPhysicalEffect() ->
            stringResource(R.string.physical_action_observed)
        action.outcome.level == ActionAcknowledgementLevel.REJECTED -> stringResource(R.string.action_rejected)
        else -> stringResource(R.string.action_unknown)
    }
    if (text != null) {
        Text(
            text = text,
            modifier = modifier.fillMaxWidth().padding(12.dp).testTag("action_outcome_text"),
            color = if (action.error != null) WiboxColors.error else WiboxColors.selected,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun PermissionRationale(
    state: PermissionRationaleState,
    onResult: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.permission_required)) },
        text = {
            Text(
                text = when (state.kind) {
                    PermissionKind.CAMERA -> stringResource(R.string.camera_permission_rationale)
                    PermissionKind.LOCATION -> stringResource(R.string.location_permission_rationale)
                    PermissionKind.MICROPHONE -> stringResource(R.string.microphone_permission_rationale)
                    PermissionKind.STORAGE -> stringResource(R.string.storage_permission_rationale)
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            TextButton(onClick = { onResult(true) }, modifier = Modifier.testTag("permission_grant")) {
                Text(stringResource(R.string.continue_action))
            }
        },
        dismissButton = {
            TextButton(onClick = { onResult(false) }, modifier = Modifier.testTag("permission_deny")) {
                Text(stringResource(R.string.not_now))
            }
        },
        containerColor = WiboxColors.surface,
        shape = RoundedCornerShape(16.dp),
    )
}

@Composable
fun PairingProgress(stage: ProvisionStage, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth().testTag("pairing_progress"), horizontalAlignment = Alignment.CenterHorizontally) {
        if (stage !in setOf(ProvisionStage.READY, ProvisionStage.IDLE, ProvisionStage.FAILED, ProvisionStage.CANCELLED)) {
            CircularProgressIndicator(color = WiboxColors.selected)
            Spacer(Modifier.height(12.dp))
        }
        Text(
            text = when (stage) {
                ProvisionStage.IDLE -> stringResource(R.string.waiting)
                ProvisionStage.CONNECTING -> stringResource(R.string.connect_device)
                ProvisionStage.SENDING -> stringResource(R.string.send_data)
                ProvisionStage.WAITING -> stringResource(R.string.waiting_device_online)
                ProvisionStage.BINDING -> stringResource(R.string.binding_local)
                ProvisionStage.READY -> stringResource(R.string.local_pairing_ready)
                ProvisionStage.FAILED -> stringResource(R.string.pairing_failed)
                ProvisionStage.CANCELLED -> stringResource(R.string.pairing_cancelled)
                ProvisionStage.PERMISSION_REQUIRED -> stringResource(R.string.permission_required)
                ProvisionStage.OUTCOME_UNKNOWN -> stringResource(R.string.action_unknown)
            },
            style = MaterialTheme.typography.titleMedium,
            color = if (stage == ProvisionStage.FAILED) WiboxColors.error else WiboxColors.text,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun MediaControls(
    playing: Boolean,
    muted: Boolean,
    positionSeconds: Float,
    durationSeconds: Int,
    onPlayPause: () -> Unit,
    onMute: () -> Unit,
    onSeek: (Float) -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(formatDuration(positionSeconds.toInt()), style = MaterialTheme.typography.bodySmall)
            Slider(
                value = positionSeconds,
                onValueChange = onSeek,
                valueRange = 0f..durationSeconds.coerceAtLeast(1).toFloat(),
                modifier = Modifier.weight(1f).testTag("video_seek"),
            )
            Text(formatDuration(durationSeconds), style = MaterialTheme.typography.bodySmall)
        }
        Row(
            modifier = Modifier.fillMaxWidth().height(60.dp).background(WiboxColors.primary),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SmallIconButton(
                painter = if (playing) R.drawable.ic_stop else R.drawable.ic_play,
                contentDescription = stringResource(if (playing) R.string.pause else R.string.play),
                onClick = onPlayPause,
                tag = "video_play_pause",
                tint = WiboxColors.onPrimary,
            )
            SmallIconButton(
                painter = if (muted) R.drawable.ic_volume_off else R.drawable.ic_volume,
                contentDescription = stringResource(if (muted) R.string.unmute else R.string.mute),
                onClick = onMute,
                tag = "video_mute",
                tint = WiboxColors.onPrimary,
            )
        }
    }
}

@Composable
fun SelectionBar(
    onSelectAll: () -> Unit,
    onDelete: () -> Unit,
    deleteEnabled: Boolean,
    onSave: (() -> Unit)? = null,
    onShare: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(55.dp).background(WiboxColors.primary),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SmallIconButton(R.drawable.ic_select_all, stringResource(R.string.select_all), onSelectAll, tag = "select_all", tint = WiboxColors.onPrimary)
        onSave?.let { SmallIconButton(R.drawable.ic_save, stringResource(R.string.save), it, tag = "save_selection", tint = WiboxColors.onPrimary) }
        onShare?.let { SmallIconButton(R.drawable.ic_share, stringResource(R.string.share), it, tag = "share_selection", tint = WiboxColors.onPrimary) }
        SmallIconButton(R.drawable.ic_delete, stringResource(R.string.delete), onDelete, enabled = deleteEnabled, tag = "delete_selection", tint = WiboxColors.onPrimary)
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = { Text(text, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.testTag("dialog_confirm")) {
                Text(confirmText, color = WiboxColors.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
        containerColor = WiboxColors.surface,
        shape = RoundedCornerShape(16.dp),
    )
}

@Composable
fun DemoMediaArtwork(
    media: MediaUi,
    modifier: Modifier = Modifier,
    dark: Boolean = false,
) {
    val background = if (dark) Color.Black else Color(0xFF52636B)
    Box(
        modifier = modifier.background(background),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                painter = painterResource(if (media.kind == MediaKind.VIDEO) R.drawable.ic_video else R.drawable.ic_photo),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(52.dp),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.local_media_placeholder),
                color = Color.White,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
fun SmallIconButton(
    painter: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tag: String? = null,
    tint: Color = WiboxColors.text,
    stateDescription: String? = null,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .size(48.dp)
            .then(if (stateDescription == null) Modifier else Modifier.semantics { this.stateDescription = stateDescription })
            .then(if (tag == null) Modifier else Modifier.testTag(tag)),
    ) {
        Icon(
            painter = painterResource(painter),
            contentDescription = contentDescription,
            tint = if (enabled) tint else WiboxColors.secondaryText,
        )
    }
}

fun formatTimestamp(timestamp: Long): String =
    SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(timestamp))

fun formatDay(timestamp: Long): String =
    SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(timestamp))

fun formatDuration(seconds: Int): String = "%02d:%02d".format(seconds / 60, seconds % 60)
