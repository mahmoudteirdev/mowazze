package com.mowazze3.ui.onboarding

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mowazze3.MowazzeApp
import com.mowazze3.data.AndroidDeviceProbe

/** Keeps the flow alive across screen rotation. All the logic is in [OnboardingModel]. */
class OnboardingViewModel(application: Application) : AndroidViewModel(application) {
    val model = OnboardingModel(
        probe = AndroidDeviceProbe(application),
        store = (application as MowazzeApp).settingsStore,
        scope = viewModelScope,
    )
}
