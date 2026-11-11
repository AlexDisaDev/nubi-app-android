package app.nubi.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
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

    /**
     * Foto de perfil del bebé: la lee de una Uri (galería o cámara), la
     * redimensiona a un máximo de 400px de lado y la codifica en base64
     * como JPEG al 70% — igual que iOS (ver NubiApp.resized y
     * bebe.fotoData en el repo de iOS, y backend/015_foto_bebe.sql). No se
     * guarda como fichero propio: al pesar poco ya redimensionada, vive
     * directamente en DatosNubi.fotoPerfilBase64, igual que el resto de
     * campos que no son listas.
     *
     * Devuelve null si la Uri no se puede leer o decodificar como imagen.
     */
    fun comprimirParaPerfil(contexto: Context, uri: Uri, ladoMaximo: Int = 400): String? {
        val original = try {
            contexto.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
        } catch (e: Exception) {
            null
        } ?: return null

        val escala = ladoMaximo.toFloat() / maxOf(original.width, original.height)
        val redimensionada = if (escala < 1f) {
            Bitmap.createScaledBitmap(
                original,
                (original.width * escala).toInt().coerceAtLeast(1),
                (original.height * escala).toInt().coerceAtLeast(1),
                true
            )
        } else {
            original
        }

        val salida = ByteArrayOutputStream()
        redimensionada.compress(Bitmap.CompressFormat.JPEG, 70, salida)
        return Base64.encodeToString(salida.toByteArray(), Base64.NO_WRAP)
    }

    /** El inverso de comprimirParaPerfil: de base64 a Bitmap, para pintarla. */
    fun decodificarPerfil(base64: String): Bitmap? = try {
        val bytes = Base64.decode(base64, Base64.NO_WRAP)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    } catch (e: Exception) {
        null
    }
}
