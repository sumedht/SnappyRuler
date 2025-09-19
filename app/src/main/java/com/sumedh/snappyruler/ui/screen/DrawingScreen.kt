package com.sumedh.snappyruler.ui

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.drawToBitmap
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sumedh.snappyruler.model.Shape
import com.sumedh.snappyruler.model.Tool
import com.sumedh.snappyruler.utils.Geometry
import com.sumedh.snappyruler.viewmodel.DrawingViewModel
import org.checkerframework.common.subtyping.qual.Bottom
import java.io.File
import java.io.FileOutputStream
import kotlin.math.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DrawingScreen(vm: DrawingViewModel = viewModel()) {
    val shapes = vm.shapes
    val tools = vm.tools
    val selectedTool by vm.selectedTool
    val haptic = LocalHapticFeedback.current
    val view = LocalView.current
    val context = LocalContext.current

    var scale by remember { mutableStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }

    var currentStroke = remember { mutableStateListOf<Offset>() }
    var rotatingToolId by remember { mutableStateOf<String?>(null) }
    var rotateStartAngle by remember { mutableStateOf(0f) }
    var initialToolRotation by remember { mutableStateOf(0f) }
    var arcStartAngle by remember { mutableStateOf<Float?>(null) }

    val infiniteTransition = rememberInfiniteTransition()
    val animatedRadius by infiniteTransition.animateFloat(
        initialValue = 12f, targetValue = 24f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF6F6F6))
                .pointerInput(Unit) {
                    detectTapGestures { tap ->
                        val world = (tap - pan) / scale
                        val shape = vm.findShapeAt(world)
                        if (shape != null) {
                            vm.selectedShape.value = shape
                            vm.selectedTool.value = null
                            return@detectTapGestures
                        }
                        val tool = vm.findToolAt(world)
                        vm.selectedTool.value = tool
                        vm.selectedShape.value = null
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val world = (offset - pan) / scale
                            val tool = vm.selectedTool.value

                            if (tool != null) {
                                // check rotate handle
                                val toolScreen = (tool.transform.position * scale) + pan
                                val theta = Math.toRadians(tool.transform.rotation.toDouble())
                                val dir = Offset(cos(theta).toFloat(), sin(theta).toFloat())
                                val perp = Offset(-dir.y, dir.x)
                                val heightScreen = 40f * scale
                                val handleScreen = toolScreen + perp * (heightScreen / 2f + 28f)
                                if ((offset - handleScreen).getDistance() <= 28f) {
                                    rotatingToolId = tool.id
                                    rotateStartAngle = Geometry.angleDegrees(tool.transform.position, world)
                                    initialToolRotation = tool.transform.rotation
                                    return@detectDragGestures
                                }
                            }

                            if (tool != null && vm.toolDrawMode.value) {
                                currentStroke.clear()
                                if (tool is Tool.Protractor) {
                                    arcStartAngle = Geometry.angleDegrees(tool.transform.position, world)
                                } else {
                                    currentStroke.add(world)
                                }
                            } else if (tool == null) {
                                currentStroke.clear()
                                currentStroke.add(world)
                            }
                        },
                        onDrag = { change, _ ->
                            val world = (change.position - pan) / scale
                            val tool = vm.selectedTool.value

                            if (rotatingToolId != null) {
                                val activeTool = vm.tools.find { it.id == rotatingToolId }
                                if (activeTool != null) {
                                    val center = activeTool.transform.position
                                    val currentAngle = Geometry.angleDegrees(center, world)
                                    val delta = currentAngle - rotateStartAngle
                                    var newRotation = initialToolRotation + delta
                                    newRotation = (newRotation % 360f + 360f) % 360f
                                    val (snapped, flag) = vm.snapAngleIfClose(newRotation)
                                    if (flag) {
                                        vm.updateToolTransform(
                                            activeTool.id,
                                            activeTool.transform.copy(rotation = snapped)
                                        )
                                        vm.setSnap(snapped)
                                        haptic.performHapticFeedback(
                                            androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress
                                        )
                                    } else {
                                        vm.updateToolTransform(
                                            activeTool.id,
                                            activeTool.transform.copy(rotation = newRotation)
                                        )
                                        vm.setSnap(null)
                                    }
                                }
                                return@detectDragGestures
                            }

                            when (tool) {
                                is Tool.Ruler -> if (vm.toolDrawMode.value) {
                                    val start = currentStroke.firstOrNull() ?: world
                                    if (shapes.isNotEmpty() && shapes.last() is Shape.LineSegment) shapes.removeLast()
                                    shapes.add(Shape.LineSegment(start, world))
                                } else {
                                    vm.updateToolTransform(tool.id, tool.transform.copy(position = world))
                                }
                                is Tool.SetSquare -> if (vm.toolDrawMode.value) {
                                    val start = currentStroke.firstOrNull() ?: world
                                    val snapped = vm.snapToSetSquareAngle(start, world, tool.variant45)
                                    if (shapes.isNotEmpty() && shapes.last() is Shape.LineSegment) shapes.removeLast()
                                    shapes.add(Shape.LineSegment(start, snapped))
                                } else {
                                    vm.updateToolTransform(tool.id, tool.transform.copy(position = world))
                                }
                                is Tool.Protractor -> if (vm.toolDrawMode.value) {
                                    val center = tool.transform.position
                                    val startA = arcStartAngle ?: Geometry.angleDegrees(center, world)
                                    val currentA = Geometry.angleDegrees(center, world)
                                    val sweepCCW = (currentA - startA + 360f) % 360f
                                    val radius = tool.transform.length / 2f
                                    if (shapes.isNotEmpty() && shapes.last() is Shape.Arc) shapes.removeLast()
                                    vm.addArcPreview(center, radius, startA, sweepCCW)
                                } else {
                                    vm.updateToolTransform(tool.id, tool.transform.copy(position = world))
                                }
                                else -> currentStroke.add(world)
                            }
                        },
                        onDragEnd = {
                            if (rotatingToolId != null) {
                                rotatingToolId = null
                                return@detectDragGestures
                            }
                            val tool = vm.selectedTool.value
                            when (tool) {
                                is Tool.Ruler -> if (vm.toolDrawMode.value) {
                                    val start = currentStroke.firstOrNull() ?: return@detectDragGestures
                                    val end = currentStroke.lastOrNull() ?: start
                                    vm.commitRulerLine(start, end)
                                }
                                is Tool.SetSquare -> if (vm.toolDrawMode.value) {
                                    val start = currentStroke.firstOrNull() ?: return@detectDragGestures
                                    val end = currentStroke.lastOrNull() ?: start
                                    val snapped = vm.snapToSetSquareAngle(start, end, tool.variant45)
                                    vm.commitRulerLine(start, snapped)
                                }
                                is Tool.Protractor -> if (vm.toolDrawMode.value) {
                                    val center = tool.transform.position
                                    val startA = arcStartAngle ?: return@detectDragGestures
                                    val endWorld = currentStroke.lastOrNull() ?: center
                                    val endA = Geometry.angleDegrees(center, endWorld)
                                    val sweepCCW = (endA - startA + 360f) % 360f
                                    val radius = tool.transform.length / 2f
                                    vm.commitArc(center, radius, startA, sweepCCW)
                                }
                                else -> if (currentStroke.size >= 2) vm.commitStroke(currentStroke)
                            }
                            currentStroke.clear()
                            arcStartAngle = null
                        },
                        onDragCancel = {
                            rotatingToolId = null
                            arcStartAngle = null
                            currentStroke.clear()
                        }
                    )
                }
        ) {
            val toScreen: (Offset) -> Offset = { world -> (world * scale) + pan }

            // committed shapes
            for (s in shapes) {
                when (s) {
                    is Shape.LineSegment -> {
                        drawLine(Color.Black, toScreen(s.start), toScreen(s.end), strokeWidth = 3f)
                    }
                    is Shape.Circle -> {
                        drawCircle(Color.Blue, radius = s.radius * scale, center = toScreen(s.center))
                    }
                    is Shape.Arc -> {
                        val centerScreen = toScreen(s.center)
                        val rScreen = s.radius * scale
                        val leftTop = centerScreen - Offset(rScreen, rScreen)
                        val size = Size(rScreen * 2f, rScreen * 2f)
                        val startForDraw = -s.startAngleDeg
                        val sweepForDraw = -s.sweepAngleDeg
                        drawArc(
                            color = Color.Magenta,
                            startForDraw,
                            sweepForDraw,
                            useCenter = false,
                            topLeft = leftTop,
                            size = size,
                            style = Stroke(width = 3f)
                        )
                    }
                }
            }

            // tools
            for (t in tools) {
                when (t) {
                    is Tool.Ruler -> drawRuler(t, selectedTool, scale, pan, toScreen, rotatingToolId)
                    is Tool.SetSquare -> drawSetSquare(t, selectedTool, scale, pan, toScreen, rotatingToolId)
                    is Tool.Protractor -> drawProtractor(t, selectedTool, scale, pan, toScreen, rotatingToolId)
                }
            }

            // snap HUD
            if (vm.snapActive.value) {
                val pos = toScreen((vm.selectedTool.value?.transform?.position) ?: Offset.Zero)
                drawCircle(Color.Red.copy(alpha = 0.6f), animatedRadius, center = pos)
            }
        }

        // Toolbar
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(12.dp),
            verticalArrangement = Arrangement.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // --- Core actions ---
            Button(onClick = { vm.undo() }) { Text("Undo") }
            Button(onClick = { vm.redo() }) { Text("Redo") }
            Button(onClick = {
                val bmp: Bitmap = view.drawToBitmap()
                val file = File(context.cacheDir, "drawing_export.png")
                FileOutputStream(file).use { out -> bmp.compress(Bitmap.CompressFormat.PNG, 100, out) }
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, file)
                }
                context.startActivity(Intent.createChooser(intent, "Share Drawing"))
            }) { Text("Export") }
            Button(onClick = { vm.toolDrawMode.value = !vm.toolDrawMode.value }) {
                Text(if (vm.toolDrawMode.value) "Draw Mode" else "Move Mode")
            }

            // --- Tool selection buttons ---
            ToolButton(
                text = "Ruler",
                isSelected = vm.selectedTool.value is Tool.Ruler
            ) { vm.selectedTool.value = vm.tools.filterIsInstance<Tool.Ruler>().firstOrNull() }

            ToolButton(
                text = "Set Square",
                isSelected = vm.selectedTool.value is Tool.SetSquare
            ) { vm.selectedTool.value = vm.tools.filterIsInstance<Tool.SetSquare>().firstOrNull() }

            ToolButton(
                text = "Protractor",
                isSelected = vm.selectedTool.value is Tool.Protractor
            ) { vm.selectedTool.value = vm.tools.filterIsInstance<Tool.Protractor>().firstOrNull() }

            ToolButton(
                text = "Deselect",
                isSelected = vm.selectedTool.value == null
            ) {
                vm.selectedTool.value = null
                vm.selectedShape.value = null
                vm.toolDrawMode.value = false
            }
        }
    }
}

@Composable
fun ToolButton(text: String, isSelected: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            if (isSelected) Color.Red else MaterialTheme.colors.primary
        )
    ) {
        Text(text)
    }
}

/* ---------- Tool draw helpers ---------- */

private fun DrawScope.drawRuler(
    t: Tool.Ruler, selectedTool: Tool?, scale: Float, pan: Offset,
    toScreen: (Offset) -> Offset, rotatingToolId: String?
) {
    val pos = toScreen(t.transform.position)
    val length = t.transform.length * scale
    val height = 40f * scale
    val theta = Math.toRadians(t.transform.rotation.toDouble())
    val dir = Offset(cos(theta).toFloat(), sin(theta).toFloat())
    val perp = Offset(-dir.y, dir.x)
    val p1 = pos - dir * length / 2f - perp * height / 2f
    val p2 = pos + dir * length / 2f - perp * height / 2f
    val p3 = pos + dir * length / 2f + perp * height / 2f
    val p4 = pos - dir * length / 2f + perp * height / 2f
    val path = Path().apply { moveTo(p1.x, p1.y); lineTo(p2.x, p2.y); lineTo(p3.x, p3.y); lineTo(p4.x, p4.y); close() }
    drawPath(path, color = if (selectedTool?.id == t.id) Color(0xFFB3E5FC) else Color(0xFFE0E0E0), style = Stroke(width = 2f))

    // ticks + numbers
    val tickStep = 20f * scale
    var d = -length / 2f
    var count = 0
    while (d <= length / 2f) {
        val tickStart = pos + dir * d - perp * (height / 2f)
        val tickEnd = tickStart + perp * (height * 0.4f)
        drawLine(Color.DarkGray, tickStart, tickEnd, strokeWidth = 2f)

        if (count % 5 == 0) {
            drawContext.canvas.nativeCanvas.drawText(
                "$count",
                tickStart.x,
                tickEnd.y + 20,
                android.graphics.Paint().apply {
                    color = android.graphics.Color.BLACK
                    textSize = 24f
                    textAlign = android.graphics.Paint.Align.CENTER
                }
            )
        }
        d += tickStep
        count++
    }

    val handlePos = pos + perp * (height / 2f + 28f)
    drawCircle(if (selectedTool?.id == t.id) Color.Red else Color.Gray, radius = 12f, center = handlePos)
}

private fun DrawScope.drawSetSquare(
    t: Tool.SetSquare, selectedTool: Tool?, scale: Float, pan: Offset,
    toScreen: (Offset) -> Offset, rotatingToolId: String?
) {
    val pos = toScreen(t.transform.position)
    val size = t.transform.length * scale
    val theta = Math.toRadians(t.transform.rotation.toDouble())
    val dir = Offset(cos(theta).toFloat(), sin(theta).toFloat())
    val perp = Offset(-dir.y, dir.x)

    val p1 = pos
    val p2 = pos + dir * size
    val p3 = if (t.variant45) pos + perp * size else pos + perp * (size * 0.866f)
    val path = Path().apply { moveTo(p1.x, p1.y); lineTo(p2.x, p2.y); lineTo(p3.x, p3.y); close() }
    drawPath(path, color = if (selectedTool?.id == t.id) Color(0xFFC8E6C9) else Color(0xFFE0E0E0), style = Stroke(width = 2f))

    // degree mapping along hypotenuse
    val steps = 9
    for (i in 1 until steps) {
        val factor = i.toFloat() / steps
        val x = p1.x + (p2.x - p1.x) * factor
        val y = p1.y + (p2.y - p1.y) * factor
        drawContext.canvas.nativeCanvas.drawText(
            "${i * 10}°",
            x,
            y,
            android.graphics.Paint().apply {
                color = android.graphics.Color.BLACK
                textSize = 20f
                textAlign = android.graphics.Paint.Align.CENTER
            }
        )
    }

    val handlePos = pos + perp * (size / 2f + 24f)
    drawCircle(if (selectedTool?.id == t.id) Color.Red else Color.Gray, radius = 10f, center = handlePos)
}


private fun DrawScope.drawProtractor(
    t: Tool.Protractor,
    selectedTool: Tool?,
    scale: Float,
    pan: Offset,
    toScreen: (Offset) -> Offset,
    rotatingToolId: String?
) {
    val pos = toScreen(t.transform.position)
    val radius = t.transform.length * scale / 2f
    val theta0 = Math.toRadians(t.transform.rotation.toDouble())

    val rectTopLeft = pos - Offset(radius, radius)
    val rectSize = Size(radius * 2, radius * 2)
    val startAngle = -Math.toDegrees(theta0).toFloat()

    drawArc(
        color = if (selectedTool?.id == t.id) Color(0xFFFFF9C4) else Color(0xFFE0E0E0),
        startAngle,
        180f,
        useCenter = false,
        topLeft = rectTopLeft,
        size = rectSize,
        style = Stroke(width = 2f)
    )

    // degree numbers every 30 degrees
    for (deg in 0..180 step 10) {
        val angle = theta0 + Math.toRadians(deg.toDouble())
        val start = pos + Offset(cos(angle).toFloat(), sin(angle).toFloat()) * (radius * 0.9f)
        val end = pos + Offset(cos(angle).toFloat(), sin(angle).toFloat()) * radius
        drawLine(Color.Black, start, end, strokeWidth = if (deg % 30 == 0) 3f else 1.5f)

        if (deg % 30 == 0) {
            val textPos = pos + Offset(cos(angle).toFloat(), sin(angle).toFloat()) * (radius * 0.75f)
            drawContext.canvas.nativeCanvas.drawText(
                "$deg°",
                textPos.x,
                textPos.y,
                android.graphics.Paint().apply {
                    color = android.graphics.Color.BLACK
                    textSize = 20f
                    textAlign = android.graphics.Paint.Align.CENTER
                }
            )
        }
    }

    val handlePos = pos - Offset(0f, radius + 26f)
    drawCircle(
        color = if (selectedTool?.id == t.id) Color.Red else Color.Gray,
        radius = 10f,
        center = handlePos
    )
}
