package com.castv.app

import android.media.AudioManager
import android.media.MediaPlayer

/**
 * [SERVICIO-1a] Dueño único (en todo el proceso) del reproductor de música del teléfono y de su estado.
 * Hasta ahora estos datos vivían dentro de MainActivity y se perdían con ella. Aquí viven fuera de la pantalla,
 * así que en SERVICIO-1b el servicio podrá seguir con la música aunque Android cierre MainActivity.
 * En esta fase solo se mueven los datos y el manejo del foco de audio: el comportamiento es el mismo de antes.
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
}
