package com.omega.attendance.presentation.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.omega.attendance.presentation.settings.SettingsScreen
import com.omega.attendance.presentation.students.StudentsScreen
import com.omega.attendance.presentation.scanner.ScannerScreen
import com.omega.attendance.presentation.attendance.AttendanceScreen
import com.omega.attendance.presentation.dashboard.DashboardScreen
import com.omega.attendance.presentation.reports.ReportsScreen
import com.omega.attendance.presentation.staff.StaffScreen
import com.omega.attendance.security.UserRole

private data class Destination(val route: String, val title: String, val icon: ImageVector)
private val destinations = listOf(
    Destination("dashboard", "الرئيسية", Icons.Rounded.Dashboard),
    Destination("scanner", "المسح", Icons.Rounded.QrCodeScanner),
    Destination("students", "الطلبة", Icons.Rounded.Groups),
    Destination("attendance", "الحضور", Icons.Rounded.FactCheck),
    Destination("reports", "التقارير", Icons.Rounded.Assessment),
    Destination("staff", "الكادر المدرسي", Icons.Rounded.Badge),
    Destination("settings", "الإعدادات", Icons.Rounded.Settings)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OmegaApp(role: UserRole = UserRole.ADMIN) {
    val navController = rememberNavController()
    val visibleDestinations = if (role == UserRole.ADMIN) destinations else destinations.filter { it.route in setOf("dashboard", "scanner", "students", "attendance") }
    val current = navController.currentBackStackEntryAsState().value?.destination?.route
    var showMore by remember { mutableStateOf(false) }
    val navigate: (Destination) -> Unit = { item -> navController.navigate(item.route) { popUpTo(navController.graph.findStartDestination().id) { saveState = true }; launchSingleTop = true; restoreState = true } }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val tablet = maxWidth >= 600.dp
        val primary = if (maxWidth < 360.dp) {
            visibleDestinations.filter { it.route in setOf("dashboard", "scanner") }
        } else {
            visibleDestinations.filter { it.route in setOf("dashboard", "scanner", "students", "attendance") }
        }
        val secondary = visibleDestinations.filterNot { it in primary }
        val content: @Composable (Modifier) -> Unit = { modifier -> NavHost(navController, startDestination = "dashboard", modifier = modifier) {
            visibleDestinations.forEach { item ->
                composable(item.route) {
                    when (item.route) {
                        "dashboard" -> DashboardScreen()
                        "settings" -> SettingsScreen()
                        "students" -> StudentsScreen(readOnly = role == UserRole.OPERATOR)
                        "scanner" -> ScannerScreen()
                        "attendance" -> AttendanceScreen(readOnly = role == UserRole.OPERATOR)
                        "reports" -> ReportsScreen()
                        "staff" -> StaffScreen()
                        else -> PlaceholderScreen(item.title, item.icon)
                    }
                }
            }
        } }
        if (tablet) Row(Modifier.fillMaxSize()) {
            NavigationRail(modifier = Modifier.fillMaxHeight().verticalScroll(rememberScrollState())) {
                Spacer(Modifier.height(12.dp))
                visibleDestinations.forEach { item -> NavigationRailItem(selected = current == item.route, onClick = { navigate(item) }, icon = { Icon(item.icon, item.title) }, label = { Text(item.title) }) }
            }
            content(Modifier.weight(1f))
        } else {
            Scaffold(bottomBar = {
                NavigationBar {
                    primary.forEach { item -> NavigationBarItem(selected = current == item.route, onClick = { navigate(item) }, icon = { Icon(item.icon, item.title) }, label = { Text(item.title, maxLines = 1) }) }
                    if (secondary.isNotEmpty()) NavigationBarItem(
                        selected = secondary.any { it.route == current },
                        onClick = { showMore = true },
                        icon = { Icon(Icons.Rounded.MoreHoriz, "المزيد") },
                        label = { Text("المزيد") }
                    )
                }
            }) { padding -> content(Modifier.padding(padding)) }
            if (showMore) ModalBottomSheet(onDismissRequest = { showMore = false }) {
                secondary.forEach { item ->
                    ListItem(
                        headlineContent = { Text(item.title) },
                        leadingContent = { Icon(item.icon, null) },
                        modifier = Modifier.fillMaxWidth().clickable { showMore = false; navigate(item) },
                        trailingContent = { if (current == item.route) Icon(Icons.Rounded.Check, null) }
                    )
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun PlaceholderScreen(title: String, icon: ImageVector) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Icon(icon, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Text("سيتم تجهيز هذه الوحدة ضمن مراحل البناء التالية.", style = MaterialTheme.typography.bodyLarge)
    }
}
