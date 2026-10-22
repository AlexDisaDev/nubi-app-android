package app.nubi.data

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * Dónde y cómo se guardan las fotos de "Alimentación" (ComidaComplementaria.
 * fotos): siempre como ficheros dentro del almacenamiento privado de la app
 * (filesDir/fotos), nunca como bytes sueltos ni como content:// de otra app
 * — así el campo "fotos: List<String>" de Modelos.kt puede ser siempre una
 * ruta de fichero normal y corriente.
 */
object FotosNubi {

    private fun carpeta(contexto: Context): File {
        val dir = File(contexto.filesDir, "fotos")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /** Fichero vacío nuevo, listo para que la cámara del sistema escriba en él. */
    fun nuevoFichero(contexto: Context): File =
        File(carpeta(contexto), "foto_${nuevoId()}.jpg")

    /** Uri de contenido (vía FileProvider) que se le pasa a la app de cámara. */
    fun uriParaFichero(contexto: Context, fichero: File): Uri =
        FileProvider.getUriForFile(contexto, "${contexto.packageName}.fileprovider", fichero)

    /**
     * Copia el contenido de una Uri ajena (por ejemplo, la que devuelve el
     * selector de fotos de la galería) a un fichero propio, y devuelve su
     * ruta. Así todas las fotos guardadas viven siempre en el mismo sitio,
     * vengan de la cámara o de la galería.
     */
    fun copiarDesdeUri(contexto: Context, uri: Uri): String? {
        val destino = nuevoFichero(contexto)
        return try {
            contexto.contentResolver.openInputStream(uri)?.use { entrada ->
                destino.outputStream().use { salida -> entrada.copyTo(salida) }
            }
            destino.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    /** Borra el fichero de una foto (al quitarla de la hoja o borrar la comida). */
    fun borrar(ruta: String) {
        try {
            File(ruta).delete()
        } catch (e: Exception) {
            // Si ya no está, no pasa nada: el objetivo (que no exista) ya se cumple.
        }
    }
}
