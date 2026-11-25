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
import android.media.PlaybackParams
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
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
        val repeat: Int, val fromQueue: Boolean, val sleepAfter: Boolean, val sleepAt: Long,
        val speed: Float = 1f, val speedTouched: Boolean = false   // [SINPANTALLA-1]
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
    private var hlSpeed = 1f            // [SINPANTALLA-1] velocidad elegida en la pantalla (no se guarda en preferencias)
    private var hlSpeedTouched = false
    private var hlEq: Equalizer? = null
    private var hlBass: BassBoost? = null
    private val main = Handler(Looper.getMainLooper())
    private var controlsReceiver: BroadcastReceiver? = null
    private var noisyReceiver: BroadcastReceiver? = null
    private val sleepRunnable = Runnable { if (headless) startFade() }   // [FUNDIDO-1]

    // [FUNDIDO-1] al cumplirse el temporizador, ~10 s bajando el volumen y luego se detiene
    private var fading = false
    private var fadeStep = 0
    private val fadeRunnable = object : Runnable {
        override fun run() {
            if (!headless || !fading) return
            fadeStep++
            val f = (1f - fadeStep / 10f).coerceAtLeast(0f)
            try { player?.setVolume(f, f) } catch (e: Exception) { }
            if (fadeStep >= 10) { fading = false; stopHeadless() } else main.postDelayed(this, 1000)
        }
    }

    private fun startFade() {
        val mp = player
        val sonando = try { mp != null && prepared && mp.isPlaying } catch (e: Exception) { false }
        if (!sonando) { stopHeadless(); return }
        fading = true
        fadeStep = 0
        main.postDelayed(fadeRunnable, 1000)
    }

    /** Cancela el fundido y devuelve el volumen (pausar/reanudar, pista nueva a mano, volver la pantalla). */
    private fun cancelFade() {
        if (!fading) return
        fading = false
        main.removeCallbacks(fadeRunnable)
        try { player?.setVolume(1f, 1f) } catch (e: Exception) { }
    }

    /** MainActivity se cierra con música del teléfono sonando: la música sigue aquí. */
    fun handOff(
        ctx: Context, cur: MediaItem, list: List<MediaItem>, index: Int, queue: List<MediaItem>,
        repeat: Int, fromQueue: Boolean, sleepAfter: Boolean, sleepAt: Long,
        speed: Float = 1f, speedTouched: Boolean = false
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
        hlSpeed = speed
        hlSpeedTouched = speedTouched
        headless = true
        bindListeners(mp)
        if (prepared) { attachHlEffects(mp); applyHlSpeed(mp) }   // [SINPANTALLA-1] la pista que ya suena conserva ecualizador y velocidad
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
        cancelFade()   // [FUNDIDO-1]
        headless = false
        releaseHlEffects()   // [SINPANTALLA-1] la pantalla vuelve a poner los suyos
        return Snapshot(cur, ArrayList(hlList), hlIndex, ArrayList(hlQueue), hlRepeat, hlFromQueue, hlSleepAfter, hlSleepAt, hlSpeed, hlSpeedTouched)
    }

    private fun bindListeners(mp: MediaPlayer) {
        mp.setOnPreparedListener { p ->
            if (headless && player === p) {
                prepared = true
                try { p.start() } catch (e: Exception) { }
                attachHlEffects(p)   // [SINPANTALLA-1]
                applyHlSpeed(p)
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
        releaseHlEffects()   // [SINPANTALLA-1]
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
        cancelFade()   // [FUNDIDO-1]
        try {
            if (mp.isPlaying) mp.pause() else { pausedByFocus = false; mp.start(); applyHlSpeed(mp) }
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
        fading = false   // [FUNDIDO-1]
        main.removeCallbacks(fadeRunnable)
        main.removeCallbacks(sleepRunnable)
        unregisterReceivers()
        releaseHlEffects()   // [SINPANTALLA-1]
        hlSpeed = 1f; hlSpeedTouched = false
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

    // ------------------------------------------------------------ [SINPANTALLA-1] Ecualizador y velocidad sin pantalla

    private fun releaseHlEffects() {
        try { hlEq?.release() } catch (e: Exception) { }
        try { hlBass?.release() } catch (e: Exception) { }
        hlEq = null
        hlBass = null
    }

    /** Preset propio activo (nivel de cada banda), leído de las mismas preferencias que usa MainActivity ([EQ-PRE-1]). */
    private fun userPresetLevels(sp: android.content.SharedPreferences): IntArray? {
        val name = sp.getString("eq_user_on", "") ?: ""
        if (name.isEmpty()) return null
        try {
            val arr = org.json.JSONArray(sp.getString("eq_user", "[]") ?: "[]")
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                if (o.getString("n") == name) {
                    val l = o.getJSONArray("l")
                    return IntArray(l.length()) { j -> l.getInt(j) }
                }
            }
        } catch (e: Exception) { }
        return null
    }

    /** Igual que attachEffects() de MainActivity: preset del sistema o propio y graves, desde las preferencias `castv`. */
    private fun attachHlEffects(mp: MediaPlayer) {
        releaseHlEffects()
        val ctx = appCtx ?: return
        val sp = ctx.getSharedPreferences("castv", Context.MODE_PRIVATE)
        val preset = sp.getInt("eqPreset", -1)
        val bassOn = sp.getBoolean("bassOn", false)
        try {
            val eq = Equalizer(0, mp.audioSessionId)
            val n = eq.numberOfPresets.toInt()
            val up = userPresetLevels(sp)
            if (up != null) {
                eq.setEnabled(true)
                val lo = eq.bandLevelRange[0].toInt()
                val hi = eq.bandLevelRange[1].toInt()
                for (b in 0 until eq.numberOfBands.toInt()) {
                    if (b < up.size) eq.setBandLevel(b.toShort(), up[b].coerceIn(lo, hi).toShort())
                }
            } else if (preset in 0 until n) {
                eq.usePreset(preset.toShort())
                eq.setEnabled(true)
            } else {
                eq.setEnabled(false)
            }
            hlEq = eq
        } catch (e: Exception) {
            hlEq = null
        }
        try {
            val bb = BassBoost(0, mp.audioSessionId)
            if (bassOn && bb.strengthSupported) {
                bb.setStrength(800.toShort())
                bb.setEnabled(true)
            } else {
                bb.setEnabled(false)
            }
            hlBass = bb
        } catch (e: Exception) {
            hlBass = null
        }
    }

    /** Solo se toca la velocidad con el reproductor sonando (igual que applySpeed() de MainActivity). */
    private fun applyHlSpeed(mp: MediaPlayer) {
        if (!hlSpeedTouched) return
        try { if (mp.isPlaying) mp.playbackParams = PlaybackParams().setSpeed(hlSpeed) } catch (e: Exception) { }
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
                        MainActivity.ACT_PREV -> { cancelFade(); step(-1) }   // [FUNDIDO-1]
                        MainActivity.ACT_NEXT -> { cancelFade(); step(+1) }
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
