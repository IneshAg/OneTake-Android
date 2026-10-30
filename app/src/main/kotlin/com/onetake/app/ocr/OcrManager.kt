package com.onetake.app.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.atan2
import kotlin.math.min

object OcrManager {

    /**
     * Extracts layout-aware text from an in-memory Bitmap.
     */
    suspend fun recognizeTextFromBitmap(bitmap: Bitmap): Result<String> = withContext(Dispatchers.Default) {
        runCatching {
            newRecognizer().use { recognizer ->
                val page = recognizePageInternal(bitmap, recognizer)
                OcrLayoutReconstructor.reconstructPage(page.lines, page.pageWidth, page.pageHeight)
            }
        }
    }

    /**
     * Extracts layout-aware text across multiple page URIs (e.g. multi-page camera scan or multi-photo import),
     * automatically stripping repeated running headers/footers across pages.
     */
    suspend fun recognizeTextFromUris(context: Context, uris: List<Uri>): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            if (uris.isEmpty()) return@runCatching ""
            if (uris.size == 1) return@runCatching recognizeTextFromUri(context, uris.first()).getOrThrow()

            val imagePages = mutableListOf<OcrLayoutReconstructor.PageInput>()
            val chunks = mutableListOf<String>()

            newRecognizer().use { recognizer ->
                for (uri in uris) {
                    val mimeType = context.contentResolver.getType(uri).orEmpty().lowercase()
                    val uriPath = uri.path.orEmpty().lowercase()
                    if (mimeType == "application/pdf" || uriPath.endsWith(".pdf") ||
                        mimeType.startsWith("text/") || mimeType == "application/rtf" ||
                        uriPath.endsWith(".txt") || uriPath.endsWith(".rtf")
                    ) {
                        if (imagePages.isNotEmpty()) {
                            chunks.add(OcrLayoutReconstructor.reconstructDocument(imagePages.toList()))
                            imagePages.clear()
                        }
                        val sub = recognizeTextFromUri(context, uri).getOrNull()?.trim().orEmpty()
                        if (sub.isNotEmpty()) chunks.add(sub)
                    } else {
                        val bmp = loadBitmapFromUri(context, uri) ?: continue
                        try {
                            imagePages.add(recognizePageInternal(bmp, recognizer))
                        } finally {
                            bmp.recycle()
                        }
                    }
                }
            }
            if (imagePages.isNotEmpty()) {
                chunks.add(OcrLayoutReconstructor.reconstructDocument(imagePages))
            }
            chunks.filter { it.isNotBlank() }.joinToString("\n\n")
        }
    }

    /** Recognizers hold native resources — every one is closed via `use`. */
    private fun newRecognizer(): TextRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /**
     * Extracts text from a document or image URI (PDF, RTF, plain text, or image file).
     */
    suspend fun recognizeTextFromUri(context: Context, uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val mimeType = context.contentResolver.getType(uri).orEmpty().lowercase()
            val uriPath = uri.lastPathSegment.orEmpty().lowercase().ifEmpty { uri.path.orEmpty().lowercase() }

            // 1. PDF Sides / Breakdown support (never fall through to BitmapFactory on PDFs!)
            if (mimeType == "application/pdf" || uriPath.endsWith(".pdf")) {
                return@runCatching extractFromPdf(context, uri).getOrThrow()
            }

            // 2. Plain text or RTF sides
            if (mimeType.startsWith("text/") || mimeType == "application/rtf" ||
                uriPath.endsWith(".txt") || uriPath.endsWith(".rtf")
            ) {
                val raw = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
                return@runCatching OcrLayoutReconstructor.stripRtfIfNeeded(raw).trim()
            }

            // 3. Unknown octet-stream magic-byte check (e.g. cloud providers serving %PDF or {\rtf without MIME)
            if (mimeType == "application/octet-stream" || mimeType.isBlank()) {
                val header = ByteArray(8)
                val read = context.contentResolver.openInputStream(uri)?.use { it.read(header) } ?: -1
                if (read >= 4) {
                    val prefix = String(header, 0, read, Charsets.US_ASCII)
                    if (prefix.startsWith("%PDF")) {
                        return@runCatching extractFromPdf(context, uri).getOrThrow()
                    }
                    if (prefix.startsWith("{\\rtf")) {
                        val raw = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
                        return@runCatching OcrLayoutReconstructor.stripRtfIfNeeded(raw).trim()
                    }
                }
            }

            // 4. Image file (JPEG, PNG, HEIC, WebP, etc.)
            val bitmap = loadBitmapFromUri(context, uri)
                ?: error("Could not decode image from URI: $uri")

            try {
                newRecognizer().use { recognizer ->
                    val page = recognizePageInternal(bitmap, recognizer)
                    OcrLayoutReconstructor.reconstructPage(page.lines, page.pageWidth, page.pageHeight)
                }
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
                        val pages = mutableListOf<OcrLayoutReconstructor.PageInput>()
                        val pageCount = minOf(renderer.pageCount, 20)
                        for (i in 0 until pageCount) {
                            val bitmap = renderer.openPage(i).use { page ->
                                // Render at ~216 DPI (3x 72-DPI PDF points), capped safely at 3200px max edge
                                val maxEdge = maxOf(page.width, page.height).coerceAtLeast(1)
                                val scale = min(3f, 3200f / maxEdge).coerceAtLeast(1.5f)
                                val renderW = (page.width * scale).toInt().coerceAtLeast(1)
                                val renderH = (page.height * scale).toInt().coerceAtLeast(1)
                                Bitmap.createBitmap(renderW, renderH, Bitmap.Config.ARGB_8888).also {
                                    it.eraseColor(Color.WHITE)
                                    page.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                                }
                            }
                            try {
                                pages.add(recognizePageInternal(bitmap, recognizer))
                            } finally {
                                bitmap.recycle()
                            }
                        }
                        OcrLayoutReconstructor.reconstructDocument(pages)
                    }
                }
            }
        }
    }

    private fun loadBitmapFromUri(context: Context, uri: Uri): Bitmap? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.isMutableRequired = true
                    val maxDim = maxOf(info.size.width, info.size.height)
                    if (maxDim > 3600) {
                        val scale = 3600f / maxDim
                        decoder.setTargetSize(
                            (info.size.width * scale).toInt().coerceAtLeast(1),
                            (info.size.height * scale).toInt().coerceAtLeast(1),
                        )
                    }
                }
            } else {
                val exifOrientation = context.contentResolver.openInputStream(uri)?.use { stream ->
                    ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                } ?: ExifInterface.ORIENTATION_NORMAL

                val raw = context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                } ?: return null

                val rotation = when (exifOrientation) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
                if (rotation == 0f) raw else {
                    val m = Matrix().apply { postRotate(rotation) }
                    Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true).also {
                        if (it !== raw) raw.recycle()
                    }
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun recognizePageInternal(
        bitmap: Bitmap,
        recognizer: TextRecognizer,
    ): OcrLayoutReconstructor.PageInput = suspendCancellableCoroutine { cont ->
        val inputImage = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(inputImage)
            .addOnSuccessListener { visionText ->
                val boxes = extractLineBoxes(visionText)
                if (boxes.isEmpty() && visionText.text.isNotBlank()) {
                    // Fallback if provider returned text without bounding boxes
                    val fallbackBoxes = visionText.text.lines().mapIndexedNotNull { index, line ->
                        line.trim().takeIf { it.isNotEmpty() }?.let {
                            OcrLineBox(
                                text = it,
                                left = 0f,
                                top = index * 30f,
                                right = 500f,
                                bottom = index * 30f + 22f,
                            )
                        }
                    }
                    cont.resume(OcrLayoutReconstructor.PageInput(fallbackBoxes, bitmap.width, bitmap.height))
                } else {
                    cont.resume(OcrLayoutReconstructor.PageInput(boxes, bitmap.width, bitmap.height))
                }
            }
            .addOnFailureListener { error ->
                cont.resumeWithException(error)
            }
    }

    private fun extractLineBoxes(visionText: Text): List<OcrLineBox> {
        val result = mutableListOf<OcrLineBox>()
        for (block in visionText.textBlocks) {
            for (line in block.lines) {
                val rect = line.boundingBox ?: continue
                val corners = line.cornerPoints
                val angle = if (corners != null && corners.size >= 2) {
                    val dx = (corners[1].x - corners[0].x).toDouble()
                    val dy = (corners[1].y - corners[0].y).toDouble()
                    Math.toDegrees(atan2(dy, dx)).toFloat()
                } else {
                    line.angle
                }
                result.add(
                    OcrLineBox(
                        text = line.text,
                        left = rect.left.toFloat(),
                        top = rect.top.toFloat(),
                        right = rect.right.toFloat(),
                        bottom = rect.bottom.toFloat(),
                        angleDegrees = angle,
                    )
                )
            }
        }
        return result
    }
}
