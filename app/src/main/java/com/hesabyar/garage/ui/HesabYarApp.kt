package com.hesabyar.garage.ui

import android.os.Build
import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.hesabyar.garage.security.DeviceAuthenticationGateway

private data class NavItem(val route: String, val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

internal object AppRoute {
    const val HOME = "home"
    const val PEOPLE = "people"
    const val INCOME = "income"
    const val SETTLEMENT = "settlement"
    const val MORE = "more"
    const val PRIVACY = "privacy"
    const val SECURITY = "security"
    const val INCOME_DETAIL = "income/{id}"
    const val TRANSFER_DETAIL = "transfer/{id}"
    const val SETTLEMENT_DETAIL = "settlement/{id}"

    val topLevel = setOf(HOME, PEOPLE, INCOME, SETTLEMENT, MORE)
}

internal enum class TopLevelNavigationAction { NONE, POP_TO_ROOT, NAVIGATE }

internal data class TopLevelNavigationPlan(
    val action: TopLevelNavigationAction,
    val targetRoute: String,
    val requestHomeScroll: Boolean = false,
)

internal data class CorrectionNavigationPlan(
    val originalDetailRoute: String,
    val replacementRoute: String,
)

internal fun incomeCorrectionNavigationPlan(replacementId: Long) =
    CorrectionNavigationPlan(AppRoute.INCOME_DETAIL, "income/$replacementId")

internal fun transferCorrectionNavigationPlan(replacementId: Long) =
    CorrectionNavigationPlan(AppRoute.TRANSFER_DETAIL, "transfer/$replacementId")

internal fun settlementCorrectionNavigationPlan(replacementId: Long) =
    CorrectionNavigationPlan(AppRoute.SETTLEMENT_DETAIL, "settlement/$replacementId")

internal fun parentTopLevelRoute(route: String?): String? = when {
    route in AppRoute.topLevel -> route
    route?.startsWith("employee/") == true -> AppRoute.PEOPLE
    route?.startsWith("income/") == true -> AppRoute.INCOME
    route == "transfer" || route?.startsWith("transfer/") == true -> AppRoute.SETTLEMENT
    route?.startsWith("settlement/") == true -> AppRoute.SETTLEMENT
    route == "history" || route == "year-close" || route == AppRoute.PRIVACY ||
        route == AppRoute.SECURITY -> AppRoute.MORE
    else -> null
}

internal fun topLevelNavigationPlan(currentRoute: String?, targetRoute: String): TopLevelNavigationPlan = when {
    targetRoute == AppRoute.HOME -> TopLevelNavigationPlan(
        action = TopLevelNavigationAction.POP_TO_ROOT,
        targetRoute = targetRoute,
        requestHomeScroll = true,
    )
    currentRoute == targetRoute -> TopLevelNavigationPlan(TopLevelNavigationAction.NONE, targetRoute)
    parentTopLevelRoute(currentRoute) == targetRoute -> TopLevelNavigationPlan(TopLevelNavigationAction.POP_TO_ROOT, targetRoute)
    else -> TopLevelNavigationPlan(TopLevelNavigationAction.NAVIGATE, targetRoute)
}

internal fun shouldDisplayRecoveryCode(access: AppAccessState, code: String?): Boolean =
    access is AppAccessState.Unlocked && code != null

@Composable
fun HesabYarApp(
    viewModel: AppViewModel,
    deviceAuthenticationGateway: DeviceAuthenticationGateway,
) {
    val access by viewModel.appAccess.collectAsState()
    val appSecuritySettings by viewModel.securitySettings.collectAsState()
    val pendingRecoveryCode by viewModel.pendingRecoveryCode.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val navController = rememberNavController()
    var homeScrollRequest by rememberSaveable { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }
    val lifecycleOwner = LocalLifecycleOwner.current
    val activity = LocalActivity.current
    DisposableEffect(activity, appSecuritySettings?.lockEnabled) {
        activity?.let { host ->
            val lockEnabled = appSecuritySettings?.lockEnabled == true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                host.setRecentsScreenshotEnabled(!lockEnabled)
            } else if (lockEnabled) {
                host.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            } else {
                host.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
        onDispose { }
    }
    DisposableEffect(lifecycleOwner, activity) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> viewModel.onAppForegrounded()
                Lifecycle.Event.ON_STOP -> if (activity?.isChangingConfigurations != true) {
                    viewModel.onAppBackgrounded()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    when (val currentAccess = access) {
        AppAccessState.Loading -> {
            SecureLoadingScreen(snackbar)
            return
        }
        is AppAccessState.Locked -> {
            AppLockScreen(
                viewModel = viewModel,
                state = currentAccess,
                deviceAuthenticationGateway = deviceAuthenticationGateway,
                snackbarHostState = snackbar,
            )
            return
        }
        AppAccessState.Unlocked -> Unit
    }

    val settings by viewModel.settings.collectAsState()

    if (!settings.onboardingDone) {
        Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
            OnboardingScreen(viewModel, Modifier.padding(padding))
        }
        return
    }

    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val topItems = listOf(
        NavItem(AppRoute.HOME, "خانه", Icons.Rounded.Home),
        NavItem(AppRoute.PEOPLE, "پرسنل", Icons.Rounded.Groups),
        NavItem(AppRoute.INCOME, "درآمد", Icons.Rounded.AddCircle),
        NavItem(AppRoute.SETTLEMENT, "تسویه", Icons.Rounded.Balance),
        NavItem(AppRoute.MORE, "بیشتر", Icons.Rounded.MoreHoriz),
    )
    val selectedTopLevelRoute = parentTopLevelRoute(route)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar {
                topItems.forEach { item ->
                    NavigationBarItem(
                        selected = selectedTopLevelRoute == item.route,
                        onClick = {
                            val plan = topLevelNavigationPlan(route, item.route)
                            if (plan.requestHomeScroll) homeScrollRequest++
                            when (plan.action) {
                                TopLevelNavigationAction.NONE -> Unit
                                TopLevelNavigationAction.POP_TO_ROOT -> {
                                    val popped = navController.popBackStack(plan.targetRoute, inclusive = false)
                                    if (!popped && route != plan.targetRoute) {
                                        navController.navigateTopLevel(plan.targetRoute, restoreSavedState = false)
                                    }
                                }
                                TopLevelNavigationAction.NAVIGATE -> {
                                    navController.navigateTopLevel(plan.targetRoute, restoreSavedState = true)
                                }
                            }
                        },
                        icon = { Icon(item.icon, item.title) },
                        label = { Text(item.title) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(navController, startDestination = AppRoute.HOME, Modifier.padding(padding)) {
            composable(AppRoute.HOME) { DashboardScreen(viewModel, navController, homeScrollRequest) }
            composable(AppRoute.PEOPLE) { PersonnelScreen(viewModel, navController) }
            composable(AppRoute.INCOME) { IncomeScreen(viewModel, navController) }
            composable(AppRoute.SETTLEMENT) { SettlementScreen(viewModel, navController) }
            composable(AppRoute.MORE) { MoreScreen(viewModel, navController) }
            composable("employee/add") { AddEmployeeScreen(viewModel) { navController.popBackStack() } }
            composable("employee/{id}") { entry ->
                PersonnelDetailScreen(viewModel, entry.arguments?.getString("id")?.toLongOrNull() ?: 0, navController)
            }
            composable("employee/{id}/edit") { entry ->
                EditEmployeeScreen(viewModel, entry.arguments?.getString("id")?.toLongOrNull() ?: 0) { navController.popBackStack() }
            }
            composable("employee/{id}/absence") { entry ->
                AddAbsenceScreen(viewModel, entry.arguments?.getString("id")?.toLongOrNull() ?: 0) { navController.popBackStack() }
            }
            composable("employee/{id}/absence/{absenceId}") { entry ->
                EditAbsenceScreen(
                    viewModel,
                    entry.arguments?.getString("id")?.toLongOrNull() ?: 0,
                    entry.arguments?.getString("absenceId")?.toLongOrNull() ?: 0,
                ) { navController.popBackStack() }
            }
            composable("employee/{id}/employment") { entry ->
                AddEmploymentScreen(viewModel, entry.arguments?.getString("id")?.toLongOrNull() ?: 0) { navController.popBackStack() }
            }
            composable("employee/{id}/employment/{periodId}") { entry ->
                EditEmploymentScreen(
                    viewModel,
                    entry.arguments?.getString("id")?.toLongOrNull() ?: 0,
                    entry.arguments?.getString("periodId")?.toLongOrNull() ?: 0,
                ) { navController.popBackStack() }
            }
            composable("employee/{id}/card") { entry ->
                AddCardScreen(viewModel, entry.arguments?.getString("id")?.toLongOrNull() ?: 0) { navController.popBackStack() }
            }
            composable("transfer") { TransferScreen(viewModel) { navController.popBackStack() } }
            composable(AppRoute.INCOME_DETAIL) { entry ->
                val id = entry.arguments?.getString("id")?.toLongOrNull() ?: 0
                IncomeDetailScreen(
                    viewModel,
                    id,
                    onBack = { navController.popBackStack() },
                    onEdit = { navController.navigate("income/$id/edit") },
                    onViewPrevious = { previousId -> navController.navigate("income/$previousId") },
                    onDeleted = { navController.popBackStack() },
                    onOpenSecurity = { navController.navigate(AppRoute.SECURITY) },
                )
            }
            composable("income/{id}/edit") { entry ->
                CorrectIncomeScreen(
                    viewModel = viewModel,
                    eventId = entry.arguments?.getString("id")?.toLongOrNull() ?: 0,
                    onBack = { navController.popBackStack() },
                    onSaved = { replacementId ->
                        navController.navigateAfterCorrection(incomeCorrectionNavigationPlan(replacementId))
                    },
                )
            }
            composable(AppRoute.TRANSFER_DETAIL) { entry ->
                val id = entry.arguments?.getString("id")?.toLongOrNull() ?: 0
                TransferDetailScreen(
                    viewModel,
                    id,
                    onBack = { navController.popBackStack() },
                    onEdit = { navController.navigate("transfer/$id/edit") },
                    onViewPrevious = { previousId -> navController.navigate("transfer/$previousId") },
                    onDeleted = { navController.popBackStack() },
                    onOpenSecurity = { navController.navigate(AppRoute.SECURITY) },
                )
            }
            composable("transfer/{id}/edit") { entry ->
                CorrectTransferScreen(
                    viewModel = viewModel,
                    transferId = entry.arguments?.getString("id")?.toLongOrNull() ?: 0,
                    onBack = { navController.popBackStack() },
                    onSaved = { replacementId ->
                        navController.navigateAfterCorrection(transferCorrectionNavigationPlan(replacementId))
                    },
                )
            }
            composable(AppRoute.SETTLEMENT_DETAIL) { entry ->
                val id = entry.arguments?.getString("id")?.toLongOrNull() ?: 0
                SettlementDetailScreen(
                    viewModel,
                    id,
                    onBack = { navController.popBackStack() },
                    onEdit = { navController.navigate("settlement/$id/edit") },
                    onViewPrevious = { previousId -> navController.navigate("settlement/$previousId") },
                    onDeleted = { navController.popBackStack() },
                    onOpenSecurity = { navController.navigate(AppRoute.SECURITY) },
                )
            }
            composable("settlement/{id}/edit") { entry ->
                CorrectSettlementScreen(
                    viewModel = viewModel,
                    paymentId = entry.arguments?.getString("id")?.toLongOrNull() ?: 0,
                    onBack = { navController.popBackStack() },
                    onSaved = { replacementId ->
                        navController.navigateAfterCorrection(settlementCorrectionNavigationPlan(replacementId))
                    },
                )
            }
            composable("history") { YearHistoryScreen(viewModel, navController) }
            composable("year-close") { AnnualCloseScreen(viewModel) { navController.popBackStack() } }
            composable(AppRoute.PRIVACY) { PrivacyPolicyScreen { navController.popBackStack() } }
            composable(AppRoute.SECURITY) {
                SecurityScreen(viewModel, deviceAuthenticationGateway) { navController.popBackStack() }
            }
        }
        if (busy) LoadingOverlay()
    }
    pendingRecoveryCode
        ?.takeIf { shouldDisplayRecoveryCode(access, it) }
        ?.let { RecoveryCodeDialog(it, viewModel::acknowledgeRecoveryCode) }
}

private fun androidx.navigation.NavController.navigateAfterCorrection(plan: CorrectionNavigationPlan) {
    navigate(plan.replacementRoute) {
        popUpTo(plan.originalDetailRoute) { inclusive = true }
    }
}

private fun androidx.navigation.NavController.navigateTopLevel(route: String, restoreSavedState: Boolean) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = restoreSavedState }
        launchSingleTop = true
        restoreState = restoreSavedState
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
