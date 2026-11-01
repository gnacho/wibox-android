package dev.nacho.wibox.ui

import androidx.compose.foundation.background
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.nacho.wibox.R
import dev.nacho.wibox.data.MediaKind
import kotlinx.coroutines.delay

@Composable
fun ImagePagerScreen(
    mediaId: String,
    viewModel: GalleryViewModel,
    navigator: AppNavigator,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val media = state.media.data.orEmpty().sortedByDescending { it.timestampMillis }
    var currentId by remember(mediaId) { mutableStateOf(mediaId) }
    val current = media.firstOrNull { it.id == currentId }
    val index = media.indexOfFirst { it.id == currentId }.coerceAtLeast(0)
    var dark by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    WiboxScreen("screen_image_pager") {
        if (!dark) {
            WiboxTopBar(
                title = stringResource(R.string.page_indicator, index + 1, media.size.coerceAtLeast(1)),
                onBack = { navigator.back() },
            )
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(if (dark) Color.Black else WiboxColors.background)
                .clickable { dark = !dark },
        ) {
            if (current == null) {
                EmptyContent(stringResource(R.string.media_not_found))
            } else {
                DemoMediaArtwork(
                    media = current,
                    modifier = Modifier.fillMaxSize(),
                    dark = dark,
                )
                Row(
                    modifier = Modifier.align(Alignment.CenterStart).padding(8.dp),
                ) {
                    SmallIconButton(
                        painter = R.drawable.ic_chevron_left,
                        contentDescription = stringResource(R.string.previous),
                        onClick = { currentId = media[(index - 1 + media.size) % media.size].id },
                        enabled = media.size > 1,
                        tag = "media_previous",
                        tint = if (dark) Color.White else WiboxColors.text,
                    )
                }
                Row(
                    modifier = Modifier.align(Alignment.CenterEnd).padding(8.dp),
                ) {
                    SmallIconButton(
                        painter = R.drawable.ic_chevron_right,
                        contentDescription = stringResource(R.string.next),
                        onClick = { currentId = media[(index + 1) % media.size].id },
                        enabled = media.size > 1,
                        tag = "media_next",
                        tint = if (dark) Color.White else WiboxColors.text,
                    )
                }
                if (current.kind == MediaKind.VIDEO) {
                    SmallIconButton(
                        painter = R.drawable.ic_play,
                        contentDescription = stringResource(R.string.play_video),
                        onClick = { navigator.navigateInventory(AppDestinationRequest("video_player", mediaId = current.id)) },
                        modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp),
                        tag = "open_video_player",
                        tint = Color.White,
                    )
                }
            }
        }
        if (!dark && current != null) {
            Row(
                modifier = Modifier.fillMaxWidth().height(64.dp).background(MaterialTheme.colorScheme.surfaceContainer),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SmallIconButton(
                    painter = R.drawable.ic_share,
                    contentDescription = stringResource(R.string.share),
                    onClick = {},
                    enabled = false,
                    tag = "pager_share",
                )
                SmallIconButton(
                    painter = R.drawable.ic_delete,
                    contentDescription = stringResource(R.string.delete),
                    onClick = { confirmDelete = true },
                    tag = "pager_delete",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
    if (confirmDelete && current != null) {
        ConfirmDialog(
            title = stringResource(R.string.delete_media),
            text = stringResource(R.string.delete_media_confirmation),
            confirmText = stringResource(R.string.delete),
            onConfirm = {
                viewModel.delete(current.id)
                confirmDelete = false
                navigator.back()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
fun VideoPlayerScreen(
    mediaId: String,
    viewModel: GalleryViewModel,
    navigator: AppNavigator,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val media = state.media.data.orEmpty().firstOrNull { it.id == mediaId }
    var playing by remember { mutableStateOf(false) }
    var muted by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var toolsVisible by remember { mutableStateOf(true) }
    val duration = media?.durationSeconds?.coerceAtLeast(1) ?: 1
    LaunchedEffect(playing, progress) {
        if (playing) {
            delay(1_000)
            if (progress >= duration - 1) {
                progress = duration.toFloat()
                playing = false
            } else {
                progress += 1
            }
        }
    }
    LaunchedEffect(toolsVisible) {
        if (toolsVisible) {
            delay(5_000)
            toolsVisible = false
        }
    }
    WiboxScreen("screen_video_player") {
        if (toolsVisible) {
            WiboxTopBar(stringResource(R.string.video_player), onBack = { navigator.back() })
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color.Black)
                .clickable { toolsVisible = !toolsVisible }
                .testTag("video_surface"),
        ) {
            if (media == null) {
                EmptyContent(stringResource(R.string.media_not_found))
            } else {
                DemoMediaArtwork(media, modifier = Modifier.fillMaxSize(), dark = true)
                if (!playing) {
                    Icon(
                        painter = painterResource(R.drawable.ic_play),
                        contentDescription = stringResource(R.string.play),
                        tint = Color.White,
                        modifier = Modifier.align(Alignment.Center).size(64.dp),
                    )
                }
                Text(
                    text = stringResource(R.string.simulated_video_playback),
                    modifier = Modifier.align(Alignment.TopCenter).padding(20.dp),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            }
        }
        if (toolsVisible && media != null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(formatDuration(progress.toInt()), style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = progress,
                    onValueChange = {
                        progress = it
                        playing = false
                    },
                    valueRange = 0f..duration.toFloat(),
                    modifier = Modifier.weight(1f).testTag("video_seek"),
                )
                Text(formatDuration(duration), style = MaterialTheme.typography.bodySmall)
            }
            Row(
                modifier = Modifier.fillMaxWidth().height(64.dp).background(MaterialTheme.colorScheme.surfaceContainer),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SmallIconButton(
                    painter = if (playing) R.drawable.ic_stop else R.drawable.ic_play,
                    contentDescription = stringResource(if (playing) R.string.pause else R.string.play),
                    onClick = { playing = !playing },
                    tag = "video_play_pause",
                )
                SmallIconButton(
                    painter = if (muted) R.drawable.ic_volume_off else R.drawable.ic_volume,
                    contentDescription = stringResource(if (muted) R.string.unmute else R.string.mute),
                    onClick = { muted = !muted },
                    tag = "video_mute",
                )
            }
        }
    }
}
