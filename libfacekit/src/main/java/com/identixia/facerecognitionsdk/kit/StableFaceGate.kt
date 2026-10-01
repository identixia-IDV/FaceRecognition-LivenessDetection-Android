package com.identixia.facerecognitionsdk.kit

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import androidx.preference.PreferenceManager
import com.identixia.facerecognitionsdk.FaceRecognitionSDK
import kotlin.math.abs

/**
 * Auto-capture gate: single face, pose within thresholds, eyes open, held briefly.
 */
class StableFaceGate(context: Context) {
    private val prefs: SharedPreferences =
        PreferenceManager.getDefaultSharedPreferences(context.applicationContext)

    private var stableSinceMs = 0L
    private var lastOk = false

    fun reset() {
        stableSinceMs = 0L
        lastOk = false
    }

    /** @return true when the frame should be processed for the current mode */
    fun shouldCapture(detectJson: String?, nowMs: Long = System.currentTimeMillis()): Boolean {
        val faces = FaceJson.parseDetect(detectJson)
        if (faces.size != 1) {
            reset()
            return false
        }
        val face = faces[0]
        val yawMax = prefs.getString("yaw_threshold", "40.0")?.toFloatOrNull() ?: 40f
        val rollMax = prefs.getString("roll_threshold", "40.0")?.toFloatOrNull() ?: 40f
        val pitchMax = prefs.getString("pitch_threshold", "40.0")?.toFloatOrNull() ?: 40f
        val eyeClose = prefs.getString("eyeclose_threshold", "0.5")?.toFloatOrNull() ?: 0.5f

        val poseOk =
            abs(face.yaw) <= yawMax &&
                abs(face.roll) <= rollMax &&
                abs(face.pitch) <= pitchMax
        val left = face.attributes["eyesLeft"] ?: face.attributes["eyeLeft"]
        val right = face.attributes["eyesRight"] ?: face.attributes["eyeRight"]
        val eyesOk = !EyeOpenness.isClosed(left, right, eyeClose)

        val ok = poseOk && eyesOk
        if (!ok) {
            reset()
            return false
        }
        if (!lastOk) {
            stableSinceMs = nowMs
            lastOk = true
            return false
        }
        val holdMs = (
            (prefs.getString("identity_hold_duration", "0.5")?.toFloatOrNull() ?: 0.5f)
                .coerceIn(0.1f, 5f) * 1000f
            ).toLong().coerceAtLeast(100L)
        if (nowMs - stableSinceMs >= holdMs) {
            reset()
            return true
        }
        return false
    }

    companion object {
        const val HOLD_MS = 500L
    }
}
/**
 * Per-mode still analysis — one function customers can copy.
 */
object ModeAnalyzer {
    fun analyze(
        mode: FaceMode,
        bitmap: Bitmap,
        odd: Bitmap? = null,
        landmarkMode: Int = FaceRecognitionSDK.LANDMARK_MODE_68,
    ): String? {
        return when (mode) {
            FaceMode.FACE_DETECT ->
                FaceRecognitionSDK.faceDetect(bitmap, false)
            FaceMode.FACE_ATTRIBUTE ->
                FaceRecognitionSDK.faceAttribute(bitmap, false)
            FaceMode.IMAGE_QUALITY ->
                FaceRecognitionSDK.imageQuality(bitmap, false)
            FaceMode.LANDMARKS ->
                FaceRecognitionSDK.landmarks(bitmap, landmarkMode)
            FaceMode.MATCH -> {
                val other = odd ?: return null
                FaceRecognitionSDK.match(other, bitmap, false)
            }
            FaceMode.LIVENESS ->
                FaceRecognitionSDK.livenessAll(bitmap)
            FaceMode.ENROLL ->
                FaceRecognitionSDK.getFeature(bitmap)
            FaceMode.IDENTITY,
            FaceMode.ENROLLED_LIST ->
                FaceRecognitionSDK.faceDetect(bitmap, false)
        }
    }
}
