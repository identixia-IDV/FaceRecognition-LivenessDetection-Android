package com.identixia.facerecognitionsdk

import android.graphics.BitmapFactory
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.identixia.facerecognitionsdk.ui.ResultDetails
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Device smoke: Liveness mode must return both liveness2d and deepfake traits
 * (Windows FaceSDK_liveness_all parity).
 */
@RunWith(AndroidJUnit4::class)
class LivenessSmokeTest {
    @Test
    fun livenessAll_returnsLivenessAndDeepfake() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val license =
            "pyyR2AEC9OOJtzGzuUiqnj1UoycDkCGbbI4QJOyk7/gwHGIAAAAOQ5Kyt/i1G3FerFj8/3i91gzLJCSx2P5IE+KM0uD7V8lSe57423nUFDc2YkS4siW+Nmt56Dpg+5s8l2kmjaUHzc7ArIMCPUTe7hTAG064A5aEjGsSNOLMSlm5A2njLclVM2YAMGQCMCzpL9yW4JYyQ+6LOq+3d+7/1DoQ4Q5N//A8XPN5yxNq30ZRxkInjsTvgHWjwKdkpAIwWImkTNEFVHBQsrZO4J8frcoHevTdrnzh1g22fL79wjwngeGNQsGaZkRO9E6p6Xhw"

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
