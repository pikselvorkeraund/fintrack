@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.financetracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.financetracker.data.model.AccountEntity
import com.example.financetracker.ui.locale.LocalStrings
import com.example.financetracker.ui.viewmodel.AccountsViewModel
import kotlinx.coroutines.launch

@Composable
fun AccountsScreen(
    onBack: () -> Unit,
    onSwitchAccount: (Int) -> Unit
) {
    val s = LocalStrings.current
    val vm: AccountsViewModel = hiltViewModel()
    val accounts by vm.accounts.collectAsState()
    val currentId = vm.currentAccountId()
    val err by vm.error.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showAdd by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<AccountEntity?>(null) }
    var deleteTarget by remember { mutableStateOf<AccountEntity?>(null) }

    // Показываем ошибку в snackbar и очищаем
    LaunchedEffect(err) {
        err?.let { code ->
            val msg = when (code) {
                "name_empty" -> s.errNameEmpty
                "last_account" -> s.errLastAccount
                "current_account" -> s.errCurrentAccount
                else -> s.dbError
            }
            snackbar.showSnackbar(msg)
            vm.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(s.accounts) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, s.back)
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdd = true }) {
                Icon(Icons.Default.Add, s.addAccount)
            }
        }
    ) { p ->
        LazyColumn(
            Modifier.padding(p),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(accounts, key = { it.id }) { a ->
                val isCurrent = a.id == currentId
                Card(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (!isCurrent) {
                                vm.select(a.id)
                                onSwitchAccount(a.id)
                                onBack()
                            }
                        }
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(Color(a.color.toULong()))
                            )
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(
                                    a.name,
                                    fontWeight = FontWeight.Medium,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                if (isCurrent) {
                                    Text(
                                        s.currentBadge,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                        // Кнопки: переименовать всегда, удалить — только если не текущий и не последний
                        Row {
                            IconButton(onClick = { renameTarget = a }) {
                                Icon(Icons.Default.Edit, s.rename)
                            }
                            if (accounts.size > 1 && !isCurrent) {
                                IconButton(onClick = { deleteTarget = a }) {
                                    Icon(
                                        Icons.Default.Delete, s.delete,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Диалог добавления
    if (showAdd) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text(s.addAccount) },
            text = {
                OutlinedTextField(
                    name, { name = it },
                    label = { Text(s.accountName) },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (vm.add(name)) showAdd = false
                    }
                ) { Text(s.add) }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text(s.cancel) } }
        )
    }

    // Диалог переименования
    renameTarget?.let { target ->
        var newName by remember { mutableStateOf(target.name) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text(s.rename) },
            text = {
                OutlinedTextField(
                    newName, { newName = it },
                    label = { Text(s.accountName) },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (vm.rename(target.id, newName)) renameTarget = null
                    }
                ) { Text(s.add) }
            },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text(s.cancel) } }
        )
    }

    // Подтверждение удаления
    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(s.deleteAccount) },
            text = { Text(s.deleteAccountMsg.format(target.name)) },
            confirmButton = {
                Button(
                    onClick = {
                        if (vm.delete(target.id)) deleteTarget = null
                    }
                ) { Text(s.delete) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text(s.cancel) } }
        )
    }
}