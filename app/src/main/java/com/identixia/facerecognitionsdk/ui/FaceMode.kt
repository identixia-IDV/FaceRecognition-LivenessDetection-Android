package com.identixia.facerecognitionsdk.ui

/**
 * Demo modes — Android edition of FaceRecognition-LivenessDetection-Windows Gradio tabs.
 */
enum class FaceMode(
    val title: String,
    val needsRecognition: Boolean,
    val needsLiveness: Boolean,
    val usesVideoWorker: Boolean,
) {
    FACE_DETECT("Face detect", true, false, false),
    FACE_ATTRIBUTE("Face attribute", true, false, false),
    IMAGE_QUALITY("Image quality", true, false, false),
    LANDMARKS("Landmarks", true, false, false),
    MATCH("Match", true, false, false),
    LIVENESS("Liveness", false, true, false),
    ENROLL("Enroll", true, false, false),
    IDENTITY("Identity", true, false, true),
    ENROLLED_LIST("Enrolled list", true, false, false);

    companion object {
        const val EXTRA = "face_mode"

        fun fromName(name: String?): FaceMode =
            entries.firstOrNull { it.name == name } ?: FACE_DETECT
    }
}
