package com.castv.app

import java.text.SimpleDateFormat
import java.util.Date
import java.util.LinkedList
import java.util.Locale

/** Registro en memoria. Se puede leer desde Termux con: curl http://127.0.0.1:8000/log */
object DebugLog {
    private val lines = LinkedList<String>()
    private val fmt = SimpleDateFormat("HH:mm:ss", Locale.US)

    @Synchronized
    fun add(msg: String) {
        lines.addLast(fmt.format(Date()) + " " + msg)
        while (lines.size > 400) lines.removeFirst()
        android.util.Log.d("CASTV", msg)
    }

    @Synchronized
    fun dump(): String =
        if (lines.isEmpty()) "(sin registros todavia)\n" else lines.joinToString("\n") + "\n"

    @Synchronized
    fun clear() { lines.clear() }
}
