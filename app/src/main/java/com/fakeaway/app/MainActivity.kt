package com.fakeaway.app

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fakeaway.app.core.ui.theme.ForensicCyan
import com.fakeaway.app.core.ui.theme.FakeAwayTheme
import com.fakeaway.app.core.ui.theme.MidnightBg
import com.fakeaway.app.core.ui.theme.MidnightSurface
import com.fakeaway.app.core.ui.theme.TextSecondary
import com.fakeaway.app.features.about.presentation.AboutScreen
import com.fakeaway.app.features.fact_checker.presentation.FactCheckScreen
import com.fakeaway.app.features.fact_checker.presentation.FactCheckViewModel
import com.fakeaway.app.features.history.presentation.HistoryScreen
import com.fakeaway.app.features.history.presentation.HistoryViewModel
import com.fakeaway.app.features.screen_shield.presentation.ScreenShieldScreen
import com.fakeaway.app.features.screen_shield.presentation.ScreenShieldViewModel
import com.fakeaway.app.features.screen_shield.service.FloatingShieldService
import com.fakeaway.app.features.settings.presentation.SettingsScreen
import com.fakeaway.app.features.settings.presentation.SettingsViewModel
import com.fakeaway.app.features.share_router.SharedPayload
import com.fakeaway.app.features.voice_detector.presentation.VoiceDetectorScreen
import com.fakeaway.app.features.voice_detector.presentation.VoiceDetectorViewModel

enum class FakeAwayTab(
    val routeKey: String,
    val labelRes: Int,
    val icon: ImageVector,
    val testTag: String
) {
    VOICE("voice", R.string.nav_voice, Icons.Filled.GraphicEq, "nav_voice"),
    SHIELD("shield", R.string.nav_shield, Icons.Filled.Layers, "nav_shield"),
    VERIFY("verify", R.string.nav_verify, Icons.AutoMirrored.Filled.FactCheck, "nav_verify"),
    HISTORY("history", R.string.nav_history, Icons.Filled.History, "nav_history"),
    SETTINGS("settings", R.string.nav_settings, Icons.Filled.Settings, "nav_settings")
}

class MainActivity : AppCompatActivity() {

    private val container: AppContainer
        get() = (application as FakeAwayApplication).container

    private lateinit var voiceViewModel: VoiceDetectorViewModel
    private lateinit var shieldViewModel: ScreenShieldViewModel
    private lateinit var factCheckViewModel: FactCheckViewModel
    private lateinit var historyViewModel: HistoryViewModel
    private lateinit var settingsViewModel: SettingsViewModel

    private var activeTab by mutableStateOf(FakeAwayTab.VOICE)
    private var showAbout by mutableStateOf(false)
    private var pendingStartAfterOverlay = false

    private val projectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val serviceIntent = Intent(this, FloatingShieldService::class.java).apply {
                putExtra(FloatingShieldService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(FloatingShieldService.EXTRA_DATA, result.data)
            }
            ContextCompat.startForegroundService(this, serviceIntent)
            moveTaskToBack(true)
        }
    }

    private val shieldPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        launchScreenCaptureConsent()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        initViewModels()
        savedInstanceState?.getString(STATE_ACTIVE_TAB)?.let { saved ->
            runCatching { FakeAwayTab.valueOf(saved) }.getOrNull()?.let { activeTab = it }
        }
        if (savedInstanceState == null) {
            handleIncomingIntent(intent)
        }

        setContent {
            val language by container.preferences.language.collectAsStateWithLifecycle()
            FakeAwayTheme(language = language) {
                if (showAbout) {
                    BackHandler { showAbout = false }
                    AboutScreen(onBack = { showAbout = false })
                } else {
                    FakeAwayRootScaffold(
                        activeTab = activeTab,
                        onSelectTab = { activeTab = it },
                        onOpenAbout = { showAbout = true },
                        voiceViewModel = voiceViewModel,
                        shieldViewModel = shieldViewModel,
                        factCheckViewModel = factCheckViewModel,
                        historyViewModel = historyViewModel,
                        settingsViewModel = settingsViewModel,
                        onRequestStartShield = ::beginFloatingShieldFlow,
                        onRequestStopShield = ::stopFloatingShield,
                        onRequestOverlayPermission = ::openOverlayPermissionSettings
                    )
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_ACTIVE_TAB, activeTab.name)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        if (pendingStartAfterOverlay && Settings.canDrawOverlays(this)) {
            pendingStartAfterOverlay = false
            requestRuntimePermissionsAndProjection()
        }
    }

    private fun initViewModels() {
        voiceViewModel = ViewModelProvider(
            this,
            simpleFactory {
                VoiceDetectorViewModel(
                    analyzeVoiceUseCase = container.analyzeVoiceUseCase,
                    dispatchers = container.dispatchers,
                    audioPlayer = container.pcmAudioPlayer
                )
            }
        )[VoiceDetectorViewModel::class.java]

        shieldViewModel = ViewModelProvider(
            this,
            simpleFactory {
                ScreenShieldViewModel(
                    analyzeImageUseCase = container.analyzeImageUseCase,
                    analyzeScreenUseCase = container.analyzeScreenUseCase,
                    shieldRuntimeState = container.shieldRuntimeState,
                    dispatchers = container.dispatchers,
                    currentLanguage = { container.preferences.language.value }
                )
            }
        )[ScreenShieldViewModel::class.java]

        factCheckViewModel = ViewModelProvider(
            this,
            simpleFactory {
                FactCheckViewModel(
                    verifyClaimUseCase = container.verifyClaimUseCase,
                    currentLanguage = { container.preferences.language.value },
                    tathabbatClient = container.tathabbatClient,
                    tathabbatUrlProvider = { container.preferences.tathabbatBaseUrl.value },
                    dispatchers = container.dispatchers
                )
            }
        )[FactCheckViewModel::class.java]

        historyViewModel = ViewModelProvider(
            this,
            simpleFactory {
                HistoryViewModel(
                    observeHistory = container.observeHistoryUseCase,
                    deleteItem = container.deleteHistoryItemUseCase,
                    clearHistory = container.clearHistoryUseCase
                )
            }
        )[HistoryViewModel::class.java]

        settingsViewModel = ViewModelProvider(
            this,
            simpleFactory {
                SettingsViewModel(
                    preferences = container.preferences,
                    modelStore = container.modelStore,
                    tathabbatClient = container.tathabbatClient,
                    dispatchers = container.dispatchers
                )
            }
        )[SettingsViewModel::class.java]
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        val targetTab = intent.getStringExtra(EXTRA_TARGET_TAB)
        if (targetTab == "shield") {
            activeTab = FakeAwayTab.SHIELD
            return
        } else if (targetTab == "voice") {
            activeTab = FakeAwayTab.VOICE
            container.shieldRuntimeState.latestOverlayVoiceScan.value?.let {
                voiceViewModel.analyzeExternalResult(it)
            }
            return
        }

        val payload = container.parseShareIntentUseCase.fromAndroidIntent(this, intent) ?: return
        when (payload) {
            is SharedPayload.Audio -> {
                activeTab = FakeAwayTab.VOICE
                voiceViewModel.analyzeUri(
                    context = this,
                    uri = Uri.parse(payload.uriString),
                    sourceLabel = payload.displayName,
                    bannerMessage = getString(R.string.share_received_audio)
                )
            }
            is SharedPayload.Video -> {
                activeTab = FakeAwayTab.VOICE
                voiceViewModel.analyzeUri(
                    context = this,
                    uri = Uri.parse(payload.uriString),
                    sourceLabel = payload.displayName,
                    bannerMessage = getString(R.string.share_received_video)
                )
            }
            is SharedPayload.Image -> {
                activeTab = FakeAwayTab.SHIELD
                shieldViewModel.inspectImageUri(
                    context = this,
                    uri = Uri.parse(payload.uriString),
                    runFullScreenOcrToo = true,
                    bannerMessage = getString(R.string.share_received_image)
                )
            }
            is SharedPayload.TextOrUrl -> {
                activeTab = FakeAwayTab.VERIFY
                factCheckViewModel.verify(
                    textOverride = payload.text,
                    isFacebookLink = payload.isFacebookLink
                )
            }
        }
    }

    private fun beginFloatingShieldFlow() {
        if (!Settings.canDrawOverlays(this)) {
            pendingStartAfterOverlay = true
            openOverlayPermissionSettings()
            return
        }
        requestRuntimePermissionsAndProjection()
    }

    private fun openOverlayPermissionSettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivity(intent)
    }

    private fun requestRuntimePermissionsAndProjection() {
        val needed = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                    this@MainActivity,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
            if (ContextCompat.checkSelfPermission(
                    this@MainActivity,
                    Manifest.permission.RECORD_AUDIO
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                add(Manifest.permission.RECORD_AUDIO)
            }
        }
        if (needed.isNotEmpty()) {
            shieldPermissionsLauncher.launch(needed.toTypedArray())
        } else {
            launchScreenCaptureConsent()
        }
    }

    private fun launchScreenCaptureConsent() {
        val mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projectionLauncher.launch(mgr.createScreenCaptureIntent())
    }

    private fun stopFloatingShield() {
        val intent = Intent(this, FloatingShieldService::class.java).apply {
            action = FloatingShieldService.ACTION_STOP
        }
        startService(intent)
        stopService(Intent(this, FloatingShieldService::class.java))
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : ViewModel> simpleFactory(creator: () -> T): ViewModelProvider.Factory =
        object : ViewModelProvider.Factory {
            override fun <VM : ViewModel> create(modelClass: Class<VM>): VM = creator() as VM
        }

    companion object {
        const val EXTRA_TARGET_TAB = "extra_target_tab"
        private const val STATE_ACTIVE_TAB = "state_active_tab"
    }
}

@Composable
private fun FakeAwayRootScaffold(
    activeTab: FakeAwayTab,
    onSelectTab: (FakeAwayTab) -> Unit,
    onOpenAbout: () -> Unit,
    voiceViewModel: VoiceDetectorViewModel,
    shieldViewModel: ScreenShieldViewModel,
    factCheckViewModel: FactCheckViewModel,
    historyViewModel: HistoryViewModel,
    settingsViewModel: SettingsViewModel,
    onRequestStartShield: () -> Unit,
    onRequestStopShield: () -> Unit,
    onRequestOverlayPermission: () -> Unit
) {
    Scaffold(
        containerColor = MidnightBg,
        bottomBar = {
            NavigationBar(containerColor = MidnightSurface) {
                FakeAwayTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = activeTab == tab,
                        onClick = { onSelectTab(tab) },
                        modifier = Modifier.testTag(tab.testTag),
                        icon = {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = stringResource(tab.labelRes)
                            )
                        },
                        label = {
                            Text(
                                text = stringResource(tab.labelRes),
                                maxLines = 1
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = ForensicCyan,
                            selectedTextColor = ForensicCyan,
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary,
                            indicatorColor = ForensicCyan.copy(alpha = 0.16f)
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (activeTab) {
                FakeAwayTab.VOICE -> VoiceDetectorScreen(viewModel = voiceViewModel)
                FakeAwayTab.SHIELD -> ScreenShieldScreen(
                    viewModel = shieldViewModel,
                    onRequestStartShield = onRequestStartShield,
                    onRequestStopShield = onRequestStopShield,
                    onRequestOverlayPermission = onRequestOverlayPermission
                )
                FakeAwayTab.VERIFY -> FactCheckScreen(
                    viewModel = factCheckViewModel,
                    onJumpToShield = { onSelectTab(FakeAwayTab.SHIELD) }
                )
                FakeAwayTab.HISTORY -> HistoryScreen(viewModel = historyViewModel)
                FakeAwayTab.SETTINGS -> SettingsScreen(
                    viewModel = settingsViewModel,
                    onOpenAbout = onOpenAbout
                )
            }
        }
    }
}
