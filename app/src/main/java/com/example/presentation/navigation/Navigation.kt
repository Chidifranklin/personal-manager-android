package com.example.presentation.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Description
import androidx.compose.ui.graphics.vector.ImageVector

enum class Screen(val route: String, val title: String, val icon: ImageVector) {
    Dashboard("dashboard", "Dashboard", Icons.Default.Dashboard),
    Finance("finance", "Finance", Icons.Default.AccountBalance),
    Productivity("productivity", "Productivity", Icons.Default.CheckCircle),
    Notes("notes", "Notes", Icons.Default.Description),
    Assistant("assistant", "AI Assistant", Icons.Default.AutoAwesome)
}
