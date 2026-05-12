package org.medialiteracy

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform

@androidx.compose.runtime.Composable
expect fun setSystemAppearance(isDark: Boolean)