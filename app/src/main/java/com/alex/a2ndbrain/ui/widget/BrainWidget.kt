package com.alex.a2ndbrain.ui.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.alex.a2ndbrain.MainActivity

class BrainWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent { WidgetContent() }
    }

    @Composable
    private fun WidgetContent() {
        val prefs = currentState<androidx.datastore.preferences.core.Preferences>()
        val score = prefs[PREF_SCORE] ?: 0
        val steps = prefs[PREF_STEPS] ?: 0
        val habitsText = prefs[PREF_HABITS] ?: "—"

        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ColorProvider(Color(0xFF1C1C1E)))
                .clickable(actionStartActivity<MainActivity>())
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Column(modifier = GlanceModifier.fillMaxSize()) {
                Text(
                    text = "2nd Brain",
                    style = TextStyle(
                        color = ColorProvider(Color(0xFF8E8E93)),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
                Spacer(modifier = GlanceModifier.height(8.dp))
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatCell(modifier = GlanceModifier.defaultWeight(), label = "Sense of Day", value = "$score", unit = "/100")
                    StatCell(modifier = GlanceModifier.defaultWeight(), label = "Steps", value = formatSteps(steps), unit = "")
                    StatCell(modifier = GlanceModifier.defaultWeight(), label = "Tasks", value = habitsText, unit = "")
                }
            }
        }
    }

    @Composable
    private fun StatCell(modifier: GlanceModifier = GlanceModifier, label: String, value: String, unit: String) {
        Column(
            modifier = modifier,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = value,
                    style = TextStyle(
                        color = ColorProvider(Color.White),
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                if (unit.isNotEmpty()) {
                    Text(
                        text = unit,
                        style = TextStyle(
                            color = ColorProvider(Color(0xFF8E8E93)),
                            fontSize = 13.sp
                        )
                    )
                }
            }
            Text(
                text = label,
                style = TextStyle(
                    color = ColorProvider(Color(0xFF8E8E93)),
                    fontSize = 11.sp
                )
            )
        }
    }

    private fun formatSteps(steps: Int): String = when {
        steps >= 1000 -> "${steps / 1000}.${(steps % 1000) / 100}k"
        else -> "$steps"
    }

    companion object {
        val PREF_SCORE = intPreferencesKey("widget_score")
        val PREF_STEPS = intPreferencesKey("widget_steps")
        val PREF_HABITS = stringPreferencesKey("widget_habits")
    }
}
