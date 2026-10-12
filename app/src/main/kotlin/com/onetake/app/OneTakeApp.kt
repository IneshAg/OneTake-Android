package com.onetake.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.onetake.app.ui.auditions.AddAuditionDialog
import com.onetake.app.ui.auditions.AuditionDetailScreen
import com.onetake.app.ui.auditions.AuditionsScreen
import com.onetake.app.ui.home.HomeScreen
import com.onetake.app.ui.packageview.PackageScreen
import com.onetake.app.ui.practice.PracticeScreen
import com.onetake.app.ui.profile.ProfileScreen
import com.onetake.app.ui.shoot.ShootScreen
import com.onetake.app.ui.takes.TakesScreen
import com.onetake.app.ui.tapecheck.TapeCheckScreen
import com.onetake.app.ui.theme.OneTakeTheme

sealed class BottomTab(val route: String, val label: String, val icon: ImageVector) {
    object Home : BottomTab("home", "Home", Icons.Default.Home)
    object Auditions : BottomTab("auditions", "Auditions", Icons.Default.VideoLibrary)
    object Practice : BottomTab("practice", "Practice", Icons.Default.Mic)
    object Profile : BottomTab("profile", "Profile", Icons.Default.Person)
}

val bottomTabs = listOf(
    BottomTab.Home,
    BottomTab.Auditions,
    BottomTab.Practice,
    BottomTab.Profile
)

@Composable
fun OneTakeApp(
    viewModel: AppViewModel
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    var showAddDialog by remember { mutableStateOf(false) }

    val isTopLevelDestination = bottomTabs.any { it.route == currentRoute }

    Scaffold(
        bottomBar = {
            if (isTopLevelDestination) {
                NavigationBar(
                    containerColor = OneTakeTheme.colors.surfaceCard,
                    contentColor = OneTakeTheme.colors.labelPrimary
                ) {
                    bottomTabs.forEach { tab ->
                        val isSelected = currentRoute == tab.route
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = OneTakeTheme.colors.accent,
                                selectedTextColor = OneTakeTheme.colors.accent,
                                indicatorColor = OneTakeTheme.colors.accentSoft,
                                unselectedIconColor = OneTakeTheme.colors.labelTertiary,
                                unselectedTextColor = OneTakeTheme.colors.labelTertiary
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            NavHost(
                navController = navController,
                startDestination = BottomTab.Home.route,
                modifier = Modifier.fillMaxSize()
            ) {
                composable(BottomTab.Home.route) {
                    HomeScreen(
                        viewModel = viewModel,
                        onNavigateToAudition = { id -> navController.navigate("auditionDetail/$id") },
                        onNavigateToShoot = { id -> navController.navigate("shoot/$id") },
                        onNavigateToPackage = { id -> navController.navigate("package/$id") },
                        onAddAudition = { showAddDialog = true }
                    )
                }

                composable(BottomTab.Auditions.route) {
                    AuditionsScreen(
                        viewModel = viewModel,
                        onNavigateToAudition = { id -> navController.navigate("auditionDetail/$id") },
                        onNavigateToShoot = { id -> navController.navigate("shoot/$id") },
                        onNavigateToPackage = { id -> navController.navigate("package/$id") },
                        onAddAudition = { showAddDialog = true }
                    )
                }

                composable(BottomTab.Practice.route) {
                    PracticeScreen(viewModel = viewModel)
                }

                composable(BottomTab.Profile.route) {
                    ProfileScreen(viewModel = viewModel)
                }

                composable(
                    route = "auditionDetail/{id}",
                    arguments = listOf(navArgument("id") { type = NavType.StringType })
                ) { backStackEntry ->
                    val id = backStackEntry.arguments?.getString("id") ?: ""
                    AuditionDetailScreen(
                        auditionId = id,
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() },
                        onNavigateToShoot = { auditionId -> navController.navigate("shoot/$auditionId") },
                        onNavigateToTakes = { auditionId -> navController.navigate("takes/$auditionId") },
                        onNavigateToPackage = { auditionId -> navController.navigate("package/$auditionId") }
                    )
                }

                composable(
                    route = "shoot/{id}",
                    arguments = listOf(navArgument("id") { type = NavType.StringType })
                ) { backStackEntry ->
                    val id = backStackEntry.arguments?.getString("id") ?: ""
                    ShootScreen(
                        auditionId = id,
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() },
                        onTakeRecorded = {
                            navController.navigate("takes/$id") {
                                popUpTo("shoot/$id") { inclusive = true }
                            }
                        }
                    )
                }

                composable(
                    route = "takes/{id}",
                    arguments = listOf(navArgument("id") { type = NavType.StringType })
                ) { backStackEntry ->
                    val id = backStackEntry.arguments?.getString("id") ?: ""
                    TakesScreen(
                        auditionId = id,
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() },
                        onGoAgain = { navController.navigate("shoot/$id") },
                        onTapeCheck = { navController.navigate("tapeCheck/$id") }
                    )
                }

                composable(
                    route = "tapeCheck/{id}",
                    arguments = listOf(navArgument("id") { type = NavType.StringType })
                ) { backStackEntry ->
                    val id = backStackEntry.arguments?.getString("id") ?: ""
                    TapeCheckScreen(
                        auditionId = id,
                        onBack = { navController.popBackStack() },
                        onContinueToPackage = { navController.navigate("package/$id") }
                    )
                }

                composable(
                    route = "package/{id}",
                    arguments = listOf(navArgument("id") { type = NavType.StringType })
                ) { backStackEntry ->
                    val id = backStackEntry.arguments?.getString("id") ?: ""
                    PackageScreen(
                        auditionId = id,
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() },
                        onDone = {
                            navController.popBackStack(BottomTab.Auditions.route, inclusive = false)
                        }
                    )
                }
            }

            if (showAddDialog) {
                AddAuditionDialog(
                    onDismiss = { showAddDialog = false },
                    onAdd = { newAudition ->
                        viewModel.addAudition(newAudition)
                        showAddDialog = false
                        navController.navigate("auditionDetail/${newAudition.id}")
                    }
                )
            }
        }
    }
}
