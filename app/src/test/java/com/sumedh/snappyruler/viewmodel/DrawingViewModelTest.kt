package com.sumedh.snappyruler.viewmodel

import androidx.compose.ui.geometry.Offset
import com.sumedh.snappyruler.model.Shape
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class DrawingViewModelTest {

    private lateinit var vm: DrawingViewModel

    @Before
    fun setup() {
        vm = DrawingViewModel()
    }

    @Test
    fun commitRulerLine_addsLine() {
        val start = Offset(0f, 0f)
        val end = Offset(100f, 0f)

        vm.commitRulerLine(start, end)

        val shapes = vm.shapes
        assertEquals(1, shapes.size)
        assertTrue(shapes.first() is Shape.LineSegment)
    }

    @Test
    fun undoRedo_restoresShapes() {
        val start = Offset(0f, 0f)
        val end = Offset(50f, 50f)

        vm.commitRulerLine(start, end)
        assertEquals(1, vm.shapes.size)

        vm.undo()
        assertTrue(vm.shapes.isEmpty())

        vm.redo()
        assertEquals(1, vm.shapes.size)
    }

    @Test
    fun commitArc_addsArc() {
        val center = Offset(0f, 0f)
        val radius = 50f
        val startAngle = 0f
        val sweep = 90f

        vm.commitArc(center, radius, startAngle, sweep)

        val shapes = vm.shapes
        assertEquals(1, shapes.size)
        assertTrue(shapes.first() is Shape.Arc)
    }

    @Test
    fun snapToSetSquareAngle_snapsCorrectly() {
        val start = Offset(0f, 0f)
        val end = Offset(100f, 10f) // ~6° line
        val snapped = vm.snapToSetSquareAngle(start, end, variant45 = true)

        // should snap closer to 0° (horizontal line)
        assertEquals(0f, snapped.y, 1f) // y close to 0
    }

    @Test
    fun snapAngleIfClose_snapsTo90() {
        val (snappedAngle, flag) = vm.snapAngleIfClose(92f)

        assertTrue(flag)
        assertEquals(90f, snappedAngle, 0.1f)
    }
}