package com.wolnerman.calories

import android.content.Context
import android.net.Uri
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

/** Lecture OCR hors-ligne d'une photo d'étiquette (ML Kit latin embarqué, sans clé API). */
object LabelOcr {
    suspend fun readLines(ctx: Context, photoPath: String): List<OcrLine> = withContext(Dispatchers.IO) {
        val file = File(photoPath)
        require(file.exists() && file.length() > 0L) { "photo introuvable" }
        val image = InputImage.fromFilePath(ctx, Uri.fromFile(file))
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val result = Tasks.await(recognizer.process(image), 20, TimeUnit.SECONDS)
            result.textBlocks.flatMap { block ->
                block.lines.map { line ->
                    val box = line.boundingBox
                    OcrLine(
                        text = line.text.trim(),
                        height = box?.height(),
                        width = box?.width(),
                        top = box?.top,
                    )
                }
            }.filter { it.text.isNotBlank() }
        } finally {
            runCatching { recognizer.close() }
        }
    }

    suspend fun extract(ctx: Context, photoPath: String): OcrExtraction = try {
        val lines = readLines(ctx, photoPath)
        if (lines.isEmpty()) OcrExtraction(rawText = "", usable = false)
        else LabelOcrLogic.extract(lines)
    } catch (_: Throwable) {
        OcrExtraction(rawText = "", usable = false)
    }
}
