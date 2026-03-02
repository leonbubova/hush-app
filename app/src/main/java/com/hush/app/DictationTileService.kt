package com.hush.app

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import android.graphics.drawable.Icon

class DictationTileService : TileService() {

    companion object {
        private const val TAG = "DictationTileService"
    }

    override fun onStartListening() {
        updateTile()
    }

    override fun onClick() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.i(TAG, "RECORD_AUDIO not granted, launching app for permission")
            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startActivityAndCollapse(PendingIntent.getActivity(
                    this, 0, intent, PendingIntent.FLAG_IMMUTABLE
                ))
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
            return
        }

        Log.i(TAG, "Tile clicked, toggling dictation")
        val toggleIntent = Intent(this, DictationService::class.java).apply {
            action = DictationService.ACTION_TOGGLE
        }
        startForegroundService(toggleIntent)
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        val state = DictationService.currentState

        when (state) {
            DictationService.DictationState.IDLE -> {
                tile.state = Tile.STATE_INACTIVE
                tile.icon = Icon.createWithResource(this, R.drawable.ic_mic)
                tile.label = getString(R.string.app_name)
            }
            DictationService.DictationState.RECORDING -> {
                tile.state = Tile.STATE_ACTIVE
                tile.icon = Icon.createWithResource(this, R.drawable.ic_mic_active)
                tile.label = "Recording..."
            }
            DictationService.DictationState.STREAMING -> {
                tile.state = Tile.STATE_ACTIVE
                tile.icon = Icon.createWithResource(this, R.drawable.ic_mic_active)
                tile.label = "Streaming..."
            }
            DictationService.DictationState.PROCESSING -> {
                tile.state = Tile.STATE_UNAVAILABLE
                tile.icon = Icon.createWithResource(this, R.drawable.ic_mic)
                tile.label = "Processing..."
            }
            DictationService.DictationState.DONE, DictationService.DictationState.ERROR -> {
                tile.state = Tile.STATE_INACTIVE
                tile.icon = Icon.createWithResource(this, R.drawable.ic_mic)
                tile.label = getString(R.string.app_name)
            }
        }
        tile.updateTile()
    }
}
