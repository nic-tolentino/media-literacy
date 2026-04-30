package org.medialiteracy.ui

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.launch
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

actual class ImagePickerLauncher(
    private val onGallery: () -> Unit,
    private val onCamera: () -> Unit
) {
    actual fun launchGallery() = onGallery()
    actual fun launchCamera() = onCamera()
}

@Composable
actual fun rememberImagePickerLauncher(onResult: (ByteArray?) -> Unit): ImagePickerLauncher {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val bytes = withContext(Dispatchers.IO) {
                    try {
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            val bitmap = android.graphics.BitmapFactory.decodeStream(input)
                            if (bitmap != null) {
                                val scaled = Bitmap.createScaledBitmap(bitmap, 448, 448, true)
                                org.medialiteracy.domain.Logger.d("MediaPicker", "Gallery: Scaled bitmap to ${scaled.width}x${scaled.height}")
                                val stream = ByteArrayOutputStream()
                                scaled.compress(Bitmap.CompressFormat.JPEG, 90, stream)
                                stream.toByteArray()
                            } else null
                        }
                    } catch (e: Exception) {
                        org.medialiteracy.domain.Logger.e("MediaPicker", "Gallery processing error: ${e.message}")
                        null
                    }
                }
                onResult(bytes)
            }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            scope.launch {
                val bytes = withContext(Dispatchers.IO) {
                    // Vision models often expect square images (e.g., 224x224 or 448x448)
                    // The 180x240 thumbnail might be triggering a native patcher bug
                    val scaled = Bitmap.createScaledBitmap(bitmap, 448, 448, true)
                    org.medialiteracy.domain.Logger.d("MediaPicker", "Camera: Scaled bitmap to ${scaled.width}x${scaled.height}")
                    val stream = ByteArrayOutputStream()
                    scaled.compress(Bitmap.CompressFormat.JPEG, 90, stream)
                    stream.toByteArray()
                }
                onResult(bytes)
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            cameraLauncher.launch()
        }
    }

    return remember {
        ImagePickerLauncher(
            onGallery = { galleryLauncher.launch("image/*") },
            onCamera = { 
                val permission = Manifest.permission.CAMERA
                if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
                    cameraLauncher.launch()
                } else {
                    permissionLauncher.launch(permission)
                }
            }
        )
    }
}

actual class AudioPickerLauncher(
    private val context: Context,
    private val onResult: (ByteArray?) -> Unit
) {
    private var mediaRecorder: MediaRecorder? = null
    private var audioFile: File? = null
    private var galleryLauncher: (() -> Unit)? = null
    private var permissionRequester: (() -> Unit)? = null

    fun setGalleryLauncher(launcher: () -> Unit) {
        this.galleryLauncher = launcher
    }

    fun setPermissionRequester(requester: () -> Unit) {
        this.permissionRequester = requester
    }

    actual fun launchGallery() {
        galleryLauncher?.invoke()
    }

    actual fun startRecording() {
        val permission = Manifest.permission.RECORD_AUDIO
        if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
            performRecording()
        } else {
            permissionRequester?.invoke()
        }
    }

    private fun performRecording() {
        try {
            audioFile = File(context.cacheDir, "temp_audio.m4a")
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(audioFile!!.absolutePath)
                prepare()
                start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    actual fun stopRecording() {
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            
            audioFile?.let { file ->
                if (file.exists()) {
                    onResult(file.readBytes())
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

@Composable
actual fun rememberAudioPickerLauncher(onResult: (ByteArray?) -> Unit): AudioPickerLauncher {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val bytes = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                }
                onResult(bytes)
            }
        }
    }

    val launcher = remember { AudioPickerLauncher(context, onResult) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            launcher.startRecording()
        }
    }
    
    SideEffect {
        launcher.setGalleryLauncher { galleryLauncher.launch("audio/*") }
        launcher.setPermissionRequester { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }
    }

    return launcher
}
