package com.alex.a2ndbrain.ui.home

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Wraps [content] and shows a one-time coaching callout below it.
 * The callout auto-dismisses after [autoDismissMs] and is never shown again
 * once the user taps "Got it" or the timer expires.
 */
@Composable
fun CoachMark(
    prefKey: String,
    text: String,
    modifier: Modifier = Modifier,
    autoDismissMs: Long = 5_000L,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("coach_marks", Context.MODE_PRIVATE) }
    var visible by remember { mutableStateOf(!prefs.getBoolean(prefKey, false)) }

    fun dismiss() {
        visible = false
        prefs.edit().putBoolean(prefKey, true).apply()
    }

    LaunchedEffect(visible) {
        if (visible) {
            delay(autoDismissMs)
            dismiss()
        }
    }

    Column(modifier = modifier) {
        content()
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.93f),
                modifier = Modifier
                    .padding(top = 6.dp)
                    .fillMaxWidth()
                    .clickable { dismiss() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "💡 $text",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                        modifier = Modifier.weight(1f),
                        lineHeight = androidx.compose.ui.unit.TextUnit(
                            16f, androidx.compose.ui.unit.TextUnitType.Sp
                        )
                    )
                    Text(
                        text = "Got it",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.inversePrimary
                    )
                }
            }
        }
    }
}
