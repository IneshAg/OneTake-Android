package com.onetake.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TheaterComedy
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import com.onetake.app.ui.components.LocalAppBackdrop
import com.onetake.app.ui.components.LocalLiquidGlassEnabled
import com.onetake.app.ui.components.backdrop.backdrops.layerBackdrop
import com.onetake.app.ui.components.backdrop.backdrops.rememberLayerBackdrop
import com.onetake.app.ui.components.isGlassSupported
import com.onetake.app.ui.components.navbar.LiquidGlassNavBar
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.onetake.app.ui.auditions.AddAuditionSheet
import com.onetake.app.ui.auditions.AuditionDetailScreen
import com.onetake.app.ui.auditions.AuditionsScreen
import com.onetake.app.ui.search.SearchScreen
import com.onetake.app.ui.onboarding.OnboardingFlow
import com.onetake.app.ui.packageview.PackageScreen
import com.onetake.app.ui.practice.AudioWalkScreen
import com.onetake.app.ui.practice.PracticeScreen
import com.onetake.app.ui.practice.PracticeSetupScreen
import com.onetake.app.ui.practice.PracticeTakeScreen
import com.onetake.app.ui.prepare.PrepareScreen
import com.onetake.app.ui.prepare.ReaderScreen
import com.onetake.app.ui.prepare.RecordReaderScreen
import com.onetake.app.ui.prepare.RunLinesScreen
import com.onetake.app.ui.profile.EditSlateScreen
import com.onetake.app.ui.profile.HeadshotFlowScreen
import com.onetake.app.ui.profile.HeadshotsScreen
import com.onetake.app.ui.profile.NotificationSettingsScreen
import com.onetake.app.ui.profile.PrivacyScreen
import com.onetake.app.ui.profile.ProfileScreen
import com.onetake.app.ui.shoot.ShootFlowScreen
import com.onetake.app.ui.shoot.ShootModeScreen
import com.onetake.app.ui.shoot.ShootSetupScreen
import com.onetake.app.ui.takes.TakesScreen
import com.onetake.app.ui.takes.TapeCheckScreen
import com.onetake.app.ui.profile.ProfileSheet
import com.onetake.app.ui.theme.OT
import com.onetake.app.ui.theme.StudioFinish

@Composable
fun OneTakeApp(
    store: AppStore,
    currentFinish: StudioFinish = StudioFinish.VELVET,
    onSelectFinish: (StudioFinish) -> Unit = {},
) {
    CompositionLocalProvider(LocalAppStore provides store) {
        if (!store.isOnboarded) {
            OnboardingFlow()
        } else {
            RootTabs(
                currentFinish = currentFinish,
                onSelectFinish = onSelectFinish,
            )
        }
    }
}

@Composable
private fun RootTabs(
    currentFinish: StudioFinish,
    onSelectFinish: (StudioFinish) -> Unit,
) {
    val store = LocalAppStore.current
    val nav = rememberNavController()
    val navigator = remember(nav) { Navigator(nav) }
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    LaunchedEffect(route) { navigator.syncTab(route) }
    val showBar = AppTab.entries.any { it.route == route }
    var isProfileSheetVisible by remember { mutableStateOf(false) }

    val windowBackground = OT.colors.bgCanvas
    val paintBackdrop: androidx.compose.ui.graphics.drawscope.ContentDrawScope.() -> Unit = remember(windowBackground) {
        {
            drawRect(windowBackground)
            drawContent()
        }
    }
    val appBackdrop = rememberLayerBackdrop(onDraw = paintBackdrop)
    val glassActive = store.liquidGlass && isGlassSupported()

    CompositionLocalProvider(
        LocalNavigator provides navigator,
        LocalAppBackdrop provides appBackdrop,
        LocalLiquidGlassEnabled provides glassActive,
    ) {
        Box(Modifier.fillMaxSize().background(OT.colors.bgCanvas)) {
            NavHost(
                navController = nav,
                startDestination = AppTab.Auditions.route,
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (glassActive) Modifier.layerBackdrop(appBackdrop) else Modifier),
                // iOS-style push: new screen slides in from the right, old one shifts a little.
                enterTransition = { slideInHorizontally(tween(300)) { it } },
                exitTransition = { slideOutHorizontally(tween(300)) { -it / 4 } + fadeOut(tween(300)) },
                popEnterTransition = { slideInHorizontally(tween(300)) { -it / 4 } + fadeIn(tween(300)) },
                popExitTransition = { slideOutHorizontally(tween(300)) { it } },
            ) {
                tabRoot(AppTab.Auditions) {
                    AuditionsScreen(onOpenProfile = { isProfileSheetVisible = true })
                }
                tabRoot(AppTab.Practice) {
                    PracticeScreen(onOpenProfile = { isProfileSheetVisible = true })
                }
                tabRoot(AppTab.Search) { SearchScreen() }
                composable(Route.Settings.path) { ProfileScreen() }

                withId(Route.Pattern.AUDITION) { AuditionDetailScreen(it) }
                withIdScene(Route.Pattern.PREPARE) { id, s -> PrepareScreen(id, s) }
                withIdScene(Route.Pattern.RUN_LINES) { id, s -> RunLinesScreen(id, s) }
                withIdScene(Route.Pattern.AUDIO_WALK) { id, s -> AudioWalkScreen(id, s) }
                withId(Route.Pattern.READER) { ReaderScreen(it) }
                withId(Route.Pattern.RECORD_READER) { RecordReaderScreen(it) }
                withId(Route.Pattern.SHOOT_MODE) { ShootModeScreen(it) }
                withId(Route.Pattern.SHOOT_SETUP) { ShootSetupScreen(it) }
                withId(Route.Pattern.TAKES) { TakesScreen(it) }
                withId(Route.Pattern.TAPE_CHECK) { TapeCheckScreen(it) }
                withId(Route.Pattern.PACKAGE) { PackageScreen(it) }
                composable(Route.Headshots.path) { HeadshotsScreen() }
                withId(Route.Pattern.HEADSHOTS_AUDITION) { HeadshotsScreen(it) }
                composable(Route.EditSlate.path) { EditSlateScreen() }
                composable(Route.Privacy.path) { PrivacyScreen() }
                composable(Route.Notifications.path) { NotificationSettingsScreen() }
                composable(Route.PracticeSetup.path) { PracticeSetupScreen() }

                // Full-screen camera flows slide up like a modal.
                cameraRoute(Route.Pattern.CAMERA_SHOOT, withId = true) { ShootFlowScreen(it.idArg()) }
                cameraRoute(Route.Pattern.CAMERA_HEADSHOT) { HeadshotFlowScreen() }
                cameraRoute(Route.Pattern.CAMERA_PRACTICE) { PracticeTakeScreen() }
            }

            AnimatedVisibility(
                visible = showBar,
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it },
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                com.onetake.app.ui.components.navbar.LiquidGlassNavBar(
                    selected = navigator.tab,
                    onSelect = navigator::select,
                )
            }

            if (navigator.isAddAuditionVisible) {
                AddAuditionSheet(onDismiss = { navigator.isAddAuditionVisible = false })
            }

            if (isProfileSheetVisible) {
                ProfileSheet(
                    currentFinish = currentFinish,
                    onSelectFinish = onSelectFinish,
                    onDismiss = { isProfileSheetVisible = false },
                )
            }
        }
    }
}

// MARK: - NavHost helpers

private fun NavGraphBuilder.tabRoot(tab: AppTab, content: @Composable () -> Unit) {
    composable(
        tab.route,
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None },
        popEnterTransition = { EnterTransition.None },
        popExitTransition = { ExitTransition.None },
    ) { content() }
}

private fun NavBackStackEntry.idArg(): String = arguments?.getString("id").orEmpty()

private fun NavGraphBuilder.withId(pattern: String, content: @Composable (String) -> Unit) {
    composable(pattern, arguments = listOf(navArgument("id") { type = NavType.StringType })) {
        content(it.idArg())
    }
}

private fun NavGraphBuilder.withIdScene(pattern: String, content: @Composable (String, Int) -> Unit) {
    composable(
        pattern,
        arguments = listOf(
            navArgument("id") { type = NavType.StringType },
            navArgument("scene") { type = NavType.IntType },
        ),
    ) { content(it.idArg(), it.arguments?.getInt("scene") ?: 0) }
}

private fun NavGraphBuilder.cameraRoute(
    pattern: String,
    withId: Boolean = false,
    content: @Composable (NavBackStackEntry) -> Unit,
) {
    composable(
        pattern,
        arguments = if (withId) listOf(navArgument("id") { type = NavType.StringType }) else emptyList(),
        enterTransition = { slideInVertically(tween(320)) { it } },
        exitTransition = { fadeOut(tween(200)) },
        popEnterTransition = { fadeIn(tween(200)) },
        popExitTransition = { slideOutVertically(tween(320)) { it } },
    ) { content(it) }
}


/** Bottom space each tab-root screen leaves so content can scroll above the floating bar. */
val TabBarClearance = 96.dp
