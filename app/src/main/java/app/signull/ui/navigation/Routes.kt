package app.signull.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import kotlinx.serialization.Serializable

@Serializable
data object LiveRoute

/** [spotId] links the scan to a saved spot. [guide] jumps straight to guiding toward that spot's best angle. */
@Serializable
data class FinderRoute(val spotId: Long = NO_SPOT, val guide: Boolean = false) {
    companion object {
        const val NO_SPOT = -1L
    }
}

@Serializable
data object MapsRoute

@Serializable
data class BuildingRoute(val buildingId: Long)

/** [place] opens the map ready to drop a new spot. */
@Serializable
data class FloorRoute(val floorId: Long, val place: Boolean = false)

@Serializable
data object SettingsRoute

@Serializable
data object UpdateRoute

@Serializable
data class BuildingViewRoute(val buildingId: Long)

/** [focusBuildingId] centers the map on one building; -1 shows them all. */
@Serializable
data class StreetMapRoute(val focusBuildingId: Long = -1)

enum class TopLevel(
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
    val route: Any,
) {
    SIGNAL("Signal", Icons.Rounded.NetworkCheck, Icons.Rounded.NetworkCheck, LiveRoute),
    ANGLE("Angle", Icons.Outlined.Explore, Icons.Rounded.Explore, FinderRoute()),
    MAPS("Maps", Icons.Outlined.Map, Icons.Rounded.Map, MapsRoute),
    ;

    fun matches(destination: NavDestination?): Boolean = destination?.hierarchy?.any {
        when (this) {
            SIGNAL -> it.hasRoute(LiveRoute::class)
            ANGLE -> it.hasRoute(FinderRoute::class)
            MAPS -> it.hasRoute(MapsRoute::class)
        }
    } == true
}

fun NavDestination?.isTopLevel(): Boolean = TopLevel.entries.any { it.matches(this) }
