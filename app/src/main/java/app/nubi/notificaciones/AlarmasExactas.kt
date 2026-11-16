package app.nubi.notificaciones

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import app.nubi.data.Repositorio

/**
 * Permiso "Alarmas y recordatorios" (Android 12+, desactivado por defecto
 * desde Android 14). Sin él los avisos llegan, pero pueden retrasarse unos
 * minutos con el móvil en reposo (ver AvisosNubi.programar).
 *
 * No se enseña ninguna tarjeta fija en Ajustes (parecía que la app fallaba):
 * se pregunta UNA vez, en el momento en que tiene sentido — cuando se activa
 * el aviso de comida (ver HojaAvisoComida).
 */
object AlarmasExactas {
    private const val PREFS = "nubi_alarmas_exactas"
    private const val CLAVE_PREGUNTADO = "preguntado"

    /** true si se mandó al usuario al ajuste y hay que mirar al volver. */
    @Volatile private var pendienteDeComprobar = false

    fun permitidas(contexto: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return contexto.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() ?: true
    }

    /** Hay que preguntar: Android 12+, sin permiso y nunca preguntado antes. */
    fun debePreguntar(contexto: Context): Boolean {
        if (permitidas(contexto)) return false
        return !contexto.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(CLAVE_PREGUNTADO, false)
    }

    fun marcarPreguntado(contexto: Context) {
        contexto.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(CLAVE_PREGUNTADO, true).apply()
    }

    /** Abre directamente el ajuste "Alarmas y recordatorios" de Nubi. */
    fun abrirAjuste(contexto: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        pendienteDeComprobar = true
        runCatching {
            contexto.startActivity(
                Intent(
                    Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    Uri.parse("package:" + contexto.packageName)
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.onFailure { pendienteDeComprobar = false }
    }

    /**
     * Desde MainActivity.onStart: si el usuario viene del ajuste y lo ha
     * activado, se reprograman los avisos ya a la hora exacta.
     */
    fun alVolver(contexto: Context) {
        if (!pendienteDeComprobar) return
        pendienteDeComprobar = false
        if (permitidas(contexto)) Repositorio.reprogramarAvisos(contexto)
    }
}
