package org.beobma.classWarPlugin.util

import org.bukkit.entity.Display
import org.bukkit.entity.ItemDisplay
import org.bukkit.util.Transformation
import org.bukkit.util.Vector
import org.joml.Quaternionf
import org.joml.Vector3f
import kotlin.math.atan2

/** 아이템 모델의 손잡이→검끝 축을 실제 월드 진행 방향에 정렬한다. */
object DisplayOrientationUtil {
    // ItemDisplayRenderer가 display transformation 뒤에 Y축 180도 회전을 적용한다.
    // 따라서 transformation이 실제로 받는 기본 검 축은 우하단 손잡이→좌상단 검끝이다.
    private val swordModelBladeAxis = Vector3f(-1.0f, 1.0f, 0.0f).normalize()
    private val swordModelFaceNormal = Vector3f(0.0f, 0.0f, -1.0f)

    /** 검의 넓은 면이 위를 향하도록 수평으로 눕힌다. */
    fun alignSwordBladeHorizontally(display: ItemDisplay, bladeDirection: Vector, scale: Float) {
        alignSwordBlade(display, bladeDirection, Vector3f(0.0f, 1.0f, 0.0f), scale)
    }

    /** 검날이 진행 방향을 향하면서 검의 면은 수직으로 서도록 한다. */
    fun alignSwordBladeVertically(display: ItemDisplay, bladeDirection: Vector, scale: Float) {
        if (bladeDirection.lengthSquared() < 1.0E-8) return
        val normalized = bladeDirection.clone().normalize()
        val faceNormal = if (kotlin.math.abs(normalized.y) < 0.95) {
            Vector3f(-normalized.z.toFloat(), 0.0f, normalized.x.toFloat()).normalize()
        } else {
            Vector3f(0.0f, 0.0f, 1.0f)
        }
        alignSwordBlade(display, normalized, faceNormal, scale)
    }

    private fun alignSwordBlade(
        display: ItemDisplay,
        bladeDirection: Vector,
        faceNormal: Vector3f,
        scale: Float,
    ) {
        if (bladeDirection.lengthSquared() < 1.0E-8) return
        val normalized = bladeDirection.clone().normalize()
        val targetAxis = Vector3f(normalized.x.toFloat(), normalized.y.toFloat(), normalized.z.toFloat())
        val targetFaceNormal = perpendicularNormal(targetAxis, faceNormal)

        // 먼저 손잡이→검끝 축을 맞춘 뒤, 그 축을 중심으로 roll을 보정해 검의 면까지 맞춘다.
        // 이렇게 매 틱 동일한 기준 모델에서 회전을 계산하면 회전 애니메이션도 연속적이다.
        val bladeRotation = Quaternionf().rotationTo(swordModelBladeAxis, targetAxis)
        val rotatedFaceNormal = Vector3f(swordModelFaceNormal).rotate(bladeRotation).normalize()
        val twistAngle = atan2(
            targetAxis.dot(Vector3f(rotatedFaceNormal).cross(targetFaceNormal)),
            rotatedFaceNormal.dot(targetFaceNormal),
        )
        val rotation = Quaternionf()
            .rotationAxis(twistAngle, targetAxis)
            .mul(bladeRotation)
            .normalize()

        display.itemDisplayTransform = ItemDisplay.ItemDisplayTransform.NONE
        display.billboard = Display.Billboard.FIXED
        // 스폰 Location에서 상속된 yaw/pitch가 모델 변환과 중복 적용되지 않게 한다.
        display.setRotation(0.0f, 0.0f)
        display.transformation = Transformation(
            Vector3f(),
            rotation,
            Vector3f(scale, scale, scale),
            Quaternionf(),
        )
    }

    private fun perpendicularNormal(axis: Vector3f, requestedNormal: Vector3f): Vector3f {
        val normal = Vector3f(requestedNormal)
            .sub(Vector3f(axis).mul(requestedNormal.dot(axis)))
        if (normal.lengthSquared() >= 1.0E-8f) return normal.normalize()

        val fallback = if (kotlin.math.abs(axis.y) < 0.95f) {
            Vector3f(0.0f, 1.0f, 0.0f)
        } else {
            Vector3f(0.0f, 0.0f, 1.0f)
        }
        return fallback
            .sub(Vector3f(axis).mul(fallback.dot(axis)))
            .normalize()
    }
}
