package io.github.bijojp.thingspot.ui.components

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import coil3.compose.AsyncImage
import io.github.bijojp.thingspot.R
import io.github.bijojp.thingspot.ui.InventoryViewModel
import java.io.File

/** Resolves a stored photo's file name to its file. Provided once by the app. */
val LocalPhotoFiles = staticCompositionLocalOf<(String) -> File> { error("LocalPhotoFiles not provided") }

/** A square, rounded photo thumbnail. */
@Composable
fun PhotoThumbnail(photo: String, modifier: Modifier = Modifier) {
    AsyncImage(
        model = LocalPhotoFiles.current(photo),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.size(40.dp).clip(RoundedCornerShape(8.dp)),
    )
}

/** A wide photo, tap to see it full screen. */
@Composable
fun PhotoBanner(photo: String, modifier: Modifier = Modifier) {
    var viewing by rememberSaveable { mutableStateOf(false) }
    AsyncImage(
        model = LocalPhotoFiles.current(photo),
        contentDescription = stringResource(R.string.photo_description),
        contentScale = ContentScale.Crop,
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(4f / 3f)
            .clip(RoundedCornerShape(16.dp))
            .clickable { viewing = true },
    )
    if (viewing) PhotoViewer(photo, onDismiss = { viewing = false })
}

@Composable
private fun PhotoViewer(photo: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black).clickable(onClick = onDismiss)) {
            AsyncImage(
                model = LocalPhotoFiles.current(photo),
                contentDescription = stringResource(R.string.photo_description),
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * The one optional photo of an Item or Place: take it with the camera app or choose one.
 * The new file is imported straight away; it only becomes permanent when the form is saved.
 */
@Composable
fun PhotoField(photo: String?, viewModel: InventoryViewModel, onChange: (String?) -> Unit) {
    val context = LocalContext.current
    var cameraFile by rememberSaveable { mutableStateOf<String?>(null) }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val file = cameraFile?.let(::File)
        cameraFile = null
        if (file != null) {
            if (saved) viewModel.importPhoto(file, onImported = onChange) else file.delete()
        }
    }
    val choosePicture = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.importPhoto(uri, onImported = onChange)
    }
    val takePhoto = {
        val file = viewModel.newCameraFile()
        cameraFile = file.path
        try {
            takePicture.launch(FileProvider.getUriForFile(context, "${context.packageName}.files", file))
        } catch (_: ActivityNotFoundException) {
            cameraFile = null
            viewModel.reportNoCamera()
        }
    }
    val choosePhoto = { choosePicture.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (photo != null) {
            PhotoBanner(photo)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = takePhoto) { Text(stringResource(R.string.photo_retake)) }
                TextButton(onClick = choosePhoto) { Text(stringResource(R.string.photo_choose_other)) }
                TextButton(onClick = { onChange(null) }) { Text(stringResource(R.string.photo_remove)) }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = takePhoto, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.photo_take)) }
                OutlinedButton(onClick = choosePhoto, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.photo_choose)) }
            }
        }
    }
}
