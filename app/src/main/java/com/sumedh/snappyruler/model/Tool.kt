package com.sumedh.snappyruler.model

import androidx.compose.ui.geometry.Offset
import java.util.UUID

sealed class Tool(id: String, transform: ToolTransform) {
    abstract val id: String
    abstract val transform: ToolTransform

    // --- Shared transform for all tools ---
    data class ToolTransform(
        val position: Offset = Offset(500f, 500f), // default center-ish
        val rotation: Float = 0f,
        val length: Float = 400f // used by ruler / set square
    )

    data class Ruler(
        override val id: String = UUID.randomUUID().toString(),
        override val transform: ToolTransform = ToolTransform()
    ) : Tool(id, transform)

    // --- Set Square (45° or 30°/60° variant) ---
    data class SetSquare(
        override val id: String = UUID.randomUUID().toString(),
        val variant45: Boolean = true, // true = 45° set square, false = 30°/60°
        override val transform: ToolTransform = ToolTransform()
    ) : Tool(id, transform)

    // --- Protractor ---
    data class Protractor(
        override val id: String = UUID.randomUUID().toString(),
        override val transform: ToolTransform = ToolTransform()
    ) : Tool(id, transform)
}