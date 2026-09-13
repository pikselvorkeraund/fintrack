package com.example.financetracker.ui.navigation
import androidx.compose.runtime.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.financetracker.ui.viewmodel.LockState
import com.example.financetracker.ui.viewmodel.LockViewModel
import com.example.financetracker.ui.screens.DashboardScreen
import com.example.financetracker.ui.screens.LockScreen
@Composable
fun AppNavGraph() {
    val nav = rememberNavController()
    val vm: LockViewModel = hiltViewModel()
    val st by vm.state.collectAsState()
    NavHost(nav, startDestination = "lock") {
        composable("lock") {
            LockScreen(st, vm::onPattern,
                onOk = { nav.navigate("main") { popUpTo("lock") { inclusive = true } } },
                onWipe = { vm.reset() })
        }
        composable("main") { DashboardScreen() }
    }
}