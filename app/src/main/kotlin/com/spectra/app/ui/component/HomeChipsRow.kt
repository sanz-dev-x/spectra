package com.spectra.app.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spectra.innertube.pages.HomePage

/** Chip suasana di Beranda (Relax, Feel good, Energize, ...): pil kaca cair, yang terpilih diwarnai aksen. */
@Composable
fun HomeChipsRow(
    chips: List<HomePage.Chip>,
    selected: HomePage.Chip?,
    onToggle: (HomePage.Chip) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(50)
    LazyRow(
        contentPadding = PaddingValues(horizontal = HomeSideInset),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.padding(vertical = 8.dp),
    ) {
        items(chips, key = { it.title }) { chip ->
            val touch = remember { MutableInteractionSource() }
            Text(
                text = englishChipTitle(chip.title),
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier =
                    Modifier
                        .songTouchAnimation(touch)
                        .clip(shape)
                        .background(
                            if (chip == selected) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f)
                            },
                        )
                        .clickable(interactionSource = touch, indication = null) { onToggle(chip) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

private val EnglishChipTitles =
    mapOf(
        "senang" to "Feel good",
        "bersantai" to "Relax",
        "santai" to "Relax",
        "mengisi energi" to "Energize",
        "energi" to "Energize",
        "perjalanan" to "Commute",
        "olahraga" to "Workout",
        "latihan" to "Workout",
        "fokus" to "Focus",
        "pesta" to "Party",
        "romantis" to "Romance",
        "sedih" to "Sad",
        "tidur" to "Sleep",
        "podcast" to "Podcasts",
        "jelajahi" to "Explore",
    )

private fun englishChipTitle(title: String): String = EnglishChipTitles[title.trim().lowercase()] ?: title
