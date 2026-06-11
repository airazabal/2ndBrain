package com.alex.a2ndbrain.ui.widget

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.alex.a2ndbrain.MainActivity

class SenseOfDayTileService : TileService() {

    companion object {
        const val PREFS_NAME = "2ndbrain_tile"
        const val KEY_SCORE  = "score"
    }

    override fun onStartListening() {
        super.onStartListening()
        refresh()
    }

    override fun onClick() {
        super.onClick()
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("tile_nav", "MOOD")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pi = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            startActivityAndCollapse(pi)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private fun refresh() {
        val tile = qsTile ?: return
        val score = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getInt(KEY_SCORE, -1)

        tile.state = if (score >= 50) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = if (score >= 0) "$score / 100" else "No data yet"
        }
        tile.updateTile()
    }
}
