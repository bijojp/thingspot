package io.github.bijojp.thingspot.ui.labels

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import io.github.bijojp.thingspot.R
import io.github.bijojp.thingspot.domain.LabelCode
import io.github.bijojp.thingspot.ui.InventoryViewModel
import io.github.bijojp.thingspot.ui.LocalSnackbarHostState
import io.github.bijojp.thingspot.ui.Navigator
import io.github.bijojp.thingspot.ui.PlaceRoute
import java.util.concurrent.Executors

/**
 * The in-app Label scanner. A linked Label opens its Place; a blank one asks where it goes.
 * Decoding happens on the phone with ZXing; frames are never stored.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(viewModel: InventoryViewModel, nav: Navigator) {
    val inventory = viewModel.inventory.collectAsStateWithLifecycle().value ?: return
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var asked by rememberSaveable { mutableStateOf(false) }
    val askCamera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
        asked = true
    }
    LaunchedEffect(Unit) { if (!granted) askCamera.launch(Manifest.permission.CAMERA) }

    var blankCode by rememberSaveable { mutableStateOf<String?>(null) }
    var sawOtherCode by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.scan_title)) },
                navigationIcon = {
                    IconButton(onClick = nav::back) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_cancel)) }
                },
            )
        },
        snackbarHost = { SnackbarHost(LocalSnackbarHostState.current) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (granted) {
                CameraPreview(
                    modifier = Modifier.fillMaxSize(),
                    onQr = { text ->
                        if (blankCode != null) return@CameraPreview
                        val code = LabelCode.fromQr(text)
                        if (code == null) {
                            sawOtherCode = true
                            return@CameraPreview
                        }
                        val place = inventory.placeFor(code)
                        if (place != null) nav.replaceTop(PlaceRoute(place.id.value)) else blankCode = code.value
                    },
                )
                Text(
                    stringResource(if (sawOtherCode) R.string.scan_not_a_label else R.string.scan_hint),
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(24.dp)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                )
            } else {
                Column(
                    Modifier.fillMaxWidth().align(Alignment.Center).padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(stringResource(R.string.scan_needs_camera), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                    Button(onClick = {
                        if (asked) {
                            context.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                            )
                        } else {
                            askCamera.launch(Manifest.permission.CAMERA)
                        }
                    }) { Text(stringResource(R.string.scan_allow_camera)) }
                }
            }
        }
    }

    blankCode?.let { code ->
        BlankLabelDialog(
            code = LabelCode(code),
            inventory = inventory,
            viewModel = viewModel,
            onLinked = { placeId -> nav.replaceTop(PlaceRoute(placeId.value)) },
            onDismiss = { blankCode = null },
        )
    }
}

@Composable
private fun CameraPreview(modifier: Modifier, onQr: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnQr by rememberUpdatedState(onQr)
    val previewView = remember { PreviewView(context) }

    DisposableEffect(lifecycleOwner) {
        val analysisThread = Executors.newSingleThreadExecutor()
        val mainThread = ContextCompat.getMainExecutor(context)
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            val provider = providerFuture.get()
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { it.setAnalyzer(analysisThread, QrAnalyzer { text -> mainThread.execute { currentOnQr(text) } }) }
            provider.unbindAll()
            runCatching { provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis) }
        }, mainThread)
        onDispose {
            if (providerFuture.isDone) providerFuture.get().unbindAll()
            analysisThread.shutdown()
        }
    }
    AndroidView(factory = { previewView }, modifier = modifier)
}

/** Finds QR codes in the camera's brightness channel. */
private class QrAnalyzer(private val onFound: (String) -> Unit) : ImageAnalysis.Analyzer {
    private val reader = QRCodeReader()
    private val hints = mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE))
    private var bytes = ByteArray(0)

    override fun analyze(image: ImageProxy) {
        image.use {
            val plane = it.planes[0]
            val buffer = plane.buffer.apply { rewind() }
            val size = plane.rowStride * it.height
            if (bytes.size < size) bytes = ByteArray(size)
            buffer.get(bytes, 0, minOf(buffer.remaining(), size))
            val source = PlanarYUVLuminanceSource(bytes, plane.rowStride, it.height, 0, 0, it.width, it.height, false)
            try {
                onFound(reader.decode(BinaryBitmap(HybridBinarizer(source)), hints).text)
            } catch (_: ReaderException) {
                // No QR code in this frame.
            } finally {
                reader.reset()
            }
        }
    }
}
