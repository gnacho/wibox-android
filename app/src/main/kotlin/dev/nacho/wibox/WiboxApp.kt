package dev.nacho.wibox

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.activity.compose.LocalActivity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navigation
import dev.nacho.wibox.data.WiboxRepository
import dev.nacho.wibox.ui.AboutScreen
import dev.nacho.wibox.ui.AddTypeScreen
import dev.nacho.wibox.ui.AppDestinationRequest
import dev.nacho.wibox.ui.AppNavigator
import dev.nacho.wibox.ui.CallViewModel
import dev.nacho.wibox.ui.DebugScreen
import dev.nacho.wibox.ui.DeviceBindScreen
import dev.nacho.wibox.ui.DeviceConfigScreen
import dev.nacho.wibox.ui.DeviceDetailViewModel
import dev.nacho.wibox.ui.DeviceInfoScreen
import dev.nacho.wibox.ui.DevicesViewModel
import dev.nacho.wibox.ui.ErrorPanel
import dev.nacho.wibox.ui.EventsViewModel
import dev.nacho.wibox.ui.GalleryViewModel
import dev.nacho.wibox.ui.Graphs
import dev.nacho.wibox.ui.HandleWifiScreen
import dev.nacho.wibox.ui.ImagePagerScreen
import dev.nacho.wibox.ui.MainScreen
import dev.nacho.wibox.ui.MainTab
import dev.nacho.wibox.ui.OnlineDevicesScreen
import dev.nacho.wibox.ui.OnlineTypeScreen
import dev.nacho.wibox.ui.PairingFailScreen
import dev.nacho.wibox.ui.PairingViewModel
import dev.nacho.wibox.ui.PasswordModifyScreen
import dev.nacho.wibox.ui.PreviewScreen
import dev.nacho.wibox.ui.PreviewViewModel
import dev.nacho.wibox.ui.ProductIntroScreen
import dev.nacho.wibox.ui.LocalProviderStatus
import dev.nacho.wibox.ui.ProviderStatusBanner
import dev.nacho.wibox.ui.PushCallScreen
import dev.nacho.wibox.ui.PushRelay
import dev.nacho.wibox.ui.RouterScreen
import dev.nacho.wibox.ui.Routes
import dev.nacho.wibox.ui.ScanCodeScreen
import dev.nacho.wibox.ui.SessionViewModel
import dev.nacho.wibox.ui.SettingsViewModel
import dev.nacho.wibox.ui.ShareDeviceScreen
import dev.nacho.wibox.ui.ShareManageScreen
import dev.nacho.wibox.ui.SharingViewModel
import dev.nacho.wibox.ui.UiMapper
import dev.nacho.wibox.ui.UnlockSettingScreen
import dev.nacho.wibox.ui.UpgradeScreen
import dev.nacho.wibox.ui.UserNotice
import dev.nacho.wibox.ui.VideoPlayerScreen
import dev.nacho.wibox.ui.VoiceProvisioningScreen
import dev.nacho.wibox.ui.WiboxTheme
import dev.nacho.wibox.ui.WifiProvisioningScreen
import dev.nacho.wibox.ui.factoryFor

@Composable
fun WiboxApp(
    repository: WiboxRepository,
    onExit: () -> Unit = {},
    navigator: AppNavigator = remember { AppNavigator() },
) {
    WiboxTheme {
        val navController = rememberNavController()
        LaunchedEffect(navController) { navigator.attach(navController) }
        val sessionViewModel: SessionViewModel = viewModel(factory = repository.factoryFor(::SessionViewModel))
        val devicesViewModel: DevicesViewModel = viewModel(factory = repository.factoryFor(::DevicesViewModel))
        val eventsViewModel: EventsViewModel = viewModel(factory = repository.factoryFor(::EventsViewModel))
        val galleryViewModel: GalleryViewModel = viewModel(factory = repository.factoryFor(::GalleryViewModel))
        val settingsViewModel: SettingsViewModel = viewModel(factory = repository.factoryFor(::SettingsViewModel))
        val sessionState by sessionViewModel.state.collectAsStateWithLifecycle()
        val settingsState by settingsViewModel.state.collectAsStateWithLifecycle()
        val snackbarHostState = remember { SnackbarHostState() }
        val activity = LocalActivity.current
        val consumedDeepLinks = rememberSaveable { mutableStateOf(emptySet<String>()) }

        LaunchedEffect(sessionState.ready, activity?.intent) {
            val request = PushRelay.fromIntent(activity?.intent)
            if (request != null) {
                val signature = activity?.intent?.dataString.orEmpty()
                if (signature !in consumedDeepLinks.value) {
                    consumedDeepLinks.value = consumedDeepLinks.value + signature
                    if (request.demoScenario != null && BuildConfig.DEBUG) {
                        request.demoScenario.let(settingsViewModel::setScenario)
                    }
                    navigator.navigateInventory(request)
                    activity?.intent = Intent(activity?.intent).apply { data = null }
                }
            } else if (sessionState.ready) {
                navigator.goMain()
            }
        }

        val noticeMessage = settingsState.notice?.let { userNoticeText(it) }
        LaunchedEffect(settingsState.noticeId) {
            if (noticeMessage != null) {
                snackbarHostState.showSnackbar(noticeMessage)
                settingsViewModel.consumeNotice()
            }
        }

        CompositionLocalProvider(LocalProviderStatus.provides(UiMapper.providerStatus(repository.provider.descriptor))) {
            Box(modifier = Modifier.fillMaxSize()) {
                AppNavHost(
                navController = navController,
                repository = repository,
                sessionViewModel = sessionViewModel,
                devicesViewModel = devicesViewModel,
                eventsViewModel = eventsViewModel,
                galleryViewModel = galleryViewModel,
                settingsViewModel = settingsViewModel,
                navigator = navigator,
                onExit = onExit,
            )
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter).testTag("app_snackbar"),
            )
            }
        }
    }
}

@Composable
private fun AppNavHost(
    navController: NavHostController,
    repository: WiboxRepository,
    sessionViewModel: SessionViewModel,
    devicesViewModel: DevicesViewModel,
    eventsViewModel: EventsViewModel,
    galleryViewModel: GalleryViewModel,
    settingsViewModel: SettingsViewModel,
    navigator: AppNavigator,
    onExit: () -> Unit,
) {
    NavHost(navController = navController, startDestination = Graphs.SESSION) {
        navigation(startDestination = Routes.LOADING, route = Graphs.SESSION) {
            composable(Routes.LOADING) {
                SessionScreen(sessionViewModel)
            }
        }
        navigation(startDestination = Routes.MAIN, route = Graphs.MAIN) {
            composable(Routes.MAIN) {
                MainScreen(
                    sessionViewModel = sessionViewModel,
                    devicesViewModel = devicesViewModel,
                    eventsViewModel = eventsViewModel,
                    galleryViewModel = galleryViewModel,
                    settingsViewModel = settingsViewModel,
                    navigator = navigator,
                    onExit = onExit,
                )
            }
        }
        navigation(startDestination = Routes.ADD_TYPE, route = Graphs.PAIRING) {
            composable(Routes.ADD_TYPE) { entry ->
                val viewModel = graphViewModel<PairingViewModel>(navController, entry, Graphs.PAIRING, repository)
                AddTypeScreen(viewModel, navigator)
            }
            composable(Routes.PRODUCT_INTRO) { entry ->
                val viewModel = graphViewModel<PairingViewModel>(navController, entry, Graphs.PAIRING, repository)
                ProductIntroScreen(viewModel, navigator)
            }
            composable(Routes.SCAN) { entry ->
                val viewModel = graphViewModel<PairingViewModel>(navController, entry, Graphs.PAIRING, repository)
                ScanCodeScreen(viewModel, navigator)
            }
            composable(Routes.ONLINE_TYPE) { entry ->
                val viewModel = graphViewModel<PairingViewModel>(navController, entry, Graphs.PAIRING, repository)
                OnlineTypeScreen(viewModel, navigator)
            }
            composable(Routes.ONLINE_DEVICES) { entry ->
                val viewModel = graphViewModel<PairingViewModel>(navController, entry, Graphs.PAIRING, repository)
                OnlineDevicesScreen(viewModel, navigator)
            }
            composable(Routes.DEVICE_BIND) { entry ->
                val viewModel = graphViewModel<PairingViewModel>(navController, entry, Graphs.PAIRING, repository)
                DeviceBindScreen(viewModel, shared = false, navigator = navigator)
            }
            composable(Routes.SHARE_BIND) { entry ->
                val viewModel = graphViewModel<PairingViewModel>(navController, entry, Graphs.PAIRING, repository)
                DeviceBindScreen(viewModel, shared = true, navigator = navigator)
            }
            composable(Routes.HANDLE_WIFI) { entry ->
                val viewModel = graphViewModel<PairingViewModel>(navController, entry, Graphs.PAIRING, repository)
                HandleWifiScreen(viewModel, navigator)
            }
            composable(
                route = Routes.ROUTER,
                arguments = listOf(androidx.navigation.navArgument("modify") { type = NavType.BoolType; defaultValue = false }),
            ) { entry ->
                val viewModel = graphViewModel<PairingViewModel>(navController, entry, Graphs.PAIRING, repository)
                RouterScreen(viewModel, entry.arguments?.getBoolean("modify") == true, navigator)
            }
            composable(Routes.VOICE) { entry ->
                val viewModel = graphViewModel<PairingViewModel>(navController, entry, Graphs.PAIRING, repository)
                VoiceProvisioningScreen(viewModel, navigator)
            }
            composable(Routes.WIFI_PROGRESS) { entry ->
                val viewModel = graphViewModel<PairingViewModel>(navController, entry, Graphs.PAIRING, repository)
                WifiProvisioningScreen(viewModel, navigator)
            }
            composable(Routes.PAIRING_FAIL) { entry ->
                val viewModel = graphViewModel<PairingViewModel>(navController, entry, Graphs.PAIRING, repository)
                PairingFailScreen(viewModel, navigator)
            }
        }
        navigation(startDestination = Routes.PREVIEW, route = Graphs.DEVICE) {
            composable(Routes.PREVIEW) { entry ->
                val deviceId = entry.arguments?.getString("deviceId").orEmpty()
                val viewModel: PreviewViewModel = viewModel(factory = repository.factoryFor(::PreviewViewModel))
                PreviewScreen(deviceId, viewModel, navigator)
            }
            composable(Routes.CONFIG) { entry ->
                val deviceId = entry.arguments?.getString("deviceId").orEmpty()
                val viewModel = graphViewModel<DeviceDetailViewModel>(navController, entry, Graphs.DEVICE, repository)
                DeviceConfigScreen(deviceId, viewModel, navigator)
            }
            composable(Routes.PASSWORD_MODIFY) { entry ->
                val deviceId = entry.arguments?.getString("deviceId").orEmpty()
                val viewModel = graphViewModel<DeviceDetailViewModel>(navController, entry, Graphs.DEVICE, repository)
                PasswordModifyScreen(deviceId, entry.arguments?.getString("mode") ?: "device", viewModel, navigator)
            }
            composable(Routes.UNLOCK_SETTING) { entry ->
                val viewModel = graphViewModel<DeviceDetailViewModel>(navController, entry, Graphs.DEVICE, repository)
                UnlockSettingScreen(entry.arguments?.getString("deviceId").orEmpty(), viewModel, navigator)
            }
            composable(Routes.DEVICE_INFO) { entry ->
                val viewModel = graphViewModel<DeviceDetailViewModel>(navController, entry, Graphs.DEVICE, repository)
                DeviceInfoScreen(entry.arguments?.getString("deviceId").orEmpty(), viewModel, navigator)
            }
            composable(Routes.UPGRADE) { entry ->
                val viewModel = graphViewModel<DeviceDetailViewModel>(navController, entry, Graphs.DEVICE, repository)
                UpgradeScreen(entry.arguments?.getString("deviceId").orEmpty(), viewModel, navigator)
            }
        }
        navigation(startDestination = Routes.SHARE_MANAGE, route = Graphs.SHARING) {
            composable(Routes.SHARE_MANAGE) { entry ->
                val viewModel = graphViewModel<SharingViewModel>(navController, entry, Graphs.SHARING, repository)
                ShareManageScreen(entry.arguments?.getString("deviceId").orEmpty(), viewModel, navigator)
            }
            composable(Routes.SHARE_DEVICE) { entry ->
                val viewModel = graphViewModel<SharingViewModel>(navController, entry, Graphs.SHARING, repository)
                ShareDeviceScreen(entry.arguments?.getString("invitationId").orEmpty(), viewModel, navigator)
            }
        }
        navigation(startDestination = Routes.IMAGE_PAGER, route = Graphs.MEDIA) {
            composable(Routes.IMAGE_PAGER) { entry ->
                ImagePagerScreen(entry.arguments?.getString("mediaId").orEmpty(), galleryViewModel, navigator)
            }
            composable(Routes.VIDEO_PLAYER) { entry ->
                VideoPlayerScreen(entry.arguments?.getString("mediaId").orEmpty(), galleryViewModel, navigator)
            }
        }
        navigation(startDestination = Routes.PUSH_CALL, route = Graphs.SYSTEM) {
            composable(Routes.PUSH_CALL) { entry ->
                val viewModel: CallViewModel = viewModel(factory = repository.factoryFor(::CallViewModel))
                PushCallScreen(entry.arguments?.getString("callId").orEmpty(), viewModel, navigator)
            }
            composable(Routes.ABOUT) {
                AboutScreen(settingsViewModel, navigator)
            }
            composable(Routes.DEBUG) {
                DebugScreen(settingsViewModel, navigator)
            }
        }
    }
}

@Composable
private inline fun <reified T : ViewModel> graphViewModel(
    navController: NavHostController,
    holder: NavBackStackEntry,
    graphId: String,
    repository: WiboxRepository,
): T = viewModel(
    viewModelStoreOwner = remember(holder) { navController.getBackStackEntry(graphId) },
    factory = when (T::class) {
        PairingViewModel::class -> repository.factoryFor(::PairingViewModel)
        DeviceDetailViewModel::class -> repository.factoryFor(::DeviceDetailViewModel)
        SharingViewModel::class -> repository.factoryFor(::SharingViewModel)
        else -> error("Unsupported graph ViewModel ${T::class.java.name}")
    },
)

@Composable
private fun SessionScreen(viewModel: SessionViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Box(modifier = Modifier.fillMaxSize().testTag("screen_loading")) {
        Image(
            painter = painterResource(R.drawable.start),
            contentDescription = stringResource(R.string.app_name),
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        Box(modifier = Modifier.align(Alignment.BottomCenter)) {
            if (state.error != null) {
                ErrorPanel(state.error, viewModel::prepare)
            } else {
                ProviderStatusBanner()
            }
        }
    }
}

@Composable
private fun userNoticeText(notice: UserNotice): String = when (notice) {
    UserNotice.LOCAL_ACTION_ACKNOWLEDGED -> stringResource(R.string.local_action_ack)
    UserNotice.ACTION_FAILED -> stringResource(R.string.action_failed)
    UserNotice.ACTION_UNKNOWN -> stringResource(R.string.action_unknown)
    UserNotice.UNSUPPORTED -> stringResource(R.string.unsupported_local)
    UserNotice.DELETED -> stringResource(R.string.deleted_local)
    UserNotice.SAVED_LOCAL -> stringResource(R.string.saved_local)
    UserNotice.MEDIA_EXPORT_UNSUPPORTED -> stringResource(R.string.media_export_unsupported)
    UserNotice.INVITATION_CREATED -> stringResource(R.string.invitation_created)
    UserNotice.DEMO_RESET -> stringResource(R.string.demo_reset_done)
}
