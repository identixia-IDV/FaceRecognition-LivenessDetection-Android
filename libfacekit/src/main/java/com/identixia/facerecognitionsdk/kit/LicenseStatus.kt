package com.identixia.facerecognitionsdk.kit

import com.identixia.facerecognitionsdk.FaceRecognitionSDK
import org.json.JSONObject

/** Parsed [FaceRecognitionSDK.getLicenseStatus] for UI and capability checks. */
data class LicenseStatus(
    val licensed: Boolean,
    val level: Int,
    val levelName: String,
    val recognition: Boolean,
    val liveness: Boolean,
    val label: String,
    val missingDatabases: List<String> = emptyList(),
) {
    companion object {
        fun current(): LicenseStatus {
            return try {
                fromJson(FaceRecognitionSDK.getLicenseStatus()).copy(
                    missingDatabases = FaceRecognitionSDK.getMissingDatabases()
                        .split(',')
                        .map { it.trim() }
                        .filter { it.isNotEmpty() },
                )
            } catch (_: Throwable) {
                notLicensed()
            }
        }

        fun fromJson(json: String?): LicenseStatus {
            return try {
                val o = JSONObject(json ?: "{}")
                LicenseStatus(
                    licensed = o.optBoolean("licensed", false),
                    level = o.optInt("level", -1),
                    levelName = o.optString("levelName", "None"),
                    recognition = o.optBoolean("recognition", false),
                    liveness = o.optBoolean("liveness", false),
                    label = o.optString("label", "No license"),
                )
            } catch (_: Exception) {
                notLicensed()
            }
        }

        private fun notLicensed() = LicenseStatus(
            licensed = false,
            level = -1,
            levelName = "None",
            recognition = false,
            liveness = false,
            label = "No license",
        )
    }

    fun denyMessage(wantRecognition: Boolean, wantLiveness: Boolean): String? {
        val parts = mutableListOf<String>()
        if (wantLiveness && !liveness) {
            parts += "This license does not include liveness ($label)."
        }
        if (wantRecognition && !recognition) {
            parts += "This license does not include recognition ($label)."
        }
        return parts.takeIf { it.isNotEmpty() }?.joinToString("\n")
    }

    fun missingDatabasesMessage(): String? {
        if (missingDatabases.isEmpty()) return null
        return "Missing databases (features skipped): ${missingDatabases.joinToString(", ")}"
    }
}
