package app.nubi.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * Modelo de datos de Nubi, traducido de Modelos.swift (proyecto iOS).
 *
 * Los nombres que se serializan (@SerialName) son EXACTAMENTE los rawValue
 * de los enum de Swift, con sus tildes y mayúsculas, y coinciden con los
 * CHECK de las tablas de Supabase. No los cambies por su cuenta: son el
 * contrato entre las dos plataformas y la base de datos.
 */

// ---------------------------------------------------------------------
// Fechas
//
// Se usa java.time.Instant (disponible desde API 26, que es nuestro minSdk)
// con un serializador propio en ISO-8601. Así no hace falta añadir ninguna
// librería de fechas, y el formato es el que entiende Postgres para
// timestamptz.
// ---------------------------------------------------------------------
object InstantSerializer : KSerializer<Instant> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("Instant", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Instant) {
        encoder.encodeString(DateTimeFormatter.ISO_INSTANT.format(value))
    }

    override fun deserialize(decoder: Decoder): Instant {
        // Postgres devuelve "2026-10-21T16:18:14.374732+00:00" y nosotros
        // escribimos "...Z": OffsetDateTime entiende las dos formas.
        return OffsetDateTime.parse(decoder.decodeString()).toInstant()
    }
}

typealias Fecha = @Serializable(with = InstantSerializer::class) Instant

fun nuevoId(): String = UUID.randomUUID().toString()

// ---------------------------------------------------------------------
// Bebé
// ---------------------------------------------------------------------
@Serializable
data class Bebe(
    val id: String = nuevoId(),
    val nombre: String,
    @SerialName("fecha_nacimiento") val fechaNacimiento: Fecha,
    @SerialName("semanas_prematuro") val semanasPrematuro: Int = 0,
    @SerialName("creado_en") val creadoEn: Fecha = Instant.now(),
    @SerialName("actualizado_en") val actualizadoEn: Fecha = Instant.now()
) {
    /** Edad corregida por prematuridad, igual que en iOS. */
    fun edadEnDias(ahora: Instant = Instant.now()): Int {
        val bruta = java.time.Duration.between(fechaNacimiento, ahora).toDays().toInt()
        return maxOf(0, bruta - semanasPrematuro * 7)
    }

    fun edadEnMeses(ahora: Instant = Instant.now()): Double = edadEnDias(ahora) / 30.44
}

// ---------------------------------------------------------------------
// Enumerados
// ---------------------------------------------------------------------
@Serializable
enum class UnidadBiberon {
    @SerialName("ml") ML,
    @SerialName("oz") OZ;

    val etiqueta: String get() = if (this == ML) "ml" else "oz"

    /** Factor de conversión a mililitros. */
    val factor: Double get() = if (this == ML) 1.0 else 29.5735
}

@Serializable
enum class LadoPecho {
    @SerialName("izquierda") IZQUIERDA,
    @SerialName("derecha") DERECHA
}

@Serializable
enum class TipoRegistro {
    @SerialName("siesta") SIESTA,
    @SerialName("noche") NOCHE,
    @SerialName("biberon") BIBERON,
    @SerialName("pecho") PECHO,
    @SerialName("panal") PANAL,
    @SerialName("despertar") DESPERTAR,
    @SerialName("medicina") MEDICINA,
    @SerialName("baño") BANO;

    /** Tiene principio y fin, no es un instante suelto. */
    val esIntervalo: Boolean get() = this == SIESTA || this == NOCHE || this == DESPERTAR
    val esSueno: Boolean get() = this == SIESTA || this == NOCHE
    /** El despertar descuenta tiempo de sueño en vez de sumarlo. */
    val restaSueno: Boolean get() = this == DESPERTAR
    val esAlimentacion: Boolean get() = this == BIBERON || this == PECHO
}

@Serializable
enum class LugarSueno {
    @SerialName("brazos") BRAZOS,
    @SerialName("pecho") PECHO,
    @SerialName("biberon") BIBERON,
    @SerialName("cuna") CUNA,
    @SerialName("carrito") CARRITO,
    @SerialName("coche") COCHE
}

@Serializable
enum class TipoLeche {
    @SerialName("materna") MATERNA,
    @SerialName("formula") FORMULA
}

@Serializable
enum class TipoCita {
    @SerialName("pediatra") PEDIATRA,
    @SerialName("enfermeria") ENFERMERIA,
    @SerialName("otra") OTRA
}

@Serializable
enum class AlmacenamientoLeche {
    // Con tilde y mayúscula a propósito: es el rawValue que iOS ya tiene
    // escrito en el JSON de quien usa la app desde antes.
    @SerialName("Frigorífico") FRIGORIFICO,
    @SerialName("Congelador") CONGELADOR;

    val duracionDias: Int get() = if (this == FRIGORIFICO) 4 else 180
}

@Serializable
enum class CategoriaAlimento {
    @SerialName("Fruta") FRUTA,
    @SerialName("Verdura") VERDURA,
    @SerialName("Proteína") PROTEINA,
    @SerialName("Cereal") CEREAL,
    @SerialName("Lácteo") LACTEO,
    @SerialName("Otro") OTRO
}

@Serializable
enum class GustoComida {
    @SerialName("leGusto") LE_GUSTO,
    @SerialName("noLeGusto") NO_LE_GUSTO
}

// ---------------------------------------------------------------------
// Registros y demás entidades
// ---------------------------------------------------------------------
@Serializable
data class Registro(
    val id: String = nuevoId(),
    @SerialName("bebe_id") val bebeId: String,
    val tipo: TipoRegistro,
    val inicio: Fecha,
    val fin: Fecha? = null,
    val nota: String = "",
    val lado: LadoPecho? = null,
    @SerialName("cantidad_biberon") val cantidadBiberon: Double? = null,
    @SerialName("unidad_biberon") val unidadBiberon: UnidadBiberon? = null,
    @SerialName("panal_pis") val panalPis: Boolean? = null,
    @SerialName("panal_caca") val panalCaca: Boolean? = null,
    @SerialName("tipo_leche") val tipoLeche: TipoLeche? = null,
    @SerialName("bano_contento") val banoContento: Boolean? = null,
    @SerialName("lugar_sueno") val lugarSueno: LugarSueno? = null,
    @SerialName("actualizado_en") val actualizadoEn: Fecha = Instant.now()
) {
    /** Empezado y sin terminar: una siesta en curso, un pecho en marcha. */
    val enCurso: Boolean
        get() = fin == null && (tipo.esIntervalo || tipo == TipoRegistro.PECHO)

    val duracionSegundos: Long?
        get() = fin?.let { java.time.Duration.between(inicio, it).seconds }
}

@Serializable
data class Medicina(
    val id: String = nuevoId(),
    @SerialName("bebe_id") val bebeId: String,
    @SerialName("fecha_toma") val fechaToma: Fecha,
    val nombre: String = "",
    val nota: String = "",
    @SerialName("hora_siguiente") val horaSiguiente: Fecha? = null,
    @SerialName("actualizado_en") val actualizadoEn: Fecha = Instant.now()
)

@Serializable
data class Medida(
    val id: String = nuevoId(),
    @SerialName("bebe_id") val bebeId: String,
    val fecha: Fecha,
    @SerialName("peso_kg") val pesoKg: Double? = null,
    @SerialName("altura_cm") val alturaCm: Double? = null,
    @SerialName("perimetro_cm") val perimetroCm: Double? = null,
    val nota: String = "",
    @SerialName("actualizado_en") val actualizadoEn: Fecha = Instant.now()
) {
    val vacia: Boolean get() = pesoKg == null && alturaCm == null && perimetroCm == null
}

@Serializable
data class Cita(
    val id: String = nuevoId(),
    @SerialName("bebe_id") val bebeId: String,
    val tipo: TipoCita = TipoCita.PEDIATRA,
    val titulo: String = "",
    val fecha: Fecha,
    val lugar: String = "",
    val nota: String = "",
    val recordar: Boolean = true,
    @SerialName("actualizado_en") val actualizadoEn: Fecha = Instant.now()
) {
    fun pasada(ahora: Instant = Instant.now()): Boolean = fecha.isBefore(ahora)
}

@Serializable
data class EstadoVacuna(
    @SerialName("bebe_id") val bebeId: String,
    val clave: String,
    val puesta: Boolean = false,
    val fecha: Fecha? = null,
    val nota: String = "",
    @SerialName("actualizado_en") val actualizadoEn: Fecha = Instant.now()
)

@Serializable
data class GrupoVacunas(
    val id: String = nuevoId(),
    @SerialName("bebe_id") val bebeId: String,
    val etiqueta: String,
    val meses: Double,
    @SerialName("actualizado_en") val actualizadoEn: Fecha = Instant.now()
)

@Serializable
data class VacunaPropia(
    val id: String = nuevoId(),
    @SerialName("grupo_id") val grupoId: String,
    val nombre: String,
    val puesta: Boolean = false,
    val fecha: Fecha? = null,
    @SerialName("actualizado_en") val actualizadoEn: Fecha = Instant.now()
)

@Serializable
data class EntradaDiario(
    val id: String = nuevoId(),
    @SerialName("bebe_id") val bebeId: String,
    val fecha: Fecha,
    val titulo: String = "",
    val texto: String = "",
    val hito: Boolean = false,
    @SerialName("actualizado_en") val actualizadoEn: Fecha = Instant.now()
)

@Serializable
data class ExtraccionLeche(
    val id: String = nuevoId(),
    @SerialName("bebe_id") val bebeId: String,
    val fecha: Fecha,
    @SerialName("cantidad_ml") val cantidadMl: Double,
    val almacenamiento: AlmacenamientoLeche = AlmacenamientoLeche.CONGELADOR,
    val nota: String = "",
    @SerialName("actualizado_en") val actualizadoEn: Fecha = Instant.now()
) {
    val caducidad: Instant
        get() = fecha.plus(java.time.Duration.ofDays(almacenamiento.duracionDias.toLong()))
}

@Serializable
data class UsoLeche(
    val id: String = nuevoId(),
    @SerialName("bebe_id") val bebeId: String,
    val fecha: Fecha,
    @SerialName("cantidad_ml") val cantidadMl: Double,
    val nota: String = "",
    @SerialName("actualizado_en") val actualizadoEn: Fecha = Instant.now()
)

@Serializable
data class ComidaComplementaria(
    val id: String = nuevoId(),
    @SerialName("bebe_id") val bebeId: String,
    val fecha: Fecha,
    val nombre: String = "",
    val categoria: CategoriaAlimento = CategoriaAlimento.OTRO,
    val gusto: GustoComida? = null,
    val nota: String = "",
    /**
     * En iOS las fotos son los bytes del JPEG dentro del propio objeto.
     * Aquí son rutas: en local, ficheros del móvil; en Supabase, rutas
     * dentro del bucket de Storage. Máximo 2, como en iOS.
     */
    val fotos: List<String> = emptyList(),
    @SerialName("actualizado_en") val actualizadoEn: Fecha = Instant.now()
)

@Serializable
data class Enfermedad(
    val id: String = nuevoId(),
    @SerialName("bebe_id") val bebeId: String,
    val nombre: String = "",
    @SerialName("fecha_inicio") val fechaInicio: Fecha,
    @SerialName("fecha_fin") val fechaFin: Fecha? = null,
    val medicacion: String = "",
    val notas: String = "",
    @SerialName("actualizado_en") val actualizadoEn: Fecha = Instant.now()
) {
    val activa: Boolean get() = fechaFin == null
}
