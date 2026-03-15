package com.daklok.pixelguard.ui

import androidx.compose.ui.graphics.ImageBitmap

data class AppItem(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap,
    val isLocked: Boolean
)
