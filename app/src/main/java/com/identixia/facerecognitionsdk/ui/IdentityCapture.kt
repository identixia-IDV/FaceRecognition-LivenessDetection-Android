package com.identixia.facerecognitionsdk.ui

import android.content.Context
import android.util.Size
import com.identixia.facerecognitionsdk.FaceBox
import com.identixia.facerecognitionsdk.R
import com.identixia.facerecognitionsdk.kit.DetectedFace
import com.identixia.facerecognitionsdk.kit.EyeOpenness
import kotlin.math.abs
import kotlin.math.max

object IdentityCapture {

    fun messageRes(state: FACE_CAPTURE_STATE): Int = when (state) {
        FACE_CAPTURE_STATE.NO_FACE -> R.string.hint_identity_live
        FACE_CAPTURE_STATE.MULTIPLE_FACES -> R.string.identity_status_multiple
        FACE_CAPTURE_STATE.FIT_IN_CIRCLE -> R.string.identity_status_fit
        FACE_CAPTURE_STATE.MOVE_CLOSER -> R.string.identity_status_closer
        FACE_CAPTURE_STATE.NO_FRONT -> R.string.identity_status_front
        FACE_CAPTURE_STATE.FACE_OCCLUDED -> R.string.identity_status_occluded
        FACE_CAPTURE_STATE.EYE_CLOSED -> R.string.identity_status_eyes
        FACE_CAPTURE_STATE.MOUTH_OPENED -> R.string.identity_status_mouth
        FACE_CAPTURE_STATE.SPOOFED_FACE -> R.string.identity_status_spoof
        FACE_CAPTURE_STATE.CAPTURE_OK -> R.string.identity_status_hold
    }

    fun evaluate(
        context: Context,
        faces: List<DetectedFace>,
        frameSize: Size,
    ): FACE_CAPTURE_STATE {
        if (faces.isEmpty()) return FACE_CAPTURE_STATE.NO_FACE
        if (faces.size > 1) return FACE_CAPTURE_STATE.MULTIPLE_FACES
        return evaluateFace(context, faces[0], frameSize)
    }

    fun evaluateBoxes(
        context: Context,
        boxes: List<FaceBox>,
        frameSize: Size,
    ): FACE_CAPTURE_STATE {
        if (boxes.isEmpty()) return FACE_CAPTURE_STATE.NO_FACE
        if (boxes.size > 1) return FACE_CAPTURE_STATE.MULTIPLE_FACES
        val box = boxes[0]
        var faceLeft = Float.MAX_VALUE
        var faceRight = 0f
        var faceBottom = 0f
        val nMarks = max(0, minOf(box.landmarkCount, box.landmarks_68.size / 2))
        if (nMarks >= 5) {
            for (i in 0 until nMarks) {
                faceLeft = minOf(faceLeft, box.landmarks_68[i * 2])
                faceRight = maxOf(faceRight, box.landmarks_68[i * 2])
                faceBottom = maxOf(faceBottom, box.landmarks_68[i * 2 + 1])
            }
        } else {
            faceLeft = box.x1.toFloat()
            faceRight = box.x2.toFloat()
            faceBottom = box.y2.toFloat()
        }
        val roi = IdentityGuideView.roiInFrame(frameSize)
        val centerY = (box.y2 + box.y1) / 2f
        val topY = centerY - (box.y2 - box.y1) * 2f / 3f
        val interX = max(0f, roi.left - faceLeft) + max(0f, faceRight - roi.right)
        val interY = max(0f, roi.top - topY) + max(0f, faceBottom - roi.bottom)
        if (interX / roi.width() > 0.03f || interY / roi.height() > 0.03f) {
            return FACE_CAPTURE_STATE.FIT_IN_CIRCLE
        }
        if ((box.y2 - box.y1) * (box.x2 - box.x1) < roi.width() * roi.height() * 0.30f) {
            return FACE_CAPTURE_STATE.MOVE_CLOSER
        }
        if (abs(box.yaw) > SettingsActivity.getYawThreshold(context) ||
            abs(box.roll) > SettingsActivity.getRollThreshold(context) ||
            abs(box.pitch) > SettingsActivity.getPitchThreshold(context)
        ) {
            return FACE_CAPTURE_STATE.NO_FRONT
        }
        val mask = box.maskLabel.orEmpty().lowercase()
        if (mask.contains("yes")) return FACE_CAPTURE_STATE.FACE_OCCLUDED

        val eyeClose = SettingsActivity.getEyecloseThreshold(context)
        val left = box.eyesLeftLabel.orEmpty().lowercase()
        val right = box.eyesRightLabel.orEmpty().lowercase()
        if (left.contains("closed") || right.contains("closed")) {
            return FACE_CAPTURE_STATE.EYE_CLOSED
        }
        // FaceBoxParser maps Open→0, Closed→confidence as left/right_eye_closed.
        if (box.left_eye_closed > eyeClose || box.right_eye_closed > eyeClose) {
            return FACE_CAPTURE_STATE.EYE_CLOSED
        }
        return FACE_CAPTURE_STATE.CAPTURE_OK
    }

    private fun evaluateFace(
        context: Context,
        face: DetectedFace,
        frameSize: Size,
    ): FACE_CAPTURE_STATE {
        val region = face.region
        var faceLeft = region.left
        var faceRight = region.right
        var faceBottom = region.bottom
        if (face.landmarks.size >= 5) {
            faceLeft = face.landmarks.minOf { it.x }
            faceRight = face.landmarks.maxOf { it.x }
            faceBottom = face.landmarks.maxOf { it.y }
        }
        val roi = IdentityGuideView.roiInFrame(frameSize)
        val centerY = region.centerY()
        val topY = centerY - region.height() * 2f / 3f
        val interX = max(0f, roi.left - faceLeft) + max(0f, faceRight - roi.right)
        val interY = max(0f, roi.top - topY) + max(0f, faceBottom - roi.bottom)
        if (interX / roi.width() > 0.03f || interY / roi.height() > 0.03f) {
            return FACE_CAPTURE_STATE.FIT_IN_CIRCLE
        }
        if (region.width() * region.height() < roi.width() * roi.height() * 0.30f) {
            return FACE_CAPTURE_STATE.MOVE_CLOSER
        }
        if (abs(face.yaw) > SettingsActivity.getYawThreshold(context) ||
            abs(face.roll) > SettingsActivity.getRollThreshold(context) ||
            abs(face.pitch) > SettingsActivity.getPitchThreshold(context)
        ) {
            return FACE_CAPTURE_STATE.NO_FRONT
        }
        val mask = face.attributes["mask"]?.value.orEmpty().lowercase()
            .ifBlank { face.attributes["medicalMask"]?.value.orEmpty().lowercase() }
        if (mask.contains("yes") || mask.contains("masked")) {
            return FACE_CAPTURE_STATE.FACE_OCCLUDED
        }

        val eyeClose = SettingsActivity.getEyecloseThreshold(context)
        val left = face.attributes["eyesLeft"] ?: face.attributes["eyeLeft"]
        val right = face.attributes["eyesRight"] ?: face.attributes["eyeRight"]
        if (EyeOpenness.isClosed(left, right, eyeClose)) {
            return FACE_CAPTURE_STATE.EYE_CLOSED
        }
        return FACE_CAPTURE_STATE.CAPTURE_OK
    }
}
