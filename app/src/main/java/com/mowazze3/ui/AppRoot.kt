package com.mowazze3.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mowazze3.MowazzeApp
import com.mowazze3.domain.AppSettings
import com.mowazze3.ui.components.LocalSnackbar
import com.mowazze3.ui.components.SnackbarController
import com.mowazze3.ui.onboarding.OnboardingScreen
import com.mowazze3.ui.shell.AppShell

/** First-run setup until a level is saved, then the main app. */
@Composable
fun AppRoot() {
    val app = LocalContext.current.applicationContext as MowazzeApp
    val settings by app.settingsStore.settings.collectAsStateWithLifecycle(initialValue = null as AppSettings?)
    val snackbar = remember { SnackbarController() }

    CompositionLocalProvider(LocalSnackbar provides snackbar) {
        val loaded = settings
        when {
            // Stays blank for the moment it takes to load, so setup doesn't flash for returning users.
            loaded == null -> Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {}
            loaded.level == null -> OnboardingScreen()
            else -> AppShell()
        }
    }
}
