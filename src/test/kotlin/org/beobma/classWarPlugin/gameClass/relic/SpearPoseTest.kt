package org.beobma.classWarPlugin.gameClass.relic

import org.joml.Vector3f
import kotlin.test.Test
import kotlin.test.assertTrue

class SpearPoseTest {
    @Test fun `rendered tip stays on trajectory for horizontal vertical and diagonal throws`() {
        for (heading in listOf(Vector3f(1f,0f,0f),Vector3f(-1f,0f,0f),
            Vector3f(0f,1f,0f),Vector3f(0f,-1f,0f),Vector3f(0f,0f,1f),
            Vector3f(0f,0f,-1f),Vector3f(1f,2f,-3f))) {
            val pose=SpearPose.create(heading)
            fun render(raw: Vector3f): Vector3f = raw
                .mul(1f,-1f,-1f).sub(0.5f,0.5f,0.5f)
                .rotateY(Math.PI.toFloat()).mul(pose.scale)
                .rotate(pose.leftRotation).add(pose.translation)
            val tip=render(Vector3f(0f,-4f/16f,0f))
            val tail=render(Vector3f(0f,27f/16f,0f))
            assertTrue(tip.length()<1e-5f,"Tip offset: $tip, heading: $heading")
            assertTrue(Vector3f(tip).sub(tail).normalize().dot(Vector3f(heading).normalize())>0.99999f)
        }
    }
}
