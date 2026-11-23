package com.castv.app

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat

/**
 * [SERVICIO-1a] Dueño único (en todo el proceso) del reproductor de música del teléfono y de su estado.
 * [SERVICIO-1b] "Modo sin pantalla": si Android cierra MainActivity con música sonando en el teléfono, la pantalla
 * entrega aquí la pista, la lista, la cola y el modo repetir; la música sigue (pasa a la siguiente sola), la
 * notificación mantiene ⏮ ⏯ ⏭ y, al reabrir la app, la pantalla recoge todo de nuevo con [adopt].
 * Mientras MainActivity está viva este modo NO hace nada: ella manda igual que siempre.
 */
object PlayerCore {
    /** MediaPlayer de la música que suena en el teléfono (null si no hay). */
    @Volatile var player: MediaPlayer? = null

    /** true mientras la música va por el teléfono (aunque esté en pausa o preparándose). */
    @Volatile var active = false

    /** true cuando el MediaPlayer ya está preparado y se puede controlar. */
    @Volatile var prepared = false

    /** true si la pausa la causó otra app (llamada, alarma…); al volver el foco se reanuda sola. */
    @Volatile var pausedByFocus = false

    /** Escucha del foco de audio: pausa si otra app toma el audio y reanuda al recuperarlo. */
    val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                if (active && prepared && player?.isPlaying == true) {
                    pausedByFocus = true
                    try { player?.pause() } catch (e: Exception) { }
                }
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (pausedByFocus && active) {
                    pausedByFocus = false
                    try { player?.start() } catch (e: Exception) { }
                }
            }
        }
    }

    // ------------------------------------------------------------ [SERVICIO-1b] Modo sin pantalla

    /** Lo que la pantalla recoge al volver a abrirse. */
    class Snapshot(
        val current: MediaItem, val list: List<MediaItem>, val index: Int, val queue: List<MediaItem>,
        val repeat: Int, val fromQueue: Boolean, val sleepAfter: Boolean, val sleepAt: Long
    )

    /** true mientras la música sigue sin MainActivity. */
    @Volatile var headless = false

    private var appCtx: Context? = null
    private var hlCurrent: MediaItem? = null
    private var hlList: List<MediaItem> = emptyList()
    private var hlIndex = -1
    private val hlQueue = ArrayList<MediaItem>()
    private var hlRepeat = 0            // 0 = no repetir · 1 = repetir todo · 2 = repetir una
    private var hlFromQueue = false
    private var hlSleepAfter = false
    private var hlSleepAt = 0L          // SystemClock.elapsedRealtime() en que se apaga (0 = sin temporizador)
    private val main = Handler(Looper.getMainLooper())
    private var controlsReceiver: BroadcastReceiver? = null
    private var noisyReceiver: BroadcastReceiver? = null
    private val sleepRunnable = Runnable { if (headless) stopHeadless() }

    /** MainActivity se cierra con música del teléfono sonando: la música sigue aquí. */
    fun handOff(
        ctx: Context, cur: MediaItem, list: List<MediaItem>, index: Int, queue: List<MediaItem>,
        repeat: Int, fromQueue: Boolean, sleepAfter: Boolean, sleepAt: Long
    ) {
        val mp = player ?: return
        appCtx = ctx.applicationContext
        hlCurrent = cur
        hlList = ArrayList(list)
        hlIndex = index
        hlQueue.clear(); hlQueue.addAll(queue)
        hlRepeat = repeat
        hlFromQueue = fromQueue
        hlSleepAfter = sleepAfter
        hlSleepAt = sleepAt
        headless = true
        bindListeners(mp)
        registerReceivers()
        scheduleSleep()
        notifyNow()
        main.postDelayed({ notifyNow() }, 800)   // por si un último aviso de la pantalla pisó la notificación
    }

    /** La pantalla se abre de nuevo: recoge el estado y el modo sin pantalla se retira. Null si no había nada. */
    fun adopt(): Snapshot? {
        if (!headless) return null
        val cur = hlCurrent
        if (cur == null || player == null) { stopHeadless(); return null }
        unregisterReceivers()
        main.removeCallbacks(sleepRunnable)
        headless = false
        return Snapshot(cur, ArrayList(hlList), hlIndex, ArrayList(hlQueue), hlRepeat, hlFromQueue, hlSleepAfter, hlSleepAt)
    }

    private fun bindListeners(mp: MediaPlayer) {
        mp.setOnPreparedListener { p ->
            if (headless && player === p) {
                prepared = true
                try { p.start() } catch (e: Exception) { }
                notifyNow()
            }
        }
        mp.setOnCompletionListener { p -> if (headless && player === p) onCompleted() }
        mp.setOnErrorListener { p, _, _ ->
            if (headless && player === p) stopHeadless()
            true
        }
    }

    private fun onCompleted() {
        hlCurrent?.let { Store.clearResume(it) }
        if (hlSleepAfter) { hlSleepAfter = false; stopHeadless(); return }
        val cur = hlCurrent
        if (hlRepeat == 2 && cur != null) { startHeadless(cur); return }
        if (hlRepeat == 1 || hlQueue.isNotEmpty()) step(+1) else stopHeadless()
    }

    /** Igual que step() de MainActivity: cola primero, luego la lista (circular). */
    private fun step(delta: Int) {
        if (delta > 0 && hlQueue.isNotEmpty()) {
            val next = hlQueue.removeAt(0)
            hlFromQueue = true
            startHeadless(next)
            return
        }
        if (delta < 0 && hlFromQueue && hlIndex in hlList.indices) {
            hlFromQueue = false
            startHeadless(hlList[hlIndex])
            return
        }
        hlFromQueue = false
        if (hlList.isEmpty()) { stopHeadless(); return }
        hlIndex = if (hlIndex < 0) 0 else Math.floorMod(hlIndex + delta, hlList.size)
        startHeadless(hlList[hlIndex])
    }

    private fun startHeadless(item: MediaItem) {
        val ctx = appCtx ?: return
        val old = player
        player = null   // los avisos del reproductor anterior ya no cuentan
        try { old?.release() } catch (e: Exception) { }
        prepared = false
        pausedByFocus = false
        hlCurrent = item
        Store.registerPlay(item)
        val mp = MediaPlayer()
        player = mp
        active = true
        try {
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            mp.setWakeMode(ctx, PowerManager.PARTIAL_WAKE_LOCK)
            mp.setDataSource(ctx, Uri.parse(item.uriString))
            bindListeners(mp)
            mp.prepareAsync()
            notifyNow()
        } catch (e: Exception) {
            DebugLog.add("SERVICIO: no se pudo abrir la pista: ${e.message}")
            stopHeadless()
        }
    }

    private fun toggle() {
        val mp = player ?: return
        if (!prepared) return
        try {
            if (mp.isPlaying) mp.pause() else { pausedByFocus = false; mp.start() }
        } catch (e: Exception) { }
        notifyNow()
    }

    /** Termina el modo sin pantalla: libera el reproductor y deja la notificación sencilla del servicio. */
    private fun stopHeadless() {
        val ctx = appCtx
        val mp = player
        player = null
        active = false
        prepared = false
        pausedByFocus = false
        headless = false
        main.removeCallbacks(sleepRunnable)
        unregisterReceivers()
        try { mp?.release() } catch (e: Exception) { }
        if (ctx != null) {
            try {
                (ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager).abandonAudioFocus(focusListener)
            } catch (e: Exception) { }
            try {
                val n = NotificationCompat.Builder(ctx, MainActivity.CHANNEL_ID)
                    .setContentTitle("CASTV activo")
                    .setContentText("Listo para transmitir al TV")
                    .setSmallIcon(android.R.drawable.ic_media_play)
                    .build()
                ctx.getSystemService(NotificationManager::class.java).notify(1, n)
            } catch (e: Exception) { }
        }
    }

    private fun scheduleSleep() {
        main.removeCallbacks(sleepRunnable)
        if (hlSleepAt > 0L) {
            main.postDelayed(sleepRunnable, maxOf(hlSleepAt - SystemClock.elapsedRealtime(), 0L))
        }
    }

    private fun registerReceivers() {
        val ctx = appCtx ?: return
        unregisterReceivers()
        try {
            val r = object : BroadcastReceiver() {
                override fun onReceive(c: Context?, intent: Intent?) {
                    if (!headless) return
                    when (intent?.action) {
                        MainActivity.ACT_PREV -> step(-1)
                        MainActivity.ACT_NEXT -> step(+1)
                        MainActivity.ACT_TOGGLE -> toggle()
                    }
                }
            }
            val f = IntentFilter().apply {
                addAction(MainActivity.ACT_PREV)
                addAction(MainActivity.ACT_TOGGLE)
                addAction(MainActivity.ACT_NEXT)
            }
            if (Build.VERSION.SDK_INT >= 33) ctx.registerReceiver(r, f, Context.RECEIVER_NOT_EXPORTED)
            else ctx.registerReceiver(r, f)
            controlsReceiver = r
        } catch (e: Exception) { }
        try {
            val nr = object : BroadcastReceiver() {
                override fun onReceive(c: Context?, intent: Intent?) {
                    if (!headless || intent?.action != AudioManager.ACTION_AUDIO_BECOMING_NOISY) return
                    try { if (player?.isPlaying == true) { player?.pause(); notifyNow() } } catch (e: Exception) { }
                }
            }
            val nf = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            if (Build.VERSION.SDK_INT >= 33) ctx.registerReceiver(nr, nf, Context.RECEIVER_EXPORTED)
            else ctx.registerReceiver(nr, nf)
            noisyReceiver = nr
        } catch (e: Exception) { }
    }

    private fun unregisterReceivers() {
        val ctx = appCtx ?: return
        controlsReceiver?.let { try { ctx.unregisterReceiver(it) } catch (e: Exception) { } }
        noisyReceiver?.let { try { ctx.unregisterReceiver(it) } catch (e: Exception) { } }
        controlsReceiver = null
        noisyReceiver = null
    }

    /** Notificación de la pista (misma de siempre: ⏮ ⏯ ⏭), ya sin MainActivity. */
    private fun notifyNow() {
        if (!headless) return
        val ctx = appCtx ?: return
        val item = hlCurrent ?: return
        val playing = try { player?.isPlaying == true } catch (e: Exception) { false }
        val cover: Bitmap? = Thumbs.cached(item)
        val title = item.name.substringBeforeLast('.')
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        fun act(action: String, req: Int) =
            PendingIntent.getBroadcast(ctx, req, Intent(action).setPackage(ctx.packageName), flags)
        val open = PendingIntent.getActivity(
            ctx, 0,
            Intent(ctx, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            flags
        )
        try {
            val n = NotificationCompat.Builder(ctx, MainActivity.CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle(title)
                .setLargeIcon(cover)
                .setContentText(if (playing) "Sonando en el teléfono" else "En pausa · teléfono")
                .setContentIntent(open)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .addAction(android.R.drawable.ic_media_previous, "Anterior", act(MainActivity.ACT_PREV, 1))
                .addAction(
                    if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                    if (playing) "Pausa" else "Reproducir", act(MainActivity.ACT_TOGGLE, 2)
                )
                .addAction(android.R.drawable.ic_media_next, "Siguiente", act(MainActivity.ACT_NEXT, 3))
                .setStyle(
                    androidx.media.app.NotificationCompat.MediaStyle()
                        .setShowActionsInCompactView(0, 1, 2)
                )
                .build()
            ctx.getSystemService(NotificationManager::class.java).notify(1, n)
        } catch (e: Exception) { }
    }
}
