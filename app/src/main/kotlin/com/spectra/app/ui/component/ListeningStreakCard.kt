package com.spectra.app.ui.component

import android.content.Intent
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spectra.app.LocalDatabase
import com.spectra.app.R
import com.spectra.app.constants.ShowStreakOnHomeKey
import com.spectra.app.utils.computeListeningStreak
import com.spectra.app.utils.nextStreakMilestone
import com.spectra.app.utils.rememberPreference
import java.time.LocalDate
import java.time.LocalTime

private class StreakTier(
    val from: Int,
    @StringRes val name: Int,
)

private val StreakTiers =
    listOf(
        StreakTier(0, R.string.streak_tier_0),
        StreakTier(3, R.string.streak_tier_3),
        StreakTier(7, R.string.streak_tier_7),
        StreakTier(14, R.string.streak_tier_14),
        StreakTier(30, R.string.streak_tier_30),
        StreakTier(60, R.string.streak_tier_60),
        StreakTier(100, R.string.streak_tier_100),
        StreakTier(180, R.string.streak_tier_180),
        StreakTier(365, R.string.streak_tier_365),
    )

@Composable
fun ListeningStreakCard(modifier: Modifier = Modifier) {
    val database = LocalDatabase.current
    val context = LocalContext.current
    val today = LocalDate.now()
    val dayEnd = remember(today) { today.atTime(LocalTime.MAX) }
    val weekStart = remember(today) { today.minusDays(6).atStartOfDay() }

    val rawDays by database.listeningDays().collectAsStateWithLifecycle(initialValue = emptyList())
    val weekMs by remember(today) { database.getTotalPlayTimeInRange(weekStart, dayEnd) }
        .collectAsStateWithLifecycle(initialValue = 0L)
    val (showOnHome, onShowOnHomeChange) = rememberPreference(ShowStreakOnHomeKey, defaultValue = true)
    var showInfo by remember { mutableStateOf(false) }

    val streak =
        remember(rawDays, today) {
            computeListeningStreak(rawDays.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }, today)
        }
    if (rawDays.isEmpty()) return

    val current = streak.current
    val ink = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val tier = StreakTiers.lastOrNull { it.from <= current } ?: StreakTiers.first()
    val milestone = nextStreakMilestone(current)
    val nextTier = milestone?.let { m -> StreakTiers.firstOrNull { it.from == m } }
    val progress =
        if (milestone == null) {
            1f
        } else {
            ((current - tier.from).toFloat() / (milestone - tier.from).coerceAtLeast(1)).coerceIn(0f, 1f)
        }
    val weekMinutes = ((weekMs ?: 0L) / 60_000L).toInt()
    val appName = stringResource(R.string.app_name)

    Column(
        modifier =
            modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .fillMaxWidth()
                .menuGroupSurface()
                .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StreakFlame(lit = streak.listenedToday, modifier = Modifier.size(150.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(tier.name), fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = muted)
                Text(current.toString(), fontSize = 60.sp, fontWeight = FontWeight.Bold, color = ink, lineHeight = 64.sp)
                Text(stringResource(R.string.home_streak_card_days), fontSize = 18.sp, color = muted)
            }
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Icon(
                    painter = painterResource(R.drawable.share),
                    contentDescription = null,
                    tint = ink,
                    modifier =
                        Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .clickable {
                                val intent =
                                    Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, context.getString(R.string.streak_share_text, current, appName))
                                    }
                                context.startActivity(Intent.createChooser(intent, null))
                            }.padding(10.dp),
                )
                Icon(
                    painter = painterResource(R.drawable.info),
                    contentDescription = null,
                    tint = ink,
                    modifier = Modifier.size(48.dp).clip(CircleShape).clickable { showInfo = true }.padding(10.dp),
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        Text(
            text =
                if (nextTier != null && milestone != null) {
                    stringResource(R.string.streak_milestone_left, stringResource(nextTier.name), milestone - current)
                } else {
                    stringResource(R.string.streak_max_milestone)
                },
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            color = ink,
        )
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth().height(10.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)) {
            Box(
                Modifier
                    .fillMaxWidth(progress)
                    .height(10.dp)
                    .clip(CircleShape)
                    .background(Brush.horizontalGradient(listOf(Color(0xFFFFB347), Color(0xFFFF5A1F)))),
            )
        }

        Spacer(Modifier.height(18.dp))
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f))
                    .padding(vertical = 16.dp),
        ) {
            StreakStat(streak.best.toString(), stringResource(R.string.streak_best), Modifier.weight(1f))
            StreakStat(rawDays.size.toString(), stringResource(R.string.streak_total_days), Modifier.weight(1f))
            StreakStat(stringResource(R.string.streak_minutes, weekMinutes), stringResource(R.string.streak_week), Modifier.weight(1f))
        }

        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.streak_on_home_short), fontSize = 19.sp, color = ink, modifier = Modifier.weight(1f))
            Switch(checked = showOnHome, onCheckedChange = onShowOnHomeChange)
        }
    }

    if (showInfo) {
        AlertDialog(
            onDismissRequest = { showInfo = false },
            title = { Text(stringResource(R.string.streak_title)) },
            text = { Text(stringResource(R.string.streak_info_text)) },
            confirmButton = {
                TextButton(onClick = { showInfo = false }) { Text(stringResource(android.R.string.ok)) }
            },
        )
    }
}

@Composable
private fun StreakStat(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        Text(label, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
