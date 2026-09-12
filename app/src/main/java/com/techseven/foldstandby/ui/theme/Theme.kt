package com.techseven.foldstandby.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val NightstandInk = Color(0xFFE8E4DC)
val NightstandMuted = Color(0xFF9A958C)
val NightstandAccent = Color(0xFFC4A882)
val NightstandNight = Color(0xFFFF6B4A)
val NightstandBg = Color(0xFF000000)

private val DarkColors = darkColorScheme(
    primary = NightstandInk,
    secondary = NightstandAccent,
    background = NightstandBg,
    surface = NightstandBg,
    onPrimary = NightstandBg,
    onSecondary = NightstandBg,
    onBackground = NightstandInk,
    onSurface = NightstandInk
)

@Composable
fun FoldStandByTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        content = content
    )
}
