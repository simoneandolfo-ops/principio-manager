package com.recallshot.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.lifecycle.lifecycleScope
import com.recallshot.app.settings.SettingsRepository
import com.recallshot.app.ui.screens.AppRoot
import com.recallshot.app.ui.screens.OnboardingScreen
import com.recallshot.app.ui.theme.RecallShotTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val vm by viewModels<MainViewModel>()
    private lateinit var settingsRepository: SettingsRepository
    private var pendingOpenId by mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsRepository = SettingsRepository(this)
        handleIntent(intent)
        setContent {
            RecallShotTheme {
                val settings by settingsRepository.settings.collectAsState(initial = null)
                val current = settings ?: return@RecallShotTheme
                val screenshotNotificationPermission = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { }

                if (!current.onboardingComplete) {
                    OnboardingScreen(onDone = {
                        lifecycleScope.launch { settingsRepository.finishOnboarding() }
                    })
                } else {
                    LaunchedEffect(
                        current.onboardingComplete,
                        current.autoImportScreenshots,
                        current.screenshotNotificationPermissionAsked
                    ) {
                        if (current.autoImportScreenshots) {
                            vm.scanNow(full = current.lastMediaScanSeconds == 0L)
                            if (
                                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                !current.screenshotNotificationPermissionAsked &&
                                ContextCompat.checkSelfPermission(
                                    this@MainActivity,
                                    Manifest.permission.POST_NOTIFICATIONS
                                ) != PackageManager.PERMISSION_GRANTED
                            ) {
                                settingsRepository.markScreenshotNotificationPermissionAsked()
                                screenshotNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }
                    }
                    AppRoot(vm,current,onSetting={key,value->
                        lifecycleScope.launch {
                            when(key){
                                "auto_import" -> {
                                    settingsRepository.setAutoImport(value)
                                    if (value) vm.scanNow(full = false)
                                }
                                "auto_ocr" -> {
                                    settingsRepository.setAutoOcr(value)
                                    if (value) vm.processPendingOcr()
                                }
                                "reminders" -> {
                                    settingsRepository.setReminders(value)
                                    if (!value) vm.disableAllReminders()
                                }
                            }
                        }
                    },openId=pendingOpenId,onConsumedOpenId={pendingOpenId=null})
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if(intent==null) return
        pendingOpenId = intent.getLongExtra("open_id",-1).takeIf { it>0 } ?: pendingOpenId
        if(intent.type?.startsWith("image/") == true) {
            val source = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_REFERRER, Uri::class.java)?.host
                ?: intent.getStringExtra(Intent.EXTRA_REFERRER_NAME)?.removePrefix("android-app://")?.substringBefore('/')
            when (intent.action) {
                Intent.ACTION_SEND -> {
                    IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                        ?.let { vm.importShared(it, source) }
                }
                Intent.ACTION_SEND_MULTIPLE -> {
                    IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                        .orEmpty()
                        .forEach { vm.importShared(it, source) }
                }
            }
        }
    }
}
