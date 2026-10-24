package com.onetake.app.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
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
            newRecognizer().use { recognizeFromBitmapInternal(bitmap, it) }
        }
    }

    /** Recognizers hold native resources — every one is closed via `use`. */
    private fun newRecognizer(): TextRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

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

            try {
                newRecognizer().use { recognizeFromBitmapInternal(bitmap, it) }
            } finally {
                bitmap.recycle()
            }
        }
    }

    private suspend fun extractFromPdf(context: Context, uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                ?: return@runCatching ""
            pfd.use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    newRecognizer().use { recognizer ->
                        val sb = StringBuilder()
                        val pageCount = minOf(renderer.pageCount, 15) // Read up to 15 pages of sides
                        for (i in 0 until pageCount) {
                            val bitmap = renderer.openPage(i).use { page ->
                                Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888).also {
                                    // PdfRenderer draws onto a transparent bitmap; without a white
                                    // page, black text can reach the recogniser as black-on-black.
                                    it.eraseColor(Color.WHITE)
                                    page.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                }
                            }
                            val pageText = try {
                                recognizeFromBitmapInternal(bitmap, recognizer)
                            } finally {
                                bitmap.recycle()
                            }
                            if (pageText.isNotBlank()) {
                                if (sb.isNotEmpty()) sb.append("\n\n")
                                sb.append(pageText)
                            }
                        }
                        sb.toString()
                    }
                }
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
