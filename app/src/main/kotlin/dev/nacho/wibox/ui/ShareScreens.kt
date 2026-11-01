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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import dev.nacho.wibox.ui.scan.QrCodeImage

@Composable
fun ShareManageScreen(
    deviceId: String,
    viewModel: SharingViewModel,
    navigator: AppNavigator,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var pendingDelete by remember { mutableStateOf<InvitationUi?>(null) }
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
                        onRetry = {},
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
                                    onDelete = { pendingDelete = invitation },
                                )
                            }
                        }
                    }
                }
                WiboxButton(
                    text = stringResource(R.string.share_create_invitation),
                    onClick = { viewModel.createInvitation(deviceId) },
                    modifier = Modifier.padding(vertical = 12.dp),
                    tag = "share_create_invitation",
                    primary = true,
                )
            }
        }
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
        Text(
            text = stringResource(R.string.invitation_permissions),
            style = MaterialTheme.typography.bodyMedium,
            color = WiboxColors.secondaryText,
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
