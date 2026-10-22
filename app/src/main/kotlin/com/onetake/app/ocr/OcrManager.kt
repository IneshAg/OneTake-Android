package com.onetake.app.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object OcrManager {

    /**
     * Extracts text from an in-memory Bitmap (e.g. from camera scan preview).
     */
    suspend fun recognizeTextFromBitmap(bitmap: Bitmap): Result<String> = withContext(Dispatchers.Default) {
        runCatching {
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            recognizeFromBitmapInternal(bitmap, recognizer)
        }
    }

    /**
     * Extracts text from a document or image URI (PDF, image file, or text document).
     */
    suspend fun recognizeTextFromUri(context: Context, uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val mimeType = context.contentResolver.getType(uri).orEmpty().lowercase()
            val uriPath = uri.path.orEmpty().lowercase()

            // 1. PDF Sides support: Render pages and run OCR on each page
            if (mimeType == "application/pdf" || uriPath.endsWith(".pdf")) {
                val pdfText = extractFromPdf(context, uri).getOrNull()
                if (!pdfText.isNullOrBlank()) {
                    return@runCatching pdfText
                }
            }

            // 2. Plain text sides
            if (mimeType.startsWith("text/") || uriPath.endsWith(".txt")) {
                val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                if (!text.isNullOrBlank()) {
                    return@runCatching text
                }
            }

            // 3. Image file (JPEG, PNG, HEIC, WebP, etc.)
            val bitmap = loadBitmapFromUri(context, uri)
                ?: error("Could not decode image from URI: $uri")

            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val result = recognizeFromBitmapInternal(bitmap, recognizer)
            runCatching { bitmap.recycle() }
            result
        }
    }

    private suspend fun extractFromPdf(context: Context, uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                ?: return@runCatching ""
            pfd.use { descriptor ->
                val renderer = PdfRenderer(descriptor)
                val sb = StringBuilder()
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                val pageCount = minOf(renderer.pageCount, 15) // Read up to 15 pages of sides

                for (i in 0 until pageCount) {
                    val page = renderer.openPage(i)
                    val width = page.width * 2
                    val height = page.height * 2
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()

                    val pageText = recognizeFromBitmapInternal(bitmap, recognizer)
                    runCatching { bitmap.recycle() }
                    if (pageText.isNotBlank()) {
                        if (sb.isNotEmpty()) sb.append("\n\n")
                        sb.append(pageText)
                    }
                }
                renderer.close()
                sb.toString()
            }
        }
    }

    private fun loadBitmapFromUri(context: Context, uri: Uri): Bitmap? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.isMutableRequired = true
                }
            } else {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun recognizeFromBitmapInternal(
        bitmap: Bitmap,
        recognizer: TextRecognizer
    ): String = suspendCancellableCoroutine { cont ->
        val inputImage = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(inputImage)
            .addOnSuccessListener { visionText ->
                cont.resume(visionText.text)
            }
            .addOnFailureListener { error ->
                cont.resumeWithException(error)
            }
    }
}
