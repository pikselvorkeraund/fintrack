package com.example.financetracker.ui.theme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
@Composable
fun AppTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val cs = when {
        dark -> darkColorScheme(primary = Color(0xFF90CAF9), secondary = Color(0xFFA5D6A7))
        else -> lightColorScheme(primary = Color(0xFF1976D2), secondary = Color(0xFF388E3C))
    }
    MaterialTheme(colorScheme = cs, content = content)
}