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

    // For full-resolution camera capture
    var photoUri by remember { mutableStateOf<Uri?>(null) }
    
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val bytes = withContext(Dispatchers.IO) {
                    try {
                        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    } catch (e: Exception) {
                        org.medialiteracy.domain.Logger.e("MediaPicker", "Gallery read error: ${e.message}")
                        null
                    }
                }
                onResult(bytes)
            }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            photoUri?.let { uri ->
                scope.launch {
                    val bytes = withContext(Dispatchers.IO) {
                        try {
                            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        } catch (e: Exception) {
                            org.medialiteracy.domain.Logger.e("MediaPicker", "Camera read error: ${e.message}")
                            null
                        }
                    }
                    onResult(bytes)
                }
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val file = File(context.cacheDir, "camera_capture_${System.currentTimeMillis()}.jpg")
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file
            )
            photoUri = uri
            cameraLauncher.launch(uri)
        }
    }

    return remember {
        ImagePickerLauncher(
            onGallery = { galleryLauncher.launch("image/*") },
            onCamera = { 
                val permission = Manifest.permission.CAMERA
                if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
                    val file = File(context.cacheDir, "camera_capture_${System.currentTimeMillis()}.jpg")
                    val uri = androidx.core.content.FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.provider",
                        file
                    )
                    photoUri = uri
                    cameraLauncher.launch(uri)
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
                    val rawBytes = file.readBytes()
                    // Decode to PCM before returning
                    kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                        onResult(org.medialiteracy.domain.AudioDecoder.decodeToPcm(context, rawBytes))
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

@Composable
actual fun rememberAudioPickerLauncher(
    onLoading: (Boolean) -> Unit,
    onResult: (ByteArray?) -> Unit
): AudioPickerLauncher {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            onLoading(true)
            scope.launch {
                val pcmBytes = withContext(Dispatchers.IO) {
                    val rawBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    if (rawBytes != null) {
                        org.medialiteracy.domain.AudioDecoder.decodeToPcm(context, rawBytes)
                    } else null
                }
                onResult(pcmBytes)
                onLoading(false)
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
