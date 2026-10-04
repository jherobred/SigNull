package app.signull.ui.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import app.signull.data.AppSettings
import app.signull.ui.components.LocalHaptics
import app.signull.ui.finder.FinderDestination
import app.signull.ui.floor.FloorDestination
import app.signull.ui.live.LiveDestination
import app.signull.ui.maps.BuildingDestination
import app.signull.ui.maps.BuildingViewDestination
import app.signull.ui.maps.MapsDestination
import app.signull.ui.onboarding.OnboardingDestination
import app.signull.ui.LocalAppContainer
import app.signull.ui.settings.SettingsDestination
import app.signull.ui.streetmap.StreetMapDestination
import app.signull.ui.update.UpdateDestination

@Composable
fun SigNullApp(settings: AppSettings, onOnboardingDone: () -> Unit, openUpdateTick: Int = 0) {
    // Onboarding sits outside the nav graph so the graph always starts at the Signal tab.
    AnimatedContent(
        targetState = settings.onboardingDone,
        transitionSpec = { (fadeIn(tween(400)) + scaleIn(initialScale = 0.96f)) togetherWith fadeOut(tween(200)) },
        label = "onboarding",
    ) { done ->
        if (done) MainNavigation(openUpdateTick) else OnboardingDestination(onDone = onOnboardingDone)
    }
}

@Composable
private fun MainNavigation(openUpdateTick: Int) {
    val nav = rememberNavController()
    val updater = LocalAppContainer.current.updater
    LaunchedEffect(Unit) { updater.checkIfDue() }
    // A tap on the "update available" notification lands here.
    LaunchedEffect(openUpdateTick) { if (openUpdateTick > 0) nav.navigate(UpdateRoute) { launchSingleTop = true } }
    val entry by nav.currentBackStackEntryAsState()
    val destination = entry?.destination
    val haptics = LocalHaptics.current

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            AnimatedVisibility(
                visible = destination.isTopLevel(),
                enter = slideInVertically(spring(dampingRatio = 0.8f, stiffness = 380f)) { it } + fadeIn(),
                exit = slideOutVertically(tween(180)) { it } + fadeOut(tween(180)),
            ) {
                NavigationBar {
                    TopLevel.entries.forEach { item ->
                        val selected = item.matches(destination)
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                if (!selected) haptics?.tick()
                                nav.navigateTab(item)
                            },
                            icon = { Icon(if (selected) item.selectedIcon else item.icon, contentDescription = null) },
                            label = { Text(item.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = LiveRoute,
            modifier = Modifier
                .padding(padding)
                .consumeWindowInsets(padding),
            enterTransition = { if (isTabSwitch()) fadeThroughIn() else slideIn(forward = true) },
            exitTransition = { if (isTabSwitch()) fadeOut(tween(90)) else slideOut(forward = true) },
            popEnterTransition = { if (isTabSwitch()) fadeThroughIn() else slideIn(forward = false) },
            popExitTransition = { if (isTabSwitch()) fadeOut(tween(90)) else slideOut(forward = false) },
        ) {
            composable<LiveRoute> {
                LiveDestination(
                    onOpenUpdate = { nav.navigate(UpdateRoute) { launchSingleTop = true } },
                    onOpenSettings = { nav.navigate(SettingsRoute) },
                    onFindAngle = { nav.navigateTab(TopLevel.ANGLE) },
                    onOpenFloor = { nav.navigate(FloorRoute(it, place = true)) },
                    onOpenMaps = { nav.navigateTab(TopLevel.MAPS) },
                )
            }
            composable<FinderRoute> { backStackEntry ->
                val route = backStackEntry.toRoute<FinderRoute>()
                FinderDestination(
                    onBack = if (route.spotId != FinderRoute.NO_SPOT) {
                        { nav.popBackStack() }
                    } else {
                        null
                    },
                    onOpenFloor = { nav.navigate(FloorRoute(it, place = true)) },
                    onOpenMaps = { nav.navigateTab(TopLevel.MAPS) },
                )
            }
            composable<MapsRoute> {
                MapsDestination(
                    onOpenBuilding = { nav.navigate(BuildingRoute(it)) },
                    onOpenSettings = { nav.navigate(SettingsRoute) },
                    onOpenStreetMap = { nav.navigate(StreetMapRoute()) },
                )
            }
            composable<StreetMapRoute> {
                StreetMapDestination(
                    onBack = { nav.popBackStack() },
                    onOpenBuilding = { nav.navigate(BuildingRoute(it)) },
                )
            }
            composable<BuildingRoute> { entry ->
                val buildingId = entry.toRoute<BuildingRoute>().buildingId
                BuildingDestination(
                    onBack = { nav.popBackStack() },
                    onOpenFloor = { nav.navigate(FloorRoute(it)) },
                    onOpen3D = { nav.navigate(BuildingViewRoute(buildingId)) },
                    onShowOnMap = { nav.navigate(StreetMapRoute(buildingId)) },
                )
            }
            composable<BuildingViewRoute> {
                BuildingViewDestination(
                    onBack = { nav.popBackStack() },
                    onOpenFloor = { nav.navigate(FloorRoute(it)) },
                )
            }
            composable<FloorRoute> {
                FloorDestination(
                    onBack = { nav.popBackStack() },
                    onFindAngle = { spotId, guide -> nav.navigate(FinderRoute(spotId, guide)) },
                )
            }
            composable<SettingsRoute> {
                SettingsDestination(
                    onBack = { nav.popBackStack() },
                    onOpenUpdates = { nav.navigate(UpdateRoute) { launchSingleTop = true } },
                )
            }
            composable<UpdateRoute> {
                UpdateDestination(onBack = { nav.popBackStack() })
            }
        }
    }
}

private fun NavHostController.navigateTab(item: TopLevel) {
    navigate(item.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.isTabSwitch(): Boolean =
    initialState.destination.isTopLevel() && targetState.destination.isTopLevel()

private fun fadeThroughIn(): EnterTransition =
    fadeIn(tween(220, delayMillis = 90)) + scaleIn(initialScale = 0.94f, animationSpec = tween(220, delayMillis = 90))

private fun slideIn(forward: Boolean): EnterTransition =
    slideInHorizontally(spring(dampingRatio = 0.9f, stiffness = 500f, visibilityThreshold = IntOffset(1, 1))) {
        if (forward) it / 4 else -it / 4
    } + fadeIn(tween(220))

private fun slideOut(forward: Boolean): ExitTransition =
    slideOutHorizontally(spring(dampingRatio = 0.9f, stiffness = 500f, visibilityThreshold = IntOffset(1, 1))) {
        if (forward) -it / 4 else it / 4
    } + fadeOut(tween(160))
