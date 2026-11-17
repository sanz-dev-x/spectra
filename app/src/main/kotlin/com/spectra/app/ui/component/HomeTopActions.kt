/**
 * Spectra Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.spectra.app.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spectra.app.LocalDatabase
import com.spectra.app.R
import com.spectra.app.constants.ShowStreakOnHomeKey
import com.spectra.app.utils.computeListeningStreak
import com.spectra.app.utils.rememberPreference
import java.time.LocalDate

private val ActionSize = 44.dp

@Composable
fun HomeTopActions(
    onStreakClick: () -> Unit,
    onCommunityClick: () -> Unit,
    onWrappedClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val database = LocalDatabase.current
    val today = remember { LocalDate.now() }
    val (showStreak) = rememberPreference(ShowStreakOnHomeKey, defaultValue = true)
    val rawDays by remember(database) { database.listeningDays() }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val streak =
        remember(rawDays, today) {
            computeListeningStreak(rawDays.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }, today)
        }
    val scheme = MaterialTheme.colorScheme

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.padding(end = 12.dp),
    ) {
        if (showStreak && streak.current > 0) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier =
                    Modifier
                        .height(ActionSize)
                        .widthIn(min = ActionSize)
                        .clip(CircleShape)
                        .clickable(onClick = onStreakClick)
                        .padding(horizontal = 14.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ios_flame),
                    contentDescription = null,
                    tint = if (streak.listenedToday) scheme.primary else scheme.onSurfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier.size(22.dp),
                )
                Text(
                    text = streak.current.toString(),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.onSurface,
                    maxLines = 1,
                )
            }
        }
        ActionCircle(R.drawable.ios_people, null, onCommunityClick)
        ActionCircle(R.drawable.ios_chart, null, onWrappedClick)
        ActionCircle(R.drawable.ios_sliders, stringResource(R.string.settings), onSettingsClick)
    }
}

@Composable
private fun ActionCircle(
    icon: Int,
    description: String?,
    onClick: () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .size(ActionSize)
                .clip(CircleShape)
                .clickable(onClick = onClick),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = description,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(22.dp),
        )
    }
}
