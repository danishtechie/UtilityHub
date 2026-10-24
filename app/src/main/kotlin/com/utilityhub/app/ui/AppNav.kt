package com.utilityhub.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.utilityhub.app.core.auth.AuthState
import com.utilityhub.app.data.AppSettings
import com.utilityhub.app.ui.screens.AboutScreen
import com.utilityhub.app.ui.screens.AccountScreen
import com.utilityhub.app.ui.screens.AuthScreen
import com.utilityhub.app.ui.screens.EmailVerificationScreen
import com.utilityhub.app.ui.screens.ExploreScreen
import com.utilityhub.app.ui.screens.HistoryScreen
import com.utilityhub.app.ui.screens.HomeScreen
import com.utilityhub.app.ui.screens.OnboardingScreen
import com.utilityhub.app.ui.screens.PaywallScreen
import com.utilityhub.app.ui.screens.PermissionsScreen
import com.utilityhub.app.ui.screens.PrivacyScreen
import com.utilityhub.app.ui.screens.SettingsScreen
import com.utilityhub.app.ui.screens.ToolRoute
import com.utilityhub.app.ui.screens.appContainer
import com.utilityhub.app.ui.theme.UtilityHubTheme
import com.utilityhub.app.ui.viewmodel.MainViewModel

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("home", "Home", Icons.Rounded.Home),
    Tab("explore", "Explore", Icons.Rounded.Explore),
    Tab("history", "History", Icons.Rounded.History),
    Tab("settings", "Settings", Icons.Rounded.Settings),
)

@Composable
fun UtilityHubRoot() {
    val container = appContainer()
    val vm: MainViewModel = viewModel(factory = viewModelFactory { initializer { MainViewModel(container) } })
    val settings by vm.settings.collectAsStateWithLifecycle()
    val authState by vm.authState.collectAsStateWithLifecycle()

    UtilityHubTheme(settings.theme) {
        if (!settings.loaded) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        } else {
            AppNav(vm, settings, authState)
        }
    }
}

@Composable
private fun AppNav(vm: MainViewModel, settings: AppSettings, authState: AuthState) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val showBar = tabs.any { it.route == route }

    fun openTool(id: String) = nav.navigate("tool/$id")

    // Determine initial start route once settings and auth state are loaded
    val initialStartRoute = remember {
        when (authState) {
            is AuthState.SignedOut -> "auth"
            is AuthState.SignedIn -> {
                if (!authState.emailVerified) "verify-email"
                else if (!settings.permissionsPrompted) "permissions"
                else if (!settings.onboardingDone) "onboarding"
                else "home"
            }
        }
    }

    // Auth & Email Verification Routing Enforcement
    LaunchedEffect(authState) {
        when (val s = authState) {
            is AuthState.SignedOut -> {
                if (nav.currentDestination?.route != "auth") {
                    nav.navigate("auth") { popUpTo(0) { inclusive = true } }
                }
            }
            is AuthState.SignedIn -> {
                if (!s.emailVerified && nav.currentDestination?.route != "verify-email" && nav.currentDestination?.route != "auth") {
                    nav.navigate("verify-email") { popUpTo(0) { inclusive = true } }
                } else if (s.emailVerified && nav.currentDestination?.route == "verify-email") {
                    val target = if (!settings.permissionsPrompted) "permissions" else if (!settings.onboardingDone) "onboarding" else "home"
                    nav.navigate(target) { popUpTo(0) { inclusive = true } }
                }
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBar) NavigationBar {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = route == tab.route,
                        onClick = {
                            nav.navigate(tab.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = initialStartRoute, modifier = Modifier.padding(padding)) {
            composable("auth") {
                AuthScreen(onAuthSuccess = {
                    val current = vm.authState.value
                    if (current is AuthState.SignedIn && !current.emailVerified) {
                        nav.navigate("verify-email") { popUpTo("auth") { inclusive = true } }
                    } else {
                        val target = if (!settings.permissionsPrompted) "permissions" else if (!settings.onboardingDone) "onboarding" else "home"
                        nav.navigate(target) { popUpTo("auth") { inclusive = true } }
                    }
                })
            }
            composable("verify-email") {
                EmailVerificationScreen(
                    onVerified = {
                        val target = if (!settings.permissionsPrompted) "permissions" else if (!settings.onboardingDone) "onboarding" else "home"
                        nav.navigate(target) { popUpTo("verify-email") { inclusive = true } }
                    },
                    onSignOut = {
                        nav.navigate("auth") { popUpTo(0) { inclusive = true } }
                    }
                )
            }
            composable("permissions") {
                PermissionsScreen(onPermissionsDone = {
                    vm.finishPermissionsPrompt()
                    val target = if (!settings.onboardingDone) "onboarding" else "home"
                    nav.navigate(target) { popUpTo("permissions") { inclusive = true } }
                })
            }
            composable("onboarding") {
                OnboardingScreen(onDone = {
                    vm.finishOnboarding()
                    nav.navigate("home") { popUpTo("onboarding") { inclusive = true } }
                })
            }
            composable("home") {
                HomeScreen(
                    vm, onOpenTool = ::openTool,
                    onOpenCategory = { c -> vm.selectCategory(c); nav.navigate("explore") { launchSingleTop = true } },
                    onPaywall = { nav.navigate("paywall") }, onSettings = { nav.navigate("settings") { launchSingleTop = true } },
                )
            }
            composable("explore") { ExploreScreen(vm, ::openTool) }
            composable("history") { HistoryScreen(vm, ::openTool) }
            composable("settings") { SettingsScreen(vm, { nav.navigate("paywall") }, { nav.navigate("privacy") }, { nav.navigate("about") }, { nav.navigate("account") }) }
            composable("account") { AccountScreen { nav.popBackStack() } }
            composable("tool/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) { e ->
                ToolRoute(e.arguments?.getString("id").orEmpty(), vm, onBack = { nav.popBackStack() }, onPaywall = { nav.navigate("paywall") })
            }
            composable("paywall") { PaywallScreen(vm) { nav.popBackStack() } }
            composable("privacy") { PrivacyScreen { nav.popBackStack() } }
            composable("about") { AboutScreen { nav.popBackStack() } }
        }
    }
}
