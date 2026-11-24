package com.tiffles.blockgains.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle

/** M3 default type scale. */
internal val BlockGainsTypography = Typography()

/** Tabular (fixed-width) figures so money columns line up (ui-guidelines → Numbers). */
fun TextStyle.tabular(): TextStyle = copy(fontFeatureSettings = "tnum")
