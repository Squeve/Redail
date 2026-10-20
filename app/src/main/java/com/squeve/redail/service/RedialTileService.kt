package com.squeve.redail.service

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.squeve.redail.MainActivity
import com.squeve.redail.model.EngineState

/** Quick Settings tile: tap to start the last run, tap again to stop. */
class RedialTileService : TileService() {

    private fun running(): Boolean {
        val s = RedialService.status.value
        return s !is EngineState.Idle && s !is EngineState.Finished
    }

    override fun onStartListening() {
        super.onStartListening()
        refresh()
    }

    override fun onClick() {
        super.onClick()
        if (running()) {
            RunLauncher.stop(this)
        } else {
            // Open the app, which starts the run while it is on screen (the reliable way to start a call run).
            val intent = Intent(this, MainActivity::class.java)
                .setAction(MainActivity.ACTION_START_LAST)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (Build.VERSION.SDK_INT >= 34) {
                startActivityAndCollapse(
                    PendingIntent.getActivity(
                        this, 11, intent,
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    ),
                )
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
        }
        refresh()
    }

    private fun refresh() {
        val tile = qsTile ?: return
        tile.label = "Redial"
        tile.state = if (running()) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()
    }
}
