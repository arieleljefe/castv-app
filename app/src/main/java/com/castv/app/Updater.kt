package com.castv.app

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale

/**
 * [UPDATE-1] Actualizar CASTV desde la propia app.
 * Lee el último Release público de GitHub (sin token), descarga el APK a la carpeta interna,
 * comprueba SHA-256, paquete, versión y firma, y abre el instalador de Android.
 * Solo acepta URLs de https://github.com/arieleljefe/castv-app/releases/download/ (y los
 * servidores de descarga de GitHub a los que redirige).
 */
object Updater {

    private const val API_URL = "https://api.github.com/repos/arieleljefe/castv-app/releases/latest"
    private const val DOWNLOAD_PREFIX = "https://github.com/arieleljefe/castv-app/releases/download/"
    private const val MAX_APK_BYTES = 300L * 1024L * 1024L
    private const val MAX_JSON_BYTES = 1024 * 1024
    private const val APK_NAME = "CASTV-update.apk"
    private const val TIMEOUT_MS = 15000

    class Release(
        val tag: String,
        val notes: String,
        val apkName: String,
        val apkUrl: String,
        val size: Long,
        val sha256: String?
    )

    class CheckResult(
        val ok: Boolean,
        val message: String,
        val release: Release?,
        val newer: Boolean
    )

    class DownloadResult(
        val ok: Boolean,
        val message: String,
        val file: File?,
        val shaChecked: Boolean,
        val cancelled: Boolean
    )

    private fun updatesDir(ctx: Context): File = File(ctx.filesDir, "updates")

    // ------------------------------------------------------------ Consultar

    fun check(ctx: Context): CheckResult {
        var conn: HttpURLConnection? = null
        try {
            conn = URL(API_URL).openConnection() as HttpURLConnection
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("User-Agent", "CASTV-updater")
            val code = conn.responseCode
            if (code == 404) return CheckResult(false, "Todavía no hay ningún Release publicado.", null, false)
            if (code == 403 || code == 429) {
                return CheckResult(false, "GitHub limitó las consultas (máximo 60 por hora). Prueba más tarde.", null, false)
            }
            if (code != 200) return CheckResult(false, "GitHub respondió con el código $code.", null, false)

            val text = conn.inputStream.use { readLimited(it, MAX_JSON_BYTES) }
            val j = JSONObject(text)
            val tag = j.optString("tag_name", "")
            if (tag.isEmpty()) return CheckResult(false, "La respuesta de GitHub no trae la versión.", null, false)
            val notes = if (j.isNull("body")) "" else j.optString("body", "")

            var chosen: Release? = null
            val assets = j.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val a = assets.optJSONObject(i) ?: continue
                    val name = a.optString("name", "")
                    val url = a.optString("browser_download_url", "")
                    val size = a.optLong("size", -1L)
                    if (!name.endsWith(".apk", ignoreCase = true)) continue
                    if (!url.startsWith(DOWNLOAD_PREFIX)) continue
                    if (size <= 0L || size > MAX_APK_BYTES) continue
                    val digest = if (a.isNull("digest")) "" else a.optString("digest", "")
                    val sha = if (digest.startsWith("sha256:")) digest.substring(7).lowercase(Locale.ROOT) else null
                    chosen = Release(tag, notes, name, url, size, sha)
                    break
                }
            }
            if (chosen == null) {
                return CheckResult(false, "El Release $tag no tiene un APK válido.", null, false)
            }
            val installed = try {
                ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: ""
            } catch (e: Exception) {
                ""
            }
            val newer = isNewer(tag, installed)
            return CheckResult(true, "Última: $tag; instalada: $installed; " + (if (newer) "hay nueva" else "al día"), chosen, newer)
        } catch (e: java.net.UnknownHostException) {
            return CheckResult(false, "Sin conexión a internet.", null, false)
        } catch (e: java.net.SocketTimeoutException) {
            return CheckResult(false, "GitHub tardó demasiado en responder.", null, false)
        } catch (e: Exception) {
            return CheckResult(false, "No se pudo consultar GitHub: " + (e.message ?: e.javaClass.simpleName), null, false)
        } finally {
            try { conn?.disconnect() } catch (e: Exception) { }
        }
    }

    private fun readLimited(ins: InputStream, max: Int): String {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        while (true) {
            val n = ins.read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
            if (out.size() > max) throw IOException("Respuesta demasiado grande")
        }
        return out.toString("UTF-8")
    }

    private fun nums(s: String): List<Long> =
        Regex("\\d+").findAll(s).map { it.value.toLongOrNull() ?: 0L }.toList()

    /** true si [remote] (p. ej. "v3.2") es mayor que [local] (p. ej. "3.1"). */
    fun isNewer(remote: String, local: String): Boolean {
        val a = nums(remote)
        val b = nums(local)
        if (a.isEmpty()) return false
        val n = maxOf(a.size, b.size)
        for (i in 0 until n) {
            val x = if (i < a.size) a[i] else 0L
            val y = if (i < b.size) b[i] else 0L
            if (x != y) return x > y
        }
        return false
    }

    // ------------------------------------------------------------ Descargar

    private fun allowedUrl(s: String): Boolean = try {
        val u = URL(s)
        u.protocol == "https" && (u.host == "github.com" || u.host.endsWith(".githubusercontent.com"))
    } catch (e: Exception) {
        false
    }

    private fun openFollowing(start: String): HttpURLConnection {
        var url = start
        for (hop in 0..5) {
            val c = URL(url).openConnection() as HttpURLConnection
            c.instanceFollowRedirects = false
            c.connectTimeout = TIMEOUT_MS
            c.readTimeout = TIMEOUT_MS
            c.setRequestProperty("User-Agent", "CASTV-updater")
            c.setRequestProperty("Accept-Encoding", "identity")
            val code = c.responseCode
            if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                val loc = c.getHeaderField("Location")
                c.disconnect()
                if (loc.isNullOrEmpty()) throw IOException("Redirección sin destino")
                val next = URL(URL(url), loc).toString()
                if (!allowedUrl(next)) throw IOException("Destino de descarga no permitido")
                url = next
                continue
            }
            if (code != 200) {
                c.disconnect()
                throw IOException("GitHub respondió con el código $code")
            }
            return c
        }
        throw IOException("Demasiadas redirecciones")
    }

    private fun hex(b: ByteArray): String {
        val sb = StringBuilder()
        for (x in b) sb.append(String.format("%02x", x.toInt() and 0xff))
        return sb.toString()
    }

    private fun sha256Of(f: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { ins ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = ins.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return hex(md.digest())
    }

    private fun fail(msg: String) = DownloadResult(false, msg, null, false, false)

    /** Se ejecuta en un hilo. [onProgress] recibe (porcentaje o -1, MB descargados). */
    fun download(
        ctx: Context,
        r: Release,
        onProgress: (Int, Long) -> Unit,
        isCancelled: () -> Boolean
    ): DownloadResult {
        if (!r.apkUrl.startsWith(DOWNLOAD_PREFIX) || !r.apkName.endsWith(".apk", ignoreCase = true)) {
            return fail("La dirección de descarga no es de confianza.")
        }
        val dir = updatesDir(ctx)
        try { dir.mkdirs() } catch (e: Exception) { }
        val finalFile = File(dir, APK_NAME)
        val part = File(dir, "$APK_NAME.part")

        // Si ya se bajó antes y coincide exactamente con la suma publicada, se reutiliza.
        try {
            if (r.sha256 != null && finalFile.exists() && finalFile.length() == r.size &&
                sha256Of(finalFile) == r.sha256) {
                return DownloadResult(true, "Ya estaba descargada.", finalFile, true, false)
            }
        } catch (e: Exception) { }
        try { finalFile.delete() } catch (e: Exception) { }
        try { part.delete() } catch (e: Exception) { }

        var conn: HttpURLConnection? = null
        try {
            conn = openFollowing(r.apkUrl)
            val len = conn.contentLengthLong
            val total = if (len > 0L) len else r.size
            if (total > MAX_APK_BYTES) return fail("El archivo es demasiado grande.")
            val md = MessageDigest.getInstance("SHA-256")
            var done = 0L
            var lastPct = -2
            var cancelled = false
            conn.inputStream.use { ins ->
                FileOutputStream(part).use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        if (isCancelled()) { cancelled = true; break }
                        val n = ins.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        md.update(buf, 0, n)
                        done += n
                        if (done > MAX_APK_BYTES) throw IOException("El archivo es demasiado grande")
                        val pct = if (total > 0L) ((done * 100L) / total).toInt().coerceIn(0, 100) else -1
                        if (pct != lastPct) {
                            lastPct = pct
                            onProgress(pct, done / 1_000_000L)
                        }
                    }
                }
            }
            if (cancelled) {
                try { part.delete() } catch (e: Exception) { }
                return DownloadResult(false, "Descarga cancelada.", null, false, true)
            }
            if (done != r.size) {
                try { part.delete() } catch (e: Exception) { }
                return fail("La descarga quedó incompleta ($done de ${r.size} bytes). Prueba otra vez.")
            }
            val got = hex(md.digest())
            if (r.sha256 != null && got != r.sha256) {
                try { part.delete() } catch (e: Exception) { }
                return fail("La suma SHA-256 no coincide con la publicada en GitHub. No se instalará.")
            }
            if (!part.renameTo(finalFile)) {
                try { part.delete() } catch (e: Exception) { }
                return fail("No se pudo guardar el APK en la carpeta interna.")
            }
            return DownloadResult(true, "Descargada (${done / 1_000_000L} MB).", finalFile, r.sha256 != null, false)
        } catch (e: java.net.UnknownHostException) {
            try { part.delete() } catch (e2: Exception) { }
            return fail("Sin conexión a internet.")
        } catch (e: Exception) {
            try { part.delete() } catch (e2: Exception) { }
            return fail("No se pudo descargar: " + (e.message ?: e.javaClass.simpleName))
        } finally {
            try { conn?.disconnect() } catch (e: Exception) { }
        }
    }

    // ------------------------------------------------------------ Comprobar

    @Suppress("DEPRECATION")
    private fun pkgFlags(): Int =
        if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES
        else PackageManager.GET_SIGNATURES

    @Suppress("DEPRECATION")
    private fun codeOf(p: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= 28) p.longVersionCode else p.versionCode.toLong()

    @Suppress("DEPRECATION")
    private fun signaturesOf(p: PackageInfo): Set<String>? {
        val arr: Array<android.content.pm.Signature>? = if (Build.VERSION.SDK_INT >= 28) {
            val si = p.signingInfo
            if (si == null) null else si.apkContentsSigners
        } else {
            p.signatures
        }
        if (arr == null || arr.isEmpty()) return null
        val set = HashSet<String>()
        for (s in arr) {
            set.add(hex(MessageDigest.getInstance("SHA-256").digest(s.toByteArray())))
        }
        return set
    }

    /** null = el APK es válido para instalar encima; si no, el motivo. */
    fun verify(ctx: Context, f: File): String? {
        val pm = ctx.packageManager
        val flags = pkgFlags()
        val arch: PackageInfo? = try {
            pm.getPackageArchiveInfo(f.absolutePath, flags)
        } catch (e: Exception) {
            null
        }
        if (arch == null) return "Android no pudo leer el APK descargado."
        if (arch.packageName != ctx.packageName) return "El APK descargado no es de CASTV."
        val cur: PackageInfo = try {
            pm.getPackageInfo(ctx.packageName, flags)
        } catch (e: Exception) {
            return "No se pudo leer la versión instalada."
        }
        val newCode = codeOf(arch)
        val curCode = codeOf(cur)
        if (newCode <= curCode) {
            return "El APK no es más nuevo que el instalado (código $newCode; instalado $curCode)."
        }
        val a = signaturesOf(arch)
        val b = signaturesOf(cur)
        if (a != null && b != null && a != b) {
            return "La firma del APK no coincide con la de la app instalada. Android no la dejaría actualizar."
        }
        return null
    }

    /** Borra descargas ya instaladas, inválidas o a medias. */
    fun tidy(ctx: Context) {
        try {
            val f = File(updatesDir(ctx), APK_NAME)
            if (f.exists() && verify(ctx, f) != null) f.delete()
        } catch (e: Exception) { }
        try { File(updatesDir(ctx), "$APK_NAME.part").delete() } catch (e: Exception) { }
    }

    // ------------------------------------------------------------ Instalar

    /** true = se abrió el instalador; false = falta permitir instalar desde CASTV (se abrió esa pantalla). */
    fun install(ctx: Context, f: File): Boolean {
        if (Build.VERSION.SDK_INT >= 26 && !ctx.packageManager.canRequestPackageInstalls()) {
            val p = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + ctx.packageName))
            ctx.startActivity(p)
            return false
        }
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", f)
        val i = Intent(Intent.ACTION_VIEW)
        i.setDataAndType(uri, "application/vnd.android.package-archive")
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(i)
        return true
    }
}
