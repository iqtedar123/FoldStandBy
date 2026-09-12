package com.techseven.foldstandby.ui.adaptive

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class AppWidthClass { Compact, Medium, Expanded }

@Composable
fun rememberAppWidthClass(): AppWidthClass {
    val widthDp = LocalConfiguration.current.screenWidthDp
    return when {
        widthDp < 600 -> AppWidthClass.Compact
        widthDp < 840 -> AppWidthClass.Medium
        else -> AppWidthClass.Expanded
    }
}

/** Alarm list/detail split only when wide and unfolded (not tent/half-open). */
@Composable
fun rememberAlarmListDetailSplit(): Boolean {
    val widthClass = rememberAppWidthClass()
    val fold = rememberTentFoldLayout().value
    return widthClass != AppWidthClass.Compact && fold.supportsListDetailSplit
}

fun contentMaxWidth(widthClass: AppWidthClass): Dp? = when (widthClass) {
    AppWidthClass.Compact -> null
    AppWidthClass.Medium -> 720.dp
    AppWidthClass.Expanded -> 960.dp
}

fun screenPadding(widthClass: AppWidthClass): PaddingValues = when (widthClass) {
    AppWidthClass.Compact -> PaddingValues(horizontal = 20.dp, vertical = 24.dp)
    AppWidthClass.Medium -> PaddingValues(horizontal = 32.dp, vertical = 28.dp)
    AppWidthClass.Expanded -> PaddingValues(horizontal = 48.dp, vertical = 32.dp)
}

fun isCoverNarrow(maxWidth: Dp): Boolean = maxWidth < 420.dp
