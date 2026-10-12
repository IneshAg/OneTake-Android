package com.onetake.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.onetake.app.ui.auditions.SearchScreen
import com.onetake.app.ui.home.HomeScreen
import com.onetake.app.ui.onboarding.OnboardingFlow
import com.onetake.app.ui.packageview.PackageScreen
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
import com.onetake.app.ui.shoot.MultiDeviceScreen
import com.onetake.app.ui.shoot.ShootFlowScreen
import com.onetake.app.ui.shoot.ShootModeScreen
import com.onetake.app.ui.shoot.ShootSetupScreen
import com.onetake.app.ui.takes.TakesScreen
import com.onetake.app.ui.takes.TapeCheckScreen
import com.onetake.app.ui.theme.OT

@Composable
fun OneTakeApp(store: AppStore) {
    CompositionLocalProvider(LocalAppStore provides store) {
        if (!store.isOnboarded) {
            OnboardingFlow()
        } else {
            RootTabs()
        }
    }
}

@Composable
private fun RootTabs() {
    val nav = rememberNavController()
    val navigator = remember(nav) { Navigator(nav) }
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    LaunchedEffect(route) { navigator.syncTab(route) }
    val showBar = AppTab.entries.any { it.route == route }

    CompositionLocalProvider(LocalNavigator provides navigator) {
        Box(Modifier.fillMaxSize().background(OT.colors.bgCanvas)) {
            NavHost(
                navController = nav,
                startDestination = AppTab.Home.route,
                modifier = Modifier.fillMaxSize(),
                // iOS-style push: new screen slides in from the right, old one shifts a little.
                enterTransition = { slideInHorizontally(tween(300)) { it } },
                exitTransition = { slideOutHorizontally(tween(300)) { -it / 4 } + fadeOut(tween(300)) },
                popEnterTransition = { slideInHorizontally(tween(300)) { -it / 4 } + fadeIn(tween(300)) },
                popExitTransition = { slideOutHorizontally(tween(300)) { it } },
            ) {
                tabRoot(AppTab.Home) { HomeScreen() }
                tabRoot(AppTab.Auditions) { AuditionsScreen() }
                tabRoot(AppTab.Practice) { PracticeScreen() }
                tabRoot(AppTab.Profile) { ProfileScreen() }
                tabRoot(AppTab.Search) { SearchScreen() }

                withId(Route.Pattern.AUDITION) { AuditionDetailScreen(it) }
                withIdScene(Route.Pattern.PREPARE) { id, s -> PrepareScreen(id, s) }
                withIdScene(Route.Pattern.RUN_LINES) { id, s -> RunLinesScreen(id, s) }
                withId(Route.Pattern.READER) { ReaderScreen(it) }
                withId(Route.Pattern.RECORD_READER) { RecordReaderScreen(it) }
                withId(Route.Pattern.SHOOT_MODE) { ShootModeScreen(it) }
                withId(Route.Pattern.MULTI_DEVICE) { MultiDeviceScreen(it) }
                withId(Route.Pattern.SHOOT_SETUP) { ShootSetupScreen(it) }
                withId(Route.Pattern.TAKES) { TakesScreen(it) }
                withId(Route.Pattern.TAPE_CHECK) { TapeCheckScreen(it) }
                withId(Route.Pattern.PACKAGE) { PackageScreen(it) }
                composable(Route.Headshots.path) { HeadshotsScreen() }
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
                FloatingTabBar(selected = navigator.tab, onSelect = navigator::select)
            }

            if (navigator.isAddAuditionVisible) {
                AddAuditionSheet(onDismiss = { navigator.isAddAuditionVisible = false })
            }
        }
    }
}

// MARK: - NavHost helpers

private fun NavGraphBuilder.tabRoot(tab: AppTab, content: @Composable () -> Unit) {
    composable(
        tab.route,
        enterTransition = { fadeIn(tween(150)) },
        exitTransition = { fadeOut(tween(150)) },
        popEnterTransition = { fadeIn(tween(150)) },
        popExitTransition = { fadeOut(tween(150)) },
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

// MARK: - Floating tab bar (matches the Figma tab bar: pill with 4 tabs + separate round search)

private data class TabSpec(val tab: AppTab, val icon: ImageVector)

private val tabSpecs = listOf(
    TabSpec(AppTab.Home, Icons.Filled.Home),
    TabSpec(AppTab.Auditions, Icons.Filled.Movie),
    TabSpec(AppTab.Practice, Icons.Filled.TheaterComedy),
    TabSpec(AppTab.Profile, Icons.Filled.AccountCircle),
)

@Composable
private fun FloatingTabBar(selected: AppTab, onSelect: (AppTab) -> Unit) {
    val c = OT.colors
    val barBg = if (c.isDark) c.surfaceRaised else c.surfaceCard
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .height(62.dp)
                .shadow(16.dp, CircleShape, ambientColor = c.labelPrimary.copy(alpha = 0.10f), spotColor = c.labelPrimary.copy(alpha = 0.14f))
                .clip(CircleShape)
                .background(barBg)
                .border(0.5.dp, c.separator, CircleShape)
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabSpecs.forEach { spec ->
                val isSel = spec.tab == selected
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(if (isSel) c.fillField else barBg)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Tab,
                        ) { onSelect(spec.tab) }
                        .semantics { this.selected = isSel },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(spec.icon, contentDescription = null, tint = if (isSel) c.accent else c.labelPrimary, modifier = Modifier.size(24.dp))
                    Text(
                        spec.tab.label,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isSel) c.accent else c.labelPrimary,
                        maxLines = 1,
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .size(62.dp)
                .shadow(16.dp, CircleShape, ambientColor = c.labelPrimary.copy(alpha = 0.10f), spotColor = c.labelPrimary.copy(alpha = 0.14f))
                .clip(CircleShape)
                .background(if (selected == AppTab.Search) c.fillField else barBg)
                .border(0.5.dp, c.separator, CircleShape)
                .clickable(role = Role.Tab) { onSelect(AppTab.Search) }
                .semantics { contentDescription = "Search"; this.selected = selected == AppTab.Search },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Search, contentDescription = null, tint = if (selected == AppTab.Search) c.accent else c.labelPrimary, modifier = Modifier.size(26.dp))
        }
    }
}

/** Bottom space each tab-root screen leaves so content can scroll above the floating bar. */
val TabBarClearance = 96.dp
