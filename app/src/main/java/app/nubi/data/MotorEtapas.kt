package app.nubi.data

/**
 * Motor de etapas del bebé, traducido de MotorEtapas.swift (proyecto iOS).
 *
 * Solo se porta el contenido en español: la app Android todavía no tiene
 * una capa de idiomas como L10n.swift, así que estos textos van fijos aquí
 * igual que el resto de textos de la interfaz.
 *
 * El campo `cuandoConsultar` se conserva aunque la hoja de detalle (ver
 * HojaEtapa en HojasRegistro.kt) no lo muestre todavía: en iOS tampoco se
 * enseña ahí, solo queda disponible para cuando se porte algún aviso o
 * notificación que lo necesite.
 */
enum class ColorEtapa { MENTA, MELOCOTON, CIELO, LILA, MANTEQUILLA, ROSA }

data class EtapaBebe(
    val id: String,
    val titulo: String,
    val edadMinDias: Int,
    val edadMaxDias: Int,
    val porQue: String,
    val quePasara: String,
    val consejos: List<String>,
    val cuandoConsultar: String,
    val color: ColorEtapa
)

object MotorEtapas {

    val etapas: List<EtapaBebe> = listOf(
        EtapaBebe(
            id = "brote_7dias",
            titulo = "Brote de crecimiento",
            edadMinDias = 7,
            edadMaxDias = 14,
            porQue = "Entre la primera y la segunda semana muchos bebés pasan unos días con más apetito. Es una respuesta normal a su crecimiento rápido y, si toma pecho, ayuda a que tu producción de leche se ajuste a lo que necesita.",
            quePasara = "Durante 2 o 3 días pedirá comer más a menudo, a veces cada hora, le costará más calmarse y dormirá a ratos más cortos.",
            consejos = listOf(
                "Ofrécele de comer cuando muestre señales de hambre: llevarse las manos a la boca, girar la cabeza buscando o chuparse los labios. El llanto es una señal tardía.",
                "Si le das el pecho, que pida tan a menudo no significa que tengas poca leche: las tomas frecuentes son justo lo que aumenta la producción.",
                "Descansa cuando duerma y deja que otras personas se ocupen de la casa estos días.",
                "Suele pasar en 2 o 3 días."
            ),
            cuandoConsultar = "Consulta a tu pediatra si moja menos de 6 pañales al día, si duerme tanto que cuesta despertarle para comer, si tiene fiebre (38 °C o más) o si su piel o el blanco de los ojos se ven cada vez más amarillos.",
            color = ColorEtapa.MENTA
        ),
        EtapaBebe(
            id = "crisis_lactancia_3sem",
            titulo = "Segundo brote de apetito",
            edadMinDias = 18,
            edadMaxDias = 25,
            porQue = "Hacia las 3 semanas es habitual otro periodo de más apetito. Además su forma de comer está madurando: succiona con más fuerza y más rápido, y el ritmo de las tomas cambia.",
            quePasara = "Puede pedir más a menudo, agitarse mientras come o soltar y volver a agarrarse. Es fácil pensar que no le llega, pero suele ser pasajero.",
            consejos = listOf(
                "Dale de comer en un sitio tranquilo y con poca luz: a esta edad se distrae y se agobia con facilidad.",
                "Si toma pecho y se agita, prueba a cambiar de postura o de pecho.",
                "Si toma biberón, comprueba que la tetina sea la adecuada para su edad: un flujo demasiado lento o demasiado rápido también le agobia.",
                "La mejor señal de que come lo suficiente es que moja pañales y gana peso, no lo que dura cada toma."
            ),
            cuandoConsultar = "Consulta si a las 3 semanas no ha recuperado su peso de nacimiento o no gana peso, si vomita con fuerza después de las tomas o si moja menos de 6 pañales al día. Si das el pecho y tienes dolor o grietas, una matrona o asesora de lactancia puede ayudarte.",
            color = ColorEtapa.MELOCOTON
        ),
        EtapaBebe(
            id = "brote_6semanas",
            titulo = "Pico de llanto",
            edadMinDias = 35,
            edadMaxDias = 60,
            porQue = "Entre las 2 semanas y los 3–4 meses casi todos los bebés lloran más, con el máximo hacia las 6–8 semanas. Es una fase normal del desarrollo: no significa que le pase algo ni que lo estés haciendo mal.",
            quePasara = "Puede llorar más, sobre todo al final de la tarde, a veces sin motivo aparente y sin calmarse del todo aunque lo intentes todo. Va mejorando poco a poco a partir de los 3 meses.",
            consejos = listOf(
                "Repasa lo básico: hambre, pañal, calor o frío y si necesita echar un eructo.",
                "Prueba a calmarle con movimiento suave, contacto piel con piel, un sonido constante de fondo o un paseo.",
                "Si le llevas en portabebés, su cara debe quedar siempre visible y la barbilla separada del pecho para que respire bien.",
                "Si sientes que no puedes más, déjale boca arriba en su cuna y sal unos minutos a respirar, o pide relevo. Nunca le sacudas: puede causarle lesiones cerebrales graves."
            ),
            cuandoConsultar = "Acude al pediatra si el llanto viene con fiebre (38 °C o más; si tiene menos de 3 meses, consulta sin esperar), vómitos, si rechaza las tomas durante horas, si llora más cuando le tocas o le mueves, o si notas cambios en su aspecto o en su forma de comportarse.",
            color = ColorEtapa.CIELO
        ),
        EtapaBebe(
            id = "regresion_4meses",
            titulo = "Regresión del sueño",
            edadMinDias = 105,
            edadMaxDias = 135,
            porQue = "Hacia los 4 meses su sueño madura y empieza a organizarse en ciclos parecidos a los de un adulto, con despertares breves entre uno y otro. No es un retroceso: es una señal de desarrollo.",
            quePasara = "Durante unas semanas puede despertarse más por la noche, tardar más en dormirse y hacer siestas más cortas.",
            consejos = listOf(
                "Mantén una rutina corta y siempre igual antes de dormir: por ejemplo, pañal, pijama, toma y una canción.",
                "Intenta acostarle con sueño pero aún despierto: así aprende a enlazar los ciclos de sueño por su cuenta.",
                "Una habitación oscura ayuda. Si usas ruido blanco, ponlo a volumen bajo y lejos de la cuna, a unos 2 metros.",
                "Sigue acostándole boca arriba, en su cuna, sin almohadas, mantas sueltas ni peluches."
            ),
            cuandoConsultar = "Consulta si ronca de forma habitual o hace pausas al respirar mientras duerme, si los despertares vienen con fiebre, dolor o vómitos, o si el sueño no mejora después de 4–6 semanas.",
            color = ColorEtapa.LILA
        ),
        EtapaBebe(
            id = "denticion_6meses",
            titulo = "Primeros dientes",
            edadMinDias = 165,
            edadMaxDias = 210,
            porQue = "Los primeros dientes suelen asomar hacia los 6 meses, aunque es normal que salgan entre los 4 y los 12. Casi siempre aparecen primero los dos incisivos de abajo.",
            quePasara = "Puede babear más, querer morderlo todo, tener las encías hinchadas y estar más irritable o dormir peor algunos días.",
            consejos = listOf(
                "Dale un mordedor frío de la nevera, nunca congelado, o masajea sus encías con un dedo limpio.",
                "Seca la baba a menudo para evitar que se irrite la piel de la barbilla y el cuello.",
                "No uses geles para las encías con benzocaína o lidocaína, ni collares o pulseras de dentición: no son seguros para bebés.",
                "En cuanto asome el primer diente, cepíllalo dos veces al día con pasta con flúor, en una cantidad del tamaño de un grano de arroz."
            ),
            cuandoConsultar = "Los dientes no causan fiebre alta ni diarrea. Si tiene 38 °C o más, diarrea o está muy irritable varios días seguidos, consulta: probablemente sea otra cosa. Consulta también si a los 12 meses aún no le ha salido ningún diente.",
            color = ColorEtapa.MANTEQUILLA
        ),
        EtapaBebe(
            id = "separacion_8meses",
            titulo = "Ansiedad por separación",
            edadMinDias = 240,
            edadMaxDias = 300,
            porQue = "Hacia los 8 meses ya entiende que las personas siguen existiendo aunque no las vea, pero todavía no sabe que vas a volver. Por eso le angustia que te vayas. Es una señal de que su vínculo contigo es fuerte.",
            quePasara = "Puede llorar cuando sales de la habitación, extrañar a personas que no ve a menudo y despertarse más por la noche buscándote. Suele aparecer entre los 8 y los 12 meses y va cediendo a lo largo del segundo año.",
            consejos = listOf(
                "Juega a esconderte y aparecer: tápate la cara con las manos y destápala diciendo «¡aquí estoy!», o esconde un juguete bajo una manta y deja que lo encuentre. Le enseña que lo que se va, vuelve.",
                "Despídete siempre con un ritual corto y cariñoso, aunque llore. Irte a escondidas aumenta su inquietud.",
                "Dile cuándo volverás con referencias que entienda, como «después de la siesta», y cúmplelo.",
                "Practica separaciones cortas en casa y presenta a los nuevos cuidadores estando tú delante antes de dejarle a solas con ellos."
            ),
            cuandoConsultar = "Consulta si la angustia es tan intensa que le impide comer o dormir durante semanas, o si no reacciona a su nombre ni busca tu mirada.",
            color = ColorEtapa.ROSA
        )
    )

    /** La etapa que le toca al bebé ahora mismo, si hay alguna definida para su edad. */
    fun etapaActual(edadEnDias: Int): EtapaBebe? =
        etapas.firstOrNull { edadEnDias in it.edadMinDias..it.edadMaxDias }
}
