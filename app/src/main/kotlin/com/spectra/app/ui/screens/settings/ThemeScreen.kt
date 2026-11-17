package com.spectra.app.ui.screens.settings

import android.content.res.Configuration
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.spectra.app.ui.component.TopAppBar
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.materialkolor.PaletteStyle
import com.materialkolor.rememberDynamicColorScheme
import com.spectra.app.R
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Slider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.spectra.app.constants.AppFontFamilyKey
import com.spectra.app.constants.AppFontSizeKey
import com.spectra.app.constants.AppFontWeightKey
import com.spectra.app.ui.theme.AppFont
import com.spectra.app.ui.theme.FontBaseSize
import com.spectra.app.ui.theme.FontDefaultWeight
import com.spectra.app.ui.theme.FontMaxSize
import com.spectra.app.ui.theme.FontMinSize
import com.spectra.app.ui.theme.appFontFamily
import com.spectra.app.ui.theme.importFont
import com.spectra.app.ui.theme.isFontImported
import kotlin.math.roundToInt
import com.spectra.app.constants.DarkModeKey
import com.spectra.app.constants.DynamicThemeKey
import com.spectra.app.constants.PureBlackKey
import com.spectra.app.constants.PureBlackMiniPlayerKey
import com.spectra.app.constants.SelectedThemeColorKey
import com.spectra.app.ui.theme.BrandSeedColor
import com.spectra.app.ui.theme.DefaultThemeColor
import com.spectra.app.ui.theme.SpectraTheme
import com.spectra.app.utils.rememberEnumPreference
import com.spectra.app.utils.rememberPreference

data class ThemePalette(
    val nameRes: Int,
    val seedColor: Color
)

val PaletteColors = listOf(
    ThemePalette(R.string.palette_dynamic, Color.Transparent), // Sentinel for System/Dynamic colors
    ThemePalette(R.string.palette_crimson, Color(0xFFEC5464)), // Slightly shifted from DefaultThemeColor (0xFFED5564) to avoid conflict
    ThemePalette(R.string.palette_rose, Color(0xFFD81B60)),
    ThemePalette(R.string.palette_purple, Color(0xFF8E24AA)),
    ThemePalette(R.string.palette_deep_purple, Color(0xFF5E35B1)),
    ThemePalette(R.string.palette_indigo, Color(0xFF3949AB)),
    ThemePalette(R.string.palette_blue, Color(0xFF1E88E5)),
    ThemePalette(R.string.palette_sky_blue, Color(0xFF039BE5)),
    ThemePalette(R.string.palette_cyan, Color(0xFF00ACC1)),
    ThemePalette(R.string.palette_teal, Color(0xFF00897B)),
    ThemePalette(R.string.palette_green, Color(0xFF43A047)),
    ThemePalette(R.string.palette_light_green, Color(0xFF7CB342)),
    ThemePalette(R.string.palette_lime, Color(0xFFC0CA33)),
    ThemePalette(R.string.palette_yellow, Color(0xFFFDD835)),
    ThemePalette(R.string.palette_amber, Color(0xFFFFB300)),
    ThemePalette(R.string.palette_orange, Color(0xFFFB8C00)),
    ThemePalette(R.string.palette_deep_orange, Color(0xFFF4511E)),
    ThemePalette(R.string.palette_brown, Color(0xFF6D4C41)),
    ThemePalette(R.string.palette_grey, Color(0xFF757575)),
    ThemePalette(R.string.palette_blue_grey, Color(0xFF546E7A)),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeScreen(
    navController: NavController,
) {
    val (darkMode, onDarkModeChange) = rememberEnumPreference(DarkModeKey, DarkMode.AUTO)
    val (pureBlack, onPureBlackChangeRaw) = rememberPreference(PureBlackKey, defaultValue = false)
    val (_, onPureBlackMiniPlayerChange) = rememberPreference(
        PureBlackMiniPlayerKey,
        defaultValue = false
    )

    val onPureBlackChange: (Boolean) -> Unit = { enabled ->
        onPureBlackChangeRaw(enabled)
        onPureBlackMiniPlayerChange(enabled)
    }
    val (selectedThemeColorInt, onSelectedThemeColorChange) = rememberPreference(
        SelectedThemeColorKey,
        DefaultThemeColor.toArgb()
    )
    val (_, onDynamicThemeChange) = rememberPreference(DynamicThemeKey, defaultValue = true)

    val selectedThemeColor = Color(selectedThemeColorInt)
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Helper function to handle color selection with dynamic theme toggle
    val handleColorSelection: (Color) -> Unit = { color ->
        onSelectedThemeColorChange(color.toArgb())
        // Enable dynamic theme only when selecting the default/dynamic color
        // Disable it when selecting any other color
        val isDynamicColor = color == DefaultThemeColor
        onDynamicThemeChange(isDynamicColor)
    }

    if (isLandscape) {
        LandscapeThemeLayout(
            innerPadding = PaddingValues(0.dp),
            darkMode = darkMode,
            onDarkModeChange = onDarkModeChange,
            pureBlack = pureBlack,
            onPureBlackChange = onPureBlackChange,
            selectedThemeColor = selectedThemeColor,
            onSelectedThemeColorChange = handleColorSelection
        )
    } else {
        PortraitThemeLayout(
            innerPadding = PaddingValues(0.dp),
            darkMode = darkMode,
            onDarkModeChange = onDarkModeChange,
            pureBlack = pureBlack,
            onPureBlackChange = onPureBlackChange,
            selectedThemeColor = selectedThemeColor,
            onSelectedThemeColorChange = handleColorSelection
        )
    }

    TopAppBar(
        title = { Text(stringResource(R.string.theme_colors)) },
        navigationIcon = {
            IconButton(onClick = { navController.navigateUp() }) {
                Icon(
                    painter = painterResource(R.drawable.arrow_back),
                    contentDescription = stringResource(R.string.cd_back)
                )
            }
        }
    )
}

@Composable
fun PortraitThemeLayout(
    innerPadding: PaddingValues,
    darkMode: DarkMode,
    onDarkModeChange: (DarkMode) -> Unit,
    pureBlack: Boolean,
    onPureBlackChange: (Boolean) -> Unit,
    selectedThemeColor: Color,
    onSelectedThemeColorChange: (Color) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(innerPadding),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .width(120.dp)
                .height(240.dp),
            contentAlignment = Alignment.Center
        ) {
            ThemeMockupPortrait(
                darkMode = darkMode,
                pureBlack = pureBlack,
                themeColor = selectedThemeColor
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        ThemeControls(
            darkMode = darkMode,
            onDarkModeChange = onDarkModeChange,
            pureBlack = pureBlack,
            onPureBlackChange = onPureBlackChange,
            selectedThemeColor = selectedThemeColor,
            onSelectedThemeColorChange = onSelectedThemeColorChange
        )

        Spacer(modifier = Modifier.height(120.dp))
    }
}

@Composable
fun LandscapeThemeLayout(
    innerPadding: PaddingValues,
    darkMode: DarkMode,
    onDarkModeChange: (DarkMode) -> Unit,
    pureBlack: Boolean,
    onPureBlackChange: (Boolean) -> Unit,
    selectedThemeColor: Color,
    onSelectedThemeColorChange: (Color) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
    ) {
        Column(
            modifier = Modifier
                .weight(0.4f)
                .fillMaxHeight()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .heightIn(max = 300.dp),
                contentAlignment = Alignment.Center
            ) {
                ThemeMockup(
                    darkMode = darkMode,
                    pureBlack = pureBlack,
                    themeColor = selectedThemeColor
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(0.6f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(end = 16.dp, top = 16.dp, bottom = 16.dp)
        ) {
            ThemeControls(
                darkMode = darkMode,
                onDarkModeChange = onDarkModeChange,
                pureBlack = pureBlack,
                onPureBlackChange = onPureBlackChange,
                selectedThemeColor = selectedThemeColor,
                onSelectedThemeColorChange = onSelectedThemeColorChange
            )

            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@Composable
fun ThemeControls(
    darkMode: DarkMode,
    onDarkModeChange: (DarkMode) -> Unit,
    pureBlack: Boolean,
    onPureBlackChange: (Boolean) -> Unit,
    selectedThemeColor: Color,
    onSelectedThemeColorChange: (Color) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.theme_mode),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // System mode (AUTO)
                    ModeCircle(
                        darkMode = darkMode,
                        pureBlack = pureBlack,
                        targetMode = DarkMode.AUTO,
                        targetPureBlack = pureBlack,
                        onClick = {
                            onDarkModeChange(DarkMode.AUTO)
                        },
                        showIcon = true
                    )
                    
                    // Vertical divider to separate System from manual modes
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(32.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant)
                    )
                    
                    // Manual modes (Light, Dark, Pure Black)
                    ModeCircle(
                        darkMode = darkMode,
                        pureBlack = pureBlack,
                        targetMode = DarkMode.OFF,
                        targetPureBlack = false,
                        onClick = {
                            onDarkModeChange(DarkMode.OFF)
                            onPureBlackChange(false)
                        },
                        showIcon = false
                    )
                    
                    ModeCircle(
                        darkMode = darkMode,
                        pureBlack = pureBlack,
                        targetMode = DarkMode.ON,
                        targetPureBlack = false,
                        onClick = {
                            onDarkModeChange(DarkMode.ON)
                            onPureBlackChange(false)
                        },
                        showIcon = false
                    )
                    
                    ModeCircle(
                        darkMode = darkMode,
                        pureBlack = pureBlack,
                        targetMode = DarkMode.ON,
                        targetPureBlack = true,
                        onClick = {
                            onDarkModeChange(DarkMode.ON)
                            onPureBlackChange(true)
                        },
                        showIcon = false
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.color_palette),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    items(PaletteColors) { palette ->
                        val isDynamicPalette = palette.seedColor == Color.Transparent
                        val isSelected = if (isDynamicPalette) {
                            selectedThemeColor == DefaultThemeColor
                        } else {
                            selectedThemeColor == palette.seedColor
                        }
                        
                        PaletteItem(
                            palette = palette,
                            isSelected = isSelected,
                            onClick = { 
                                val colorToSave = if (isDynamicPalette) DefaultThemeColor else palette.seedColor
                                onSelectedThemeColorChange(colorToSave) 
                            }
                        )
                    }
                }
            }

            FontControls()
        }
    }
}

@Composable
fun ModeCircle(
    darkMode: DarkMode,
    pureBlack: Boolean,
    targetMode: DarkMode,
    targetPureBlack: Boolean,
    showIcon: Boolean,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val isSystemDark = isSystemInDarkTheme()
    val isSelected = darkMode == targetMode && pureBlack == targetPureBlack
    
    val effectiveDark = when (targetMode) {
        DarkMode.AUTO -> isSystemDark
        DarkMode.ON -> true
        DarkMode.OFF -> false
    }
    
    // Use actual system colors for AUTO mode on Android 12+
    val modeColorScheme = if (targetMode == DarkMode.AUTO && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (effectiveDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        rememberDynamicColorScheme(
            seedColor = BrandSeedColor,
            isDark = effectiveDark,
            style = PaletteStyle.TonalSpot
        )
    }
    
    val fillColor = when {
        targetPureBlack -> Color.Black
        effectiveDark -> modeColorScheme.surface
        else -> modeColorScheme.surface
    }
    
    // Animated border width
    val borderWidth by animateDpAsState(
        targetValue = if (isSelected) 3.dp else 0.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "borderWidth"
    )
    
    // Animated scale for the entire circle
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.05f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "scale"
    )
    
    val interactionSource = remember { MutableInteractionSource() }
    
    val contentDesc = when {
        targetPureBlack -> stringResource(R.string.cd_pure_black_mode)
        targetMode == DarkMode.OFF -> stringResource(R.string.cd_light_mode)
        targetMode == DarkMode.ON -> stringResource(R.string.cd_dark_mode)
        else -> stringResource(R.string.cd_system_mode)
    }
    
    Box(
        modifier = Modifier
            .size(48.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(fillColor)
            .then(
                if (borderWidth > 0.dp) {
                    Modifier.border(
                        width = borderWidth,
                        color = MaterialTheme.colorScheme.inversePrimary,
                        shape = CircleShape
                    )
                } else {
                    Modifier
                }
            )
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(),
                onClick = onClick
            )
            .semantics {
                contentDescription = contentDesc
            },
        contentAlignment = Alignment.Center
    ) {
        when {
            showIcon -> {
                Icon(
                    painter = painterResource(R.drawable.sync),
                    contentDescription = null,
                    tint = modeColorScheme.onSurface,
                    modifier = Modifier.size(20.dp)
                )
            }
            isSelected -> {
                AnimatedVisibility(
                    visible = isSelected,
                    enter = fadeIn(animationSpec = tween(300)) + scaleIn(
                        initialScale = 0.3f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        )
                    ),
                    exit = fadeOut(animationSpec = tween(150)) + scaleOut(
                        targetScale = 0.3f,
                        animationSpec = tween(150)
                    )
                ) {
                    Icon(
                        painter = painterResource(R.drawable.check),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.inversePrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun PaletteItem(
    palette: ThemePalette,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val isSystemDark = isSystemInDarkTheme()
    
    val colorScheme = rememberDynamicColorScheme(
        seedColor = palette.seedColor,
        isDark = isSystemDark,
        style = PaletteStyle.TonalSpot
    )
    
    val cornerRadius by animateDpAsState(
        targetValue = if (isSelected) 48.dp * 0.25f else 24.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "cornerRadius"
    )
    
    val borderWidth by animateDpAsState(
        targetValue = if (isSelected) 3.dp else 0.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "borderWidth"
    )
    
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.08f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "scale"
    )
    
    val shape = RoundedCornerShape(cornerRadius)
    val interactionSource = remember { MutableInteractionSource() }
    
    val paletteName = stringResource(palette.nameRes)
    val contentDesc = stringResource(R.string.cd_palette_item, paletteName)
    
    Box(
        modifier = Modifier
            .size(48.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .then(
                if (borderWidth > 0.dp) {
                    Modifier.border(
                        width = borderWidth,
                        color = MaterialTheme.colorScheme.inversePrimary,
                        shape = shape
                    )
                } else {
                    Modifier
                }
            )
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(),
                onClick = onClick
            )
            .semantics {
                contentDescription = contentDesc
            }
    ) {
        if (palette.seedColor == Color.Transparent) {
            // Draw Dynamic/System icon using Material Design icon
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.palette),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }
        } else {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val height = size.height
                
                drawRect(
                    color = colorScheme.onPrimary,
                    topLeft = Offset(0f, 0f),
                    size = Size(width, height / 2)
                )
                
                drawRect(
                    color = colorScheme.secondary,
                    topLeft = Offset(0f, height / 2),
                    size = Size(width / 2, height / 2)
                )
                
                drawRect(
                    color = colorScheme.tertiary,
                    topLeft = Offset(width / 2, height / 2),
                    size = Size(width / 2, height / 2)
                )
            }
        }
    }
}

@Composable
fun ThemeMockup(
    darkMode: DarkMode,
    pureBlack: Boolean,
    themeColor: Color
) {
    val isSystemDark = isSystemInDarkTheme()
    val useDark = when (darkMode) {
        DarkMode.AUTO -> isSystemDark
        DarkMode.ON -> true
        DarkMode.OFF -> false
    }

    SpectraTheme(
        darkTheme = useDark,
        pureBlack = pureBlack,
        themeColor = themeColor
    ) {
        Card(
            modifier = Modifier
                .fillMaxSize()
                .aspectRatio(9f / 18f),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .padding(10.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape)
                        )
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .background(MaterialTheme.colorScheme.secondary, CircleShape)
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(32.dp)
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp))
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .background(MaterialTheme.colorScheme.secondary, RoundedCornerShape(6.dp))
                        )
                        
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .background(MaterialTheme.colorScheme.tertiary, RoundedCornerShape(6.dp))
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    contentAlignment = Alignment.BottomEnd
                ) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                    )
                }
            }
        }
    }
}

@Composable
fun ThemeMockupPortrait(
    darkMode: DarkMode,
    pureBlack: Boolean,
    themeColor: Color
) {
    val isSystemDark = isSystemInDarkTheme()
    val useDark = when (darkMode) {
        DarkMode.AUTO -> isSystemDark
        DarkMode.ON -> true
        DarkMode.OFF -> false
    }

    SpectraTheme(
        darkTheme = useDark,
        pureBlack = pureBlack,
        themeColor = themeColor
    ) {
        Card(
            modifier = Modifier
                .fillMaxSize(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Header (20% of height)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.2f)
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .padding(6.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape)
                        )
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(MaterialTheme.colorScheme.secondary, CircleShape)
                        )
                    }
                }

                // Main Content (60% of height)
                Column(
                    modifier = Modifier
                        .weight(0.6f)
                        .padding(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                    )
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1.2f),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.secondary, RoundedCornerShape(4.dp))
                        )
                        
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.tertiary, RoundedCornerShape(4.dp))
                        )
                    }
                }

                // FAB Area (20% of height)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.2f)
                        .padding(6.dp),
                    contentAlignment = Alignment.BottomEnd
                ) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                    )
                }
            }
        }
    }
}


private val FontWeightOptions = listOf(300 to R.string.sx_font_weight_light, 400 to R.string.sx_font_weight_regular, 500 to R.string.sx_font_weight_medium, 600 to R.string.sx_font_weight_semibold, 700 to R.string.sx_font_weight_bold)

@Composable
private fun FontControls() {
    val context = LocalContext.current
    val (fontId, setFontId) = rememberPreference(AppFontFamilyKey, defaultValue = AppFont.SYSTEM.id)
    val (weight, setWeight) = rememberPreference(AppFontWeightKey, defaultValue = FontDefaultWeight)
    val (size, setSize) = rememberPreference(AppFontSizeKey, defaultValue = FontBaseSize)
    val selected = AppFont.byId(fontId)
    var importTick by remember { mutableIntStateOf(0) }
    var failed by remember { mutableStateOf(false) }
    val importer =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                failed = !importFont(context, selected, uri)
                importTick++
            }
        }
    val imported = remember(selected, importTick) { isFontImported(context, selected) }
    val family = remember(selected, weight, importTick) { appFontFamily(context, selected, weight) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(R.string.sx_font_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(AppFont.entries) { f ->
                FontChip(stringResource(f.label), f == selected) { setFontId(f.id) }
            }
        }
        if (selected != AppFont.SYSTEM) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(if (failed) R.string.sx_font_import_failed else if (imported) R.string.sx_font_imported else R.string.sx_font_not_imported),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                FontChip(stringResource(R.string.sx_font_import), false) { importer.launch(arrayOf("font/*", "application/octet-stream", "application/x-font-ttf", "application/x-font-opentype")) }
            }
        }
        Text(stringResource(R.string.sx_font_weight), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(FontWeightOptions) { (w, label) -> FontChip(stringResource(label), w == weight) { setWeight(w) } }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.sx_font_size), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
            Text("${size.roundToInt()} sp", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = size,
            onValueChange = { setSize(it.roundToInt().toFloat()) },
            valueRange = FontMinSize..FontMaxSize,
            steps = (FontMaxSize - FontMinSize).toInt() - 1,
        )
        Text(
            text = stringResource(R.string.sx_font_preview),
            fontFamily = family,
            fontWeight = FontWeight(weight),
            fontSize = size.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                .padding(16.dp),
        )
    }
}

@Composable
private fun FontChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    )
}
