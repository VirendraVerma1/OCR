package com.kreasaar.ocr

import android.Manifest
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.Size
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.kreasaar.ocr.ui.theme.OcrmodelTheme
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.Executors

@ExperimentalMaterial3Api
class MainActivity : ComponentActivity() {

    private lateinit var imageCapture: ImageCapture
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val handler = Handler(Looper.getMainLooper())

    private val highlightWords = listOf("20", "24", "40", "60", "67")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        setContent {
            OcrmodelTheme {
                var recognizedText by remember { mutableStateOf("Text will appear here") }
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                        val context = LocalContext.current
                        val lifecycleOwner = LocalLifecycleOwner.current
                        val cameraPreview = remember { Preview.Builder().build() }
                        val previewView = remember { androidx.camera.view.PreviewView(context) }

                        Button(
                            onClick = {
                                val cameraProvider = cameraProviderFuture.get()
                                cameraProvider.unbindAll()
                                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                                imageCapture = ImageCapture.Builder().build()

                                cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    cameraSelector,
                                    cameraPreview,
                                    imageCapture
                                )

                                cameraPreview.setSurfaceProvider(previewView.surfaceProvider)

                                startOcrRecognition { text ->
                                    recognizedText = text
                                }
                            },
                            modifier = Modifier.fillMaxWidth().padding(16.dp)
                        ) {
                            Text(text = "Open Camera")
                        }

                        AndroidView(factory = { previewView }, modifier = Modifier.weight(1f))

                        HighlightedText(
                            text = recognizedText,
                            highlightWords = highlightWords,
                            modifier = Modifier.fillMaxWidth().padding(16.dp)
                        )
                    }
                }
            }
        }

        val requestPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                // Permission is granted. Continue the action or workflow in your app.
            } else {
                // Explain to the user that the feature is unavailable because
                // the feature requires a permission that the user has denied.
            }
        }

        requestPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    private fun startOcrRecognition(onTextRecognized: (String) -> Unit) {
        val captureInterval = 2000L // 2 seconds

        val captureRunnable = object : Runnable {
            override fun run() {
                imageCapture.takePicture(
                    ContextCompat.getMainExecutor(this@MainActivity),
                    object : ImageCapture.OnImageCapturedCallback() {
                        override fun onCaptureSuccess(imageProxy: ImageProxy) {
                            val mediaImage = imageProxy.image
                            if (mediaImage != null) {
                                val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                                textRecognizer.process(image)
                                    .addOnSuccessListener { visionText ->
                                        onTextRecognized(visionText.text)
                                    }
                                    .addOnFailureListener { e ->
                                        Log.e("OCR", "Text recognition failed", e)
                                    }
                                    .addOnCompleteListener {
                                        imageProxy.close()
                                    }
                            }
                        }

                        override fun onError(exception: ImageCaptureException) {
                            Log.e("OCR", "Image capture failed", exception)
                        }
                    }
                )
                handler.postDelayed(this, captureInterval)
            }
        }
        handler.post(captureRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        handler.removeCallbacksAndMessages(null)
    }
}

@Composable
fun HighlightedText(text: String, highlightWords: List<String>, modifier: Modifier = Modifier) {
    val annotatedString = buildAnnotatedString {
        append(text)
        highlightWords.forEach { word ->
            val start = text.indexOf(word)
            if (start >= 0) {
                addStyle(
                    style = SpanStyle(color = Color.Green),
                    start = start,
                    end = start + word.length
                )
            }
        }
    }

    Text(
        text = annotatedString,
        modifier = modifier
    )
}
