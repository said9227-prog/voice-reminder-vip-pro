package com.voicereminder.vip

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.voicereminder.vip.core.model.ThemeMode
import com.voicereminder.vip.core.util.AlarmPermissions
import com.voicereminder.vip.ui.VoiceReminderRoot
import com.voicereminder.vip.ui.theme.VoiceReminderTheme
import com.voicereminder.vip.ui.vm.AppViewModel
import com.voicereminder.vip.ui.vm.AppViewModelFactory

class MainActivity : ComponentActivity() {

    private val container by lazy { (application as VoiceReminderApp).container }
    private val appViewModel: AppViewModel by viewModels {
        AppViewModelFactory((application as VoiceReminderApp).container)
    }

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !AlarmPermissions.canPostNotifications(this)
        ) {
            runCatching {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            val settings by appViewModel.settings.collectAsStateWithLifecycle()
            val dark = when (settings.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
            VoiceReminderTheme(darkTheme = dark) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    VoiceReminderRoot(app = appViewModel, container = container)
                }
            }
        }
    }
}
