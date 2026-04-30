package org.medialiteracy.ui

import androidx.compose.runtime.Composable

/**
 * Platform-agnostic interface for picking images.
 */
expect class ImagePickerLauncher {
    fun launchGallery()
    fun launchCamera()
}

/**
 * Creates and remembers an [ImagePickerLauncher] for the current platform.
 */
@Composable
expect fun rememberImagePickerLauncher(onResult: (ByteArray?) -> Unit): ImagePickerLauncher

/**
 * Platform-agnostic interface for audio operations.
 */
expect class AudioPickerLauncher {
    fun launchGallery()
    fun startRecording()
    fun stopRecording()
}

/**
 * Creates and remembers an [AudioPickerLauncher] for the current platform.
 */
@Composable
expect fun rememberAudioPickerLauncher(onResult: (ByteArray?) -> Unit): AudioPickerLauncher
