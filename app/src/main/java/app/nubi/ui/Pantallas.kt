package app.nubi.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.nubi.data.Bebe
import app.nubi.data.Registro
import app.nubi.data.Repositorio
import app.nubi.data.Supabase
import app.nubi.data.TipoRegistro
import app.nubi.ui.theme.NubiGeometria
import app.nubi.ui.theme.NubiTheme
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// =====================================================================
// Canjear código — se usa en dos sitios: al entrar por primera vez y
// desde Ajustes, igual que en iOS.
// =====================================================================
@Composable
fun BloqueCanjearCodigo(
    alCanjear: () -> Unit = {}
) {
    var codigo by remember { mutableStateOf("") }
    var mensaje by remember { mutableStateOf<String?>(null) }
    var enviando by remember { mutableStateOf(false) }
    val ambito = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = codigo,
            onValueChange = { codigo = it },
            label = { Text("Código de regalo") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                ambito.launch {
                    enviando = true
                    mensaje = try {
                        Supabase.client.postgrest.rpc(
                            "canjear_codigo_regalo",
                            buildJsonObject { put("p_codigo", codigo.trim()) }
                        )
                        alCanjear()
                        "¡Listo! Premium activado."
                    } catch (e: Exception) {
                        // El servidor lanza 'codigo_invalido' o 'codigo_ya_usado';
                        // se traducen para que el mensaje signifique algo.
                        when {
                            e.message?.contains("codigo_ya_usado") == true ->
                                "Ese código ya se había usado."
                            e.message?.contains("codigo_invalido") == true ->
                                "No encontramos ese código. Revisa que esté bien escrito."
                            else -> "No se pudo canjear ahora mismo. Inténtalo en un momento."
                        }
                    }
                    enviando = false
                }
            },
            enabled = codigo.isNotBlank() && !enviando,
            shape = RoundedCornerShape(NubiGeometria.radioChico),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (enviando) "Canjeando…" else "Canjear")
        }
        mensaje?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = MaterialTheme.typography.bodySmall)
        }
    }
}

// =====================================================================
// Primera entrada: canjear código o saltar
// =====================================================================
@Composable
fun PantallaBienvenida(alContinuar: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(NubiGeometria.margen),
        verticalArrangement = Arrangement.Center
    ) {
        Text("Nubi", style = MaterialTheme.typography.displayMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "Si te han regalado un código, puedes canjearlo ahora.",
            style = MaterialTheme.typography.bodyMedium,
            color = NubiTheme.colores.tintaSuave
        )
        Spacer(Modifier.height(24.dp))

        BloqueCanjearCodigo(alCanjear = alContinuar)

        Spacer(Modifier.height(16.dp))
        TextButton(
            onClick = alContinuar,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Saltar")
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Tienes 7 días de prueba. Siempre puedes canjear un código más tarde desde Ajustes.",
            style = MaterialTheme.typography.bodySmall,
            color = NubiTheme.colores.tintaTenue,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// =====================================================================
// Alta del bebé — sin esto no hay nada que mostrar
// =====================================================================
@Composable
fun PantallaNuevoBebe(alGuardar: () -> Unit) {
    var nombre by remember { mutableStateOf("") }
    var dia by remember { mutableStateOf("") }
    var mes by remember { mutableStateOf("") }
    var anio by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(NubiGeometria.margen),
        verticalArrangement = Arrangement.Center
    ) {
        Text("¿Cómo se llama?", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = nombre,
            onValueChange = { nombre = it },
            label = { Text("Nombre del bebé") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(20.dp))
        Text("Fecha de nacimiento", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = dia, onValueChange = { dia = it.filter(Char::isDigit).take(2) },
                label = { Text("Día") }, singleLine = true, modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(
                value = mes, onValueChange = { mes = it.filter(Char::isDigit).take(2) },
                label = { Text("Mes") }, singleLine = true, modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(
                value = anio, onValueChange = { anio = it.filter(Char::isDigit).take(4) },
                label = { Text("Año") }, singleLine = true, modifier = Modifier.weight(1.4f)
            )
        }

        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = {
                val fecha = runCatching {
                    LocalDate.of(anio.toInt(), mes.toInt(), dia.toInt())
                }.getOrNull()

                when {
                    nombre.isBlank() -> error = "Pon el nombre del bebé."
                    fecha == null -> error = "Esa fecha no existe. Revísala."
                    fecha.isAfter(LocalDate.now()) -> error = "La fecha no puede ser futura."
                    else -> {
                        Repositorio.guardarBebe(
                            Bebe(
                                nombre = nombre.trim(),
                                fechaNacimiento = fecha.atTime(LocalTime.NOON)
                                    .atZone(ZoneId.systemDefault()).toInstant()
                            )
                        )
                        alGuardar()
                    }
                }
            },
            shape = RoundedCornerShape(NubiGeometria.radioChico),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Empezar")
        }
    }
}

// =====================================================================
// Pestaña Hoy — resumen del día.
//
// Aquí NO se registra nada: en iOS (VistaInicio.swift) esta pantalla son
// tarjetas de resumen y la etapa del bebé. Los botones de apuntar viven
// en la pestaña Día.
// =====================================================================
private val formatoHora: DateTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())

private fun registrosDeHoy(registros: List<Registro>): List<Registro> {
    val hoy = LocalDate.now(ZoneId.systemDefault())
    return registros
        .filter { it.inicio.atZone(ZoneId.systemDefault()).toLocalDate() == hoy }
        .sortedByDescending { it.inicio }
}

@Composable
fun PantallaHoy(bebe: Bebe, registros: List<Registro>) {
    val deHoy = registrosDeHoy(registros)

    val minutosSueno = deHoy
        .filter { it.tipo.esSueno }
        .sumOf { (it.duracionSegundos ?: 0L) / 60 }
    val tomas = deHoy.count { it.tipo.esAlimentacion }
    val panales = deHoy.count { it.tipo == TipoRegistro.PANAL }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(NubiGeometria.margen)
    ) {
        Text(bebe.nombre, style = MaterialTheme.typography.displaySmall)
        Text(
            textoEdad(bebe),
            style = MaterialTheme.typography.bodyMedium,
            color = NubiTheme.colores.tintaSuave
        )

        Spacer(Modifier.height(24.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            TarjetaResumen("Sueño", textoDuracion(minutosSueno),
                NubiTheme.colores.lila, Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            TarjetaResumen("Tomas", "$tomas",
                NubiTheme.colores.melocoton, Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            TarjetaResumen("Pañales", "$panales",
                NubiTheme.colores.cielo, Modifier.weight(1f))
        }

        Spacer(Modifier.height(24.dp))
        Card(
            shape = RoundedCornerShape(NubiGeometria.radio),
            colors = CardDefaults.cardColors(containerColor = NubiTheme.colores.superficie),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Etapa", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Pendiente de portar desde iOS (MotorEtapas.swift).",
                    style = MaterialTheme.typography.bodySmall,
                    color = NubiTheme.colores.tintaTenue
                )
            }
        }
    }
}

@Composable
private fun TarjetaResumen(
    titulo: String,
    valor: String,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(NubiGeometria.radioChico),
        colors = CardDefaults.cardColors(containerColor = color),
        modifier = modifier
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(titulo, style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(4.dp))
            Text(valor, style = MaterialTheme.typography.titleLarge)
        }
    }
}

private fun textoDuracion(minutos: Long): String = when {
    minutos <= 0L -> "—"
    minutos < 60L -> "$minutos min"
    minutos % 60L == 0L -> "${minutos / 60} h"
    else -> "${minutos / 60} h ${minutos % 60} min"
}

// =====================================================================
// Pestaña Día — aquí sí se apunta.
//
// Versión mínima de VistaLinea.swift (1.698 líneas): la botonera rápida y
// lo apuntado hoy. Faltan la línea de tiempo vertical, la barra de sueño
// de 24 h y las hojas de edición con hora.
// =====================================================================
@Composable
fun PantallaDia(bebe: Bebe, registros: List<Registro>) {
    val deHoy = registrosDeHoy(registros)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(NubiGeometria.margen)
    ) {
        Text("Apuntar", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            BotonRapido("Siesta", TipoRegistro.SIESTA, bebe.id, Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            BotonRapido("Noche", TipoRegistro.NOCHE, bebe.id, Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            BotonRapido("Biberón", TipoRegistro.BIBERON, bebe.id, Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            BotonRapido("Pecho", TipoRegistro.PECHO, bebe.id, Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            BotonRapido("Pañal", TipoRegistro.PANAL, bebe.id, Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            BotonRapido("Baño", TipoRegistro.BANO, bebe.id, Modifier.weight(1f))
        }

        Spacer(Modifier.height(24.dp))
        Text("Hoy", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        if (deHoy.isEmpty()) {
            Text(
                "Todavía no hay nada apuntado hoy.",
                style = MaterialTheme.typography.bodyMedium,
                color = NubiTheme.colores.tintaTenue
            )
        } else {
            LazyColumn {
                items(deHoy, key = { it.id }) { registro ->
                    Card(
                        shape = RoundedCornerShape(NubiGeometria.radioChico),
                        colors = CardDefaults.cardColors(
                            containerColor = NubiTheme.colores.superficie
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                nombreTipo(registro.tipo),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                formatoHora.format(registro.inicio),
                                style = MaterialTheme.typography.bodyMedium,
                                color = NubiTheme.colores.tintaSuave
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BotonRapido(
    texto: String,
    tipo: TipoRegistro,
    bebeId: String,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = {
            Repositorio.guardarRegistro(
                Registro(bebeId = bebeId, tipo = tipo, inicio = Instant.now())
            )
        },
        shape = RoundedCornerShape(NubiGeometria.radioChico),
        modifier = modifier
    ) {
        Text(texto, style = MaterialTheme.typography.labelLarge)
    }
}

private fun nombreTipo(tipo: TipoRegistro): String = when (tipo) {
    TipoRegistro.SIESTA -> "Siesta"
    TipoRegistro.NOCHE -> "Sueño nocturno"
    TipoRegistro.BIBERON -> "Biberón"
    TipoRegistro.PECHO -> "Pecho"
    TipoRegistro.PANAL -> "Pañal"
    TipoRegistro.DESPERTAR -> "Despertar"
    TipoRegistro.MEDICINA -> "Medicina"
    TipoRegistro.BANO -> "Baño"
}

private fun textoEdad(bebe: Bebe): String {
    val dias = bebe.edadEnDias()
    return when {
        dias < 14 -> if (dias == 1) "1 día" else "$dias días"
        dias < 61 -> "${dias / 7} semanas"
        else -> "${(dias / 30.44).toInt()} meses"
    }
}

// =====================================================================
// Pestañas todavía por portar
// =====================================================================
@Composable
fun PantallaEnObras(nombre: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(nombre, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(
                "Pendiente de portar desde iOS.",
                style = MaterialTheme.typography.bodyMedium,
                color = NubiTheme.colores.tintaTenue
            )
        }
    }
}

// =====================================================================
// Ajustes
// =====================================================================
@Composable
fun PantallaAjustes(bebe: Bebe?, alCerrarSesion: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(NubiGeometria.margen)
    ) {
        Text("Ajustes", style = MaterialTheme.typography.headlineMedium)

        bebe?.let {
            Spacer(Modifier.height(20.dp))
            Text("Bebé", style = MaterialTheme.typography.titleMedium)
            Text(
                "${it.nombre} · ${textoEdad(it)}",
                style = MaterialTheme.typography.bodyMedium,
                color = NubiTheme.colores.tintaSuave
            )
        }

        Spacer(Modifier.height(28.dp))
        Text("Canjear código de regalo", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        BloqueCanjearCodigo()

        Spacer(Modifier.height(32.dp))
        OutlinedButton(
            onClick = alCerrarSesion,
            shape = RoundedCornerShape(NubiGeometria.radioChico),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Cerrar sesión")
        }
    }
}
