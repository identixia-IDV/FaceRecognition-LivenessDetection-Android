package com.identixia.facerecognitionsdk.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.identixia.facerecognitionsdk.FaceRecognitionSDK
import com.identixia.facerecognitionsdk.R
import com.identixia.facerecognitionsdk.kit.FaceRecognitionClient
import com.identixia.facerecognitionsdk.kit.LicenseStatus

class MainActivity : AppCompatActivity() {

    companion object {
        /** Demo license for applicationId com.identixia.facerecognitionsdk. */
        private const val LICENSE_KEY = "pyyR2AEC9OOJtzGzuUiqnj1UoycDkCGbbI4QJOyk7/gwHGIAAAAOQ5Kyt/i1G3FerFj8/3i91gzLJCSx2P5IE+KM0uD7V8lSe57423nUFDc2YkS4siW+Nmt56Dpg+5s8l2kmjaUHzc7ArIMCPUTe7hTAG064A5aEjGsSNOLMSlm5A2njLclVM2YAMGQCMCzpL9yW4JYyQ+6LOq+3d+7/1DoQ4Q5N//A8XPN5yxNq30ZRxkInjsTvgHWjwKdkpAIwWImkTNEFVHBQsrZO4J8frcoHevTdrnzh1g22fL79wjwngeGNQsGaZkRO9E6p6Xhw"
        private const val REQ_CAMERA = 1001
    }

    private lateinit var txtLicense: TextView
    private lateinit var txtStatus: TextView
    private lateinit var txtWarning: TextView
    private lateinit var modeButtons: List<View>
    private var sdkReady = false
    private var pendingMode: FaceMode? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SettingsActivity.applyEngineDefaults(this)
        setContentView(R.layout.activity_main)

        txtLicense = findViewById(R.id.txtLicenseChip)
        txtStatus = findViewById(R.id.txtStatusChip)
        txtWarning = findViewById(R.id.txtWarning)

        modeButtons = listOf(
            findViewById(R.id.btnFaceDetect),
            findViewById(R.id.btnFaceAttribute),
            findViewById(R.id.btnImageQuality),
            findViewById(R.id.btnLandmarks),
            findViewById(R.id.btnMatch),
            findViewById(R.id.btnLiveness),
            findViewById(R.id.btnEnroll),
            findViewById(R.id.btnIdentity),
            findViewById(R.id.btnEnrolledList),
        )

        bindMode(R.id.btnFaceDetect, FaceMode.FACE_DETECT)
        bindMode(R.id.btnFaceAttribute, FaceMode.FACE_ATTRIBUTE)
        bindMode(R.id.btnImageQuality, FaceMode.IMAGE_QUALITY)
        bindMode(R.id.btnLandmarks, FaceMode.LANDMARKS)
        bindMode(R.id.btnMatch, FaceMode.MATCH)
        bindMode(R.id.btnLiveness, FaceMode.LIVENESS)
        bindMode(R.id.btnEnroll, FaceMode.ENROLL)
        bindMode(R.id.btnIdentity, FaceMode.IDENTITY)
        findViewById<View>(R.id.btnEnrolledList).setOnClickListener {
            if (!ensureReady(FaceMode.ENROLLED_LIST)) return@setOnClickListener
            startActivity(Intent(this, EnrolledListActivity::class.java))
        }
        findViewById<View>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<View>(R.id.btnAbout).setOnClickListener {
            startActivity(Intent(this, AboutActivity::class.java))
        }

        setModesEnabled(false)
        bootSdk()
    }

    private fun bindMode(id: Int, mode: FaceMode) {
        findViewById<View>(id).setOnClickListener { openMode(mode) }
    }

    private fun openMode(mode: FaceMode) {
        if (!ensureReady(mode)) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            pendingMode = mode
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), REQ_CAMERA)
            return
        }
        startActivity(
            Intent(this, ModeCameraActivity::class.java)
                .putExtra(FaceMode.EXTRA, mode.name),
        )
    }

    private fun ensureReady(mode: FaceMode): Boolean {
        if (!sdkReady) {
            Toast.makeText(this, R.string.sdk_failed, Toast.LENGTH_SHORT).show()
            return false
        }
        val license = LicenseStatus.current()
        if (mode.needsRecognition && !license.recognition) {
            Toast.makeText(this, R.string.license_recognition_unavailable, Toast.LENGTH_SHORT).show()
            return false
        }
        if (mode.needsLiveness && !license.liveness) {
            Toast.makeText(this, R.string.license_liveness_unavailable, Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    private fun bootSdk() {
        txtLicense.setText(R.string.about_license_loading)
        txtStatus.setText(R.string.sdk_loading)
        txtWarning.visibility = View.VISIBLE
        txtWarning.setText(R.string.sdk_loading)
        val client = FaceRecognitionClient.get(this)
        if (client.isEngineReady) {
            applyReady()
            return
        }
        client.activate(LICENSE_KEY) { code ->
            runOnUiThread {
                if (code == FaceRecognitionSDK.SDK_SUCCESS) {
                    applyReady()
                } else {
                    sdkReady = false
                    setModesEnabled(false)
                    txtLicense.text = getString(R.string.about_license_fmt, LicenseStatus.current().label)
                    txtStatus.setText(R.string.sdk_engine_failed)
                    txtWarning.visibility = View.VISIBLE
                    txtWarning.text = when (code) {
                        FaceRecognitionSDK.SDK_LICENSE_INVALID -> getString(R.string.sdk_license_invalid)
                        FaceRecognitionSDK.SDK_LICENSE_EXPIRED -> getString(R.string.sdk_license_expired)
                        FaceRecognitionSDK.SDK_NOT_ACTIVATED -> getString(R.string.sdk_not_activated)
                        else -> getString(R.string.sdk_init_failed)
                    }
                }
            }
        }
    }

    private fun applyReady() {
        sdkReady = true
        setModesEnabled(true)
        val status = LicenseStatus.current()
        txtLicense.text = getString(R.string.about_license_fmt, status.label)
        txtStatus.text = getString(R.string.sdk_engine_ready)
        val missing = status.missingDatabasesMessage()
        if (missing != null) {
            txtWarning.visibility = View.VISIBLE
            txtWarning.text = missing
            Toast.makeText(this, missing, Toast.LENGTH_LONG).show()
        } else {
            txtWarning.visibility = View.GONE
        }
    }

    private fun setModesEnabled(enabled: Boolean) {
        modeButtons.forEach { it.isEnabled = enabled; it.alpha = if (enabled) 1f else 0.45f }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQ_CAMERA) return
        val mode = pendingMode ?: return
        pendingMode = null
        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            openMode(mode)
        } else {
            Toast.makeText(this, R.string.camera_permission_denied, Toast.LENGTH_SHORT).show()
        }
    }
}
