package com.identixia.facerecognitionsdk.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Size
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.common.util.concurrent.ListenableFuture
import com.identixia.facerecognitionsdk.FaceBox
import com.identixia.facerecognitionsdk.FaceRecognitionSDK
import com.identixia.facerecognitionsdk.R
import com.identixia.facerecognitionsdk.kit.CameraFrameUtils
import com.identixia.facerecognitionsdk.kit.CameraPreview
import com.identixia.facerecognitionsdk.kit.DetectedFace
import com.identixia.facerecognitionsdk.kit.FaceJson
import com.identixia.facerecognitionsdk.kit.FaceRecognitionClient
import com.identixia.facerecognitionsdk.kit.FaceRecognitionQueue
import com.identixia.facerecognitionsdk.kit.ModeAnalyzer
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.min
import kotlin.math.roundToInt

class ModeCameraActivity : AppCompatActivity() {

    companion object {
        const val RESULT_JSON_PATH = "result_json_path"
        const val RESULT_TITLE = "result_title"
        const val RESULT_MODE = "result_mode"
        const val RESULT_THUMB_PATH = "result_thumb_path"
        const val RESULT_THUMB2_PATH = "result_thumb2_path"
        const val RESULT_LANDMARKS_XY = "result_landmarks_xy"

        private const val REQ_CAMERA = 2001
        private const val OVERLAY_FLAGS =
            FaceRecognitionSDK.DETECT_POSE or
                FaceRecognitionSDK.DETECT_EYES or
                FaceRecognitionSDK.DETECT_LANDMARKS
    }

    private lateinit var mode: FaceMode
    private lateinit var preview: PreviewView
    private lateinit var faceView: FaceView
    private lateinit var identityGuide: IdentityGuideView
    private lateinit var txtHint: TextView
    private lateinit var txtIdentityHint: TextView
    private lateinit var txtModeTitle: TextView
    private lateinit var lytBottomControls: View

    private lateinit var cameraExecutor: ExecutorService
    private lateinit var client: FaceRecognitionClient

    private var cameraProvider: ProcessCameraProvider? = null
    private var lensFacing: Int = CameraSelector.LENS_FACING_FRONT
    private var captureArmed = AtomicBoolean(false)

    private val resultOpened = AtomicBoolean(false)
    private val frameBusy = AtomicBoolean(false)
    private val confirming = AtomicBoolean(false)
    private val enrollPromptOpen = AtomicBoolean(false)

    private var oddBitmap: Bitmap? = null
    private var lastFrame: Bitmap? = null
    private var lastFrameW = 0
    private var lastFrameH = 0

    private var identityOkSinceMs = 0L
    private var lastIdentityState: FACE_CAPTURE_STATE = FACE_CAPTURE_STATE.NO_FACE

    private val pickImage = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode != RESULT_OK) return@registerForActivityResult
        val uri = result.data?.data ?: return@registerForActivityResult
        processGalleryUri(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mode = FaceMode.fromName(intent.getStringExtra(FaceMode.EXTRA))
        if (mode == FaceMode.ENROLLED_LIST) {
            startActivity(Intent(this, EnrolledListActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_mode_camera)
        client = FaceRecognitionClient.get(this)
        client.loadDatabase()
        cameraExecutor = Executors.newSingleThreadExecutor()
        lensFacing = SettingsActivity.getCameraLens(this)

        preview = findViewById(R.id.preview)
        faceView = findViewById(R.id.faceView)
        identityGuide = findViewById(R.id.identityGuide)
        txtHint = findViewById(R.id.txtHint)
        txtIdentityHint = findViewById(R.id.txtIdentityHint)
        txtModeTitle = findViewById(R.id.txtModeTitle)
        lytBottomControls = findViewById(R.id.lytBottomControls)
        val btnCapture = findViewById<ImageView>(R.id.btnCapture)

        txtModeTitle.text = mode.title
        txtHint.setText(
            when (mode) {
                FaceMode.MATCH -> R.string.hint_match_face_1
                else -> R.string.hint_align_face
            },
        )
        faceView.setMirrorX(lensFacing == CameraSelector.LENS_FACING_FRONT)
        identityGuide.setMirrorX(lensFacing == CameraSelector.LENS_FACING_FRONT)

        findViewById<View>(R.id.btnClose).setOnClickListener { finish() }
        findViewById<ImageView>(R.id.btnFlipCamera).setOnClickListener { flipCamera() }
        findViewById<ImageView>(R.id.btnGallery).setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            pickImage.launch(intent)
        }
        btnCapture.setOnClickListener { onCaptureClicked() }
        if (mode == FaceMode.IDENTITY) {
            faceView.visibility = View.GONE
            identityGuide.visibility = View.VISIBLE
            lytBottomControls.visibility = View.GONE
            findViewById<ImageView>(R.id.btnGallery).visibility = View.GONE
            txtIdentityHint.visibility = View.VISIBLE
            txtIdentityHint.setText(R.string.hint_identity_live)
            identityGuide.setGuideState(FACE_CAPTURE_STATE.NO_FACE, 0f)
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                REQ_CAMERA,
            )
        } else {
            preview.post { setUpCamera() }
        }
    }

    override fun onResume() {
        super.onResume()
        if (mode == FaceMode.IDENTITY && !resultOpened.get()) {
            confirming.set(false)
            identityOkSinceMs = 0L
            lastIdentityState = FACE_CAPTURE_STATE.NO_FACE
        }
    }

    override fun onPause() {
        super.onPause()
        if (mode == FaceMode.IDENTITY) {
            identityOkSinceMs = 0L
            if (::identityGuide.isInitialized) {
                identityGuide.setGuideState(FACE_CAPTURE_STATE.NO_FACE, 0f)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraProvider?.unbindAll()
        if (::cameraExecutor.isInitialized) cameraExecutor.shutdown()
        oddBitmap?.takeIf { !it.isRecycled }?.recycle()
        oddBitmap = null
        synchronized(this) {
            lastFrame?.takeIf { !it.isRecycled }?.recycle()
            lastFrame = null
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQ_CAMERA) return
        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            preview.post { setUpCamera() }
        } else {
            Toast.makeText(this, R.string.camera_permission_denied, Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun flipCamera() {
        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
            CameraSelector.LENS_FACING_BACK
        } else {
            CameraSelector.LENS_FACING_FRONT
        }
        faceView.setMirrorX(lensFacing == CameraSelector.LENS_FACING_FRONT)
        identityGuide.setMirrorX(lensFacing == CameraSelector.LENS_FACING_FRONT)
        identityOkSinceMs = 0L
        bindCameraUseCases()
    }

    private fun setUpCamera() {
        val future: ListenableFuture<ProcessCameraProvider> =
            ProcessCameraProvider.getInstance(this)
        future.addListener({
            try {
                cameraProvider = future.get()
                bindCameraUseCases()
            } catch (_: Exception) {
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindCameraUseCases() {
        val provider = cameraProvider ?: return
        try {
            CameraPreview.bind(
                this,
                provider,
                preview,
                lensFacing,
                cameraExecutor,
                ::analyzeImage,
            )
        } catch (_: Exception) {
        }
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun analyzeImage(imageProxy: androidx.camera.core.ImageProxy) {
        var frame: Bitmap? = null
        try {
            if (resultOpened.get()) return
            val backCamera = lensFacing == CameraSelector.LENS_FACING_BACK
            frame = CameraFrameUtils.fromImageProxy(imageProxy, backCamera)
        } catch (_: Exception) {
        } finally {
            imageProxy.close()
        }
        val bitmap = frame ?: return
        if (resultOpened.get()) {
            if (!bitmap.isRecycled) bitmap.recycle()
            return
        }

        if (mode == FaceMode.IDENTITY) {
            analyzeIdentityFrame(bitmap)
        } else {
            analyzeStillMode(bitmap)
        }
    }

    // region Still modes (detect / attribute / quality / landmarks / match / liveness / enroll)

    private fun analyzeStillMode(bitmap: Bitmap) {
        if (!frameBusy.compareAndSet(false, true)) {
            if (!bitmap.isRecycled) bitmap.recycle()
            return
        }
        client.async {
            try {
                if (resultOpened.get() || isFinishing) return@async
                val detectJson = FaceRecognitionQueue.detect(bitmap, false, OVERLAY_FLAGS)
                val faces = FaceJson.parseDetect(detectJson)
                val boxes = toFaceBoxes(faces)
                runOnUiThread {
                    if (bitmap.width > 0 && bitmap.height > 0) {
                        faceView.setFrameSize(Size(bitmap.width, bitmap.height))
                    }
                    faceView.setFaceBoxes(boxes)
                }
                val keep = try {
                    CameraFrameUtils.copyArgb(bitmap)
                } catch (_: Exception) {
                    null
                }
                if (keep != null) {
                    synchronized(this) {
                        lastFrame?.takeIf { !it.isRecycled }?.recycle()
                        lastFrame = keep
                        lastFrameW = keep.width
                        lastFrameH = keep.height
                    }
                }
                if (captureArmed.compareAndSet(true, false)) {
                    onStableFace(bitmap)
                }
            } catch (_: Exception) {
                captureArmed.set(false)
            } finally {
                if (!bitmap.isRecycled) bitmap.recycle()
                frameBusy.set(false)
            }
        }
    }

    private fun onCaptureClicked() {
        if (mode == FaceMode.IDENTITY || resultOpened.get() || isFinishing) return
        if (!frameBusy.compareAndSet(false, true)) {
            // Analyzer is busy — take the next preview frame.
            captureArmed.set(true)
            return
        }
        val buffered = synchronized(this) {
            lastFrame?.takeIf { !it.isRecycled }?.let {
                try {
                    CameraFrameUtils.copyArgb(it)
                } catch (_: Exception) {
                    null
                }
            }
        }
        if (buffered == null) {
            frameBusy.set(false)
            captureArmed.set(true)
            return
        }
        client.async {
            try {
                onStableFace(buffered)
            } finally {
                if (!buffered.isRecycled) buffered.recycle()
                frameBusy.set(false)
            }
        }
    }

    private fun onStableFace(bitmap: Bitmap) {
        if (resultOpened.get() || isFinishing) return
        val copy = try {
            CameraFrameUtils.copyArgb(bitmap)
        } catch (_: Exception) {
            null
        } ?: return

        when (mode) {
            FaceMode.MATCH -> handleMatchStable(copy)
            FaceMode.ENROLL -> handleEnrollStable(copy)
            FaceMode.LANDMARKS -> handleLandmarksStable(copy)
            FaceMode.FACE_DETECT,
            FaceMode.FACE_ATTRIBUTE,
            FaceMode.IMAGE_QUALITY,
            FaceMode.LIVENESS,
            -> {
                if (!resultOpened.compareAndSet(false, true)) {
                    if (!copy.isRecycled) copy.recycle()
                    return
                }
                val json = try {
                    FaceRecognitionQueue.sync {
                        ModeAnalyzer.analyze(
                            mode,
                            copy,
                            landmarkMode = SettingsActivity.getLandmarkMode(this),
                        )
                    }
                } catch (_: Exception) {
                    null
                }
                val thumb = saveThumb(cropLargestFace(copy) ?: copy)
                if (!copy.isRecycled) copy.recycle()
                openResult(json, thumb)
            }
            FaceMode.IDENTITY, FaceMode.ENROLLED_LIST -> {
                if (!copy.isRecycled) copy.recycle()
            }
        }
    }

    private fun handleLandmarksStable(copy: Bitmap) {
        if (!resultOpened.compareAndSet(false, true)) {
            if (!copy.isRecycled) copy.recycle()
            return
        }
        val json = try {
            FaceRecognitionQueue.sync {
                ModeAnalyzer.analyze(
                    FaceMode.LANDMARKS,
                    copy,
                    landmarkMode = SettingsActivity.getLandmarkMode(this),
                )
            }
        } catch (_: Exception) {
            null
        }
        val faces = FaceJson.parseDetect(json)
        val best = faces.maxByOrNull { it.region.width() * it.region.height() }
        val crop = if (best != null) {
            CameraFrameUtils.cropFace(copy, best.region)
        } else {
            null
        }
        val landmarksXy = if (best != null && best.landmarks.isNotEmpty()) {
            CameraFrameUtils.mapLandmarksToCrop(
                copy.width,
                copy.height,
                best.region,
                best.landmarks,
            )
        } else {
            null
        }
        val thumb = saveThumb(crop ?: copy)
        if (crop != null && crop !== copy && !crop.isRecycled) crop.recycle()
        if (!copy.isRecycled) copy.recycle()
        openResult(json, thumb, landmarksXy = landmarksXy)
    }

    private fun handleMatchStable(copy: Bitmap) {
        val odd = oddBitmap
        if (odd == null) {
            oddBitmap = copy
            runOnUiThread {
                txtHint.setText(R.string.hint_match_face_2)
            }
            return
        }
        if (!resultOpened.compareAndSet(false, true)) {
            if (!copy.isRecycled) copy.recycle()
            return
        }
        val json = try {
            FaceRecognitionQueue.sync { ModeAnalyzer.analyze(FaceMode.MATCH, copy, odd) }
        } catch (_: Exception) {
            null
        }
        val thumb1 = saveThumb(cropLargestFace(odd) ?: odd)
        val thumb2 = saveThumb(cropLargestFace(copy) ?: copy)
        if (!copy.isRecycled) copy.recycle()
        odd.takeIf { !it.isRecycled }?.recycle()
        oddBitmap = null
        openResult(json, thumb1, thumb2 = thumb2)
    }

    private fun handleEnrollStable(copy: Bitmap) {
        if (enrollPromptOpen.get() || resultOpened.get()) {
            if (!copy.isRecycled) copy.recycle()
            return
        }
        val featureJson = try {
            FaceRecognitionQueue.sync { ModeAnalyzer.analyze(FaceMode.ENROLL, copy) }
        } catch (_: Exception) {
            null
        }
        val feature = FaceJson.parseFeatureData(featureJson)
        if (feature == null || feature.isEmpty()) {
            if (!copy.isRecycled) copy.recycle()
            runOnUiThread {
                Toast.makeText(this, R.string.enroll_failed, Toast.LENGTH_SHORT).show()
            }
            return
        }
        if (!enrollPromptOpen.compareAndSet(false, true)) {
            if (!copy.isRecycled) copy.recycle()
            return
        }
        val crop = cropLargestFace(copy) ?: copy
        runOnUiThread {
            promptEnrollName(feature, crop, copy)
        }
    }

    private fun promptEnrollName(feature: ByteArray, thumb: Bitmap, full: Bitmap) {
        val input = EditText(this).apply {
            hint = getString(R.string.enroll_name_hint)
            setSingleLine()
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.enroll_name_title)
            .setView(input)
            .setCancelable(true)
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                enrollPromptOpen.set(false)
                if (thumb !== full && !thumb.isRecycled) thumb.recycle()
                if (!full.isRecycled) full.recycle()
            }
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val name = input.text?.toString().orEmpty()
                val person = client.enroll(name, feature, thumb)
                enrollPromptOpen.set(false)
                if (thumb !== full && !thumb.isRecycled) thumb.recycle()
                if (!full.isRecycled) full.recycle()
                if (person == null) {
                    Toast.makeText(this, R.string.enroll_failed, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (!resultOpened.compareAndSet(false, true)) return@setPositiveButton
                val json = JSONObject()
                    .put("success", true)
                    .put("mode", FaceMode.ENROLL.name)
                    .put("id", person.id)
                    .put("name", person.name)
                    .put("createdAt", person.createdAt)
                    .toString()
                val thumbPath = person.thumbnailFile?.let { fileName ->
                    File(filesDir, "face_thumbnails/$fileName").takeIf { it.isFile }?.absolutePath
                }
                openResult(json, thumbPath)
            }
            .setOnCancelListener {
                enrollPromptOpen.set(false)
                if (thumb !== full && !thumb.isRecycled) thumb.recycle()
                if (!full.isRecycled) full.recycle()
            }
            .show()
    }

    // endregion

    // region Identity (circle guide + hold ring)

    private fun analyzeIdentityFrame(bitmap: Bitmap) {
        if (!frameBusy.compareAndSet(false, true)) {
            if (!bitmap.isRecycled) bitmap.recycle()
            return
        }
        client.async {
            try {
                if (resultOpened.get() || isFinishing || confirming.get()) return@async
                val detectJson = FaceRecognitionQueue.detect(bitmap, false, OVERLAY_FLAGS)
                val faces = FaceJson.parseDetect(detectJson)
                val frameSize = Size(bitmap.width, bitmap.height)
                val state = IdentityCapture.evaluate(this, faces, frameSize)
                val now = System.currentTimeMillis()
                val holdMs = SettingsActivity.getIdentityHoldDurationMs(this)

                val keep = try {
                    CameraFrameUtils.copyArgb(bitmap)
                } catch (_: Exception) {
                    null
                }
                if (keep != null) {
                    synchronized(this) {
                        lastFrame?.takeIf { !it.isRecycled }?.recycle()
                        lastFrame = keep
                        lastFrameW = keep.width
                        lastFrameH = keep.height
                    }
                }

                val allowed = state == FACE_CAPTURE_STATE.CAPTURE_OK
                var progress = 0f
                var shouldCapture = false
                if (allowed) {
                    if (identityOkSinceMs == 0L || lastIdentityState != FACE_CAPTURE_STATE.CAPTURE_OK) {
                        identityOkSinceMs = now
                    }
                    val elapsed = now - identityOkSinceMs
                    progress = (elapsed.toFloat() / holdMs.toFloat()).coerceIn(0f, 1f)
                    if (elapsed >= holdMs) {
                        shouldCapture = true
                    }
                } else {
                    identityOkSinceMs = 0L
                    progress = 1f
                }
                lastIdentityState = state

                runOnUiThread {
                    if (isFinishing || isDestroyed || resultOpened.get()) return@runOnUiThread
                    identityGuide.setFrameSize(frameSize)
                    identityGuide.setGuideState(state, progress)
                    val prev = txtIdentityHint.text
                    txtIdentityHint.setText(IdentityCapture.messageRes(state))
                    txtIdentityHint.setTextColor(
                        ContextCompat.getColor(
                            this,
                            when (state) {
                                FACE_CAPTURE_STATE.CAPTURE_OK -> R.color.ix_ok
                                FACE_CAPTURE_STATE.NO_FACE -> R.color.ix_text
                                FACE_CAPTURE_STATE.MULTIPLE_FACES,
                                FACE_CAPTURE_STATE.FACE_OCCLUDED,
                                FACE_CAPTURE_STATE.SPOOFED_FACE,
                                -> R.color.ix_danger
                                else -> R.color.ix_warn
                            },
                        ),
                    )
                    if (prev != txtIdentityHint.text) {
                        txtIdentityHint.animate().cancel()
                        txtIdentityHint.scaleX = 0.92f
                        txtIdentityHint.scaleY = 0.92f
                        txtIdentityHint.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(180L)
                            .start()
                    }
                }

                if (shouldCapture && confirming.compareAndSet(false, true)) {
                    val captureFrame = synchronized(this) {
                        lastFrame?.takeIf { !it.isRecycled }?.let {
                            try {
                                CameraFrameUtils.copyArgb(it)
                            } catch (_: Exception) {
                                null
                            }
                        }
                    }
                    if (captureFrame == null) {
                        confirming.set(false)
                        identityOkSinceMs = 0L
                    } else {
                        runOnUiThread {
                            txtIdentityHint.setText(R.string.identity_status_capturing)
                            identityGuide.setGuideState(FACE_CAPTURE_STATE.CAPTURE_OK, 1f)
                        }
                        finishIdentityCapture(captureFrame, faces)
                    }
                }
            } catch (_: Exception) {
                identityOkSinceMs = 0L
            } finally {
                if (!bitmap.isRecycled) bitmap.recycle()
                frameBusy.set(false)
            }
        }
    }

    private fun finishIdentityCapture(bitmap: Bitmap, faces: List<DetectedFace>) {
        if (!resultOpened.compareAndSet(false, true)) {
            if (!bitmap.isRecycled) bitmap.recycle()
            confirming.set(false)
            return
        }
        val feature = try {
            client.extractFeature(bitmap)
        } catch (_: Exception) {
            null
        }
        val threshold = SettingsActivity.getIdentifyThreshold(this)
        val best = if (feature != null) client.bestMatch(feature, threshold) else null
        val cropFace = faces.maxByOrNull { it.region.width() * it.region.height() }
        val crop = if (cropFace != null) {
            CameraFrameUtils.cropFace(bitmap, cropFace.region)
        } else {
            null
        }
        val thumb = saveThumb(crop ?: bitmap)
        if (crop != null && crop !== bitmap && !crop.isRecycled) crop.recycle()
        if (!bitmap.isRecycled) bitmap.recycle()

        var enrolledThumb: String? = null
        if (best != null) {
            val fileName = best.person.thumbnailFile
            if (!fileName.isNullOrBlank()) {
                val f = File(filesDir, "face_thumbnails/$fileName")
                if (f.isFile) enrolledThumb = f.absolutePath
            }
        }
        val json = JSONObject()
            .put("success", best != null)
            .put("mode", FaceMode.IDENTITY.name)
            .put("matched", best != null)
            .apply {
                if (best != null) {
                    put("name", best.person.name)
                    put("id", best.person.id)
                    put("score", best.score.toDouble())
                }
            }
            .toString()
        openResult(
            json,
            thumb,
            title = getString(R.string.identify_result_title),
            thumb2 = enrolledThumb,
        )
    }

    // endregion

    // region Gallery

    private fun processGalleryUri(uri: Uri) {
        if (resultOpened.get() || enrollPromptOpen.get()) return
        client.async {
            val bitmap = try {
                Utils.getCorrectlyOrientedImage(this, uri)
            } catch (_: Exception) {
                null
            }
            if (bitmap == null) {
                runOnUiThread {
                    Toast.makeText(this, R.string.gallery_load_failed, Toast.LENGTH_SHORT).show()
                }
                return@async
            }
            val prepared = CameraFrameUtils.enginePreparedImage(bitmap)
            if (prepared !== bitmap && !bitmap.isRecycled) bitmap.recycle()
            when (mode) {
                FaceMode.MATCH -> handleMatchStable(prepared)
                FaceMode.ENROLL -> handleEnrollStable(prepared)
                FaceMode.LANDMARKS -> handleLandmarksStable(prepared)
                FaceMode.IDENTITY -> identifyFromStill(prepared)
                else -> {
                    if (!resultOpened.compareAndSet(false, true)) {
                        if (!prepared.isRecycled) prepared.recycle()
                        return@async
                    }
                    val json = try {
                        FaceRecognitionQueue.sync {
                            ModeAnalyzer.analyze(
                                mode,
                                prepared,
                                landmarkMode = SettingsActivity.getLandmarkMode(this),
                            )
                        }
                    } catch (_: Exception) {
                        null
                    }
                    val thumb = saveThumb(cropLargestFace(prepared) ?: prepared)
                    if (!prepared.isRecycled) prepared.recycle()
                    openResult(json, thumb)
                }
            }
        }
    }

    private fun identifyFromStill(bitmap: Bitmap) {
        if (!resultOpened.compareAndSet(false, true)) {
            if (!bitmap.isRecycled) bitmap.recycle()
            return
        }
        val feature = try {
            client.extractFeature(bitmap)
        } catch (_: Exception) {
            null
        }
        val threshold = SettingsActivity.getIdentifyThreshold(this)
        val best = if (feature != null) client.bestMatch(feature, threshold) else null
        val thumb = saveThumb(cropLargestFace(bitmap) ?: bitmap)
        if (!bitmap.isRecycled) bitmap.recycle()
        var enrolledThumb: String? = null
        if (best != null) {
            val fileName = best.person.thumbnailFile
            if (!fileName.isNullOrBlank()) {
                val f = File(filesDir, "face_thumbnails/$fileName")
                if (f.isFile) enrolledThumb = f.absolutePath
            }
        }
        val json = JSONObject()
            .put("success", best != null)
            .put("mode", FaceMode.IDENTITY.name)
            .put("matched", best != null)
            .apply {
                if (best != null) {
                    put("name", best.person.name)
                    put("id", best.person.id)
                    put("score", best.score.toDouble())
                }
            }
            .toString()
        openResult(
            json,
            thumb,
            title = getString(R.string.identify_result_title),
            thumb2 = enrolledThumb,
        )
    }

    // endregion

    private fun openResult(
        json: String?,
        thumbPath: String?,
        title: String? = null,
        thumb2: String? = null,
        landmarksXy: FloatArray? = null,
    ) {
        val path = writeJsonCache(json ?: """{"success":false,"message":"empty"}""")
        runOnUiThread {
            if (isFinishing || isDestroyed) return@runOnUiThread
            startActivity(
                Intent(this, ModeResultActivity::class.java)
                    .putExtra(RESULT_JSON_PATH, path)
                    .putExtra(RESULT_TITLE, title ?: mode.title)
                    .putExtra(RESULT_MODE, mode.name)
                    .apply {
                        if (!thumbPath.isNullOrBlank()) putExtra(RESULT_THUMB_PATH, thumbPath)
                        if (!thumb2.isNullOrBlank()) putExtra(RESULT_THUMB2_PATH, thumb2)
                        if (landmarksXy != null && landmarksXy.isNotEmpty()) {
                            putExtra(RESULT_LANDMARKS_XY, landmarksXy)
                        }
                    },
            )
            finish()
        }
    }

    private fun writeJsonCache(json: String): String {
        val file = File(cacheDir, "mode_result_${System.currentTimeMillis()}.json")
        file.writeText(json)
        return file.absolutePath
    }

    private fun saveThumb(bitmap: Bitmap?): String? {
        if (bitmap == null || bitmap.isRecycled) return null
        return try {
            val file = File(cacheDir, "mode_thumb_${System.currentTimeMillis()}.jpg")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
            file.absolutePath
        } catch (_: Exception) {
            null
        }
    }

    private fun cropLargestFace(bitmap: Bitmap): Bitmap? {
        val detectJson = try {
            FaceRecognitionQueue.detect(bitmap, false, FaceRecognitionSDK.DETECT_POSE)
        } catch (_: Exception) {
            null
        }
        val faces = FaceJson.parseDetect(detectJson)
        val best = faces.maxByOrNull { it.region.width() * it.region.height() } ?: return null
        return CameraFrameUtils.cropFace(bitmap, best.region)
    }

    private fun toFaceBoxes(faces: List<DetectedFace>): List<FaceBox> =
        faces.map { face ->
            FaceBox().apply {
                x1 = face.region.left.roundToInt()
                y1 = face.region.top.roundToInt()
                x2 = face.region.right.roundToInt()
                y2 = face.region.bottom.roundToInt()
                yaw = face.yaw.toFloat()
                pitch = face.pitch.toFloat()
                roll = face.roll.toFloat()
                val n = min(face.landmarks.size, landmarks_68.size / 2)
                landmarkCount = n
                for (i in 0 until n) {
                    landmarks_68[i * 2] = face.landmarks[i].x
                    landmarks_68[i * 2 + 1] = face.landmarks[i].y
                }
            }
        }
}
