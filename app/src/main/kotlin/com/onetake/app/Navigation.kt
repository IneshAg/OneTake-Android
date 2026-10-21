package com.onetake.app

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController

/** Bottom-bar tabs: Auditions, Practice, and Search. */
enum class AppTab(val route: String, val label: String) {
    Auditions("auditions", "Auditions"),
    Practice("practice", "Practice"),
    Search("search", "Search"),
}

/**
 * Every pushed screen — mirrors iOS `Route` and the Figma prototype keys.
 * `path` is the concrete navigation route; `Pattern` holds the NavHost templates.
 */
sealed class Route(val path: String) {
    data class AuditionDetail(val id: String) : Route("audition/$id")
    data class Prepare(val id: String, val sceneIndex: Int) : Route("prepare/$id/$sceneIndex")
    data class RunLines(val id: String, val sceneIndex: Int) : Route("runLines/$id/$sceneIndex")
    data class Reader(val id: String) : Route("reader/$id")
    data class RecordReader(val id: String) : Route("recordReader/$id")
    data class ShootMode(val id: String) : Route("shootMode/$id")
    data class MultiDevice(val id: String) : Route("multiDevice/$id")
    data class ShootSetup(val id: String) : Route("shootSetup/$id")
    data class Takes(val id: String) : Route("takes/$id")
    data class TapeCheck(val id: String) : Route("tapeCheck/$id")
    data class Package(val id: String) : Route("package/$id")
    data object Headshots : Route("headshots")
    data object EditSlate : Route("editSlate")
    data object Privacy : Route("privacy")
    data object Notifications : Route("notifications")
    data object PracticeSetup : Route("practiceSetup")
    data class AudioWalk(val id: String, val sceneIndex: Int = 0) : Route("audioWalk/$id/$sceneIndex")

    object Pattern {
        const val AUDITION = "audition/{id}"
        const val PREPARE = "prepare/{id}/{scene}"
        const val RUN_LINES = "runLines/{id}/{scene}"
        const val AUDIO_WALK = "audioWalk/{id}/{scene}"
        const val READER = "reader/{id}"
        const val RECORD_READER = "recordReader/{id}"
        const val SHOOT_MODE = "shootMode/{id}"
        const val MULTI_DEVICE = "multiDevice/{id}"
        const val SHOOT_SETUP = "shootSetup/{id}"
        const val TAKES = "takes/{id}"
        const val TAPE_CHECK = "tapeCheck/{id}"
        const val PACKAGE = "package/{id}"
        const val CAMERA_SHOOT = "camera/shoot/{id}"
        const val CAMERA_HEADSHOT = "camera/headshot"
        const val CAMERA_PRACTICE = "camera/practice"
    }
}

/** Full-screen camera flows. They hide the tab bar and are always dark, like the system Camera. */
sealed class CameraFlow(val path: String) {
    data class Shoot(val auditionId: String) : CameraFlow("camera/shoot/$auditionId")
    data object Headshot : CameraFlow("camera/headshot")
    data object PracticeTake : CameraFlow("camera/practice")
}

/**
 * Navigation API every screen uses (via `LocalNavigator.current`) — same verbs as iOS AppStore:
 * `push`, `back`, `popToRoot`, `select(tab)`, `openCamera`, `showAddAudition`.
 */
@Stable
class Navigator(private val nav: NavHostController) {
    var tab by mutableStateOf(AppTab.Auditions)
        private set
    var isAddAuditionVisible by mutableStateOf(false)

    fun push(route: Route) = nav.navigate(route.path)

    fun back() {
        nav.popBackStack()
    }

    /** Back to the current tab's root screen. */
    fun popToRoot() {
        nav.popBackStack(tab.route, inclusive = false)
    }

    fun select(tab: AppTab) {
        this.tab = tab
        nav.navigate(tab.route) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    /** Switch tab and land on its root (e.g. Sent → "Done" goes to Auditions). */
    fun resetTo(tab: AppTab) {
        this.tab = tab
        nav.navigate(tab.route) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = false }
            launchSingleTop = true
        }
    }

    fun openCamera(flow: CameraFlow) = nav.navigate(flow.path)

    /** Close a camera flow and go somewhere next (or just close). */
    fun closeCamera(then: Route? = null) {
        nav.popBackStack()
        if (then != null) push(then)
    }

    fun showAddAudition() {
        isAddAuditionVisible = true
    }

    internal fun syncTab(route: String?) {
        AppTab.entries.firstOrNull { it.route == route }?.let { tab = it }
    }
}

val LocalNavigator = staticCompositionLocalOf<Navigator> { error("Navigator not provided") }
