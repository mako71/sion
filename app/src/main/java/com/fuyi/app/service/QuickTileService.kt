package com.fuyi.app.service

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.QSTileService
import android.service.quicksettings.Tile

class QuickTileService : QSTileService() {

    private var running = false

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        if (running) {
            TranslationService.stop(this)
            FloatingWindowService.hide(this)
            running = false
        } else {
            ConsentActivity.request(this)
            running = true
        }
        updateTile()
    }

    private fun updateTile() {
        qsTile?.apply {
            state = if (running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            label = if (running) "浮译运行中" else "浮译"
            updateTile()
        }
    }
}
