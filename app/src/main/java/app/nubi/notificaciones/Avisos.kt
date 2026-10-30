package app.nubi.notificaciones

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.nubi.MainActivity
import app.nubi.R
import app.nubi.data.Repositorio
import app.nubi.i18n.L10n
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * Avisos locales de Android: de momento, "avisar de la siguiente toma" en
 * Medicina y "avisarme la tarde de antes" en Citas (ver Repositorio.kt).
 *
 * Son alarmas del propio móvil (AlarmManager), no notificaciones por red:
 * funcionan sin conexión y aunque la app esté cerrada. No existían hasta
 * ahora — este fichero es toda la infraestructura nueva.
 */
object AvisosNubi {
    const val CANAL_ID = "nubi_avisos"
    private const val ACCION_AVISO = "app.nubi.AVISO"

    /** Se llama una vez al arrancar la app (ver Repositorio.iniciar). */
    fun crearCanal(contexto: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val gestor = contexto.getSystemService(NotificationManager::class.java) ?: return
        val canal = NotificationChannel(
            CANAL_ID,
            L10n.t("Avisos de Nubi"),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = L10n.t("Recordatorios de medicina, citas y sueño")
        }
        gestor.createNotificationChannel(canal)
    }

    fun tienePermisoNotificaciones(contexto: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            contexto, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Programa un aviso para "instante", con "id" como clave única (por
     * ejemplo "medicina_<uuid>" o "cita_<uuid>"): programar dos veces con el
     * mismo id sustituye el aviso anterior en vez de duplicarlo.
     *
     * Si "instante" ya pasó, no programa nada (y cancela lo que hubiera):
     * no tiene sentido avisar de algo que ya ocurrió.
     */
    fun programar(contexto: Context, id: String, instante: Instant, titulo: String, texto: String) {
        if (!instante.isAfter(Instant.now())) {
            cancelar(contexto, id)
            return
        }
        val gestor = contexto.getSystemService(AlarmManager::class.java) ?: return
        val pendiente = pendienteDe(contexto, id, titulo, texto)

        val puedeExacta = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            gestor.canScheduleExactAlarms()

        try {
            if (puedeExacta) {
                gestor.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, instante.toEpochMilli(), pendiente)
            } else {
                // Sin permiso de alarma exacta (poco frecuente): un aviso
                // aproximado es mejor que ninguno.
                gestor.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, instante.toEpochMilli(), pendiente)
            }
        } catch (e: SecurityException) {
            gestor.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, instante.toEpochMilli(), pendiente)
        }
    }

    fun cancelar(contexto: Context, id: String) {
        val gestor = contexto.getSystemService(AlarmManager::class.java) ?: return
        gestor.cancel(pendienteDe(contexto, id, titulo = "", texto = ""))
        NotificationManagerCompat.from(contexto).cancel(id.hashCode())
    }

    private fun pendienteDe(contexto: Context, id: String, titulo: String, texto: String): PendingIntent {
        val intent = Intent(contexto, ReceptorAviso::class.java).apply {
            action = ACCION_AVISO
            putExtra("id", id)
            putExtra("titulo", titulo)
            putExtra("texto", texto)
        }
        return PendingIntent.getBroadcast(
            contexto,
            id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

/** Salta cuando llega la hora de una alarma programada con AvisosNubi.programar. */
class ReceptorAviso : BroadcastReceiver() {
    override fun onReceive(contexto: Context, intent: Intent) {
        if (!AvisosNubi.tienePermisoNotificaciones(contexto)) return
        val id = intent.getStringExtra("id") ?: return
        val titulo = intent.getStringExtra("titulo").orEmpty().ifBlank { "Nubi" }
        val texto = intent.getStringExtra("texto").orEmpty()

        val abrirApp = Intent(contexto, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendienteApp = PendingIntent.getActivity(
            contexto,
            id.hashCode(),
            abrirApp,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificacion = NotificationCompat.Builder(contexto, AvisosNubi.CANAL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(titulo)
            .setContentText(texto)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendienteApp)
            .build()

        NotificationManagerCompat.from(contexto).notify(id.hashCode(), notificacion)
    }
}

/**
 * AlarmManager olvida todas las alarmas programadas al reiniciar el móvil:
 * este receptor espera a que Repositorio termine de leer nubi.json y vuelve
 * a montarlas todas (Repositorio.reprogramarAvisos), sin esperar a que el
 * usuario abra la app.
 */
class ReceptorArranque : BroadcastReceiver() {
    override fun onReceive(contexto: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val contextoApp = contexto.applicationContext
        val resultado = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                Repositorio.esperarCarga()
                Repositorio.reprogramarAvisos(contextoApp)
            } finally {
                resultado.finish()
            }
        }
    }
}
