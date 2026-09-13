package com.example.financetracker.ui.navigation

import androidx.compose.runtime.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.financetracker.data.settings.SettingsRepository
import com.example.financetracker.ui.locale.LocalStrings
import com.example.financetracker.ui.locale.stringsFor
import com.example.financetracker.ui.screens.DashboardScreen
import com.example.financetracker.ui.screens.LockScreen
import com.example.financetracker.ui.screens.SettingsScreen
import com.example.financetracker.ui.viewmodel.LockViewModel

@Composable
fun AppNavGraph(settings: SettingsRepository) {
    val nav = rememberNavController()
    val vm: LockViewModel = hiltViewModel()
    val st by vm.state.collectAsState()
    val lang by settings.lang.collectAsState()
    CompositionLocalProvider(LocalStrings provides stringsFor(lang)) {
        NavHost(nav, startDestination = "lock") {
            composable("lock") {
                LockScreen(st, vm::onPattern,
                    onOk = { nav.navigate("main") { popUpTo("lock") { inclusive = true } } },
                    onWipe = { vm.reset() })
            }
            composable("main") {
                DashboardScreen(onOpenSettings = { nav.navigate("settings") })
            }
            composable("settings") {
                SettingsScreen(onBack = { nav.popBackStack() })
            }
        }
    }
}
