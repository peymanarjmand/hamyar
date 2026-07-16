package com.hesabyar.garage.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

private data class NavItem(val route: String, val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

@Composable
fun HesabYarApp(viewModel: AppViewModel) {
    val settings by viewModel.settings.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }

    if (!settings.onboardingDone) {
        Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
            OnboardingScreen(viewModel, Modifier.padding(padding))
        }
        return
    }

    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val topItems = listOf(
        NavItem("home", "خانه", Icons.Rounded.Home),
        NavItem("people", "پرسنل", Icons.Rounded.Groups),
        NavItem("income", "درآمد", Icons.Rounded.AddCircle),
        NavItem("settlement", "تسویه", Icons.Rounded.Balance),
        NavItem("more", "بیشتر", Icons.Rounded.MoreHoriz),
    )
    val showBottom = route in topItems.map { it.route }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (showBottom) NavigationBar {
                topItems.forEach { item ->
                    NavigationBarItem(
                        selected = route == item.route,
                        onClick = {
                            navController.navigate(item.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(item.icon, item.title) },
                        label = { Text(item.title) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(navController, startDestination = "home", Modifier.padding(padding)) {
            composable("home") { DashboardScreen(viewModel, navController) }
            composable("people") { PersonnelScreen(viewModel, navController) }
            composable("income") { IncomeScreen(viewModel, navController) }
            composable("settlement") { SettlementScreen(viewModel, navController) }
            composable("more") { MoreScreen(viewModel, navController) }
            composable("employee/add") { AddEmployeeScreen(viewModel) { navController.popBackStack() } }
            composable("employee/{id}") { entry ->
                PersonnelDetailScreen(viewModel, entry.arguments?.getString("id")?.toLongOrNull() ?: 0, navController)
            }
            composable("employee/{id}/absence") { entry ->
                AddAbsenceScreen(viewModel, entry.arguments?.getString("id")?.toLongOrNull() ?: 0) { navController.popBackStack() }
            }
            composable("employee/{id}/employment") { entry ->
                AddEmploymentScreen(viewModel, entry.arguments?.getString("id")?.toLongOrNull() ?: 0) { navController.popBackStack() }
            }
            composable("employee/{id}/card") { entry ->
                AddCardScreen(viewModel, entry.arguments?.getString("id")?.toLongOrNull() ?: 0) { navController.popBackStack() }
            }
            composable("transfer") { TransferScreen(viewModel) { navController.popBackStack() } }
            composable("income/{id}") { entry ->
                IncomeDetailScreen(viewModel, entry.arguments?.getString("id")?.toLongOrNull() ?: 0) { navController.popBackStack() }
            }
            composable("transfer/{id}") { entry ->
                TransferDetailScreen(viewModel, entry.arguments?.getString("id")?.toLongOrNull() ?: 0) { navController.popBackStack() }
            }
            composable("settlement/{id}") { entry ->
                SettlementDetailScreen(viewModel, entry.arguments?.getString("id")?.toLongOrNull() ?: 0) { navController.popBackStack() }
            }
            composable("history") { YearHistoryScreen(viewModel, navController) }
            composable("year-close") { AnnualCloseScreen(viewModel) { navController.popBackStack() } }
        }
        if (busy) LoadingOverlay()
    }
}

@Composable
private fun LoadingOverlay() {
    androidx.compose.foundation.layout.Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = .28f)),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}
