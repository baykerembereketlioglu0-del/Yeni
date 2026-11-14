package app.karala.overlay

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.app.PendingIntent
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import app.karala.MainActivity

/** Görünmez aktivite: sistemin "ekranı kaydet/yayınla" iznini ister ve sonucu servise iletir. */
class CaptureActivity : ComponentActivity() {
    private val launcher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val i = Intent(this, OverlayService::class.java)
        if (r.resultCode == Activity.RESULT_OK && r.data != null) {
            i.setAction(OverlayService.ACTION_PROJECTION)
                .putExtra(OverlayService.EXTRA_CODE, r.resultCode).putExtra(OverlayService.EXTRA_DATA, r.data)
        } else i.setAction(OverlayService.ACTION_PROJECTION_DENIED)
        ContextCompat.startForegroundService(this, i)
        finish()
        @Suppress("DEPRECATION") overridePendingTransition(0, 0)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            launcher.launch(getSystemService(MediaProjectionManager::class.java).createScreenCaptureIntent())
        }
    }
}

/** Hızlı ayarlar kutucuğu: ekran kalemini bildirim panelinden aç/kapat. */
class OverlayTileService : TileService() {
    override fun onStartListening() {
        qsTile?.apply {
            state = if (OverlayService.running.value) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            updateTile()
        }
    }

    override fun onClick() {
        if (OverlayService.running.value) { OverlayService.stop(this); qsTile?.apply { state = Tile.STATE_INACTIVE; updateTile() }; return }
        if (Settings.canDrawOverlays(this)) {
            runCatching { OverlayService.start(this) }
                .onSuccess { qsTile?.apply { state = Tile.STATE_ACTIVE; updateTile() } }
                .onFailure { openApp() }
        } else openApp()
    }

    @android.annotation.SuppressLint("StartActivityAndCollapseDeprecated") // 34 altı için tek yol
    private fun openApp() {
        val i = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_IMMUTABLE))
        } else @Suppress("DEPRECATION") startActivityAndCollapse(i)
    }
}
