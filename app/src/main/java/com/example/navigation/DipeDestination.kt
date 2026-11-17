package com.example.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.Person
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.R
import kotlinx.serialization.Serializable

@Serializable data object HomeRoute

@Serializable data object LibraryRoute

@Serializable data object StatisticsRoute

@Serializable data object ProfileRoute

@Serializable data object SettingsRoute

@Serializable data class BookDetailRoute(val workId: Long)

@Serializable data class BookDiaryRoute(val workId: Long)

enum class TopLevelDestination(
  val route: Any,
  val routeKey: String,
  @StringRes val labelRes: Int,
  val selectedIcon: ImageVector,
  val unselectedIcon: ImageVector,
) {
  HOME(
    route = HomeRoute,
    routeKey = "home",
    labelRes = R.string.nav_home,
    selectedIcon = Icons.Filled.Home,
    unselectedIcon = Icons.Outlined.Home,
  ),
  LIBRARY(
    route = LibraryRoute,
    routeKey = "library",
    labelRes = R.string.nav_library,
    selectedIcon = Icons.Filled.LibraryBooks,
    unselectedIcon = Icons.Outlined.LibraryBooks,
  ),
  STATISTICS(
    route = StatisticsRoute,
    routeKey = "statistics",
    labelRes = R.string.nav_statistics,
    selectedIcon = Icons.Filled.BarChart,
    unselectedIcon = Icons.Outlined.BarChart,
  ),
  PROFILE(
    route = ProfileRoute,
    routeKey = "profile",
    labelRes = R.string.nav_profile,
    selectedIcon = Icons.Filled.Person,
    unselectedIcon = Icons.Outlined.Person,
  ),
}
