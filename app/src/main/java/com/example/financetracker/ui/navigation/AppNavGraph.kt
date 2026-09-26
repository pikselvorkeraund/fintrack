package com.example.financetracker.ui.navigation

import androidx.compose.runtime.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.financetracker.data.settings.SettingsRepository
import com.example.financetracker.ui.locale.LocalStrings
import com.example.financetracker.ui.locale.stringsFor
import com.example.financetracker.ui.screens.AccountsScreen
import com.example.financetracker.ui.screens.DashboardScreen
import com.example.financetracker.ui.screens.LockScreen
import com.example.financetracker.ui.screens.SettingsScreen
import com.example.financetracker.ui.screens.StatsScreen
import com.example.financetracker.ui.viewmodel.FinanceViewModel
import com.example.financetracker.ui.viewmodel.LockViewModel

@Composable
fun AppNavGraph(settings: SettingsRepository) {
    val nav = rememberNavController()
    val vm: LockViewModel = hiltViewModel()
    val st by vm.state.collectAsState()
    val lang by settings.lang.collectAsState()
    val financeVm: FinanceViewModel = hiltViewModel()

    CompositionLocalProvider(LocalStrings provides stringsFor(lang)) {
        NavHost(nav, startDestination = "lock") {
            composable("lock") {
                // vm.setupHint читается при каждой рекомпозиции (st меняется
                // синхронно с флагом), поэтому подсказка всегда актуальна
                LockScreen(st, vm.setupHint, vm::onPattern,
                    onOk = { nav.navigate("main") { popUpTo("lock") { inclusive = true } } },
                    onWipe = { vm.reset() })
            }
            composable("main") {
                DashboardScreen(
                    vm = financeVm,
                    onOpenSettings = { nav.navigate("settings") },
                    onOpenAccounts = { nav.navigate("accounts") },
                    // Валюта и тип периода передаются аргументами: каждая
                    // карточка ведёт на свой период (не всегда DAY)
                    onOpenStats = { pt ->
                        nav.navigate("stats/${financeVm.currency.value.code}/${pt.name}")
                    }
                )
            }
            composable("stats/{currency}/{periodType}") {
                // StatsViewModel получает оба аргумента через SavedStateHandle
                StatsScreen(onBack = { nav.popBackStack() })
            }
            composable("accounts") {
                AccountsScreen(
                    onBack = { nav.popBackStack() },
                    onSwitchAccount = { id -> financeVm.setAccount(id) }
                )
            }
            composable("settings") {
                SettingsScreen(onBack = { nav.popBackStack() })
            }
        }
    }
}