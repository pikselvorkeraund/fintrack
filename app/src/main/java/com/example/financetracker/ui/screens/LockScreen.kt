package com.example.financetracker.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.financetracker.ui.components.PatternLock
import com.example.financetracker.ui.locale.LocalStrings
import com.example.financetracker.ui.viewmodel.LockState

@Composable
fun LockScreen(
    state: LockState,
    setupHint: Boolean,
    onPattern: (List<Int>) -> Unit,
    onOk: () -> Unit,
    onWipe: () -> Unit
) {
    val s = LocalStrings.current
    LaunchedEffect(state) {
        if (state is LockState.Unlocked) onOk()
        if (state is LockState.Wiped) onWipe()
    }
    // Технический текст диагностики (SIGSEGV, стектрейсы, lastError) больше
    // не выводится — детали уходят в logcat. Показываем только понятное.
    val msg = when (state) {
        is LockState.Setup -> s.drawPattern
        is LockState.Enter -> s.enterPattern
        is LockState.Error -> when (state.key) {
            "min4" -> s.min4
            "wrong" -> s.wrongPrefix + state.arg
            "db" -> s.dbError
            else -> s.dbError
        }
        is LockState.Wiped -> s.wiped
        else -> ""
    }
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("Finance Tracker", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text(msg, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center,
            color = if (state is LockState.Error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(32.dp))
        PatternLock(isError = state is LockState.Error, onDone = onPattern)
        // Мелкая подсказка режима: настройка ключа vs регулярный вход
        Spacer(Modifier.height(24.dp))
        Text(
            if (setupHint) s.hintSetup else s.hintEnter,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (state is LockState.Wiped) { Spacer(Modifier.height(16.dp)); Button(onClick = onWipe) { Text(s.newPattern) } }
    }
}
