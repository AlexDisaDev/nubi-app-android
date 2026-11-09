package app.nubi.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * Reseñas REALES de Nubi, para la línea de estrellas del paywall. No hay ni
 * una frase escrita a mano: salen del feed público de reseñas de Apple, el
 * mismo que alimenta la ficha de la App Store.
 *
 *   https://itunes.apple.com/<país>/rss/customerreviews/id=<appID>/sortBy=mostRecent/json
 *
 * POR QUÉ APPLE EN ANDROID: Google Play no publica las reseñas de una ficha
 * sin pasar por la Play Developer API, que necesita credenciales de servidor
 * y no puede ir dentro de la app. Las que se enseñan aquí son reseñas
 * verdaderas de Nubi escritas por gente real; lo único que cambia es la
 * tienda donde las dejaron. Si algún día quieres que Android enseñe reseñas
 * de Play, el camino es guardarlas en Supabase (que ya comparten iOS,
 * Android y la web) y leerlas de ahí.
 *
 * Reglas que conviene no romper:
 *  - Si la descarga falla, si no hay reseñas o si ninguna llega a 4
 *    estrellas, [destacada] se queda en null y el paywall NO pinta la fila.
 *    Nunca se inventa una reseña ni se deja una de relleno.
 *  - Se muestra el título tal cual lo escribió quien la dejó, con su nombre
 *    al lado. Ni se recorta a mitad de frase ni se reescribe.
 *
 * Espejo de ReseñasAppStore.swift en iOS: mismo feed, mismo filtro, misma
 * caché de un día.
 */
object ResenasAppStore {

    /** La misma que en App Store Connect. */
    private const val APP_ID = "6799178197"

    private const val PREFS = "resenas_appstore"
    private const val CLAVE_LISTA = "lista"
    private const val CLAVE_FECHA = "fecha"
    private const val VIGENCIA_MS = 24L * 60 * 60 * 1000

    data class Resena(val titulo: String, val autor: String, val estrellas: Int)

    /** La que pinta el paywall, o null si no hay ninguna que enseñar. */
    var destacada by mutableStateOf<Resena?>(null)
        private set

    private var contexto: Context? = null
    private var cargando = false
    private val ambito = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun iniciar(contexto: Context) {
        this.contexto = contexto.applicationContext
        destacada = leerDeCache()?.randomOrNull()
    }

    /** Se llama al abrir el paywall. Si la caché sigue fresca no toca la red. */
    fun cargar() {
        if (cargando) return
        val guardadas = leerDeCache()
        if (guardadas != null && cacheFresca()) {
            if (destacada == null) destacada = guardadas.randomOrNull()
            return
        }

        cargando = true
        ambito.launch {
            try {
                // Primero el país del móvil; si allí todavía no hay reseñas,
                // se prueba España, que es donde más tiene.
                val paises = buildList {
                    val region = Locale.getDefault().country.lowercase()
                    if (region.isNotBlank() && region != "es") add(region)
                    add("es")
                }
                for (pais in paises) {
                    val encontradas = descargar(pais)
                    if (!encontradas.isNullOrEmpty()) {
                        guardarEnCache(encontradas)
                        withContext(Dispatchers.Main) { destacada = encontradas.random() }
                        return@launch
                    }
                }
            } catch (_: Exception) {
                // Silencio a propósito: sin reseña, el paywall no pinta la fila.
            } finally {
                cargando = false
            }
        }
    }

    // ---------------------------------------------------------------- red

    private fun descargar(pais: String): List<Resena>? {
        val url = URL("https://itunes.apple.com/$pais/rss/customerreviews/id=$APP_ID/sortBy=mostRecent/json")
        val conexion = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 8000
            requestMethod = "GET"
        }
        return try {
            if (conexion.responseCode != 200) return null
            interpretar(conexion.inputStream.bufferedReader().use { it.readText() })
        } catch (_: Exception) {
            null
        } finally {
            conexion.disconnect()
        }
    }

    /**
     * El feed de Apple viene con todo metido en objetos con "label" dentro,
     * así que se lee a mano.
     */
    private fun interpretar(texto: String): List<Resena>? {
        val entradas = JSONObject(texto)
            .optJSONObject("feed")
            ?.optJSONArray("entry") ?: return null

        val lista = mutableListOf<Resena>()
        for (i in 0 until entradas.length()) {
            val entrada = entradas.optJSONObject(i) ?: continue
            val titulo = entrada.optJSONObject("title")?.optString("label")?.trim().orEmpty()
            val autor = entrada.optJSONObject("author")
                ?.optJSONObject("name")?.optString("label").orEmpty()
            val nota = entrada.optJSONObject("im:rating")?.optString("label")?.toIntOrNull() ?: continue

            // Solo 4 y 5 estrellas: es la fila de "esto le gusta a la gente",
            // no un resumen de todas las opiniones.
            if (nota < 4) continue
            // Títulos kilométricos no caben en una línea; mejor descartarlos
            // que cortarlos y poner en boca de alguien algo a medias.
            if (titulo.isEmpty() || titulo.length > 42 || autor.isEmpty()) continue

            lista += Resena(titulo, autor, nota)
        }
        return lista
    }

    // -------------------------------------------------------------- caché

    private fun prefs() = contexto?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun cacheFresca(): Boolean {
        val guardada = prefs()?.getLong(CLAVE_FECHA, 0L) ?: 0L
        return guardada > 0 && System.currentTimeMillis() - guardada < VIGENCIA_MS
    }

    private fun leerDeCache(): List<Resena>? {
        val crudo = prefs()?.getString(CLAVE_LISTA, null) ?: return null
        return try {
            val array = org.json.JSONArray(crudo)
            (0 until array.length()).mapNotNull { i ->
                val o = array.optJSONObject(i) ?: return@mapNotNull null
                Resena(
                    titulo = o.optString("titulo"),
                    autor = o.optString("autor"),
                    estrellas = o.optInt("estrellas")
                )
            }.takeIf { it.isNotEmpty() }
        } catch (_: Exception) {
            null
        }
    }

    private fun guardarEnCache(lista: List<Resena>) {
        val array = org.json.JSONArray()
        lista.forEach {
            array.put(
                JSONObject()
                    .put("titulo", it.titulo)
                    .put("autor", it.autor)
                    .put("estrellas", it.estrellas)
            )
        }
        prefs()?.edit()
            ?.putString(CLAVE_LISTA, array.toString())
            ?.putLong(CLAVE_FECHA, System.currentTimeMillis())
            ?.apply()
    }
}
