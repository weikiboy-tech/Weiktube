package com.weiktube.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

data class PlayerGestureFeedback(val kind: String, val text: String, val percent: Int? = null)

@Composable
internal fun PlayerGestureHud(value: PlayerGestureFeedback?, modifier: Modifier = Modifier) {
    AnimatedVisibility(value != null, modifier) {
        value?.let { feedback ->
            Surface(color = Color.Black.copy(alpha = .78f), contentColor = Color.White, shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.width(132.dp).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(when(feedback.kind) { "brightness" -> Icons.Rounded.Brightness6; "volume" -> Icons.Rounded.VolumeUp
                        "speed" -> Icons.Rounded.Speed; else -> Icons.Rounded.FastForward }, null)
                    Text(feedback.text, style = MaterialTheme.typography.titleMedium)
                    feedback.percent?.let { percent ->
                        val progress by animateFloatAsState(percent / 100f, label = "Player level")
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}
