package com.vooapp.justdance.camera

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions

/**
 * Detecta a pose de corpo inteiro com o ML Kit e devolve um [PoseSnapshot].
 * Modelo embutido — funciona offline.
 */
class PoseAnalyzer(
    private val onResult: (PoseSnapshot?) -> Unit,
) : ImageAnalysis.Analyzer {

    private val detector = PoseDetection.getClient(
        PoseDetectorOptions.Builder().setDetectorMode(PoseDetectorOptions.STREAM_MODE).build()
    )

    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) { imageProxy.close(); return }
        val rotation = imageProxy.imageInfo.rotationDegrees
        val w: Float; val h: Float
        if (rotation == 90 || rotation == 270) { w = mediaImage.height.toFloat(); h = mediaImage.width.toFloat() }
        else { w = mediaImage.width.toFloat(); h = mediaImage.height.toFloat() }

        detector.process(InputImage.fromMediaImage(mediaImage, rotation))
            .addOnSuccessListener { pose -> onResult(pose.toSnapshot(w, h)) }
            .addOnFailureListener { onResult(null) }
            .addOnCompleteListener { imageProxy.close() }
    }

    fun close() { detector.close() }

    private fun Pose.toSnapshot(w: Float, h: Float): PoseSnapshot {
        fun j(type: Int): Joint? {
            val lm: PoseLandmark = getPoseLandmark(type) ?: return null
            return Joint((lm.position.x / w).coerceIn(0f, 1f), (lm.position.y / h).coerceIn(0f, 1f),
                lm.inFrameLikelihood >= MIN_LIKELIHOOD)
        }
        return PoseSnapshot(
            leftShoulder = j(PoseLandmark.LEFT_SHOULDER), rightShoulder = j(PoseLandmark.RIGHT_SHOULDER),
            leftElbow = j(PoseLandmark.LEFT_ELBOW), rightElbow = j(PoseLandmark.RIGHT_ELBOW),
            leftWrist = j(PoseLandmark.LEFT_WRIST), rightWrist = j(PoseLandmark.RIGHT_WRIST),
            leftHip = j(PoseLandmark.LEFT_HIP), rightHip = j(PoseLandmark.RIGHT_HIP),
            leftKnee = j(PoseLandmark.LEFT_KNEE), rightKnee = j(PoseLandmark.RIGHT_KNEE),
            leftAnkle = j(PoseLandmark.LEFT_ANKLE), rightAnkle = j(PoseLandmark.RIGHT_ANKLE),
        )
    }

    companion object { private const val MIN_LIKELIHOOD = 0.4f }
}
