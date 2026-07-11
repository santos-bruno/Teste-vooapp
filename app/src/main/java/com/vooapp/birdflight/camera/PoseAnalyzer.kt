package com.vooapp.birdflight.camera

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import com.vooapp.birdflight.input.FlightInput
import com.vooapp.birdflight.input.PoseInterpreter

/**
 * Analisa cada frame da câmera com o ML Kit Pose Detection (modelo embutido,
 * funciona offline), monta um [PoseFrame] normalizado e o converte em
 * [FlightInput] através do [PoseInterpreter].
 */
class PoseAnalyzer(
    private val onResult: (FlightInput, PoseFrame?) -> Unit,
) : ImageAnalysis.Analyzer {

    private val detector = PoseDetection.getClient(
        PoseDetectorOptions.Builder()
            .setDetectorMode(PoseDetectorOptions.STREAM_MODE)
            .build()
    )

    private val interpreter = PoseInterpreter()

    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        val rotation = imageProxy.imageInfo.rotationDegrees
        // Dimensões da imagem já "em pé" (após a rotação).
        val uprightW: Int
        val uprightH: Int
        if (rotation == 90 || rotation == 270) {
            uprightW = mediaImage.height
            uprightH = mediaImage.width
        } else {
            uprightW = mediaImage.width
            uprightH = mediaImage.height
        }

        val inputImage = InputImage.fromMediaImage(mediaImage, rotation)

        detector.process(inputImage)
            .addOnSuccessListener { pose ->
                val frame = pose.toPoseFrame(
                    uprightW.toFloat(),
                    uprightH.toFloat(),
                    imageProxy.imageInfo.timestamp / 1_000_000L,
                )
                val input = interpreter.interpret(frame)
                onResult(input, frame)
            }
            .addOnFailureListener {
                onResult(FlightInput.IDLE, null)
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }

    fun close() {
        detector.close()
    }

    private fun Pose.toPoseFrame(w: Float, h: Float, tsMs: Long): PoseFrame {
        fun joint(type: Int): Joint? {
            val lm: PoseLandmark = getPoseLandmark(type) ?: return null
            val inFrame = lm.inFrameLikelihood >= MIN_LIKELIHOOD
            return Joint(
                x = (lm.position.x / w).coerceIn(0f, 1f),
                y = (lm.position.y / h).coerceIn(0f, 1f),
                inFrame = inFrame,
            )
        }
        return PoseFrame(
            leftShoulder = joint(PoseLandmark.LEFT_SHOULDER),
            rightShoulder = joint(PoseLandmark.RIGHT_SHOULDER),
            leftElbow = joint(PoseLandmark.LEFT_ELBOW),
            rightElbow = joint(PoseLandmark.RIGHT_ELBOW),
            leftWrist = joint(PoseLandmark.LEFT_WRIST),
            rightWrist = joint(PoseLandmark.RIGHT_WRIST),
            leftHip = joint(PoseLandmark.LEFT_HIP),
            rightHip = joint(PoseLandmark.RIGHT_HIP),
            timestampMs = tsMs,
        )
    }

    companion object {
        private const val MIN_LIKELIHOOD = 0.5f
    }
}
