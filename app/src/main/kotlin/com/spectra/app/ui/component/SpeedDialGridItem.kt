package com.spectra.app.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.spectra.innertube.models.ArtistItem
import com.spectra.innertube.models.SongItem
import com.spectra.innertube.models.YTItem
import com.spectra.app.R
import com.spectra.app.ui.utils.resize

val SpeedDialTitleHeight = 30.dp
val SpeedDialSongCardHeight = 58.dp

const val SpeedDialCoverAspect = 1.3f

@Composable
fun SpeedDialGridItem(
    item: YTItem,
    isPinned: Boolean,
    modifier: Modifier = Modifier,
    isActive: Boolean = false,
    isPlaying: Boolean = false,
) {
    val isArtist = item is ArtistItem
    val coverShape = RoundedCornerShape(HomeCardRadius)
    if (item is SongItem) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Row(
            modifier =
                modifier
                    .songTouchAnimation()
                    .fillMaxWidth()
                    .height(SpeedDialSongCardHeight)
                    .clip(coverShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.72f)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.fillMaxHeight().aspectRatio(1f)) {
                ItemThumbnail(
                    thumbnailUrl = item.thumbnail?.resize(300, 300),
                    isActive = isActive,
                    isPlaying = isPlaying,
                    shape = androidx.compose.ui.graphics.RectangleShape,
                    modifier = Modifier.fillMaxSize(),
                )
                if (isPinned) {
                    Icon(
                        painter = painterResource(R.drawable.ic_push_pin),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.align(Alignment.TopStart).padding(6.dp).size(14.dp),
                    )
                }
            }
            Text(
                text = item.title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 7.dp),
            )
        }
        }
        return
    }
    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(SpeedDialCoverAspect)
                    .then(if (isArtist) Modifier else Modifier.clip(coverShape)),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier =
                    (if (isArtist) Modifier.fillMaxHeight().aspectRatio(1f).clip(coverShape) else Modifier.fillMaxSize()),
            ) {
            ItemThumbnail(
                thumbnailUrl = item.thumbnail?.resize(300, 300),
                isActive = isActive,
                isPlaying = isPlaying,
                shape = coverShape,
                modifier = Modifier.fillMaxSize(),
            )
            if (isPinned) {
                Icon(
                    painter = painterResource(R.drawable.ic_push_pin),
                    contentDescription = null,
                    tint = Color.White,
                    modifier =
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(16.dp),
                )
            }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().height(SpeedDialTitleHeight).padding(start = if (isArtist) 0.dp else 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (isArtist) Arrangement.Center else Arrangement.Start,
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (item !is SongItem) {
                Icon(
                    painter = painterResource(if (isArtist) R.drawable.arrow_downward else R.drawable.navigate_next),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
