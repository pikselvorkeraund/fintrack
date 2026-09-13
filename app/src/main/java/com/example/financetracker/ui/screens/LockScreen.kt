package com.example.financetracker.ui.screens
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.financetracker.ui.components.PatternLock
import com.example.financetracker.ui.viewmodel.LockState
@Composable
fun LockScreen(state: LockState, onPattern: (List<Int>) -> Unit, onOk: () -> Unit, onWipe: () -> Unit) {
    LaunchedEffect(state) { if (state is LockState.Unlocked) onOk(); if (state is LockState.Wiped) onWipe() }
    val msg = when (state) {
        is LockState.Setup -> "Draw pattern (min 4 dots)"
        is LockState.Enter -> "Enter pattern"
        is LockState.Error -> state.msg
        is LockState.Wiped -> "Data wiped after 3 failed attempts. Create new pattern."
        else -> ""
    }
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("Finance Tracker", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text(msg, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center,
            color = if (state is LockState.Error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(32.dp))
        PatternLock(isError = state is LockState.Error, onDone = onPattern)
        if (state is LockState.Wiped) { Spacer(Modifier.height(24.dp)); Button(onClick = onWipe) { Text("New pattern") } }
    }
}