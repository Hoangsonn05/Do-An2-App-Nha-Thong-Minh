package com.example.smarthomeai.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.smarthomeai.data.SensorSample
import com.example.smarthomeai.ui.theme.*
import kotlin.math.max

@Composable
internal fun SensorChart(samples: List<SensorSample>, temperature: Boolean) {
    Canvas(Modifier.fillMaxWidth().height(170.dp).padding(vertical = 16.dp).semantics {
        contentDescription = "Biểu đồ ${if(temperature) "nhiệt độ" else "độ ẩm"}, ${samples.size} số đo. Khoảng thiếu dữ liệu không nối đường."
    }) {
        val values = samples.map { if(temperature) it.temperature.toFloat() else it.humidity.toFloat() }
        val low = values.min() - 2f
        val high = values.max() + 2f
        val span = max(1L, samples.last().timestamp - samples.first().timestamp)
        val inset = 4.dp.toPx()
        fun point(i: Int) = Offset(inset + (samples[i].timestamp - samples.first().timestamp).toFloat() / span * (size.width - 2 * inset),
            size.height - (values[i] - low) / (high - low) * size.height)
        repeat(4) { i -> val y = i * size.height / 3; drawLine(Outline, Offset(0f,y), Offset(size.width,y), 1.dp.toPx()) }
        for(i in 1 until samples.size) {
            if(samples[i].timestamp - samples[i-1].timestamp <= 600_000) drawLine(Blue600, point(i-1), point(i), 2.5.dp.toPx())
            else {
                drawCircle(Blue600, 3.dp.toPx(), point(i-1))
                drawCircle(Blue600, 3.dp.toPx(), point(i))
            }
        }
        drawCircle(Blue600, 3.dp.toPx(), point(0))
        drawCircle(Blue600, 4.dp.toPx(), point(samples.lastIndex))
    }
}
