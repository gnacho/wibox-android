package dev.nacho.wibox.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.nacho.wibox.R
import dev.nacho.wibox.core.cloud.DevicePermission
import dev.nacho.wibox.ui.scan.QrCodeImage

@Composable
fun ShareManageScreen(
    deviceId: String,
    viewModel: SharingViewModel,
    navigator: AppNavigator,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var pendingDelete by remember { mutableStateOf<InvitationUi?>(null) }
    var editor by remember { mutableStateOf<InvitationDraft?>(null) }
    LaunchedEffect(deviceId) { viewModel.refreshInvitations(deviceId) }
    LaunchedEffect(state.createdInvitationId) {
        val created = state.createdInvitationId ?: return@LaunchedEffect
        navigator.navigateInventory(
            AppDestinationRequest(inventoryId = "share_device", invitationId = created),
        )
        viewModel.consumeCreatedInvitation()
    }
    WiboxScreen(tag = "screen_share_manage") {
        WiboxTopBar(
            title = stringResource(R.string.share_manage_title),
            onBack = { navigator.back() },
        )
        ContentFrame(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                ErrorPanel(error = state.operationError)
                Box(modifier = Modifier.weight(1f)) {
                    LoadContent(
                        state = state.invitations,
                        onRetry = { viewModel.refreshInvitations(deviceId) },
                        emptyText = stringResource(R.string.share_empty_invitations),
                    ) { invitations ->
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(invitations, key = { it.id }) { invitation ->
                                InvitationRow(
                                    invitation = invitation,
                                    onOpen = {
                                        navigator.navigateInventory(
                                            AppDestinationRequest(
                                                inventoryId = "share_device",
                                                invitationId = invitation.id,
                                            ),
                                        )
                                    },
                                    onEdit = { editor = InvitationDraft(invitation) },
                                    onDelete = { pendingDelete = invitation },
                                )
                            }
                        }
                    }
                }
                WiboxButton(
                    text = stringResource(R.string.share_create_invitation),
                    onClick = { editor = InvitationDraft(deviceId = deviceId) },
                    modifier = Modifier.padding(vertical = 12.dp),
                    tag = "share_create_invitation",
                    primary = true,
                )
            }
        }
    }
    val draft = editor
    if (draft != null) {
        InvitationEditor(
            draft = draft,
            onDismiss = { editor = null },
            onSave = { name, permissions ->
                val id = draft.id
                if (id == null) {
                    viewModel.createInvitation(draft.deviceId, name, permissions)
                } else {
                    viewModel.updateInvitation(id, name, permissions)
                }
                editor = null
            },
        )
    }
    val deleteTarget = pendingDelete
    if (deleteTarget != null) {
        ConfirmDialog(
            title = stringResource(R.string.delete_invitation),
            text = stringResource(R.string.delete_invitation_confirmation),
            confirmText = stringResource(R.string.delete_invitation),
            onConfirm = {
                viewModel.deleteInvitation(deleteTarget.id)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }
}

@Composable
fun ShareDeviceScreen(
    invitationId: String,
    viewModel: SharingViewModel,
    navigator: AppNavigator,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val invitation = state.invitations.data?.firstOrNull { it.id == invitationId }
    WiboxScreen(tag = "screen_share_device") {
        WiboxTopBar(
            title = stringResource(R.string.shared_device),
            onBack = { navigator.back() },
        )
        ContentFrame(modifier = Modifier.fillMaxSize()) {
            when {
                invitation != null -> InvitationDetail(invitation) {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, invitation.displayName)
                        putExtra(Intent.EXTRA_TEXT, invitation.link)
                    }
                    context.startActivity(Intent.createChooser(send, null))
                }
                state.invitations is LoadState.Loading -> LoadingContent()
                else -> EmptyContent(stringResource(R.string.share_empty_invitations))
            }
        }
    }
}

@Composable
private fun InvitationRow(
    invitation: InvitationUi,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .semantics { contentDescription = invitation.displayName }
            .testTag("invitation_row_${invitation.id}"),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = invitation.displayName,
                style = MaterialTheme.typography.bodyLarge,
                color = WiboxColors.text,
            )
            Text(
                text = stringResource(R.string.invitation_code) + ": " + invitation.code,
                style = MaterialTheme.typography.bodySmall,
                color = WiboxColors.secondaryText,
            )
            Text(
                text = formatTimestamp(invitation.createdAtMillis),
                style = MaterialTheme.typography.bodySmall,
                color = WiboxColors.secondaryText,
            )
        }
        SmallIconButton(
            painter = R.drawable.ic_share,
            contentDescription = stringResource(R.string.share),
            onClick = onOpen,
            tag = "invitation_open_${invitation.id}",
            tint = WiboxColors.selected,
        )
        SmallIconButton(
            painter = R.drawable.ic_delete,
            contentDescription = stringResource(R.string.delete_invitation),
            onClick = onDelete,
            tag = "invitation_delete_${invitation.id}",
            tint = WiboxColors.error,
        )
        SmallIconButton(
            painter = R.drawable.ic_edit,
            contentDescription = stringResource(R.string.invitation_edit),
            onClick = onEdit,
            tag = "invitation_edit_${invitation.id}",
        )
        }
    }
}

@Composable
private fun InvitationDetail(invitation: InvitationUi, onShareLink: () -> Unit) {
    val qrDescription = stringResource(R.string.invitation_qr_description)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = invitation.displayName,
            style = MaterialTheme.typography.titleLarge,
            color = WiboxColors.text,
        )
        DetailValue(stringResource(R.string.invitation_code), invitation.code, tag = "invitation_code_value")
        DetailValue(
            stringResource(R.string.share_expires_at, formatTimestamp(invitation.expiresAtMillis)),
            null,
            tag = "invitation_expires",
        )
        DetailValue(
            stringResource(R.string.invitation_permissions_label),
            permissionSummary(invitation.permissions),
            tag = "invitation_permissions_value",
        )
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier
                    .semantics { contentDescription = qrDescription }
                    .testTag("invitation_qr"),
                shape = MaterialTheme.shapes.medium,
                color = Color.White,
            ) {
                QrCodeImage(
                    payload = invitation.link,
                    modifier = Modifier
                        .padding(12.dp)
                        .size(200.dp),
                )
            }
        }
        Text(
            text = invitation.link,
            style = MaterialTheme.typography.bodySmall,
            color = WiboxColors.selected,
        )
        Text(
            text = stringResource(R.string.share_scan_text),
            style = MaterialTheme.typography.bodySmall,
            color = WiboxColors.secondaryText,
        )
        Spacer(modifier = Modifier.height(8.dp))
        WiboxButton(
            text = stringResource(R.string.share_link),
            onClick = onShareLink,
            tag = "share_link_button",
            primary = true,
        )
    }
}

@Composable
private fun DetailValue(label: String, value: String?, tag: String?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (tag == null) Modifier else Modifier.testTag(tag)),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = WiboxColors.secondaryText,
        )
        if (value != null) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                color = WiboxColors.text,
            )
        }
    }
}

private data class InvitationDraft(
    val deviceId: String,
    val id: String? = null,
    val name: String = "",
    val permissions: Set<DevicePermission> = DevicePermission.entries.toSet(),
) {
    constructor(invitation: InvitationUi) : this(
        deviceId = invitation.deviceId,
        id = invitation.id,
        name = invitation.displayName,
        permissions = invitation.permissions,
    )
}

@Composable
private fun InvitationEditor(
    draft: InvitationDraft,
    onDismiss: () -> Unit,
    onSave: (String, Set<DevicePermission>) -> Unit,
) {
    var name by remember(draft.id) { mutableStateOf(draft.name) }
    var permissions by remember(draft.id) { mutableStateOf(draft.permissions) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(
                    if (draft.id == null) R.string.invitation_editor_create_title
                    else R.string.invitation_editor_edit_title,
                ),
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                WiboxTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = stringResource(R.string.invitation_name_label),
                    tag = "invitation_name_input",
                )
                Text(
                    text = stringResource(R.string.invitation_permissions_label),
                    modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                DevicePermission.entries.forEach { permission ->
                    WiboxSwitchRow(
                        text = permissionLabel(permission),
                        checked = permission in permissions,
                        onCheckedChange = { checked ->
                            permissions = if (checked) permissions + permission else permissions - permission
                        },
                        tag = "permission_${permission.name.lowercase()}",
                        topMargin = 4.dp,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name, permissions) },
                modifier = Modifier.testTag("invitation_save"),
            ) {
                Text(stringResource(R.string.invitation_editor_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun permissionSummary(permissions: Set<DevicePermission>): String {
    if (permissions.isEmpty()) return stringResource(R.string.invitation_permissions_none)
    val labels = mutableListOf<String>()
    for (permission in DevicePermission.entries) {
        if (permission in permissions) labels += permissionLabel(permission)
    }
    return labels.joinToString(", ")
}

@Composable
private fun permissionLabel(permission: DevicePermission): String = stringResource(
    when (permission) {
        DevicePermission.PREVIEW -> R.string.permission_preview
        DevicePermission.PLAYBACK -> R.string.permission_playback
        DevicePermission.ALARM -> R.string.permission_alarms
        DevicePermission.UNLOCK -> R.string.permission_unlock
        DevicePermission.F1 -> R.string.permission_f1
        DevicePermission.CALL_GUARD -> R.string.permission_call_guard
    },
)
