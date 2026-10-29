package com.naymy.island

import android.app.Notification
import android.content.ComponentName
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class NotifService : NotificationListenerService() {
    private var mgr: MediaSessionManager? = null
    private var ctrl: MediaController? = null
    private val cn by lazy { ComponentName(this, NotifService::class.java) }

    private val sessListener = MediaSessionManager.OnActiveSessionsChangedListener { bind(it) }

    private val cb = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) { readController() }
        override fun onPlaybackStateChanged(state: PlaybackState?) { readController() }
        override fun onSessionDestroyed() { bind(mgr?.getActiveSessions(cn)) }
    }

    override fun onListenerConnected() {
        mgr = getSystemService(MediaSessionManager::class.java)
        try {
            mgr?.addOnActiveSessionsChangedListener(sessListener, cn)
            bind(mgr?.getActiveSessions(cn))
        } catch (_: Exception) {}
        scanNav()
    }

    override fun onListenerDisconnected() {
        try { mgr?.removeOnActiveSessionsChangedListener(sessListener) } catch (_: Exception) {}
        ctrl?.unregisterCallback(cb)
        ctrl = null
        State.controller = null
        State.hasMusic = false
        State.hasNav = false
        State.notifyChange()
    }

    private fun bind(list: List<MediaController>?) {
        val pick = list?.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: list?.firstOrNull()
        if (pick?.sessionToken != ctrl?.sessionToken) {
            ctrl?.unregisterCallback(cb)
            ctrl = pick
            pick?.registerCallback(cb)
            State.controller = pick
        }
        readController()
    }

    private fun readController() {
        try { readControllerInner() } catch (_: Exception) {}
    }

    private fun readControllerInner() {
        val c = ctrl
        if (c == null) {
            State.hasMusic = false
            State.playing = false
            State.notifyChange()
            return
        }
        val m = c.metadata
        val s = c.playbackState
        State.title = m?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: ""
        State.artist = m?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: m?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST) ?: ""
        val bmp = m?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: m?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
        State.art = bmp?.let { Bitmap.createScaledBitmap(it, 96, 96, true) }
        State.durMs = m?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
        State.playing = s?.state == PlaybackState.STATE_PLAYING
        State.posMs = s?.position ?: 0L
        State.posStamp = s?.lastPositionUpdateTime ?: SystemClock.elapsedRealtime()
        State.speed = s?.playbackSpeed ?: 1f
        State.hasMusic = State.title.isNotEmpty() && s != null &&
            s.state != PlaybackState.STATE_STOPPED && s.state != PlaybackState.STATE_NONE
        State.notifyChange()
    }

    private fun scanNav() {
        var found = false
        try {
            for (n in activeNotifications) {
                val nt = n.notification
                val isNav = nt.category == Notification.CATEGORY_NAVIGATION ||
                    (n.packageName == "com.google.android.apps.maps" && n.isOngoing)
                if (isNav) {
                    val e = nt.extras
                    State.navTitle = e.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
                    State.navText = e.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
                    found = true
                    break
                }
            }
        } catch (_: Exception) {}
        State.hasNav = found
        State.notifyChange()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) { scanNav() }
    override fun onNotificationRemoved(sbn: StatusBarNotification?) { scanNav() }
}
