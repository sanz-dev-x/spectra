package com.spectra.app.spotify

import android.content.Context
import android.util.Base64
import com.spectra.app.BuildConfig
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

data class SpotifyProfile(val id: String, val name: String, val imageUrl: String?)

data class SpotifyListItem(
    val id: String,
    val name: String,
    val owner: String,
    val imageUrl: String?,
    val trackCount: Int,
)

/**
 * Login resmi Spotify (Authorization Code + PKCE) lewat halaman web Spotify. Aplikasi tidak pernah
 * menerima kata sandi; yang disimpan hanya token, di SharedPreferences yang dikecualikan dari cadangan.
 * Butuh SPOTIFY_CLIENT_ID dan redirect URI [REDIRECT_URI] yang terdaftar di dashboard developer Spotify.
 */
object SpotifyAccount {
    const val REDIRECT_URI = "spectra://spotify-callback"
    const val LIKED_ID = "liked"
    private const val PREFS = "spotify_account"
    private const val SCOPES = "user-library-read playlist-read-private playlist-read-collaborative user-read-private"
    private const val MAX_PAGES = 20

    private val json = Json { ignoreUnknownKeys = true }
    private val http = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(25, TimeUnit.SECONDS).build()

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isConfigured(): Boolean = BuildConfig.SPOTIFY_CLIENT_ID.isNotBlank()

    fun isConnected(context: Context): Boolean = prefs(context).getString("refresh_token", null) != null

    fun profile(context: Context): SpotifyProfile? {
        val p = prefs(context)
        val id = p.getString("user_id", null) ?: return null
        return SpotifyProfile(id, p.getString("user_name", null) ?: id, p.getString("user_image", null))
    }

    fun disconnect(context: Context) {
        prefs(context).edit().clear().apply()
    }

    private fun b64url(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)

    fun buildAuthorizeUrl(context: Context): String {
        val rnd = SecureRandom()
        val verifier = b64url(ByteArray(48).also { rnd.nextBytes(it) })
        val state = b64url(ByteArray(16).also { rnd.nextBytes(it) })
        val challenge = b64url(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()))
        prefs(context).edit().putString("pkce_verifier", verifier).putString("pkce_state", state).apply()
        return android.net.Uri.parse("https://accounts.spotify.com/authorize").buildUpon()
            .appendQueryParameter("client_id", BuildConfig.SPOTIFY_CLIENT_ID)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("state", state)
            .appendQueryParameter("scope", SCOPES)
            .appendQueryParameter("show_dialog", "true")
            .build().toString()
    }

    private fun tokenRequest(form: FormBody): JsonObject {
        val req = Request.Builder().url("https://accounts.spotify.com/api/token").post(form).build()
        http.newCall(req).execute().use { r ->
            val body = r.body.string()
            if (!r.isSuccessful) throw IOException("Spotify token ${r.code}")
            return json.parseToJsonElement(body).jsonObject
        }
    }

    private fun storeTokens(context: Context, o: JsonObject) {
        val access = o["access_token"]?.jsonPrimitive?.contentOrNull ?: throw IOException("no access token")
        val expiresIn = o["expires_in"]?.jsonPrimitive?.longOrNull ?: 3600L
        val e = prefs(context).edit()
            .putString("access_token", access)
            .putLong("expires_at", System.currentTimeMillis() + (expiresIn - 60) * 1000)
        o["refresh_token"]?.jsonPrimitive?.contentOrNull?.let { e.putString("refresh_token", it) }
        e.apply()
    }

    /** Dipanggil dengan URL pengalihan dari WebView. Mengembalikan profil bila sukses. Jalankan di thread IO. */
    fun completeLogin(context: Context, redirectUrl: String): SpotifyProfile {
        val uri = android.net.Uri.parse(redirectUrl)
        val p = prefs(context)
        if (uri.getQueryParameter("error") != null) throw IOException("Spotify: ${uri.getQueryParameter("error")}")
        if (uri.getQueryParameter("state") != p.getString("pkce_state", null)) throw IOException("state mismatch")
        val code = uri.getQueryParameter("code") ?: throw IOException("no code")
        val verifier = p.getString("pkce_verifier", null) ?: throw IOException("no verifier")
        storeTokens(
            context,
            tokenRequest(
                FormBody.Builder()
                    .add("grant_type", "authorization_code")
                    .add("code", code)
                    .add("redirect_uri", REDIRECT_URI)
                    .add("client_id", BuildConfig.SPOTIFY_CLIENT_ID)
                    .add("code_verifier", verifier)
                    .build(),
            ),
        )
        p.edit().remove("pkce_verifier").remove("pkce_state").apply()
        val me = apiGet(context, "https://api.spotify.com/v1/me")
        val id = me["id"]?.jsonPrimitive?.contentOrNull ?: throw IOException("no profile")
        val name = me["display_name"]?.jsonPrimitive?.contentOrNull ?: id
        val image = me["images"]?.jsonArray?.firstOrNull()?.jsonObject?.get("url")?.jsonPrimitive?.contentOrNull
        p.edit().putString("user_id", id).putString("user_name", name).putString("user_image", image).apply()
        return SpotifyProfile(id, name, image)
    }

    private fun accessToken(context: Context, force: Boolean = false): String {
        val p = prefs(context)
        val cur = p.getString("access_token", null)
        if (!force && cur != null && System.currentTimeMillis() < p.getLong("expires_at", 0L)) return cur
        val refresh = p.getString("refresh_token", null) ?: throw IOException("not connected")
        storeTokens(
            context,
            tokenRequest(
                FormBody.Builder()
                    .add("grant_type", "refresh_token")
                    .add("refresh_token", refresh)
                    .add("client_id", BuildConfig.SPOTIFY_CLIENT_ID)
                    .build(),
            ),
        )
        return p.getString("access_token", null) ?: throw IOException("no access token")
    }

    private fun apiGet(context: Context, url: String): JsonObject {
        var force = false
        repeat(2) {
            val req = Request.Builder().url(url).header("Authorization", "Bearer ${accessToken(context, force)}").build()
            http.newCall(req).execute().use { r ->
                val body = r.body.string()
                if (r.code == 401 && !force) {
                    force = true
                    return@repeat
                }
                if (!r.isSuccessful) throw IOException("Spotify API ${r.code}")
                return json.parseToJsonElement(body).jsonObject
            }
        }
        throw IOException("Spotify API unauthorized")
    }

    private fun JsonElement?.str(): String? = (this as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull

    private fun imageOf(images: JsonElement?): String? {
        val arr = images as? JsonArray ?: return null
        return (arr.getOrNull(1) ?: arr.firstOrNull())?.jsonObject?.get("url").str()
    }

    private fun pages(context: Context, firstUrl: String, onItems: (JsonArray) -> Unit) {
        var url: String? = firstUrl
        var n = 0
        while (url != null && n < MAX_PAGES) {
            val o = apiGet(context, url)
            (o["items"] as? JsonArray)?.let(onItems)
            url = o["next"].str()
            n++
        }
    }

    fun likedCount(context: Context): Int =
        apiGet(context, "https://api.spotify.com/v1/me/tracks?limit=1")["total"]?.jsonPrimitive?.intOrNull ?: 0

    fun playlists(context: Context): List<SpotifyListItem> {
        val out = mutableListOf<SpotifyListItem>()
        pages(context, "https://api.spotify.com/v1/me/playlists?limit=50") { items ->
            items.forEach { el ->
                val o = el as? JsonObject ?: return@forEach
                val id = o["id"].str() ?: return@forEach
                val count = ((o["tracks"] ?: o["items"]) as? JsonObject)?.get("total")?.jsonPrimitive?.intOrNull ?: 0
                out += SpotifyListItem(
                    id = id,
                    name = o["name"].str() ?: id,
                    owner = (o["owner"] as? JsonObject)?.get("display_name").str().orEmpty(),
                    imageUrl = imageOf(o["images"]),
                    trackCount = count,
                )
            }
        }
        return out
    }

    private fun parseTrack(el: JsonElement): SpotifyTrack? {
        val item = el as? JsonObject ?: return null
        val t = (item["track"] ?: item["item"]) as? JsonObject ?: return null
        val title = t["name"].str() ?: return null
        val artists = (t["artists"] as? JsonArray)?.mapNotNull { (it as? JsonObject)?.get("name").str() }.orEmpty()
        val album = t["album"] as? JsonObject
        return SpotifyTrack(
            title = title,
            artist = artists.joinToString(", "),
            album = album?.get("name").str().orEmpty(),
            artworkUrl = imageOf(album?.get("images")),
            durationMs = t["duration_ms"]?.jsonPrimitive?.longOrNull ?: 0L,
        )
    }

    fun tracks(context: Context, id: String): List<SpotifyTrack> {
        val out = mutableListOf<SpotifyTrack>()
        val collect: (JsonArray) -> Unit = { items -> items.mapNotNullTo(out) { parseTrack(it) } }
        if (id == LIKED_ID) {
            pages(context, "https://api.spotify.com/v1/me/tracks?limit=50", collect)
        } else {
            try {
                pages(context, "https://api.spotify.com/v1/playlists/$id/tracks?limit=50", collect)
            } catch (e: IOException) {
                out.clear()
                pages(context, "https://api.spotify.com/v1/playlists/$id/items?limit=50", collect)
            }
        }
        return out
    }
}
