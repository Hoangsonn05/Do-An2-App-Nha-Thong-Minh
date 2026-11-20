package com.example.smarthomeai.face

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.camera.core.ImageAnalysis
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.DataType
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt

data class NormBox(val left: Float, val top: Float, val right: Float, val bottom: Float)
class FaceResult(val box: NormBox, val embedding: FloatArray?, val snapshot: Bitmap?)
class FaceMatch(val name: String, val score: Float)

class FaceEngine(context: Context) {
    companion object { const val MODEL_FILE = "mobile_face_net.tflite" }
    private val detector = FaceDetection.getClient(FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST).setMinFaceSize(.25f).build())
    private val worker = Executors.newSingleThreadExecutor()
    private val safeWorker = java.util.concurrent.Executor { action ->
        if (!closed) runCatching { worker.execute(action) }
    }
    private val main = Handler(Looper.getMainLooper())
    private val busy = AtomicBoolean(false)
    @Volatile private var closed = false
    private var lastRun = 0L
    private val interpreter: Interpreter? = runCatching {
        val model = context.assets.open(MODEL_FILE).use { stream ->
            val bytes = stream.readBytes()
            ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).apply { put(bytes); rewind() }
        }
        Interpreter(model).also {
            require(it.getInputTensor(0).dataType() == DataType.FLOAT32)
            require(it.getInputTensor(0).shape().contentEquals(intArrayOf(1,112,112,3)))
            require(it.getOutputTensor(0).shape().contentEquals(intArrayOf(1,192)))
        }
    }.getOrNull()
    val modelReady get() = interpreter != null
    fun analyzer(onResult: (FaceResult?) -> Unit) = ImageAnalysis.Analyzer { proxy ->
        val now = SystemClock.elapsedRealtime()
        if(closed || now - lastRun < 300 || !busy.compareAndSet(false,true)) proxy.close()
        else {
            lastRun = now
            val bitmap = try {
                val bitmap = proxy.toBitmap()
                val area = proxy.cropRect
                val b = Bitmap.createBitmap(bitmap, area.left, area.top, area.width(), area.height())
                if(proxy.imageInfo.rotationDegrees == 0) b else Bitmap.createBitmap(b,0,0,b.width,b.height,
                    Matrix().apply { postRotate(proxy.imageInfo.rotationDegrees.toFloat()) }, true)
            } catch (_: Exception) { null } finally { proxy.close() }
            if(bitmap == null) busy.set(false)
            else detector.process(InputImage.fromBitmap(bitmap,0))
                .addOnSuccessListener(safeWorker) { faces ->
                    try {
                        if(!closed) {
                            val face = faces.singleOrNull()
                            val result = face?.let {
                                val r = it.boundingBox
                                val left = r.left.coerceIn(0,bitmap.width-1)
                                val top = r.top.coerceIn(0,bitmap.height-1)
                                val width = r.right.coerceIn(left, bitmap.width) - left
                                val height = r.bottom.coerceIn(top, bitmap.height) - top
                                if(width < 60 || height < 60) null else {
                                    val crop = Bitmap.createBitmap(bitmap,left,top,width,height)
                                    FaceResult(NormBox(left.toFloat()/bitmap.width,top.toFloat()/bitmap.height,
                                        (left+width).toFloat()/bitmap.width,(top+height).toFloat()/bitmap.height),
                                        encodeFace(crop), Bitmap.createScaledBitmap(crop,240,240,true))
                                }
                            }
                            main.post { if(!closed) onResult(result) }
                        }
                    } finally { busy.set(false) }
                }
                .addOnFailureListener(safeWorker) { busy.set(false); main.post { if(!closed) onResult(null) } }
        }
    }
    fun encodeFace(crop: Bitmap): FloatArray? = runCatching {
        val net = interpreter ?: return null
        val scaled = Bitmap.createScaledBitmap(crop,112,112,true)
        val input = ByteBuffer.allocateDirect(112*112*3*4).order(ByteOrder.nativeOrder())
        val pixels = IntArray(112*112)
        scaled.getPixels(pixels,0,112,0,0,112,112)
        pixels.forEach { p ->
            input.putFloat((((p shr 16) and 255)-128f)/128f)
            input.putFloat((((p shr 8) and 255)-128f)/128f)
            input.putFloat(((p and 255)-128f)/128f)
        }
        input.rewind()
        val result = arrayOf(FloatArray(192))
        synchronized(net) { if(closed) return null; net.run(input,result) }
        FaceMath.normalize(result[0])
    }.getOrNull()
    fun average(samples: List<FloatArray>): FloatArray {
        require(samples.isNotEmpty())
        val sum = FloatArray(192)
        samples.forEach { s -> for(i in sum.indices) sum[i] += s[i] }
        return normalize(sum)
    }
    private fun normalize(v: FloatArray): FloatArray {
        val norm = sqrt(v.sumOf { (it*it).toDouble() }).toFloat().coerceAtLeast(1e-9f)
        return FloatArray(v.size) { v[it]/norm }
    }
    fun close() {
        closed = true
        // Queue disposal after in-flight analysis; avoid closing TFLite during inference.
        worker.execute { detector.close(); interpreter?.let { synchronized(it) { it.close() } } }
        worker.shutdown()
    }
}
class FaceStore(context: Context) {
    companion object { const val THRESHOLD = .70f }
    private val prefs = context.getSharedPreferences("face_store", Context.MODE_PRIVATE)
    fun save(name: String, embedding: FloatArray) { prefs.edit().putString(name,embedding.joinToString(",")).apply() }
    fun remove(name: String) { prefs.edit().remove(name).apply() }
    fun names() = prefs.all.keys.sorted()
    fun match(embedding: FloatArray): FaceMatch? {
        var best: FaceMatch? = null
        prefs.all.forEach { (name,value) ->
            val stored = (value as? String)?.split(",")?.mapNotNull { it.toFloatOrNull() }?.toFloatArray()
            if(stored != null && stored.size == embedding.size) {
                val score = FaceMath.similarity(stored, embedding)
                if(score != null && (best == null || score > best!!.score)) best = FaceMatch(name,score)
            }
        }
        return best?.takeIf { it.score >= THRESHOLD }
    }
}
