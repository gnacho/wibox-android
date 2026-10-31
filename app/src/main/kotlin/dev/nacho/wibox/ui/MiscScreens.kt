package dev.nacho.wibox.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.nacho.wibox.R
import dev.nacho.wibox.data.AppScenario

@Composable
fun AboutScreen(
    viewModel: SettingsViewModel,
    navigator: AppNavigator,
) {
    var privacy by remember { mutableStateOf(false) }
    var taps by remember { mutableIntStateOf(0) }
    LaunchedEffect(taps) {
        if (taps >= 5) {
            taps = 0
            navigator.navigateInventory(AppDestinationRequest(inventoryId = "debug"))
        }
    }
    WiboxScreen("screen_about") {
        WiboxTopBar(stringResource(R.string.about), onBack = { navigator.back() })
        LocalDemoBanner()
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(R.drawable.logo),
                contentDescription = stringResource(R.string.app_name),
                modifier = Modifier.padding(top = 30.dp).size(80.dp),
            )
            Text(
                text = stringResource(R.string.app_name),
                modifier = Modifier.padding(top = 10.dp).clickable { taps += 1 }.testTag("about_app_name"),
                fontSize = 25.sp,
                color = WiboxColors.text,
            )
            Text(
                text = stringResource(R.string.version_local),
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(45.dp))
            WiboxButton(stringResource(R.string.privacy_policy), { privacy = true }, tag = "privacy_button")
        }
    }
    if (privacy) {
        AlertDialog(
            onDismissRequest = { privacy = false },
            title = { Text(stringResource(R.string.privacy_policy)) },
            text = {
                Text(
                    text = stringResource(R.string.local_privacy_text),
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(onClick = { privacy = false }) { Text(stringResource(R.string.ok)) }
            },
        )
    }
}

@Composable
fun DebugScreen(
    viewModel: SettingsViewModel,
    navigator: AppNavigator,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    WiboxScreen("screen_debug") {
        WiboxTopBar(stringResource(R.string.debug), onBack = { navigator.back() })
        LocalDemoBanner()
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            WiboxOptionRow(
                text = stringResource(R.string.debug_connection_type),
                onClick = {},
                enabled = false,
                trailing = {},
            )
            WiboxOptionRow(
                text = stringResource(R.string.debug_token),
                onClick = {},
                enabled = false,
                trailing = {},
            )
            WiboxOptionRow(
                text = stringResource(R.string.debug_media),
                onClick = {},
                enabled = false,
                trailing = {},
            )
            Text(
                text = stringResource(R.string.select_demo_scenario),
                modifier = Modifier.padding(20.dp),
                style = MaterialTheme.typography.titleMedium,
            )
            AppScenario.entries.forEach { scenario ->
                WiboxOptionRow(
                    text = scenarioLabel(scenario),
                    onClick = { viewModel.setScenario(scenario) },
                    tag = "debug_scenario_${scenario.name}",
                    trailing = {
                        if (scenario == state.preferences.scenario) {
                            Text(stringResource(R.string.selected), color = WiboxColors.selected)
                        }
                    },
                )
            }
            Spacer(Modifier.height(20.dp))
            WiboxButton(stringResource(R.string.reset_demo), viewModel::resetDemo, tag = "debug_reset")
            Spacer(Modifier.height(12.dp))
            WiboxButton(stringResource(R.string.restart_loading), { navigator.goLoading() }, tag = "debug_restart")
            Spacer(Modifier.height(20.dp))
        }
    }
}
