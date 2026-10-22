package app.nubi.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.random.Random

/**
 * Los tres "colores" de ruido de la pestaña Sonidos — mismos nombres que
 * en la captura de iOS (Ruido blanco / rosa / marrón).
 */
enum class TipoRuido { BLANCO, ROSA, MARRON }

/**
 * Generador de ruido blanco/rosa/marrón en tiempo real, con AudioTrack en
 * modo streaming: no hay ningún fichero de audio ni descarga de por medio,
 * cada muestra se calcula sobre la marcha (de ahí "Generados
 * matemáticamente" en la pantalla).
 *
 * - Blanco: cada muestra es un número aleatorio independiente — energía
 *   plana en todas las frecuencias.
 * - Rosa: filtro de Paul Kellett (la versión "economy" de siempre), que
 *   suaviza el blanco quitándole agudos — energía igual por octava.
 * - Marrón: integración con fuga del blanco (un paseo aleatorio que no se
 *   escapa), todavía más grave que el rosa.
 *
 * Todo corre en un hilo propio, dedicado solo a esto, que vive mientras
 * "reproducir" esté activo.
 */
object MotorRuido {
    private const val FRECUENCIA = 44_100

    private var hilo: Thread? = null

    @Volatile
    private var activo = false

    val reproduciendo: Boolean get() = activo

    fun reproducir(tipo: TipoRuido) {
        detener()
        activo = true
        hilo = Thread({ generarYReproducir(tipo) }, "nubi-motor-ruido").apply {
            isDaemon = true
            start()
        }
    }

    fun detener() {
        if (!activo && hilo == null) return
        activo = false
        hilo?.join(500)
        hilo = null
    }

    private fun generarYReproducir(tipo: TipoRuido) {
        val tamanoMinimo = AudioTrack.getMinBufferSize(
            FRECUENCIA, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val tamanoBuffer = if (tamanoMinimo > 0) tamanoMinimo * 2 else FRECUENCIA

        val pista = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(FRECUENCIA)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(tamanoBuffer)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        // Estado del filtro rosa (se conserva muestra a muestra: por eso
        // vive aquí fuera, no dentro del bucle).
        var b0 = 0.0
        var b1 = 0.0
        var b2 = 0.0
        var b3 = 0.0
        var b4 = 0.0
        var b5 = 0.0
        var b6 = 0.0
        // Estado del marrón.
        var anterior = 0.0

        val random = Random(System.nanoTime())
        val trozo = ShortArray(tamanoBuffer / 2)

        try {
            pista.play()
            while (activo) {
                for (i in trozo.indices) {
                    val blanco = random.nextDouble(-1.0, 1.0)
                    val valor = when (tipo) {
                        TipoRuido.BLANCO -> blanco * 0.5

                        TipoRuido.ROSA -> {
                            b0 = 0.99886 * b0 + blanco * 0.0555179
                            b1 = 0.99332 * b1 + blanco * 0.0750759
                            b2 = 0.96900 * b2 + blanco * 0.1538520
                            b3 = 0.86650 * b3 + blanco * 0.3104856
                            b4 = 0.55000 * b4 + blanco * 0.5329522
                            b5 = -0.7616 * b5 - blanco * 0.0168980
                            val rosa = b0 + b1 + b2 + b3 + b4 + b5 + b6 + blanco * 0.5362
                            b6 = blanco * 0.115926
                            rosa * 0.11
                        }

                        TipoRuido.MARRON -> {
                            anterior = (anterior + 0.02 * blanco) / 1.02
                            anterior * 3.5
                        }
                    }
                    trozo[i] = (valor.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()
                }
                pista.write(trozo, 0, trozo.size)
            }
        } finally {
            try {
                pista.stop()
            } catch (e: IllegalStateException) {
                // Ya estaba parada (p.ej. si nunca llegó a reproducir nada).
            }
            pista.release()
        }
    }
}
