package com.sumedh.snappyruler.utils

import androidx.compose.ui.geometry.Offset
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.pow
import kotlin.math.sqrt

object Geometry {
    fun distance(a: Offset, b: Offset): Float =
        sqrt((a.x - b.x).pow(2) + (a.y - b.y).pow(2))

    fun midpoint(a: Offset, b: Offset): Offset =
        Offset((a.x + b.x) / 2f, (a.y + b.y) / 2f)

    fun angleDegrees(a: Offset, b: Offset): Float {
        val dx = b.x - a.x
        val dy = b.y - a.y
        return Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat().let {
            // normalize to [0, 360)
            var v = it
            if (v < 0) v += 360f
            v
        }
    }

    /** Snap an angle (degrees) to nearest angle from allowedAngles when within tolerance. */
    fun snapAngleDeg(angle: Float, allowedAngles: List<Float>, toleranceDeg: Float): Pair<Float, Boolean> {
        var best = allowedAngles.first()
        var bestDiff = abs(normalizeAngleDiff(angle, best))
        for (a in allowedAngles) {
            val d = abs(normalizeAngleDiff(angle, a))
            if (d < bestDiff) {
                best = a
                bestDiff = d
            }
        }
        return if (bestDiff <= toleranceDeg) best to true else angle to false
    }

    private fun normalizeAngleDiff(a: Float, b: Float): Float {
        var diff = (a - b + 180f) % 360f - 180f
        if (diff < -180f) diff += 360f
        return diff
    }

    /** Intersection of infinite lines (p1->p2) and (p3->p4). Returns null if parallel. */
    fun lineIntersection(p1: Offset, p2: Offset, p3: Offset, p4: Offset): Offset? {
        val x1 = p1.x; val y1 = p1.y
        val x2 = p2.x; val y2 = p2.y
        val x3 = p3.x; val y3 = p3.y
        val x4 = p4.x; val y4 = p4.y
        val denom = (x1 - x2) * (y3 - y4) - (y1 - y2) * (x3 - x4)
        if (abs(denom) < 1e-6f) return null
        val px = ((x1*y2 - y1*x2)*(x3-x4) - (x1-x2)*(x3*y4 - y3*x4)) / denom
        val py = ((x1*y2 - y1*x2)*(y3-y4) - (y1-y2)*(x3*y4 - y3*x4)) / denom
        return Offset(px.toFloat(), py.toFloat())
    }
}