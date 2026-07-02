package com.example.recallai.face

import com.google.mlkit.vision.face.Face
import kotlin.math.abs

object FaceUsability {
    private const val MAX_YAW_DEG = 20f
    private const val MAX_ROLL_DEG = 15f
    private const val MIN_FACE_WIDTH_PX = 120

    fun isFaceUsable(face: Face): Boolean {
        val box = face.boundingBox
        return abs(face.headEulerAngleY) < MAX_YAW_DEG &&
            abs(face.headEulerAngleZ) < MAX_ROLL_DEG &&
            box.width() >= MIN_FACE_WIDTH_PX
    }
}
