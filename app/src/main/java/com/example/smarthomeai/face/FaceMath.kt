package com.example.smarthomeai.face

import kotlin.math.sqrt

/** Model-independent vector validation and normalized cosine matching. */
object FaceMath {
    fun normalize(vector: FloatArray): FloatArray? {
        if(vector.isEmpty() || vector.any { !it.isFinite() }) return null
        val norm = sqrt(vector.sumOf { it.toDouble() * it.toDouble() })
        if(norm < 1e-9) return null
        return FloatArray(vector.size) { (vector[it] / norm).toFloat() }
    }
    fun similarity(a: FloatArray, b: FloatArray): Float? {
        if(a.size != b.size) return null
        val x = normalize(a) ?: return null
        val y = normalize(b) ?: return null
        var score = 0f
        for(i in x.indices) score += x[i] * y[i]
        return score.coerceIn(-1f,1f)
    }
}
/** Requires consecutive observations; losing a face resets the candidate. */
class RecognitionGate(private val required: Int = 3) {
    private var candidate: String? = null
    private var count = 0
    fun observe(key: String?): Boolean {
        if(key == null) { candidate = null; count = 0; return false }
        if(candidate == key) count++ else { candidate = key; count = 1 }
        return count >= required
    }
}

/** A live camera match expires quickly and is consumed by one door request. */
class FaceAccessSession(private val lifetimeMs: Long = 1_500) {
    private var name: String? = null
    private var observedAt = 0L
    @Synchronized fun observe(verifiedName: String?, now: Long) { name=verifiedName; observedAt=now }
    @Synchronized fun canOpen(verifiedName: String, now: Long): Boolean =
        name == verifiedName && now >= observedAt && now-observedAt <= lifetimeMs
    @Synchronized fun consume(verifiedName: String, now: Long): Boolean {
        if(!canOpen(verifiedName,now)) return false
        name=null
        return true
    }
}
