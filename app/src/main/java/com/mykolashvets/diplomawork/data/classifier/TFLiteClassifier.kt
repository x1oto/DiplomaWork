package com.mykolashvets.diplomawork.data.classifier

import android.app.Application
import android.graphics.Bitmap
import com.mykolashvets.diplomawork.domain.entity.MushroomResult
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

class TFLiteClassifier(app: Application) {
    companion object {
        private const val MODEL = "model.tflite"
        private const val LABELS_TXT = "labels.txt"
        private const val INPUT = 224; private const val CHANNELS = 3
    }
    private val labels: List<String> = app.assets.open(LABELS_TXT).bufferedReader().useLines { it.toList() }
    private val interpreter: Interpreter
    init {
        val fd = app.assets.openFd(MODEL)
        val buf = FileInputStream(fd.fileDescriptor).channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
        interpreter = Interpreter(buf, Interpreter.Options().apply { setNumThreads(2) })
        // guard: shape check (avoid native crash)
        check(interpreter.inputTensorCount == 1) { "Model must have exactly one input" }
    }
    fun classify(bitmap: Bitmap): MushroomResult = synchronized(interpreter) {
        val scaled = Bitmap.createScaledBitmap(bitmap, INPUT, INPUT, true)
        val byteBuffer = ByteBuffer.allocateDirect(4 * INPUT * INPUT * CHANNELS).order(ByteOrder.nativeOrder())
        val pixels = IntArray(INPUT * INPUT)
        scaled.getPixels(pixels, 0, INPUT, 0, 0, INPUT, INPUT)
        for (pix in pixels) {
            byteBuffer.putFloat(((pix shr 16) and 0xFF) / 255f)
            byteBuffer.putFloat(((pix shr 8) and 0xFF) / 255f)
            byteBuffer.putFloat((pix and 0xFF) / 255f)
        }
        val out = Array(1) { FloatArray(labels.size) }
        interpreter.run(byteBuffer, out)
        val idx = out[0].indices.maxByOrNull { out[0][it] } ?: -1
        val conf = if (idx >= 0) out[0][idx] else 0f
        MushroomResult(labels.getOrElse(idx) { "Невідомо" }, conf)
    }
}