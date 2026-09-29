package com.identixia.facerecognitionsdk.ui

import android.graphics.BitmapFactory
import android.graphics.PointF
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.identixia.facerecognitionsdk.R
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

class ModeResultActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mode_result)

        val title = intent.getStringExtra(ModeCameraActivity.RESULT_TITLE)
            ?: getString(R.string.app_name)
        val modeName = intent.getStringExtra(ModeCameraActivity.RESULT_MODE).orEmpty()
        val jsonPath = intent.getStringExtra(ModeCameraActivity.RESULT_JSON_PATH)
        val thumbPath = intent.getStringExtra(ModeCameraActivity.RESULT_THUMB_PATH)
        val thumb2Path = intent.getStringExtra(ModeCameraActivity.RESULT_THUMB2_PATH)
        val landmarksXy = intent.getFloatArrayExtra(ModeCameraActivity.RESULT_LANDMARKS_XY)

        findViewById<TextView>(R.id.txtTitle).text = title
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }

        val jsonText = jsonPath?.let { path ->
            val file = File(path)
            if (file.isFile) file.readText() else null
        }.orEmpty()

        val root = parseRoot(jsonText)
        val view = buildFriendlyView(modeName, root)

        findViewById<TextView>(R.id.txtStatus).text = view.status
        findViewById<TextView>(R.id.txtStatus).setTextColor(
            ContextCompat.getColor(
                this,
                if (view.ok) R.color.ix_accent else R.color.ix_danger,
            ),
        )
        findViewById<TextView>(R.id.txtSummary).text = view.summary

        bindMedia(modeName, thumbPath, thumb2Path, landmarksXy, view.ok)
        bindScore(view.scoreLabel)
        bindFields(findViewById(R.id.lytFields), view.fields)

        val raw = findViewById<TextView>(R.id.txtRawJson)
        val toggle = findViewById<TextView>(R.id.btnRawToggle)
        raw.text = prettyJson(jsonText)
        raw.visibility = View.GONE
        toggle.setOnClickListener {
            val open = raw.visibility != View.VISIBLE
            raw.visibility = if (open) View.VISIBLE else View.GONE
            toggle.setText(if (open) R.string.raw_json_hide else R.string.raw_json_show)
        }
    }

    private fun bindMedia(
        modeName: String,
        thumbPath: String?,
        thumb2Path: String?,
        landmarksXy: FloatArray?,
        matchOk: Boolean,
    ) {
        val imgLandmarks = findViewById<LandmarkImageView>(R.id.imgLandmarks)
        val lytMatch = findViewById<LinearLayout>(R.id.lytMatchPair)
        val lytImages = findViewById<LinearLayout>(R.id.lytImages)
        imgLandmarks.visibility = View.GONE
        lytMatch.visibility = View.GONE
        lytImages.visibility = View.GONE

        val bmp1 = thumbPath?.takeIf { it.isNotBlank() }?.let { BitmapFactory.decodeFile(it) }
        val bmp2 = thumb2Path?.takeIf { it.isNotBlank() }?.let { BitmapFactory.decodeFile(it) }

        when {
            modeName == FaceMode.LANDMARKS.name && bmp1 != null -> {
                val points = landmarksFromExtra(landmarksXy)
                imgLandmarks.visibility = View.VISIBLE
                imgLandmarks.setContent(bmp1, points)
            }
            modeName == FaceMode.MATCH.name && bmp1 != null && bmp2 != null -> {
                lytMatch.visibility = View.VISIBLE
                findViewById<ImageView>(R.id.imgMatchLeft).setImageBitmap(bmp1)
                findViewById<ImageView>(R.id.imgMatchRight).setImageBitmap(bmp2)
                findViewById<TextView>(R.id.txtMatchLeftLabel).setText(R.string.face_one)
                findViewById<TextView>(R.id.txtMatchRightLabel).setText(R.string.face_two)
                val symbol = findViewById<TextView>(R.id.txtMatchSymbol)
                symbol.text = if (matchOk) "=" else "≠"
                symbol.setTextColor(
                    ContextCompat.getColor(
                        this,
                        if (matchOk) R.color.ix_accent else R.color.ix_danger,
                    ),
                )
            }
            modeName == FaceMode.IDENTITY.name && bmp1 != null -> {
                lytMatch.visibility = View.VISIBLE
                findViewById<ImageView>(R.id.imgMatchLeft).setImageBitmap(bmp1)
                findViewById<TextView>(R.id.txtMatchLeftLabel).setText(R.string.identified)
                if (bmp2 != null) {
                    findViewById<ImageView>(R.id.imgMatchRight).setImageBitmap(bmp2)
                    findViewById<TextView>(R.id.txtMatchRightLabel).setText(R.string.enrolled)
                } else {
                    findViewById<ImageView>(R.id.imgMatchRight).setImageResource(R.drawable.ic_people)
                    findViewById<TextView>(R.id.txtMatchRightLabel).setText(R.string.enrolled)
                }
                val symbol = findViewById<TextView>(R.id.txtMatchSymbol)
                symbol.text = if (matchOk) "=" else "≠"
                symbol.setTextColor(
                    ContextCompat.getColor(
                        this,
                        if (matchOk) R.color.ix_accent else R.color.ix_danger,
                    ),
                )
            }
            bmp1 != null -> {
                lytImages.visibility = View.VISIBLE
                findViewById<ImageView>(R.id.imgPrimary).setImageBitmap(bmp1)
                findViewById<TextView>(R.id.txtImgPrimaryLabel).setText(
                    when (modeName) {
                        FaceMode.ENROLL.name -> R.string.enrolled
                        else -> R.string.captured_face
                    },
                )
            }
        }
    }

    private fun landmarksFromExtra(xy: FloatArray?): List<PointF> {
        if (xy == null || xy.size < 2) return emptyList()
        return (0 until xy.size / 2).map { PointF(xy[it * 2], xy[it * 2 + 1]) }
    }

    private fun bindScore(label: String?) {
        val txt = findViewById<TextView>(R.id.txtScore)
        if (label.isNullOrBlank()) {
            txt.visibility = View.GONE
        } else {
            txt.visibility = View.VISIBLE
            txt.text = label
        }
    }

    private fun bindFields(container: LinearLayout, fields: List<Pair<String, String>>) {
        container.removeAllViews()
        if (fields.isEmpty()) {
            val empty = TextView(this).apply {
                text = getString(R.string.result_no_fields)
                setTextColor(ContextCompat.getColor(this@ModeResultActivity, R.color.ix_muted))
                textSize = 14f
                setPadding(dp(16), dp(12), dp(16), dp(12))
            }
            container.addView(empty)
            return
        }
        for ((label, value) in fields) {
            if (label == SECTION) {
                val header = TextView(this).apply {
                    text = value
                    textSize = 12f
                    letterSpacing = 0.04f
                    setAllCaps(true)
                    setTextColor(ContextCompat.getColor(this@ModeResultActivity, R.color.ix_accent))
                    setPadding(dp(16), dp(14), dp(16), dp(4))
                }
                container.addView(header)
                continue
            }
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), dp(8), dp(16), dp(8))
            }
            row.addView(
                TextView(this).apply {
                    text = label
                    textSize = 12f
                    setTextColor(ContextCompat.getColor(this@ModeResultActivity, R.color.ix_muted))
                },
            )
            row.addView(
                TextView(this).apply {
                    text = value
                    textSize = 15f
                    setTextColor(ContextCompat.getColor(this@ModeResultActivity, R.color.ix_text))
                    setTextIsSelectable(true)
                },
            )
            container.addView(row)
        }
    }

    private data class FriendlyView(
        val ok: Boolean,
        val status: String,
        val summary: String,
        val scoreLabel: String?,
        val fields: List<Pair<String, String>>,
    )

    private fun buildFriendlyView(modeName: String, root: JSONObject?): FriendlyView {
        if (root == null) {
            return FriendlyView(
                ok = false,
                status = getString(R.string.result_failed),
                summary = getString(R.string.no_face_detected),
                scoreLabel = null,
                fields = emptyList(),
            )
        }
        val mode = modeName.ifBlank { root.optString("mode") }
        val faces = facesOf(root)
        val score = extractScore(root)
        val fields = mutableListOf<Pair<String, String>>()
        val threshold = SettingsActivity.getIdentifyThreshold(this).toDouble()

        when (mode) {
            FaceMode.IDENTITY.name -> {
                val matched = root.optBoolean("matched", root.has("name") && score != null)
                val name = root.optString("name").ifBlank { "—" }
                if (score != null) fields += "Similarity" to formatScore(score)
                if (root.has("id")) fields += "Person id" to root.optString("id")
                fields += "Name" to name
                return FriendlyView(
                    ok = matched,
                    status = if (matched) getString(R.string.result_identified) else getString(R.string.result_no_match),
                    summary = if (matched) {
                        getString(R.string.result_identity_summary_short, name)
                    } else {
                        getString(R.string.result_no_match_hint)
                    },
                    scoreLabel = score?.let { getString(R.string.result_score_fmt, formatScore(it)) },
                    fields = fields,
                )
            }
            FaceMode.ENROLL.name -> {
                val name = root.optString("name").ifBlank { "—" }
                fields += "Name" to name
                if (root.has("id")) fields += "Person id" to root.optString("id")
                return FriendlyView(
                    ok = root.optBoolean("success", true),
                    status = getString(R.string.result_enrolled),
                    summary = getString(R.string.result_enroll_summary, name),
                    scoreLabel = null,
                    fields = fields,
                )
            }
            FaceMode.MATCH.name -> {
                val same = when {
                    root.has("same") -> root.optBoolean("same")
                    root.has("matched") -> root.optBoolean("matched")
                    score != null -> score >= threshold
                    else -> false
                }
                if (score != null) {
                    fields += SECTION to "Match"
                    fields += "Similarity" to formatScore(score)
                    fields += "Threshold" to formatScore(threshold)
                    fields += "Verdict" to if (same) "Same person" else "Different person"
                }
                appendFaceFields(fields, faces)
                return FriendlyView(
                    ok = same,
                    status = if (same) getString(R.string.result_match_same) else getString(R.string.result_match_different),
                    summary = if (score != null) {
                        getString(R.string.result_match_summary, formatScore(score))
                    } else {
                        getString(R.string.no_face_detected)
                    },
                    scoreLabel = score?.let { getString(R.string.result_score_fmt, formatScore(it)) },
                    fields = fields,
                )
            }
            FaceMode.LANDMARKS.name -> {
                val count = faces?.optJSONObject(0)?.let { face ->
                    val lm = face.optJSONArray("landmarks") ?: face.optJSONArray("facePoints")
                    lm?.length() ?: 0
                } ?: 0
                appendFaceFields(fields, faces)
                return FriendlyView(
                    ok = count > 0 || (faces?.length() ?: 0) > 0,
                    status = getString(R.string.result_landmarks_title),
                    summary = if (count > 0) {
                        getString(R.string.result_landmark_count, count)
                    } else {
                        getString(R.string.result_one_face)
                    },
                    scoreLabel = null,
                    fields = fields,
                )
            }
            FaceMode.LIVENESS.name -> {
                val auth = authenticityFromFaces(faces)
                appendFaceFields(fields, faces, preferAuthenticity = true)
                return FriendlyView(
                    ok = auth.ok,
                    status = auth.heading,
                    summary = if ((faces?.length() ?: 0) > 0) {
                        getString(R.string.result_one_face)
                    } else {
                        getString(R.string.no_face_detected)
                    },
                    scoreLabel = null,
                    fields = fields,
                )
            }
            else -> {
                val count = faces?.length() ?: 0
                val ok = root.optBoolean("success", count > 0 || root.has("result") || score != null)
                appendFaceFields(fields, faces)
                appendTopLevelExtras(fields, root)
                return FriendlyView(
                    ok = ok,
                    status = if (ok) getString(R.string.result_ok) else getString(R.string.result_failed),
                    summary = when {
                        count == 1 -> getString(R.string.result_one_face)
                        count > 1 -> getString(R.string.result_n_faces, count)
                        else -> root.optString("message").ifBlank { getString(R.string.no_face_detected) }
                    },
                    scoreLabel = score?.let { getString(R.string.result_score_fmt, formatScore(it)) },
                    fields = fields,
                )
            }
        }
    }

    private data class AuthView(val ok: Boolean, val heading: String)

    private fun authenticityFromFaces(faces: JSONArray?): AuthView {
        val face = faces?.optJSONObject(0) ?: return AuthView(false, "FAKE")
        val traits = face.optJSONObject("traits")
            ?: face.optJSONObject("attributes")
            ?: return AuthView(false, "FAKE")
        val live = traits.optJSONObject("liveness2d")
            ?: traits.optJSONObject("Liveness2D")
            ?: traits.optJSONObject("liveness")
        val df = traits.optJSONObject("deepfake")
            ?: traits.optJSONObject("Deepfake")
        val liveLabel = live?.optString("value").orEmpty()
        val liveScore = when {
            live == null -> 0f
            live.has("confidence") -> live.optDouble("confidence").toFloat()
            else -> 0f
        }
        val dfRaw = when {
            df == null -> ""
            df.opt("value") is Boolean -> df.optBoolean("value").toString()
            else -> df.optString("value")
        }.let { base ->
            val conf = df?.opt("confidence")
            if (conf is Number) "$base (${conf.toDouble()})" else base
        }
        val heading = ResultDetails.authenticityHeading(this, liveScore, liveLabel, dfRaw)
        return AuthView(heading == "REAL", heading)
    }

    private fun appendFaceFields(
        fields: MutableList<Pair<String, String>>,
        faces: JSONArray?,
        preferAuthenticity: Boolean = false,
    ) {
        if (faces == null || faces.length() == 0) return
        for (i in 0 until faces.length()) {
            val face = faces.optJSONObject(i) ?: continue
            fields += SECTION to if (faces.length() == 1) "Face" else "Face ${i + 1}"
            val region = face.optJSONObject("box")
                ?: face.optJSONObject("faceRegion")
                ?: face.optJSONObject("region")
            if (region != null) {
                val box = parseBox(region)
                if (box != null) {
                    fields += "Box" to "${box[0]}, ${box[1]} · ${box[2]}×${box[3]}"
                }
            }
            val pose = face.optJSONObject("pose") ?: face.optJSONObject("facePose")
            if (pose != null) {
                fields += "Pose" to "yaw ${fmt(pose.optDouble("yaw"))}°  roll ${fmt(pose.optDouble("roll"))}°  pitch ${fmt(pose.optDouble("pitch"))}°"
            }
            val traits = face.optJSONObject("traits")
                ?: face.optJSONObject("attributes")
                ?: face.optJSONObject("quality")
            if (traits != null) {
                if (preferAuthenticity) {
                    appendAuthenticityTraits(fields, traits)
                }
                val keys = traits.keys().asSequence().toList().sorted()
                for (key in keys) {
                    val lower = key.lowercase(Locale.US)
                    if (preferAuthenticity && (lower.contains("liveness") || lower.contains("deepfake"))) {
                        continue
                    }
                    val shown = if (lower.contains("deepfake")) {
                        deepfakeTraitText(traits.opt(key))
                    } else {
                        traitValue(traits.opt(key))
                    }
                    if (shown.isNotBlank()) fields += humanize(key) to shown
                }
            }
            val landmarks = face.optJSONArray("landmarks") ?: face.optJSONArray("facePoints")
            if (landmarks != null && landmarks.length() > 0) {
                fields += "Landmarks" to getString(R.string.result_landmark_count, landmarks.length())
            }
        }
    }

    private fun appendAuthenticityTraits(fields: MutableList<Pair<String, String>>, traits: JSONObject) {
        val live = traits.optJSONObject("liveness2d")
            ?: traits.optJSONObject("Liveness2D")
            ?: traits.optJSONObject("liveness")
        val df = traits.optJSONObject("deepfake")
            ?: traits.optJSONObject("Deepfake")
        val liveLabel = live?.optString("value").orEmpty()
        val liveScore = live?.optDouble("confidence", 0.0)?.toFloat() ?: 0f
        val dfRaw = when {
            df == null -> ""
            df.opt("value") is Boolean -> df.optBoolean("value").toString()
            else -> df.optString("value")
        }.let { base ->
            val conf = df?.opt("confidence")
            if (base.isNotBlank() && conf is Number) "$base (${conf.toDouble()})" else base
        }
        val verdict = ResultDetails.authenticityHeading(this, liveScore, liveLabel, dfRaw)
        fields += SECTION to "Authenticity"
        fields += "Verdict" to verdict
        if (live != null) {
            fields += "Liveness" to ResultDetails.livenessText(
                liveScore,
                SettingsActivity.getLivenessThreshold(this),
                liveLabel,
            )
        }
        val dfText = ResultDetails.deepfakeText(dfRaw)
        if (dfText.isNotBlank()) fields += "Deepfake" to dfText
    }

    private fun deepfakeTraitText(value: Any?): String {
        if (value == null || value == JSONObject.NULL) return ""
        if (value is JSONObject) {
            val raw = when (val v = value.opt("value")) {
                is Boolean -> v.toString()
                null, JSONObject.NULL -> ""
                else -> v.toString()
            }
            val conf = value.opt("confidence")
            val joined = if (conf is Number && raw.isNotBlank()) "$raw (${conf.toDouble()})" else raw
            return ResultDetails.deepfakeText(joined).ifBlank { traitValue(value) }
        }
        return ResultDetails.deepfakeText(value.toString())
    }

    private fun appendTopLevelExtras(fields: MutableList<Pair<String, String>>, root: JSONObject) {
        for (key in listOf("liveness", "quality", "label", "message")) {
            if (!root.has(key) || key == "message" && fields.isNotEmpty()) continue
            val shown = traitValue(root.opt(key))
            if (shown.isNotBlank() && fields.none { it.first.equals(humanize(key), true) }) {
                fields += humanize(key) to shown
            }
        }
    }

    private fun parseBox(region: JSONObject): IntArray? {
        if (region.has("width") || region.has("height") || region.has("x") || region.has("y")) {
            val x = region.optDouble("x", 0.0).roundToInt()
            val y = region.optDouble("y", 0.0).roundToInt()
            val w = region.optDouble("width", 0.0).roundToInt()
            val h = region.optDouble("height", 0.0).roundToInt()
            if (w > 0 && h > 0) return intArrayOf(x, y, w, h)
        }
        val l = when {
            region.has("left") -> region.optDouble("left")
            region.has("x1") -> region.optDouble("x1")
            else -> return null
        }.roundToInt()
        val t = when {
            region.has("top") -> region.optDouble("top")
            region.has("y1") -> region.optDouble("y1")
            else -> return null
        }.roundToInt()
        val r = when {
            region.has("right") -> region.optDouble("right")
            region.has("x2") -> region.optDouble("x2")
            else -> return null
        }.roundToInt()
        val b = when {
            region.has("bottom") -> region.optDouble("bottom")
            region.has("y2") -> region.optDouble("y2")
            else -> return null
        }.roundToInt()
        val w = r - l
        val h = b - t
        if (w <= 0 || h <= 0) return null
        return intArrayOf(l, t, w, h)
    }

    private fun facesOf(root: JSONObject): JSONArray? {
        root.optJSONArray("faces")?.let { return it }
        root.optJSONObject("result")?.optJSONArray("faces")?.let { return it }
        root.optJSONArray("data")?.let { return it }
        val detects = root.optJSONArray("detects")
        if (detects != null && detects.length() > 0) {
            // Prefer first image faces; for match, merge both detects' faces.
            val merged = JSONArray()
            for (i in 0 until detects.length()) {
                val faces = detects.optJSONObject(i)?.optJSONArray("faces") ?: continue
                for (j in 0 until faces.length()) merged.put(faces.opt(j))
            }
            if (merged.length() > 0) return merged
        }
        return null
    }

    private fun extractScore(root: JSONObject): Double? {
        fun fromObj(obj: JSONObject?): Double? {
            if (obj == null) return null
            if (obj.has("score") && !obj.isNull("score")) return obj.optDouble("score")
            if (obj.has("similarity") && !obj.isNull("similarity")) return obj.optDouble("similarity")
            return null
        }
        fromObj(root)?.let { return it }
        for (key in listOf("pairs", "match")) {
            val arr = root.optJSONArray(key) ?: continue
            if (arr.length() == 0) continue
            fromObj(arr.optJSONObject(0))?.let { return it }
        }
        val faces = facesOf(root)
        if (faces != null && faces.length() > 0) {
            fromObj(faces.optJSONObject(0))?.let { return it }
        }
        return null
    }

    private fun traitValue(value: Any?): String {
        return when (value) {
            null, JSONObject.NULL -> ""
            is JSONObject -> {
                value.optString("value").ifBlank {
                    value.optString("label").ifBlank {
                        if (value.has("confidence")) {
                            formatScore(value.optDouble("confidence"))
                        } else {
                            value.toString()
                        }
                    }
                }
            }
            is Number -> {
                val d = value.toDouble()
                if (d in 0.0..1.0) formatScore(d) else fmt(d)
            }
            is Boolean -> if (value) "Yes" else "No"
            else -> value.toString()
        }
    }

    private fun formatScore(score: Double): String {
        val pct = (score * 100.0).roundToInt().coerceIn(0, 100)
        return String.format(Locale.US, "%d%% (%.3f)", pct, score)
    }

    private fun fmt(v: Double): String = String.format(Locale.US, "%.1f", v)

    private fun humanize(key: String): String =
        key.replace('_', ' ')
            .replace(Regex("([a-z])([A-Z])"), "$1 $2")
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }

    private fun parseRoot(json: String): JSONObject? {
        if (json.isBlank()) return null
        return try {
            when {
                json.trimStart().startsWith("[") -> JSONObject().put("faces", JSONArray(json))
                else -> JSONObject(json)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun prettyJson(raw: String): String {
        if (raw.isBlank()) return "{}"
        return try {
            when {
                raw.trimStart().startsWith("[") -> JSONArray(raw).toString(2)
                else -> JSONObject(raw).toString(2)
            }
        } catch (_: Exception) {
            raw
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).roundToInt()

    companion object {
        private const val SECTION = "__section__"
    }
}
