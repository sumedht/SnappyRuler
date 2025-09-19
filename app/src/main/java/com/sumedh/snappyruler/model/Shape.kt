package com.sumedh.snappyruler.model

import androidx.compose.ui.geometry.Offset

sealed class Shape {
    data class LineSegment(val start: Offset, val end: Offset) : Shape()
    data class Circle(val center: Offset, val radius: Float) : Shape()
    // Arc stored as: center, radius, start angle (degrees, 0 = +X, CCW), sweep angle (degrees CCW, 0..360)
    data class Arc(
        val center: Offset,
        val radius: Float,
        val startAngleDeg: Float,
        val sweepAngleDeg: Float
    ) : Shape()
}