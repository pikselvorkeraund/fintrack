@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.financetracker.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.financetracker.ui.locale.Language
import com.example.financetracker.ui.locale.LocalStrings
import com.example.financetracker.ui.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: SettingsViewModel = hiltViewModel(), onBack: () -> Unit) {
    val s = LocalStrings.current
    val lang by vm.lang.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(s.settings) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                }
            )
        }
    ) { p ->
        Column(Modifier.padding(p).padding(16.dp)) {
            Text(s.language, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = lang == Language.EN,
                    onClick = { vm.setLanguage(Language.EN) }
                )
                Text(s.english, Modifier.padding(start = 8.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = lang == Language.RU,
                    onClick = { vm.setLanguage(Language.RU) }
                )
                Text(s.russian, Modifier.padding(start = 8.dp))
            }
        }
    }
}
