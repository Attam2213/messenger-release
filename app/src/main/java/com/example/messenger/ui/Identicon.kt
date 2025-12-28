package com.example.messenger.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.security.MessageDigest
import kotlin.math.absoluteValue

@Composable
fun Identicon(
    hash: String,
    size: Dp = 40.dp,
    modifier: Modifier = Modifier
) {
    val (color, grid) = remember(hash) { generateIdenticonData(hash) }

    // Background for the identicon (light gray to make it pop)
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Color(0xFFF0F0F0))
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val cellSize = this.size.width / 5
            val padding = cellSize / 2 // Small padding inside the circle

            // Adjust drawing area to fit inside
            // We draw a 5x5 grid.
            
            grid.forEachIndexed { x, col ->
                col.forEachIndexed { y, isFilled ->
                    if (isFilled) {
                        drawRect(
                            color = color,
                            topLeft = androidx.compose.ui.geometry.Offset(x * cellSize, y * cellSize),
                            size = androidx.compose.ui.geometry.Size(cellSize, cellSize)
                        )
                    }
                }
            }
        }
    }
}

private fun generateIdenticonData(input: String): Pair<Color, Array<BooleanArray>> {
    val md = MessageDigest.getInstance("SHA-256")
    val bytes = md.digest(input.toByteArray())
    
    // Color from first 3 bytes
    // Ensure high saturation/brightness for better look
    val r = (bytes[0].toInt().absoluteValue % 200) // 0-200
    val g = (bytes[1].toInt().absoluteValue % 200)
    val b = (bytes[2].toInt().absoluteValue % 200)
    // Add offset to avoid too dark colors
    val color = Color(r + 20, g + 20, b + 20)
    
    // 5x5 grid, symmetric
    val grid = Array(5) { BooleanArray(5) }
    
    // We generate a 5x5 grid where the first 3 columns are random, and last 2 are mirrors
    // We use bytes starting from index 3
    var byteIdx = 3
    
    for (x in 0..2) {
        for (y in 0..4) {
            val bVal = if (byteIdx < bytes.size) bytes[byteIdx] else 0
            val isFilled = bVal.toInt() % 2 == 0
            grid[x][y] = isFilled
            grid[4 - x][y] = isFilled // Mirror
            byteIdx++
        }
    }
    
    return color to grid
}
