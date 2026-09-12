package com.techseven.foldstandby.ui.adaptive

import android.app.Activity
import android.graphics.Rect
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

data class TentFoldLayout(
    val isHalfOpen: Boolean,
    val isHorizontalHinge: Boolean,
    val hingeTopDp: Dp,
    val hingeHeightDp: Dp,
    val hingeBoundsPx: Rect?
) {
    /** List/detail split for alarm editing: unfolded/flat only, never tent/tabletop. */
    val supportsListDetailSplit: Boolean
        get() = !isHalfOpen

    /** Half-open with a horizontal hinge — Z Fold / Flip Flex tabletop. */
    val isTabletop: Boolean
        get() = isHalfOpen && isHorizontalHinge
}

@Composable
fun rememberTentFoldLayout(): State<TentFoldLayout> {
    val context = LocalContext.current
    val activity = context as? Activity
    val density = LocalDensity.current

    return if (activity != null) {
        WindowInfoTracker.getOrCreate(activity)
            .windowLayoutInfo(activity)
            .map { info ->
                val feature = info.displayFeatures
                    .filterIsInstance<FoldingFeature>()
                    .firstOrNull()
                if (feature == null) {
                    TentFoldLayout(false, false, 0.dp, 0.dp, null)
                } else {
                    with(density) {
                        TentFoldLayout(
                            isHalfOpen = feature.state == FoldingFeature.State.HALF_OPENED,
                            isHorizontalHinge =
                                feature.orientation == FoldingFeature.Orientation.HORIZONTAL,
                            hingeTopDp = feature.bounds.top.toDp(),
                            hingeHeightDp = feature.bounds.height().toDp(),
                            hingeBoundsPx = Rect(feature.bounds)
                        )
                    }
                }
            }
            .collectAsState(
                initial = TentFoldLayout(false, false, 0.dp, 0.dp, null)
            )
    } else {
        flowOf(TentFoldLayout(false, false, 0.dp, 0.dp, null))
            .collectAsState(initial = TentFoldLayout(false, false, 0.dp, 0.dp, null))
    }
}

@Composable
fun Modifier.foldAwarePadding(): Modifier {
    val fold = rememberTentFoldLayout().value
    return if (fold.isHalfOpen && fold.isHorizontalHinge) {
        this.padding(bottom = 8.dp)
    } else {
        this
    }
}
