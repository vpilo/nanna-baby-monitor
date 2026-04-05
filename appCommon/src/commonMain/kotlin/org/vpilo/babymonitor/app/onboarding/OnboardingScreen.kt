package org.vpilo.babymonitor.app.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import org.vpilo.babymonitor.model.AppRole
import org.vpilo.babymonitor.presentation.composables.LoadingBox

@Composable
fun OnboardingScreen(
    modifier: Modifier = Modifier,
    onSavedRole: (role: AppRole) -> Unit,
    viewModel: OnboardingScreenViewModel = koinViewModel(),
) {
    viewModel.stateFlow.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.effectsFlow.collect { effect ->
            when (effect) {
                is OnboardingScreenEffect.SavedRole -> onSavedRole(effect.role)
            }
        }
    }

    OnboardingScreenContent(
        modifier = modifier,
    )
}

@Composable
private fun OnboardingScreenContent(
    modifier: Modifier,
) {
    LoadingBox(modifier)
}
