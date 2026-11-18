package com.castv.app

/**
 * Listas de canales que vienen de fábrica (CATALOGO-1).
 * Se registran una sola vez por versión (Store.installCatalog) y cada lista se descarga al abrirla.
 * Al añadir listas nuevas en el futuro, se sube VERSION.
 */
object Catalog {
    const val VERSION = 1

    private const val BASE = "https://iptv-org.github.io/iptv/"

    private fun country(name: String, code: String) = M3uList(name, BASE + "countries/" + code + ".m3u")

    private fun category(name: String, slug: String) = M3uList(name, BASE + "categories/" + slug + ".m3u")

    val items: List<M3uList> = listOf(
        country("🇩🇴 República Dominicana", "do"),
        country("🇦🇷 Argentina", "ar"),
        country("🇧🇴 Bolivia", "bo"),
        country("🇧🇷 Brasil", "br"),
        country("🇨🇱 Chile", "cl"),
        country("🇨🇴 Colombia", "co"),
        country("🇨🇷 Costa Rica", "cr"),
        country("🇨🇺 Cuba", "cu"),
        country("🇪🇨 Ecuador", "ec"),
        country("🇸🇻 El Salvador", "sv"),
        country("🇬🇹 Guatemala", "gt"),
        country("🇭🇳 Honduras", "hn"),
        country("🇲🇽 México", "mx"),
        country("🇳🇮 Nicaragua", "ni"),
        country("🇵🇦 Panamá", "pa"),
        country("🇵🇾 Paraguay", "py"),
        country("🇵🇪 Perú", "pe"),
        country("🇵🇷 Puerto Rico", "pr"),
        country("🇺🇾 Uruguay", "uy"),
        country("🇻🇪 Venezuela", "ve"),
        country("🇺🇸 Estados Unidos", "us"),
        country("🇪🇸 España", "es"),
        category("🎬 Películas", "movies"),
        category("📺 Series", "series"),
        category("⚽ Deportes", "sports"),
        category("📰 Noticias", "news"),
        category("🎵 Música", "music"),
        category("🧒 Infantil", "kids"),
        category("🎞️ Documentales", "documentary"),
        category("🎨 Animación", "animation"),
        M3uList("Free-TV (gratis)", "https://raw.githubusercontent.com/Free-TV/IPTV/master/playlist.m3u8")
    )
}
