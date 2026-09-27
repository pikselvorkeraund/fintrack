@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.financetracker.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.financetracker.data.repository.ImportMode
import com.example.financetracker.ui.locale.Language
import com.example.financetracker.ui.locale.LocalStrings
import com.example.financetracker.ui.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    vm: SettingsViewModel = hiltViewModel(),
    onDataChanged: () -> Unit,
    onBack: () -> Unit
) {
    val s = LocalStrings.current
    val lang by vm.lang.collectAsState()
    val busy by vm.busy.collectAsState()
    val msg by vm.message.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    // ---- диалоговое состояние ----
    // 0 = нет диалога; 1 = экспорт (пароль+подтверждение); 2 = импорт (пароль)
    var dialog by remember { mutableIntStateOf(0) }
    val pwd = remember { mutableStateOf("") }
    val pwd2 = remember { mutableStateOf("") }
    var pwdErr by remember { mutableStateOf<String?>(null) }
    // Файл выбран (SAF вернул uri) — после пароля для импорта спрашиваем режим
    var pendingExportUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var pendingImportUri by remember { mutableStateOf<android.net.Uri?>(null) }

    val mime = "application/octet-stream"
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(mime)
    ) { uri ->
        if (uri != null) {
            pendingExportUri = uri
            pwd.value = ""; pwd2.value = ""; pwdErr = null
            dialog = 1
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            pendingImportUri = uri
            pwd.value = ""; pwdErr = null
            dialog = 2
        }
    }

    // Сообщение результата → Snackbar
    LaunchedEffect(msg) {
        msg?.let {
            snackbar.showSnackbar(it.text)
            vm.clearMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
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
        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .padding(p)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
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

                Spacer(Modifier.height(24.dp))
                Text(s.dataSection, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))

                // Карточка «Экспорт»
                OutlinedCard(
                    Modifier
                        .fillMaxWidth()
                        .clickable { exportLauncher.launch(vm.suggestFileName()) },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Upload, null, tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(s.exportTitle, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                s.exportDesc,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                // Карточка «Импорт»
                OutlinedCard(
                    Modifier
                        .fillMaxWidth()
                        .clickable { importLauncher.launch(arrayOf(mime)) },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Download, null, tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(s.importTitle, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                s.importDesc,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Спиннер поверх всего контента на время длинной операции
            if (busy) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Column(
                            Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(12.dp))
                            Text(s.busyProcessing)
                        }
                    }
                }
            }
        }
    }

    // ---- диалог пароля экспорта ----
    if (dialog == 1) {
        AlertDialog(
            onDismissRequest = { dialog = 0; pendingExportUri = null },
            title = { Text(s.exportTitle) },
            text = {
                Column {
                    Text(s.exportPasswordHint, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = pwd.value, onValueChange = { pwd.value = it },
                        label = { Text(s.password) }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = pwd2.value, onValueChange = { pwd2.value = it },
                        label = { Text(s.passwordConfirm) }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        isError = pwdErr != null
                    )
                    pwdErr?.let {
                        Spacer(Modifier.height(4.dp))
                        Text(it, color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    when {
                        pwd.value.length < 8 -> pwdErr = s.errPasswordShort
                        pwd.value != pwd2.value -> pwdErr = s.passwordMismatch
                        else -> {
                            pwdErr = null
                            val uri = pendingExportUri
                            dialog = 0
                            pendingExportUri = null
                            if (uri != null) vm.exportBackup(uri, pwd.value, s)
                        }
                    }
                }) { Text(s.ok) }
            },
            dismissButton = {
                TextButton(onClick = { dialog = 0; pendingExportUri = null }) { Text(s.cancel) }
            }
        )
    }

    // ---- диалог пароля импорта ----
    if (dialog == 2) {
        AlertDialog(
            onDismissRequest = { dialog = 0; pendingImportUri = null },
            title = { Text(s.importTitle) },
            text = {
                Column {
                    Text(s.importPasswordHint, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = pwd.value, onValueChange = { pwd.value = it },
                        label = { Text(s.password) }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        isError = pwdErr != null
                    )
                    pwdErr?.let {
                        Spacer(Modifier.height(4.dp))
                        Text(it, color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    // Пустой пароль не имеет смысла: GCM-тег всё равно не пройдёт
                    if (pwd.value.isEmpty()) pwdErr = s.errPasswordShort
                    else {
                        pwdErr = null
                        dialog = 3 // следующий шаг — выбор режима
                    }
                }) { Text(s.ok) }
            },
            dismissButton = {
                TextButton(onClick = { dialog = 0; pendingImportUri = null }) { Text(s.cancel) }
            }
        )
    }

    // ---- выбор режима импорта ----
    if (dialog == 3) {
        AlertDialog(
            onDismissRequest = { dialog = 0; pendingImportUri = null },
            title = { Text(s.importModeTitle) },
            text = {
                Column {
                    Text(s.importModeReplace, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(s.importModeMerge, style = MaterialTheme.typography.bodyMedium)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val uri = pendingImportUri
                    dialog = 0
                    pendingImportUri = null
                    if (uri != null) vm.importBackup(uri, pwd.value, ImportMode.MERGE, s) { ok ->
                        if (ok) onDataChanged()
                    }
                }) { Text(s.importModeMergeBtn) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { dialog = 0; pendingImportUri = null }) { Text(s.cancel) }
                    TextButton(onClick = {
                        val uri = pendingImportUri
                        dialog = 0
                        pendingImportUri = null
                        if (uri != null) vm.importBackup(uri, pwd.value, ImportMode.REPLACE, s) { ok ->
                            if (ok) onDataChanged()
                        }
                    }) { Text(s.importModeReplaceBtn, color = MaterialTheme.colorScheme.error) }
                }
            }
        )
    }
}