package app.nubi.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import app.nubi.MainActivity
import app.nubi.R
import app.nubi.i18n.L10n
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * El ruido blanco/rosa/marrón dentro de un servicio en primer plano
 * (tipo mediaPlayback), con su notificación fija y un botón "Parar" — lo
 * que exige Android para que un audio siga sonando con la pantalla apagada.
 *
 * Antes MotorRuido sonaba desde la propia pantalla, sin servicio: con la
 * pantalla apagada, Android (sobre todo con el ahorro de batería de Xiaomi,
 * Samsung…) podía matar el proceso de madrugada y el ruido se cortaba.
 *
 * El temporizador (30 min / 1 h) también vive aquí, no en la pantalla: así
 * se cumple aunque la pantalla de Sonidos ya no exista.
 */
class ServicioRuido : Service() {

    private val manejador = Handler(Looper.getMainLooper())
    private val pararAlTerminar = Runnable { pararTodo() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACCION_PARAR -> {
                pararTodo()
                return START_NOT_STICKY
            }
            else -> {
                val tipo = intent?.getStringExtra(EXTRA_TIPO)
                    ?.let { runCatching { TipoRuido.valueOf(it) }.getOrNull() }
                    ?: TipoRuido.BLANCO
                val duracionMs = intent?.getLongExtra(EXTRA_DURACION_MS, -1L)?.takeIf { it > 0 }

                crearCanal(this)
                val notificacion = notificacion(tipo)
                ServiceCompat.startForeground(
                    this,
                    ID_NOTIFICACION,
                    notificacion,
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                    else 0
                )

                MotorRuido.reproducir(tipo)
                _sonando.value = true

                manejador.removeCallbacks(pararAlTerminar)
                if (duracionMs != null) manejador.postDelayed(pararAlTerminar, duracionMs)
            }
        }
        // Si el sistema mata el servicio, no se relanza solo: un ruido que
        // vuelve a sonar de repente a las tantas no es lo que nadie quiere.
        return START_NOT_STICKY
    }

    private fun pararTodo() {
        manejador.removeCallbacks(pararAlTerminar)
        MotorRuido.detener()
        _sonando.value = false
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        manejador.removeCallbacks(pararAlTerminar)
        MotorRuido.detener()
        _sonando.value = false
        super.onDestroy()
    }

    private fun notificacion(tipo: TipoRuido): Notification {
        val abrirApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val parar = PendingIntent.getService(
            this, 1,
            Intent(this, ServicioRuido::class.java).setAction(ACCION_PARAR),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val nombre = when (tipo) {
            TipoRuido.BLANCO -> L10n.t("Ruido blanco")
            TipoRuido.ROSA -> L10n.t("Ruido rosa")
            TipoRuido.MARRON -> L10n.t("Ruido marrón")
        }
        return NotificationCompat.Builder(this, CANAL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Nubi · $nombre")
            .setContentText(L10n.t("Sonando"))
            .setContentIntent(abrirApp)
            .addAction(0, L10n.t("Parar"), parar)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    companion object {
        private const val CANAL_ID = "nubi_sonidos"
        private const val ID_NOTIFICACION = 4207
        private const val ACCION_PARAR = "app.nubi.ruido.PARAR"
        private const val EXTRA_TIPO = "tipo"
        private const val EXTRA_DURACION_MS = "duracion_ms"

        private val _sonando = MutableStateFlow(false)
        /** Para que la pantalla de Sonidos pinte Play/Pausa según la realidad
         *  (p. ej. si se paró desde la notificación o terminó el temporizador). */
        val sonando: StateFlow<Boolean> = _sonando.asStateFlow()

        /** Empieza (o cambia de tipo / reinicia el temporizador). duracionMs
         *  null = sin límite. */
        fun iniciar(contexto: Context, tipo: TipoRuido, duracionMs: Long?) {
            val intent = Intent(contexto, ServicioRuido::class.java)
                .putExtra(EXTRA_TIPO, tipo.name)
                .putExtra(EXTRA_DURACION_MS, duracionMs ?: -1L)
            ContextCompat.startForegroundService(contexto, intent)
        }

        fun detener(contexto: Context) {
            if (!_sonando.value) return
            contexto.startService(Intent(contexto, ServicioRuido::class.java).setAction(ACCION_PARAR))
        }

        private fun crearCanal(contexto: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val gestor = contexto.getSystemService(NotificationManager::class.java) ?: return
            if (gestor.getNotificationChannel(CANAL_ID) != null) return
            gestor.createNotificationChannel(
                NotificationChannel(CANAL_ID, L10n.t("Sonidos para dormir"), NotificationManager.IMPORTANCE_LOW)
                    .apply { setShowBadge(false) }
            )
        }
    }
}
