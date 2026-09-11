package com.example.presentation.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R

@Composable
fun AppBrandLogo(
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    elevation: Dp = 2.dp
) {
    Box(
        modifier = modifier
            .size(size)
            .shadow(elevation, RoundedCornerShape(size * 0.28f))
            .clip(RoundedCornerShape(size * 0.28f))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF1E3A8A), // Sapphire Blue
                        Color(0xFF0F172A)  // Deep Navy
                    )
                )
            )
            .border(
                width = (size * 0.035f).coerceAtLeast(1.dp),
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFFD4AF37), // Champagne Gold
                        Color(0xFF38BDF8)  // Sky Accent
                    )
                ),
                shape = RoundedCornerShape(size * 0.28f)
            ),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_app_logo),
            contentDescription = "Personal Manager Logo",
            modifier = Modifier.fillMaxSize(0.88f)
        )
    }
}

@Composable
fun AppBrandHeader(
    modifier: Modifier = Modifier,
    logoSize: Dp = 42.dp,
    title: String = "Personal Manager",
    subtitle: String = "Executive Wealth & Vault"
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AppBrandLogo(size = logoSize)
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
