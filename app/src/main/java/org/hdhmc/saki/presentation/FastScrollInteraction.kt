package org.hdhmc.saki.presentation

import androidx.compose.runtime.staticCompositionLocalOf

internal val LocalFastScrollActiveChange = staticCompositionLocalOf<(Boolean) -> Unit> { {} }
