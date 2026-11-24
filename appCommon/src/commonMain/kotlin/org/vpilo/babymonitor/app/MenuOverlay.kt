package org.vpilo.babymonitor.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import org.koin.compose.viewmodel.koinViewModel
import org.vpilo.babymonitor.app.menu.MenuScreen
import org.vpilo.babymonitor.app.menu.MenuScreenViewModel
import org.vpilo.babymonitor.app.navigation.Route

@Composable
internal fun MenuOverlay(
    visible: Boolean,
    onHide: () -> Unit,
    onNavigateTo: (route: Route, popUpTo: Route?) -> Unit,
    onNavigateToRoot: () -> Unit,
) {
    val layoutDirection = LocalLayoutDirection.current
    val ltrFactor = if (layoutDirection == LayoutDirection.Ltr) -1 else +1
    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally(tween(300)) { full -> ltrFactor * full },
        exit = slideOutHorizontally(tween(300)) { full -> ltrFactor * full },
        modifier = Modifier.fillMaxSize(),
    ) {
        Surface {
            MenuScreen(
                modifier = Modifier.fillMaxSize(),
                viewModel = koinViewModel<MenuScreenViewModel>(),
                onBackClicked = onHide,
                onNavigateTo = { route, popUpTo ->
                    onHide()
                    onNavigateTo(route, popUpTo)
                },
                onNavigateToRoot = {
                    onHide()
                    onNavigateToRoot()
                },
            )
        }
    }
}
