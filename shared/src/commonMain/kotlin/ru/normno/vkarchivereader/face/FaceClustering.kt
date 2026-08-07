package ru.normno.vkarchivereader.face

import kotlin.math.sqrt

/** SFace cosine-similarity threshold above which two faces are "the same person". */
const val FACE_MATCH_THRESHOLD: Float = 0.363f

/** Cosine similarity of two equal-length vectors. */
fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
    var dot = 0f
    var na = 0f
    var nb = 0f
    for (i in a.indices) {
        dot += a[i] * b[i]
        na += a[i] * a[i]
        nb += b[i] * b[i]
    }
    val denom = sqrt(na) * sqrt(nb)
    return if (denom == 0f) 0f else dot / denom
}

private fun l2normalized(v: FloatArray): FloatArray {
    var n = 0f
    for (x in v) n += x * x
    n = sqrt(n)
    if (n == 0f) return v.copyOf()
    return FloatArray(v.size) { v[it] / n }
}

/**
 * Greedy online face clusterer: each embedding is assigned to the nearest group
 * centroid above [threshold], otherwise it starts a new group. Streaming and
 * low-memory — only one normalized centroid (+ count) per group is kept, so it
 * scales to tens of thousands of faces within a couple of hundred MB.
 *
 * This is the same idea behind "People" albums; it does not need to know the
 * number of people in advance.
 */
class OnlineFaceClusterer(private val threshold: Float = FACE_MATCH_THRESHOLD) {

    private val centroids = ArrayList<FloatArray>()
    private val counts = ArrayList<Int>()

    val clusterCount: Int get() = centroids.size

    /** Assign [embedding] to a cluster, returning the cluster index. */
    fun assign(embedding: FloatArray): Int {
        val e = l2normalized(embedding)
        var best = -1
        var bestSim = threshold
        for (i in centroids.indices) {
            val sim = cosineSimilarity(e, centroids[i])
            if (sim >= bestSim) {
                bestSim = sim
                best = i
            }
        }
        return if (best >= 0) {
            mergeInto(best, e)
            best
        } else {
            centroids.add(e)
            counts.add(1)
            centroids.size - 1
        }
    }

    private fun mergeInto(index: Int, e: FloatArray) {
        val c = centroids[index]
        val n = counts[index]
        // Running mean, then re-normalize so it stays a unit vector.
        val merged = FloatArray(c.size) { (c[it] * n + e[it]) / (n + 1) }
        centroids[index] = l2normalized(merged)
        counts[index] = n + 1
    }
}
