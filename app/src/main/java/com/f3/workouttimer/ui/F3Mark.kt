package com.f3.workouttimer.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.f3.workouttimer.ui.theme.F3White

/** The F3 mark: the letters in a heavy ring. */
@Composable
fun F3Mark(
    modifier: Modifier = Modifier,
    size: Dp = 72.dp,
    fontSize: TextUnit = 30.sp,
    color: Color = F3White,
) {
    Box(
        modifier = modifier.size(size).border(size / 18, color, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = "F3", color = color, fontSize = fontSize, fontWeight = FontWeight.Black)
    }
}
