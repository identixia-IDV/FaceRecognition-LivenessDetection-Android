package com.identixia.facerecognitionsdk.kit

/**
 * Eye openness from engine traits.
 *
 * Native output is label Open|Closed plus classification confidence — not a
 * "closed score". Treating Open confidence as closed (e.g. 0.95 > 0.5) falsely
 * rejects open eyes.
 */
object EyeOpenness {

    /**
     * Closed-eye score in 0..1 for thresholding, or null when unknown.
     * Open → 0; Closed → confidence (default 0.9).
     */
    fun closedScore(label: String?, confidence: String?): Float? {
        val lower = label.orEmpty().lowercase()
        if (lower.isBlank()) return null
        val conf = confidence?.toFloatOrNull()
        if (lower.contains("closed") || (lower.contains("close") && !lower.contains("closer"))) {
            return when {
                conf == null || conf < 0f -> 0.9f
                conf > 1f -> 1f
                else -> conf
            }
        }
        if (lower.contains("open")) return 0f
        return null
    }

    fun closedScore(attr: FaceAttribute?): Float? =
        if (attr == null) null else closedScore(attr.value, attr.confidence)

    /** True when either eye is closed above [threshold], or labeled closed. */
    fun isClosed(
        leftLabel: String?,
        leftConf: String?,
        rightLabel: String?,
        rightConf: String?,
        threshold: Float,
    ): Boolean {
        val left = closedScore(leftLabel, leftConf)
        val right = closedScore(rightLabel, rightConf)
        if (left == null && right == null) return false
        if (left != null && left > threshold) return true
        if (right != null && right > threshold) return true
        return false
    }

    fun isClosed(left: FaceAttribute?, right: FaceAttribute?, threshold: Float): Boolean =
        isClosed(left?.value, left?.confidence, right?.value, right?.confidence, threshold)
}
