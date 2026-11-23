package com.wolnerman.calories

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.min

/**
 * Reconnaissance hors-ligne avec le modèle Google AIY « Food V1 » (TFLite quantifié, 2023 plats, licence Apache 2.0).
 * Entrée 192×192 RGB uint8, sortie 2024 scores uint8 (échelle 1/256). Moins précis que Gemini, pas de quantité.
 */
object OfflineFood {
    private const val MODEL = "aiy_food_v1.tflite"
    private const val LABELS = "aiy_food_labels_en.txt"
    private const val SIZE = 192
    private var interpreter: Interpreter? = null
    private var labels: List<String> = emptyList()

    @Synchronized
    private fun load(ctx: Context): Interpreter {
        interpreter?.let { return it }
        val afd = ctx.assets.openFd(MODEL)
        val buf: MappedByteBuffer = FileInputStream(afd.fileDescriptor).channel
            .map(FileChannel.MapMode.READ_ONLY, afd.startOffset, afd.declaredLength)
        labels = ctx.assets.open(LABELS).bufferedReader().readLines()
        return Interpreter(buf, Interpreter.Options().setNumThreads(2)).also { interpreter = it }
    }

    /** Recadrage carré central, redimensionnement 192×192, octets RGB. */
    private fun input(bmp: Bitmap): ByteBuffer {
        val side = min(bmp.width, bmp.height)
        val crop = Bitmap.createBitmap(bmp, (bmp.width - side) / 2, (bmp.height - side) / 2, side, side)
        val small = Bitmap.createScaledBitmap(crop, SIZE, SIZE, true)
        val px = IntArray(SIZE * SIZE); small.getPixels(px, 0, SIZE, 0, 0, SIZE, SIZE)
        if (small != crop) small.recycle(); if (crop != bmp) crop.recycle()
        val bb = ByteBuffer.allocateDirect(SIZE * SIZE * 3).order(ByteOrder.nativeOrder())
        for (p in px) { bb.put((p shr 16 and 0xFF).toByte()); bb.put((p shr 8 and 0xFF).toByte()); bb.put((p and 0xFF).toByte()) }
        bb.rewind(); return bb
    }

    /** Top [max] des plats reconnus (score ≥ [minScore]), traduits en français quand c'est possible. */
    @Synchronized
    fun recognize(ctx: Context, photoPath: String, max: Int = 5, minScore: Double = 0.04): List<FoodGuess> {
        val itp = load(ctx)
        val bmp = Photos.load(photoPath, 512) ?: throw IllegalStateException("photo illisible")
        val inBuf = input(bmp); bmp.recycle()
        val out = Array(1) { ByteArray(labels.size) }
        itp.run(inBuf, out)
        return topK(out[0], labels, max, minScore)
    }

    /** Sélection des meilleurs scores (index 0 = « __background__ » ignoré). Testable sans Android. */
    fun topK(scores: ByteArray, labels: List<String>, max: Int, minScore: Double): List<FoodGuess> =
        scores.indices.asSequence()
            .filter { it > 0 && it < labels.size && !labels[it].startsWith("__") }
            .map { it to (scores[it].toInt() and 0xFF) / 256.0 }
            .filter { it.second >= minScore }
            .sortedByDescending { it.second }
            .take(max)
            .map { (i, s) ->
                val m = FoodLabels.map(labels[i])
                FoodGuess(m.name, null, m.kcal100, s, offline = true, label = labels[i], food = m.food)
            }
            .distinctBy { fold(it.name) }
            .toList()
}
