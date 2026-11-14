package com.example.privacyshield.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val DarkBackground = Color(0xFF0F0F12)       
val DarkSurface = Color(0xFF16161B)          
val DarkCard = Color(0xFF1E1E26)             
val DarkCardHover = Color(0xFF272732)        
val DarkBorder = Color(0xFF2A2A36)           

val OrangePrimary = Color(0xFFFF6A00)        
val OrangeBright = Color(0xFFFF7D1A)         
val OrangeDim = Color(0xFFE55E00)            
val OrangeMuted = Color(0x33FF6A00)          

val TextWhite = Color(0xFFFFFFFF)            
val TextMuted = Color(0xFFA2A2B0)            
val TextSubtle = Color(0xFF666675)           

val StatusGreen = Color(0xFF34C759)          
val StatusRed = Color(0xFFFF453A)            
val StatusAmber = Color(0xFFFF9F0A)          

val ClaudeBackground = DarkBackground
val ClaudeSurface = DarkSurface
val ClaudeCard = DarkCard
val ClaudeCardHover = DarkCardHover
val ClaudeBorder = DarkBorder
val ClaudeTerracotta = OrangePrimary
val ClaudeTerracottaDim = OrangeDim
val ClaudeCream = TextWhite
val ClaudeIvory = TextWhite
val ClaudeTextSecondary = TextMuted
val ClaudeTextTertiary = TextSubtle
val ClaudeSage = OrangePrimary
val ClaudeAmber = StatusAmber
val ClaudeRed = StatusRed

val PrivacyNavy = DarkBackground
val PrivacySurface = DarkSurface
val PrivacyCard = DarkCard
val PrivacyCyan = OrangePrimary
val PrivacyGreen = StatusGreen
val PrivacyRed = StatusRed
val PrivacyAmber = StatusAmber
val TextPrimary = TextWhite
val TextSecondary = TextMuted

val SpotifyBackground = DarkBackground
val SpotifySurface = DarkSurface
val SpotifyCard = DarkCard
val SpotifyCardHover = DarkCardHover
val SpotifyBorder = DarkBorder
val SpotifyGreen = OrangePrimary
val SpotifyGreenBright = OrangeBright
val SpotifyTextPrimary = TextWhite
val SpotifyTextSecondary = TextMuted
val SpotifyTextTertiary = TextSubtle

private val ModernDarkColorScheme = darkColorScheme(
    primary = OrangePrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF331A05),
    onPrimaryContainer = Color(0xFFFFDCC2),
    secondary = OrangeBright,
    onSecondary = Color.Black,
    background = DarkBackground,
    onBackground = TextWhite,
    surface = DarkSurface,
    onSurface = TextWhite,
    surfaceVariant = DarkCard,
    onSurfaceVariant = TextMuted,
    outline = DarkBorder,
    error = StatusRed,
    onError = Color.White
)

private val ModernLightColorScheme = lightColorScheme(
    primary = OrangePrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFECE0),
    onPrimaryContainer = Color(0xFF6B2600),
    secondary = OrangeBright,
    onSecondary = Color.White,
    background = Color(0xFFF9F9FB),
    onBackground = Color(0xFF141419),
    surface = Color.White,
    onSurface = Color(0xFF141419),
    surfaceVariant = Color(0xFFEFEFF4),
    onSurfaceVariant = Color(0xFF6E6E7A),
    outline = Color(0xFFDCDCE5),
    error = StatusRed,
    onError = Color.White
)

@Composable
fun PrivacyShieldTheme(
    light: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (light) ModernLightColorScheme else ModernDarkColorScheme,
        shapes = Shapes(
            small = RoundedCornerShape(10.dp),
            medium = RoundedCornerShape(16.dp),
            large = RoundedCornerShape(24.dp)
        ),
        content = content
    )
}

