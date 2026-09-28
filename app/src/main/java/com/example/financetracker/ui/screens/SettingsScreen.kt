@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.financetracker.ui.screens

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.financetracker.BuildConfig
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
    val ctx = LocalContext.current
    val lang by vm.lang.collectAsState()
    val busy by vm.busy.collectAsState()
    val msg by vm.message.collectAsState()
    val accounts by vm.accounts.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    // ---- диалоговое состояние ----
    // 0 = нет; 1 = экспорт (пароль+подтверждение); 2 = импорт (пароль);
    // 3 = режим импорта; 4 = выбор счёта для CSV
    var dialog by remember { mutableIntStateOf(0) }
    // Выбранный в диалоге счёт для CSV-экспорта (по умолчанию — текущий)
    var csvAccId by remember { mutableStateOf<Int?>(null) }
    val csvPendingAcc = remember { mutableStateOf<Int?>(null) }
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
    val csvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        val acc = csvPendingAcc.value
        csvPendingAcc.value = null
        if (uri != null && acc != null) vm.exportCsv(uri, acc, s)
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
                // Карточка «Экспорт CSV» (без пароля: только чтение истории счёта)
                OutlinedCard(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            vm.refreshAccounts()
                            csvAccId = vm.currentAccountId
                            dialog = 4
                        },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Description, null, tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(s.csvTitle, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                s.csvDesc,
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

                Spacer(Modifier.height(24.dp))
                Text(s.aboutTitle, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                // Версия = versionName из app/build.gradle.kts (BuildConfig
                // генерируется благодаря buildConfig = true в buildFeatures)
                Text(
                    "${s.versionLabel} ${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                // Ссылка на сайт разработчика: ACTION_VIEW — само приложение
                // остаётся офлайн (разрешения INTERNET нет), открывает браузер
                Row(
                    Modifier
                        .clickable {
                            try {
                                ctx.startActivity(
                                    Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse("https://github.com/pikselvorkeraund/fintrack")
                                    )
                                )
                            } catch (_: Throwable) {
                                // Нет обработчика (браузер удалён) — тихо, не роняем
                            }
                        }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Info, null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        s.developerSite,
                        Modifier.padding(start = 8.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
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

    // ---- выбор счёта для CSV-экспорта ----
    if (dialog == 4) {
        AlertDialog(
            onDismissRequest = { dialog = 0; csvPendingAcc.value = null },
            title = { Text(s.csvPickAccount) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    if (accounts.isEmpty()) {
                        // Счета ещё не подгружены или БД недоступна — безопасно
                        Text(s.csvNoAccounts,
                            style = MaterialTheme.typography.bodyMedium)
                    }
                    accounts.forEach { a ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { csvAccId = a.id }
                        ) {
                            RadioButton(
                                selected = csvAccId == a.id,
                                onClick = { csvAccId = a.id }
                            )
                            Text(a.name, Modifier.padding(start = 8.dp), maxLines = 1)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val acc = csvAccId
                    if (acc != null) {
                        csvPendingAcc.value = acc
                        dialog = 0
                        csvLauncher.launch(vm.suggestCsvName(acc))
                    }
                }) { Text(s.ok) }
            },
            dismissButton = {
                TextButton(onClick = { dialog = 0; csvPendingAcc.value = null }) { Text(s.cancel) }
            }
        )
    }
}