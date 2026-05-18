package com.example.llama

import android.graphics.BitmapFactory
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class ImageProcessor {

    suspend fun analyzeImage(imageFile: File): ImageAnalysisResult {
        val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
            ?: throw IllegalArgumentException("Could not load image")

        val inputImage = InputImage.fromBitmap(bitmap, 0)

        // Run both detectors concurrently
        val labelsDeferred = suspendCancellableCoroutine { continuation ->
            val labeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)
            labeler.process(inputImage)
                .addOnSuccessListener { labels ->
                    val description = labels.joinToString(", ") { it.text }
                    continuation.resume(description)
                    labeler.close()
                }
                .addOnFailureListener { e ->
                    continuation.resumeWithException(e)
                    labeler.close()
                }
        }

        val textDeferred = suspendCancellableCoroutine { continuation ->
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            recognizer.process(inputImage)
                .addOnSuccessListener { visionText ->
                    continuation.resume(visionText.text)
                    recognizer.close()
                }
                .addOnFailureListener { e ->
                    continuation.resumeWithException(e)
                    recognizer.close()
                }
        }

        return ImageAnalysisResult(labelsDeferred, textDeferred)
    }
}

data class ImageAnalysisResult(val labels: String, val extractedText: String)