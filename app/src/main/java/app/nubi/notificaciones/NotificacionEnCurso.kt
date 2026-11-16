package app.nubi.notificaciones

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.nubi.MainActivity
import app.nubi.R
import app.nubi.data.DatosNubi
import app.nubi.data.LadoPecho
import app.nubi.data.Registro
import app.nubi.data.Repositorio
import app.nubi.data.TipoRegistro
import app.nubi.i18n.L10n
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * El equivalente Android de las Live Activities de iOS
 * (NubiWidgetsLiveActivity.swift): mientras hay una siesta, un sueño
 * nocturno o una toma de pecho en curso, una notificación fija, silenciosa,
 * con el contador en marcha y botones que registran sin abrir la app:
 *
 *  · Siesta  → "Se ha despertado"
 *  · Noche   → "Despertar nocturno" · "Buenos días"
 *  · Pecho   → "Cambiar pecho" · "Terminar"
 *
 * Los botones hacen exactamente lo mismo que los de Hoy (Pantallas.kt).
 * Aparece sola al empezar y desaparece al terminar, también si lo termina
 * la pareja desde su móvil (se recalcula con cada cambio de datos, ver
 * Repositorio.iniciar). En Android 16+ se pide además que se muestre como
 * "Live Update" (arriba en la cortina, en la pantalla de bloqueo y como
 * etiqueta en la barra de estado), si el sistema y el usuario lo permiten.
 */
object NotificacionEnCurso {

    private const val CANAL_ID = "nubi_en_curso"
    private const val ID_SUENO = 5101
    private const val ID_TOMA = 5102

    const val ACCION_TERMINAR_SUENO = "app.nubi.encurso.TERMINAR_SUENO"
    const val ACCION_DESPERTAR_NOCTURNO = "app.nubi.encurso.DESPERTAR_NOCTURNO"
    const val ACCION_CAMBIAR_PECHO = "app.nubi.encurso.CAMBIAR_PECHO"
    const val ACCION_TERMINAR_TOMA = "app.nubi.encurso.TERMINAR_TOMA"

    private val formatoHora: DateTimeFormatter =
        DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())

    /** Lo que se pinta: cambia solo cuando cambia algo visible, así no se
     *  rehace la notificación con cada guardado que no le afecta. */
    data class Estado(
        val nombreBebe: String,
        val sueno: Registro?,
        val toma: Registro?,
        val idioma: String
    )

    fun estadoDe(datos: DatosNubi): Estado {
        val bebe = datos.bebe ?: return Estado("", null, null, L10n.idiomaEfectivo.name)
        val sueno = datos.registros
            .filter { it.bebeId == bebe.id && it.tipo.esSueno && it.fin == null && !it.inicio.isAfter(Instant.now()) }
            .maxByOrNull { it.inicio }
        val toma = Repositorio.tomaEnCurso(datos.registros, bebe.id)
        return Estado(bebe.nombre.trim(), sueno, toma, L10n.idiomaEfectivo.name)
    }

    fun crearCanal(contexto: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val gestor = contexto.getSystemService(NotificationManager::class.java) ?: return
        if (gestor.getNotificationChannel(CANAL_ID) != null) return
        gestor.createNotificationChannel(
            NotificationChannel(CANAL_ID, L10n.t("Sueño y tomas en curso"), NotificationManager.IMPORTANCE_LOW).apply {
                description = L10n.t("Contador y botones mientras el bebé duerme o toma el pecho")
                setShowBadge(false)
            }
        )
    }

    fun mostrar(contexto: Context, estado: Estado) {
        val gestor = NotificationManagerCompat.from(contexto)
        if (!AvisosNubi.tienePermisoNotificaciones(contexto)) return
        crearCanal(contexto)

        if (estado.sueno == null) gestor.cancel(ID_SUENO)
        else runCatching { gestor.notify(ID_SUENO, notificacionSueno(contexto, estado.nombreBebe, estado.sueno)) }

        if (estado.toma == null) gestor.cancel(ID_TOMA)
        else runCatching { gestor.notify(ID_TOMA, notificacionToma(contexto, estado.nombreBebe, estado.toma)) }
    }

    private fun notificacionSueno(contexto: Context, nombre: String, sueno: Registro) =
        base(contexto, sueno.inicio)
            .setContentTitle(
                if (sueno.tipo == TipoRegistro.NOCHE) "$nombre · ${L10n.t("Sueño nocturno")}"
                else "$nombre · ${L10n.t("Siesta")}"
            )
            .setContentText("${L10n.t("Dormido desde")} ${formatoHora.format(sueno.inicio)}")
            .apply {
                if (sueno.tipo == TipoRegistro.NOCHE) {
                    addAction(0, L10n.t("Despertar nocturno"), accion(contexto, ACCION_DESPERTAR_NOCTURNO))
                    addAction(0, L10n.t("Buenos días"), accion(contexto, ACCION_TERMINAR_SUENO))
                } else {
                    addAction(0, L10n.t("Se ha despertado"), accion(contexto, ACCION_TERMINAR_SUENO))
                }
            }
            .build()

    private fun notificacionToma(contexto: Context, nombre: String, toma: Registro) =
        base(contexto, toma.inicio)
            .setContentTitle(
                "$nombre · " + when (toma.lado) {
                    LadoPecho.IZQUIERDA -> L10n.t("Pecho izquierdo")
                    LadoPecho.DERECHA -> L10n.t("Pecho derecho")
                    else -> L10n.t("Pecho")
                }
            )
            .setContentText("${L10n.t("Desde las")} ${formatoHora.format(toma.inicio)}")
            .addAction(0, L10n.t("Cambiar pecho"), accion(contexto, ACCION_CAMBIAR_PECHO))
            .addAction(0, L10n.t("Terminar"), accion(contexto, ACCION_TERMINAR_TOMA))
            .build()

    /** Lo común: silenciosa, fija, con el contador desde "inicio". */
    private fun base(contexto: Context, inicio: Instant): NotificationCompat.Builder {
        val abrirApp = PendingIntent.getActivity(
            contexto, 0,
            Intent(contexto, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(contexto, CANAL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(abrirApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setWhen(inicio.toEpochMilli())
            .setShowWhen(true)
            .setUsesChronometer(true)
            .apply {
                // Android 16: pedir que se muestre como Live Update. Se pasa
                // por el extra (Notification.EXTRA_REQUEST_PROMOTED_ONGOING)
                // para no depender de la versión de androidx; en versiones
                // anteriores simplemente se ignora.
                if (Build.VERSION.SDK_INT >= 36) {
                    extras.putBoolean("android.requestPromotedOngoing", true)
                }
            }
    }

    private fun accion(contexto: Context, accion: String): PendingIntent =
        PendingIntent.getBroadcast(
            contexto, accion.hashCode(),
            Intent(contexto, ReceptorAccionEnCurso::class.java).setAction(accion),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
}

/**
 * Los botones de NotificacionEnCurso. Hacen lo mismo que los botones de Hoy
 * sin abrir la app. Si la app estaba cerrada, NubiApplication ya ha
 * arrancado Repositorio; se espera a que termine de leer los datos.
 */
class ReceptorAccionEnCurso : BroadcastReceiver() {
    override fun onReceive(contexto: Context, intent: Intent) {
        val accion = intent.action ?: return
        val resultado = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                Repositorio.esperarCarga()
                val datos = Repositorio.local.datos.value
                val bebe = datos.bebe ?: return@launch
                val ahora = Instant.now()
                val sueno = datos.registros
                    .filter { it.bebeId == bebe.id && it.tipo.esSueno && it.fin == null }
                    .maxByOrNull { it.inicio }
                when (accion) {
                    NotificacionEnCurso.ACCION_TERMINAR_SUENO ->
                        sueno?.let { Repositorio.guardarRegistro(it.copy(fin = maxOf(ahora, it.inicio.plusSeconds(60)))) }
                    NotificacionEnCurso.ACCION_DESPERTAR_NOCTURNO ->
                        if (sueno?.tipo == TipoRegistro.NOCHE) {
                            Repositorio.guardarRegistro(
                                Registro(bebeId = bebe.id, tipo = TipoRegistro.DESPERTAR, inicio = ahora)
                            )
                        }
                    NotificacionEnCurso.ACCION_CAMBIAR_PECHO -> Repositorio.cambiarPecho(bebe.id)
                    NotificacionEnCurso.ACCION_TERMINAR_TOMA -> Repositorio.terminarToma(bebe.id)
                }
                // Lo de arriba guarda y sube en el ámbito de Repositorio (en
                // segundo plano): se deja un margen antes de soltar el
                // receptor para que el guardado local y la subida arranquen
                // aunque la app no esté abierta.
                delay(2500)
            } finally {
                resultado.finish()
            }
        }
    }
}
