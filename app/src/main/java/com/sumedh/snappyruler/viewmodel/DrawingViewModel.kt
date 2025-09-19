package com.sumedh.snappyruler.viewmodel

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel
import com.sumedh.snappyruler.model.Shape
import com.sumedh.snappyruler.model.Tool
import com.sumedh.snappyruler.utils.Geometry
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

class DrawingViewModel : ViewModel() {

    // --- Canvas state ---
    val shapes = mutableStateListOf<Shape>()
    val tools = mutableStateListOf<Tool>(
        Tool.Ruler(),
        Tool.SetSquare(variant45 = true),
        Tool.Protractor()
    )
    val selectedTool = mutableStateOf<Tool?>(null)

    val canvasScale = mutableStateOf(1f)
    val canvasPan = mutableStateOf(Offset.Zero)

    // --- Undo/Redo state ---
    private val undoStack = ArrayDeque<List<Shape>>()
    private val redoStack = ArrayDeque<List<Shape>>()

    // --- Snap state ---
    val lastSnapAngle = mutableStateOf<Float?>(null)
    val snapActive = mutableStateOf(false)

    val toolDrawMode = mutableStateOf(false)

    fun setSnap(angle: Float?) {
        lastSnapAngle.value = angle
        snapActive.value = (angle != null)
    }

    // --- Stroke commit helpers ---
    fun commitStroke(points: List<Offset>) {
        if (points.size >= 2) {
            pushHistory()
            val newLines = points.zipWithNext { a, b -> Shape.LineSegment(a, b) }
            shapes.addAll(newLines)
        }
    }

    fun commitRulerLine(start: Offset, end: Offset) {
        pushHistory()
        shapes.add(Shape.LineSegment(start, end))
    }

    fun commitArc(center: Offset, point: Offset) {
        pushHistory()
        val radius = (center - point).getDistance()
        shapes.add(Shape.Circle(center, radius)) // simple arc placeholder
    }

    // --- Undo/Redo ---
    fun undo() {
        if (undoStack.isNotEmpty()) {
            redoStack.addLast(shapes.toList())
            val last = undoStack.removeLast()
            shapes.clear()
            shapes.addAll(last)
        }
    }

    fun redo() {
        if (redoStack.isNotEmpty()) {
            undoStack.addLast(shapes.toList())
            val last = redoStack.removeLast()
            shapes.clear()
            shapes.addAll(last)
        }
    }

    private fun pushHistory() {
        undoStack.addLast(shapes.toList())
        if (undoStack.size > 50) undoStack.removeFirst()
        redoStack.clear()
    }

    // --- Tool updates ---
    fun updateToolTransform(toolId: String, newTransform: Tool.ToolTransform) {
        val index = tools.indexOfFirst { it.id == toolId }
        if (index != -1) {
            tools[index] = when (val t = tools[index]) {
                is Tool.Ruler -> t.copy(transform = newTransform)
                is Tool.SetSquare -> t.copy(transform = newTransform)
                is Tool.Protractor -> t.copy(transform = newTransform)
            }
        }
    }

    // --- Tap-to-select tool ---
    fun findToolAt(pos: Offset): Tool? {
        return tools.find { (it.transform.position - pos).getDistance() < 100f }
    }

    // --- Snapping logic ---
    fun snapAngleIfClose(angle: Float, threshold: Float = 5f): Pair<Float, Boolean> {
        val commonAngles = listOf(0f, 30f, 45f, 60f, 90f, 120f, 135f, 150f, 180f)
        for (snap in commonAngles) {
            if (abs(angle - snap) <= threshold) {
                return snap to true
            }
        }
        return angle to false
    }

    fun addArcPreview(center: Offset, radius: Float, startAngleDeg: Float, sweepAngleDeg: Float) {
        // remove last preview if it is an Arc
        if (shapes.isNotEmpty() && shapes.last() is Shape.Arc) {
            shapes.removeLast()
        }
        shapes.add(Shape.Arc(center, radius, startAngleDeg, sweepAngleDeg))
    }

    /** Commit an arc as a permanent shape (pushes history). */
    fun commitArc(center: Offset, radius: Float, startAngleDeg: Float, sweepAngleDeg: Float) {
        pushHistory()
        shapes.add(Shape.Arc(center, radius, startAngleDeg, sweepAngleDeg))
    }

    // --- SetSquare snapping ---
    fun snapToSetSquareAngle(start: Offset, end: Offset, variant45: Boolean): Offset {
        val dx = end.x - start.x
        val dy = end.y - start.y
        val angle = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()

        // Allowed angles for set square
        val allowedAngles = if (variant45) {
            listOf(0f, 45f, 90f, 135f, 180f, -45f, -90f)
        } else {
            listOf(0f, 30f, 60f, 90f, 120f, 150f, -30f, -60f)
        }

        // Find closest
        val snapped = allowedAngles.minBy { abs(it - angle) }

        val length = hypot(dx.toDouble(), dy.toDouble()).toFloat()
        val rad = Math.toRadians(snapped.toDouble())
        val snappedDx = cos(rad).toFloat() * length
        val snappedDy = sin(rad).toFloat() * length

        return Offset(start.x + snappedDx, start.y + snappedDy)
    }

    fun addArc(center: Offset, point: Offset) {
        // Temporary arc preview: remove last arc if any, then add new one
        if (shapes.isNotEmpty() && shapes.last() is Shape.Circle) {
            shapes.removeLast()
        }
        val radius = (center - point).getDistance()
        shapes.add(Shape.Circle(center, radius))
    }

    val selectedShape = mutableStateOf<Shape?>(null)

    fun findShapeAt(pos: Offset, threshold: Float = 20f): Shape? {
        // crude hit test for shapes
        for (shape in shapes.asReversed()) { // check last drawn first
            when (shape) {
                is Shape.LineSegment -> {
                    val dist = pointToLineDistance(pos, shape.start, shape.end)
                    if (dist < threshold) return shape
                }
                is Shape.Circle -> {
                    val dist = (pos - shape.center).getDistance()
                    if (abs(dist - shape.radius) < threshold) return shape
                }

                is Shape.Arc -> {
                    val v = pos - shape.center
                    val dist = v.getDistance()

                    // 1. Check radius
                    if (abs(dist - shape.radius) < threshold) {
                        // 2. Convert point angle to degrees CCW
                        var angle = Math.toDegrees(atan2(v.y.toDouble(), v.x.toDouble())).toFloat()
                        if (angle < 0) angle += 360f

                        val start = shape.startAngleDeg % 360f
                        val sweep = shape.sweepAngleDeg % 360f

                        // normalize sweep to [0,360]
                        val normalizedSweep = if (sweep < 0) sweep + 360f else sweep

                        // handle wraparound
                        val end = (start + normalizedSweep) % 360f
                        val inRange = if (start <= end) {
                            angle in start..end
                        } else {
                            angle >= start || angle <= end
                        }

                        if (inRange) return shape
                    }
                }
            }
        }
        return null
    }

    private fun pointToLineDistance(p: Offset, a: Offset, b: Offset): Float {
        val ap = p - a
        val ab = b - a
        val ab2 = ab.x * ab.x + ab.y * ab.y
        val dot = (ap.x * ab.x + ap.y * ab.y) / ab2
        val proj = Offset(a.x + dot * ab.x, a.y + dot * ab.y)
        return (p - proj).getDistance()
    }
}