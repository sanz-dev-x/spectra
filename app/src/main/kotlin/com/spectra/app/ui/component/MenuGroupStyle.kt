package com.spectra.app.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

internal val MenuGroupShape = RoundedCornerShape(28.dp)
internal val MenuGroupPadding = 16.dp
internal val MenuGroupGap = 20.dp
internal val MenuRowMinHeight = 64.dp
internal val MenuIconSize = 26.dp
internal val MenuIconGap = 18.dp

@Composable
internal fun Modifier.menuGroupSurface(): Modifier =
    this
        .clip(MenuGroupShape)
        .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f))
