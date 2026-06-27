package com.wizdier.wavestream

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.wizdier.wavestream.data.settings.SettingsRepository
import com.wizdier.wavestream.ui.navigation.WaveNavHost
import com.wizdier.wavestream.ui.onboarding.OnboardingScreen
import com.wizdier.wavestream.ui.theme.WaveStreamTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {

    private val settingsRepo: SettingsRepository by inject

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeMode by settingsRepo.themeMode.collectAsState(initial = 0)
            val dynamicColor by settingsRepo.dynamicColor.collectAsState(initial = true)
            val onboardingDone by settingsRepo.onboardingCompleted.collectAsState(initial = true)

            WaveStreamTheme(themeMode = themeMode, dynamicColor = dynamicColor) {
                if (!onboardingDone) {
                    OnboardingScreen(
                        onComplete = {
                            CoroutineScope(Dispatchers.IO).launch {
                                settingsRepo.setOnboardingCompleted(true)
                            }
                        },
                        onAddProviders = {
                            CoroutineScope(Dispatchers.IO).launch {
                                settingsRepo.setOnboardingCompleted(true)
                            }
                        }
                    )
                } else {
                    WaveNavHost()
                }
            }
        }
    }
}
