package com.identixia.facerecognitionsdk

import android.graphics.BitmapFactory
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.identixia.facerecognitionsdk.ui.ResultDetails
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Device smoke: Liveness mode must return both liveness2d and deepfake traits
 * (Windows FaceSDK_liveness_all parity).
 *
 * Pass a key at runtime only — never commit licenses:
 *   adb shell am instrument -e license "YOUR_KEY" ...
 *   or set env IDENTIXIA_LICENSE before the test run.
 */
@RunWith(AndroidJUnit4::class)
class LivenessSmokeTest {
    @Test
    fun livenessAll_returnsLivenessAndDeepfake() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val license =
            InstrumentationRegistry.getArguments().getString("license")?.trim().orEmpty()
                .ifBlank { System.getenv("IDENTIXIA_LICENSE")?.trim().orEmpty() }
        assumeTrue(
            "Set instrumentation -e license=... or IDENTIXIA_LICENSE (do not commit keys)",
            license.isNotBlank(),
        )

        val act = FaceRecognitionSDK.activate(ctx, license)
        Log.i(TAG, "activate=$act")
        assertTrue("activate failed: $act", act == FaceRecognitionSDK.SDK_SUCCESS)

        val init = FaceRecognitionSDK.init(ctx)
        Log.i(TAG, "init=$init missing=${FaceRecognitionSDK.getMissingDatabases()}")
        assertTrue("init failed: $init", init == FaceRecognitionSDK.SDK_SUCCESS)

        for (asset in listOf("live_sample.jpg")) {
            val bytes = InstrumentationRegistry.getInstrumentation().context.assets.open(asset).use { it.readBytes() }
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            assertTrue("$asset decode failed", bmp != null)

            val raw = FaceRecognitionSDK.livenessAll(bmp!!)
            Log.i(TAG, "$asset raw=${raw.take(800)}")
            val env = JSONObject(raw)
            val status = env.optJSONObject("metadata")?.optInt("status")
                ?: env.optJSONObject("meta")?.optInt("status")
                ?: env.optInt("status", -999)
            assertTrue("$asset status=$status body=${raw.take(400)}", status == 0)

            val faces = env.optJSONArray("faces")
                ?: env.optJSONObject("data")?.optJSONArray("faces")
                ?: env.optJSONArray("data")
            assertTrue("$asset no faces", faces != null && faces.length() > 0)
            val face = faces!!.optJSONObject(0)
            val traits = face.optJSONObject("traits") ?: face.optJSONObject("attributes")
            assertTrue("$asset no traits", traits != null)

            val live = traits!!.optJSONObject("liveness2d")
                ?: traits.optJSONObject("Liveness2D")
                ?: traits.optJSONObject("liveness")
            val df = traits.optJSONObject("deepfake") ?: traits.optJSONObject("Deepfake")
            assertTrue("$asset missing liveness2d: $traits", live != null)
            assertTrue("$asset missing deepfake: $traits", df != null)

            val liveLabel = live!!.optString("value").ifBlank { live.optString("result") }
            val liveScore = when {
                live.has("liveness_score") -> live.optDouble("liveness_score").toFloat()
                live.has("confidence") -> live.optDouble("confidence").toFloat()
                else -> 0f
            }
            val dfRaw = when (val v = df!!.opt("value")) {
                is Boolean -> v.toString()
                else -> df.optString("value")
            }
            val verdict = ResultDetails.authenticityHeading(ctx, liveScore, liveLabel, dfRaw)
            Log.i(
                TAG,
                "$asset live=$liveLabel score=$liveScore df=$dfRaw kind=${ResultDetails.deepfakeKind(dfRaw)} verdict=$verdict",
            )
            assertTrue("$asset empty liveness value", liveLabel.isNotBlank())
            assertTrue("$asset empty deepfake value", dfRaw.isNotBlank())
            assertTrue(
                "$asset deepfake kind empty for $dfRaw",
                ResultDetails.deepfakeKind(dfRaw).isNotEmpty(),
            )
        }
    }

    companion object {
        private const val TAG = "LivenessSmoke"
    }
}
