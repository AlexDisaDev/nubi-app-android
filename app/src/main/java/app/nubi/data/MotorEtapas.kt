package app.nubi.data

import app.nubi.i18n.IdiomaNubi
import app.nubi.i18n.L10n

/**
 * Motor de etapas del bebé, traducido de MotorEtapas.swift (proyecto iOS).
 *
 * TRADUCCIÓN: aquí mismo, en los cuatro idiomas, y no en la tabla de L10n
 * — exactamente igual que en iOS. Son textos largos de salud y conviene
 * revisarlos juntos (un pediatra o un traductor ve la etapa entera de un
 * vistazo); además, en la tabla un solo carácter distinto entre la clave y
 * el código hacía que cayera al español sin avisar.
 *
 * [etapas] es una propiedad calculada (en iOS, `static var`) para que el
 * texto cambie al cambiar de idioma: leerla desde un composable suscribe
 * la recomposición, porque [L10n.idiomaEfectivo] lee un `mutableStateOf`.
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

    private fun t(es: String, en: String, it: String, fr: String): String =
        when (L10n.idiomaEfectivo) {
            IdiomaNubi.INGLES -> en
            IdiomaNubi.ITALIANO -> it
            IdiomaNubi.FRANCES -> fr
            else -> es
        }

    val etapas: List<EtapaBebe>
        get() = listOf(

            // 1–2 semanas
            EtapaBebe(
                id = "brote_7dias",
                titulo = t(
                    "Brote de crecimiento", "Growth spurt",
                    "Scatto di crescita", "Pic de croissance"
                ),
                edadMinDias = 7,
                edadMaxDias = 14,
                porQue = t(
                    "Entre la primera y la segunda semana muchos bebés pasan unos días con más apetito. Es una respuesta normal a su crecimiento rápido y, si toma pecho, ayuda a que tu producción de leche se ajuste a lo que necesita.",
                    "Between the first and second week many babies go through a few days of increased appetite. It's a normal response to rapid growth and, if you're breastfeeding, it helps your milk supply adjust to what they need.",
                    "Tra la prima e la seconda settimana molti bebè attraversano qualche giorno di appetito maggiore. È una risposta normale alla crescita rapida e, se allatti al seno, aiuta la tua produzione di latte ad adattarsi a ciò di cui ha bisogno.",
                    "Entre la première et la deuxième semaine, beaucoup de bébés traversent quelques jours d'appétit accru. C'est une réponse normale à leur croissance rapide et, si vous allaitez, cela aide votre production de lait à s'ajuster à ses besoins."
                ),
                quePasara = t(
                    "Durante 2 o 3 días pedirá comer más a menudo, a veces cada hora, le costará más calmarse y dormirá a ratos más cortos.",
                    "For 2 or 3 days they'll want to feed more often, sometimes every hour, will be harder to settle and will sleep in shorter stretches.",
                    "Per 2 o 3 giorni chiederà di mangiare più spesso, a volte ogni ora, farà più fatica a calmarsi e dormirà a tratti più brevi.",
                    "Pendant 2 ou 3 jours, votre bébé voudra manger plus souvent, parfois toutes les heures, aura plus de mal à se calmer et dormira par périodes plus courtes."
                ),
                consejos = listOf(
                    t(
                        "Ofrécele de comer cuando muestre señales de hambre: llevarse las manos a la boca, girar la cabeza buscando o chuparse los labios. El llanto es una señal tardía.",
                        "Feed when you see hunger cues: hands to mouth, turning the head to search, or lip smacking. Crying is a late sign.",
                        "Offri da mangiare quando mostra segnali di fame: mani alla bocca, girare la testa cercando, schioccare le labbra. Il pianto è un segnale tardivo.",
                        "Proposez à manger dès les signes de faim : mains à la bouche, tête qui tourne pour chercher, bruits de succion. Les pleurs sont un signe tardif."
                    ),
                    t(
                        "Si le das el pecho, que pida tan a menudo no significa que tengas poca leche: las tomas frecuentes son justo lo que aumenta la producción.",
                        "If you're breastfeeding, frequent feeding doesn't mean you have too little milk: frequent feeds are exactly what increases supply.",
                        "Se allatti al seno, che chieda così spesso non vuol dire che hai poco latte: sono proprio le poppate frequenti ad aumentare la produzione.",
                        "Si vous allaitez, des tétées fréquentes ne veulent pas dire que vous manquez de lait : ce sont justement elles qui augmentent la production."
                    ),
                    t(
                        "Descansa cuando duerma y deja que otras personas se ocupen de la casa estos días.",
                        "Rest when they sleep and let other people take care of the house for a few days.",
                        "Riposa quando dorme e lascia che altre persone si occupino della casa in questi giorni.",
                        "Reposez-vous quand votre bébé dort et laissez d'autres personnes s'occuper de la maison ces jours-ci."
                    ),
                    t(
                        "Suele pasar en 2 o 3 días.",
                        "It usually passes within 2 or 3 days.",
                        "Di solito passa in 2 o 3 giorni.",
                        "Cela passe généralement en 2 ou 3 jours."
                    )
                ),
                cuandoConsultar = t(
                    "Consulta a tu pediatra si moja menos de 6 pañales al día, si duerme tanto que cuesta despertarle para comer, si tiene fiebre (38 °C o más) o si su piel o el blanco de los ojos se ven cada vez más amarillos.",
                    "See your pediatrician if they wet fewer than 6 diapers a day, are so sleepy it's hard to wake them for feeds, have a fever (38 °C / 100.4 °F or higher), or if their skin or the whites of their eyes look increasingly yellow.",
                    "Consulta il pediatra se bagna meno di 6 pannolini al giorno, se dorme così tanto che fatica a svegliarsi per mangiare, se ha febbre (38 °C o più) o se la pelle o il bianco degli occhi diventano sempre più gialli.",
                    "Consultez votre pédiatre si votre bébé mouille moins de 6 couches par jour, s'il dort tellement qu'il est difficile de le réveiller pour manger, s'il a de la fièvre (38 °C ou plus) ou si sa peau ou le blanc de ses yeux deviennent de plus en plus jaunes."
                ),
                color = ColorEtapa.MENTA
            ),

            // 3 semanas
            EtapaBebe(
                id = "crisis_lactancia_3sem",
                titulo = t(
                    "Segundo brote de apetito", "Second appetite spurt",
                    "Secondo picco di appetito", "Deuxième pic d'appétit"
                ),
                edadMinDias = 18,
                edadMaxDias = 25,
                porQue = t(
                    "Hacia las 3 semanas es habitual otro periodo de más apetito. Además su forma de comer está madurando: succiona con más fuerza y más rápido, y el ritmo de las tomas cambia.",
                    "Around 3 weeks another period of increased appetite is common. Their way of feeding is also maturing: they suck harder and faster, and the rhythm of feeds changes.",
                    "Verso le 3 settimane è frequente un altro periodo di maggiore appetito. Inoltre il suo modo di mangiare sta maturando: succhia con più forza e più velocemente, e il ritmo delle poppate cambia.",
                    "Vers 3 semaines, une nouvelle période d'appétit accru est fréquente. Sa façon de téter mûrit aussi : votre bébé tète plus fort et plus vite, et le rythme des repas change."
                ),
                quePasara = t(
                    "Puede pedir más a menudo, agitarse mientras come o soltar y volver a agarrarse. Es fácil pensar que no le llega, pero suele ser pasajero.",
                    "They may ask to feed more often, get fussy while feeding, or come off and latch back on. It's easy to think they're not getting enough, but it usually passes.",
                    "Può chiedere più spesso, agitarsi mentre mangia o staccarsi e riattaccarsi. È facile pensare che il latte non basti, ma di solito è passeggero.",
                    "Votre bébé peut réclamer plus souvent, s'agiter pendant le repas ou lâcher le sein ou la tétine puis la reprendre. On croit facilement qu'il n'a pas assez, mais c'est généralement passager."
                ),
                consejos = listOf(
                    t(
                        "Dale de comer en un sitio tranquilo y con poca luz: a esta edad se distrae y se agobia con facilidad.",
                        "Feed in a quiet, dimly lit place: at this age they get distracted and overwhelmed easily.",
                        "Offri il pasto in un luogo tranquillo e con poca luce: a questa età si distrae e si agita facilmente.",
                        "Donnez à manger dans un endroit calme et peu éclairé : à cet âge, votre bébé se laisse facilement distraire et submerger."
                    ),
                    t(
                        "Si toma pecho y se agita, prueba a cambiar de postura o de pecho.",
                        "If breastfeeding and they get fussy, try changing position or breast.",
                        "Se allatti al seno e si agita, prova a cambiare posizione o seno.",
                        "Si vous allaitez et qu'il s'agite, essayez de changer de position ou de sein."
                    ),
                    t(
                        "Si toma biberón, comprueba que la tetina sea la adecuada para su edad: un flujo demasiado lento o demasiado rápido también le agobia.",
                        "If bottle-feeding, check the nipple is right for their age: a flow that's too slow or too fast is also upsetting.",
                        "Se usa il biberon, controlla che la tettarella sia adatta alla sua età: anche un flusso troppo lento o troppo veloce crea disagio.",
                        "Au biberon, vérifiez que la tétine est adaptée à son âge : un débit trop lent ou trop rapide le gêne aussi."
                    ),
                    t(
                        "La mejor señal de que come lo suficiente es que moja pañales y gana peso, no lo que dura cada toma.",
                        "The best sign they're getting enough is wet diapers and weight gain, not how long each feed lasts.",
                        "Il segnale migliore che mangia abbastanza è che bagna i pannolini e cresce di peso, non quanto dura ogni poppata.",
                        "Le meilleur signe qu'il mange assez, ce sont des couches mouillées et une prise de poids, pas la durée de chaque repas."
                    )
                ),
                cuandoConsultar = t(
                    "Consulta si a las 3 semanas no ha recuperado su peso de nacimiento o no gana peso, si vomita con fuerza después de las tomas o si moja menos de 6 pañales al día. Si das el pecho y tienes dolor o grietas, una matrona o asesora de lactancia puede ayudarte.",
                    "See your pediatrician if by 3 weeks they haven't regained their birth weight or aren't gaining, if they vomit forcefully after feeds, or if they wet fewer than 6 diapers a day. If breastfeeding hurts or your nipples are cracked, a midwife or lactation consultant can help.",
                    "Consulta il pediatra se a 3 settimane non ha recuperato il peso della nascita o non cresce, se vomita con forza dopo le poppate o se bagna meno di 6 pannolini al giorno. Se allatti e senti dolore o hai ragadi, un'ostetrica o una consulente per l'allattamento può aiutarti.",
                    "Consultez si, à 3 semaines, votre bébé n'a pas retrouvé son poids de naissance ou ne prend pas de poids, s'il vomit en jet après les repas ou s'il mouille moins de 6 couches par jour. Si l'allaitement est douloureux ou que vous avez des crevasses, une sage-femme ou une consultante en lactation peut vous aider."
                ),
                color = ColorEtapa.MELOCOTON
            ),

            // 5–8 semanas
            EtapaBebe(
                id = "brote_6semanas",
                titulo = t("Pico de llanto", "Peak crying", "Picco di pianto", "Pic de pleurs"),
                edadMinDias = 35,
                edadMaxDias = 60,
                porQue = t(
                    "Entre las 2 semanas y los 3–4 meses casi todos los bebés lloran más, con el máximo hacia las 6–8 semanas. Es una fase normal del desarrollo: no significa que le pase algo ni que lo estés haciendo mal.",
                    "Between 2 weeks and 3–4 months almost all babies cry more, peaking around 6–8 weeks. It's a normal stage of development: it doesn't mean something is wrong or that you're doing anything wrong.",
                    "Tra le 2 settimane e i 3–4 mesi quasi tutti i bebè piangono di più, con il massimo verso le 6–8 settimane. È una fase normale dello sviluppo: non significa che ci sia qualcosa che non va né che tu stia sbagliando.",
                    "Entre 2 semaines et 3–4 mois, presque tous les bébés pleurent davantage, avec un pic vers 6–8 semaines. C'est une étape normale du développement : cela ne veut pas dire que quelque chose ne va pas, ni que vous vous y prenez mal."
                ),
                quePasara = t(
                    "Puede llorar más, sobre todo al final de la tarde, a veces sin motivo aparente y sin calmarse del todo aunque lo intentes todo. Va mejorando poco a poco a partir de los 3 meses.",
                    "They may cry more, especially in the late afternoon and evening, sometimes for no clear reason and without fully settling however hard you try. It gradually improves from 3 months.",
                    "Può piangere di più, soprattutto verso sera, a volte senza un motivo apparente e senza calmarsi del tutto anche se provi di tutto. Migliora poco a poco dai 3 mesi.",
                    "Votre bébé peut pleurer davantage, surtout en fin d'après-midi et le soir, parfois sans raison apparente et sans se calmer tout à fait malgré tous vos efforts. Cela s'améliore peu à peu à partir de 3 mois."
                ),
                consejos = listOf(
                    t(
                        "Repasa lo básico: hambre, pañal, calor o frío y si necesita echar un eructo.",
                        "Check the basics: hunger, diaper, too hot or too cold, and whether they need burping.",
                        "Controlla le cose essenziali: fame, pannolino, caldo o freddo e se deve fare il ruttino.",
                        "Vérifiez l'essentiel : faim, couche, trop chaud ou trop froid, et s'il a besoin de faire son rot."
                    ),
                    t(
                        "Prueba a calmarle con movimiento suave, contacto piel con piel, un sonido constante de fondo o un paseo.",
                        "Try soothing with gentle motion, skin-to-skin contact, a steady background sound or a walk.",
                        "Prova a calmare il pianto con un movimento dolce, il contatto pelle a pelle, un suono costante di sottofondo o una passeggiata.",
                        "Essayez de l'apaiser avec un bercement doux, le peau à peau, un bruit de fond régulier ou une promenade."
                    ),
                    t(
                        "Si le llevas en portabebés, su cara debe quedar siempre visible y la barbilla separada del pecho para que respire bien.",
                        "If you use a baby carrier, their face should always be visible and their chin off their chest so they can breathe well.",
                        "Se usi un marsupio o una fascia, il viso deve essere sempre visibile e il mento staccato dal petto, così respira bene.",
                        "Si vous utilisez un porte-bébé, son visage doit toujours rester visible et son menton décollé de sa poitrine pour bien respirer."
                    ),
                    t(
                        "Si sientes que no puedes más, déjale boca arriba en su cuna y sal unos minutos a respirar, o pide relevo. Nunca le sacudas: puede causarle lesiones cerebrales graves.",
                        "If you feel you can't cope, lay them on their back in the crib and step away for a few minutes to breathe, or ask someone to take over. Never shake a baby: it can cause severe brain injury.",
                        "Se senti di non farcela più, sistema il bebè a pancia in su nella culla ed esci qualche minuto a respirare, oppure chiedi il cambio. Non scuotere mai un neonato: può causare gravi lesioni cerebrali.",
                        "Si vous sentez que vous n'en pouvez plus, couchez votre bébé sur le dos dans son lit et sortez quelques minutes pour respirer, ou demandez de l'aide. Ne secouez jamais un bébé : cela peut provoquer de graves lésions cérébrales."
                    )
                ),
                cuandoConsultar = t(
                    "Acude al pediatra si el llanto viene con fiebre (38 °C o más; si tiene menos de 3 meses, consulta sin esperar), vómitos, si rechaza las tomas durante horas, si llora más cuando le tocas o le mueves, o si notas cambios en su aspecto o en su forma de comportarse.",
                    "See your pediatrician if crying comes with a fever (38 °C / 100.4 °F or higher; under 3 months, seek care right away), vomiting, refusing feeds for hours, crying more when touched or moved, or if you notice changes in how they look or behave.",
                    "Rivolgiti al pediatra se il pianto si accompagna a febbre (38 °C o più; sotto i 3 mesi, senza aspettare), vomito, rifiuto delle poppate per ore, se piange di più quando viene toccato o mosso, o se noti cambiamenti nel suo aspetto o nel suo comportamento.",
                    "Consultez votre pédiatre si les pleurs s'accompagnent de fièvre (38 °C ou plus ; avant 3 mois, sans attendre), de vomissements, d'un refus de boire pendant des heures, s'il pleure davantage quand on le touche ou le bouge, ou si vous remarquez un changement dans son aspect ou son comportement."
                ),
                color = ColorEtapa.CIELO
            ),

            // 3,5–4,5 meses
            EtapaBebe(
                id = "regresion_4meses",
                titulo = t(
                    "Regresión del sueño", "Sleep regression",
                    "Regressione del sonno", "Régression du sommeil"
                ),
                edadMinDias = 105,
                edadMaxDias = 135,
                porQue = t(
                    "Hacia los 4 meses su sueño madura y empieza a organizarse en ciclos parecidos a los de un adulto, con despertares breves entre uno y otro. No es un retroceso: es una señal de desarrollo.",
                    "Around 4 months their sleep matures and starts to organize into cycles similar to an adult's, with brief wake-ups between them. It isn't a step backwards: it's a sign of development.",
                    "Verso i 4 mesi il suo sonno matura e inizia a organizzarsi in cicli simili a quelli di un adulto, con brevi risvegli tra l'uno e l'altro. Non è un passo indietro: è un segno di sviluppo.",
                    "Vers 4 mois, son sommeil mûrit et commence à s'organiser en cycles proches de ceux d'un adulte, avec de brefs réveils entre chacun. Ce n'est pas un retour en arrière : c'est un signe de développement."
                ),
                quePasara = t(
                    "Durante unas semanas puede despertarse más por la noche, tardar más en dormirse y hacer siestas más cortas.",
                    "For a few weeks they may wake more at night, take longer to fall asleep and take shorter naps.",
                    "Per qualche settimana potrebbe svegliarsi di più di notte, metterci di più ad addormentarsi e fare sonnellini più brevi.",
                    "Pendant quelques semaines, votre bébé peut se réveiller plus souvent la nuit, mettre plus de temps à s'endormir et faire des siestes plus courtes."
                ),
                consejos = listOf(
                    t(
                        "Mantén una rutina corta y siempre igual antes de dormir: por ejemplo, pañal, pijama, toma y una canción.",
                        "Keep a short bedtime routine that's always the same: for example, diaper, pajamas, feed and a song.",
                        "Mantieni una routine breve e sempre uguale prima di dormire: per esempio pannolino, pigiama, poppata e una canzone.",
                        "Gardez un rituel du coucher court et toujours identique : par exemple couche, pyjama, tétée ou biberon, et une chanson."
                    ),
                    t(
                        "Intenta acostarle con sueño pero aún despierto: así aprende a enlazar los ciclos de sueño por su cuenta.",
                        "Try putting them down drowsy but still awake: it helps them learn to link sleep cycles on their own.",
                        "Prova a coricare il bebè assonnato ma ancora sveglio: così impara a collegare i cicli di sonno da solo.",
                        "Essayez de coucher votre bébé somnolent mais encore éveillé : il apprend ainsi à enchaîner seul ses cycles de sommeil."
                    ),
                    t(
                        "Una habitación oscura ayuda. Si usas ruido blanco, ponlo a volumen bajo y lejos de la cuna, a unos 2 metros.",
                        "A dark room helps. If you use white noise, keep the volume low and the device away from the crib, about 2 meters (6–7 feet).",
                        "Una stanza buia aiuta. Se usi il rumore bianco, tienilo a volume basso e lontano dalla culla, a circa 2 metri.",
                        "Une chambre sombre aide. Si vous utilisez un bruit blanc, gardez un volume faible et l'appareil loin du lit, à environ 2 mètres."
                    ),
                    t(
                        "Sigue acostándole boca arriba, en su cuna, sin almohadas, mantas sueltas ni peluches.",
                        "Keep putting them to sleep on their back, in their crib, with no pillows, loose blankets or soft toys.",
                        "Continua a far dormire il bebè a pancia in su, nella sua culla, senza cuscini, coperte sciolte né peluche.",
                        "Continuez à le coucher sur le dos, dans son lit, sans oreiller, couverture libre ni peluche."
                    )
                ),
                cuandoConsultar = t(
                    "Consulta si ronca de forma habitual o hace pausas al respirar mientras duerme, si los despertares vienen con fiebre, dolor o vómitos, o si el sueño no mejora después de 4–6 semanas.",
                    "See your pediatrician if they snore regularly or pause in their breathing while asleep, if the wake-ups come with fever, pain or vomiting, or if sleep hasn't improved after 4–6 weeks.",
                    "Consulta il pediatra se russa abitualmente o fa pause nel respiro mentre dorme, se i risvegli si accompagnano a febbre, dolore o vomito, o se il sonno non migliora dopo 4–6 settimane.",
                    "Consultez si votre bébé ronfle régulièrement ou fait des pauses respiratoires en dormant, si les réveils s'accompagnent de fièvre, de douleur ou de vomissements, ou si le sommeil ne s'améliore pas après 4 à 6 semaines."
                ),
                color = ColorEtapa.LILA
            ),

            // 5,5–7 meses
            EtapaBebe(
                id = "denticion_6meses",
                titulo = t("Primeros dientes", "First teeth", "Primi dentini", "Premières dents"),
                edadMinDias = 165,
                edadMaxDias = 210,
                porQue = t(
                    "Los primeros dientes suelen asomar hacia los 6 meses, aunque es normal que salgan entre los 4 y los 12. Casi siempre aparecen primero los dos incisivos de abajo.",
                    "First teeth usually appear around 6 months, although anywhere between 4 and 12 months is normal. The two bottom front teeth almost always come first.",
                    "I primi dentini di solito spuntano verso i 6 mesi, anche se è normale che escano tra i 4 e i 12. Quasi sempre compaiono per primi i due incisivi inferiori.",
                    "Les premières dents apparaissent généralement vers 6 mois, mais tout âge entre 4 et 12 mois est normal. Ce sont presque toujours les deux incisives du bas qui sortent en premier."
                ),
                quePasara = t(
                    "Puede babear más, querer morderlo todo, tener las encías hinchadas y estar más irritable o dormir peor algunos días.",
                    "They may drool more, want to chew on everything, have swollen gums and be more irritable or sleep worse on some days.",
                    "Può sbavare di più, voler mordere tutto, avere le gengive gonfie ed essere più irritabile o dormire peggio in alcuni giorni.",
                    "Votre bébé peut baver davantage, vouloir tout mordiller, avoir les gencives gonflées et être plus irritable ou mal dormir certains jours."
                ),
                consejos = listOf(
                    t(
                        "Dale un mordedor frío de la nevera, nunca congelado, o masajea sus encías con un dedo limpio.",
                        "Offer a teething ring chilled in the fridge, never frozen, or massage their gums with a clean finger.",
                        "Offri un massaggiagengive freddo di frigorifero, mai congelato, oppure massaggia le gengive con un dito pulito.",
                        "Proposez un anneau de dentition refroidi au réfrigérateur, jamais congelé, ou massez ses gencives avec un doigt propre."
                    ),
                    t(
                        "Seca la baba a menudo para evitar que se irrite la piel de la barbilla y el cuello.",
                        "Wipe away drool often to prevent irritation on the chin and neck.",
                        "Asciuga spesso la bava per evitare irritazioni su mento e collo.",
                        "Essuyez souvent la bave pour éviter les irritations du menton et du cou."
                    ),
                    t(
                        "No uses geles para las encías con benzocaína o lidocaína, ni collares o pulseras de dentición: no son seguros para bebés.",
                        "Don't use gum gels containing benzocaine or lidocaine, or teething necklaces and bracelets: they aren't safe for babies.",
                        "Non usare gel per le gengive con benzocaina o lidocaina, né collane o braccialetti per la dentizione: non sono sicuri per i bebè.",
                        "N'utilisez pas de gels gingivaux contenant de la benzocaïne ou de la lidocaïne, ni de colliers ou bracelets de dentition : ils ne sont pas sûrs pour les bébés."
                    ),
                    t(
                        "En cuanto asome el primer diente, cepíllalo dos veces al día con pasta con flúor, en una cantidad del tamaño de un grano de arroz.",
                        "As soon as the first tooth appears, brush it twice a day with fluoride toothpaste, in an amount the size of a grain of rice.",
                        "Appena spunta il primo dentino, lavalo due volte al giorno con dentifricio al fluoro, in quantità pari a un chicco di riso.",
                        "Dès que la première dent apparaît, brossez-la deux fois par jour avec un dentifrice fluoré, en quantité équivalente à un grain de riz."
                    )
                ),
                cuandoConsultar = t(
                    "Los dientes no causan fiebre alta ni diarrea. Si tiene 38 °C o más, diarrea o está muy irritable varios días seguidos, consulta: probablemente sea otra cosa. Consulta también si a los 12 meses aún no le ha salido ningún diente.",
                    "Teething doesn't cause high fever or diarrhea. If they have a temperature of 38 °C / 100.4 °F or more, diarrhea, or are very irritable for several days, see your pediatrician: it's probably something else. Also check in if no teeth have appeared by 12 months.",
                    "La dentizione non causa febbre alta né diarrea. Se ha 38 °C o più, diarrea o è molto irritabile per diversi giorni, consulta il pediatra: probabilmente è altro. Consultalo anche se a 12 mesi non è ancora spuntato nessun dente.",
                    "Les poussées dentaires ne provoquent ni forte fièvre ni diarrhée. Si votre bébé a 38 °C ou plus, la diarrhée ou reste très irritable plusieurs jours, consultez : c'est probablement autre chose. Consultez aussi si aucune dent n'est sortie à 12 mois."
                ),
                color = ColorEtapa.MANTEQUILLA
            ),

            // 8–10 meses
            EtapaBebe(
                id = "separacion_8meses",
                titulo = t(
                    "Ansiedad por separación", "Separation anxiety",
                    "Ansia da separazione", "Angoisse de séparation"
                ),
                edadMinDias = 240,
                edadMaxDias = 300,
                porQue = t(
                    "Hacia los 8 meses ya entiende que las personas siguen existiendo aunque no las vea, pero todavía no sabe que vas a volver. Por eso le angustia que te vayas. Es una señal de que su vínculo contigo es fuerte.",
                    "Around 8 months they understand that people still exist when out of sight, but don't yet know you'll come back. That's why your leaving is upsetting. It's a sign of a strong bond with you.",
                    "Verso gli 8 mesi capisce che le persone continuano a esistere anche quando non le vede, ma non sa ancora che tornerai. Per questo si angoscia quando te ne vai. È il segno di un legame forte con te.",
                    "Vers 8 mois, votre bébé comprend que les personnes continuent d'exister même quand il ne les voit pas, mais ne sait pas encore que vous allez revenir. C'est pour cela que votre départ l'angoisse. C'est le signe d'un lien fort avec vous."
                ),
                quePasara = t(
                    "Puede llorar cuando sales de la habitación, extrañar a personas que no ve a menudo y despertarse más por la noche buscándote. Suele aparecer entre los 8 y los 12 meses y va cediendo a lo largo del segundo año.",
                    "They may cry when you leave the room, be wary of people they don't see often and wake more at night looking for you. It usually starts between 8 and 12 months and eases over the second year.",
                    "Può piangere quando esci dalla stanza, diffidare delle persone che non vede spesso e svegliarsi di più di notte cercandoti. Di solito compare tra gli 8 e i 12 mesi e si attenua nel corso del secondo anno.",
                    "Votre bébé peut pleurer quand vous quittez la pièce, se méfier des personnes qu'il voit peu et se réveiller plus souvent la nuit en vous cherchant. Cela apparaît généralement entre 8 et 12 mois et s'atténue au cours de la deuxième année."
                ),
                consejos = listOf(
                    t(
                        "Juega a esconderte y aparecer: tápate la cara con las manos y destápala diciendo «¡aquí estoy!», o esconde un juguete bajo una manta y deja que lo encuentre. Le enseña que lo que se va, vuelve.",
                        "Play hide-and-reappear games: cover your face with your hands and uncover it saying \"here I am!\", or hide a toy under a blanket and let them find it. It teaches them that what goes away comes back.",
                        "Gioca a nasconderti e ricomparire: copriti il viso con le mani e scoprilo dicendo «eccomi!», oppure nascondi un giocattolo sotto una copertina e lascia che lo trovi. Così impara che ciò che se ne va, ritorna.",
                        "Jouez à disparaître et réapparaître : cachez votre visage avec vos mains puis découvrez-le en disant « coucou, je suis là ! », ou cachez un jouet sous une couverture et laissez votre bébé le trouver. Cela lui apprend que ce qui part revient."
                    ),
                    t(
                        "Despídete siempre con un ritual corto y cariñoso, aunque llore. Irte a escondidas aumenta su inquietud.",
                        "Always say goodbye with a short, loving ritual, even if they cry. Sneaking out makes them more anxious.",
                        "Saluta sempre con un piccolo rituale affettuoso, anche se piange. Andartene di nascosto aumenta la sua inquietudine.",
                        "Dites toujours au revoir avec un petit rituel tendre, même s'il pleure. Partir en cachette augmente son inquiétude."
                    ),
                    t(
                        "Dile cuándo volverás con referencias que entienda, como «después de la siesta», y cúmplelo.",
                        "Tell them when you'll be back using things they understand, like \"after your nap\", and keep your word.",
                        "Di' quando tornerai con riferimenti che può capire, come «dopo la nanna», e mantieni la parola.",
                        "Dites-lui quand vous reviendrez avec des repères qu'il comprend, comme « après la sieste », et tenez parole."
                    ),
                    t(
                        "Practica separaciones cortas en casa y presenta a los nuevos cuidadores estando tú delante antes de dejarle a solas con ellos.",
                        "Practice short separations at home, and introduce new caregivers while you're there before leaving them alone together.",
                        "Fai prove di brevi separazioni in casa e presenta le nuove persone che si prenderanno cura del bebè mentre ci sei anche tu, prima di lasciarli da soli.",
                        "Entraînez-vous à de courtes séparations à la maison et présentez les nouvelles personnes qui s'en occuperont en votre présence avant de les laisser seuls ensemble."
                    )
                ),
                cuandoConsultar = t(
                    "Consulta si la angustia es tan intensa que le impide comer o dormir durante semanas, o si no reacciona a su nombre ni busca tu mirada.",
                    "See your pediatrician if the distress is so intense that it stops them eating or sleeping for weeks, or if they don't respond to their name or seek your gaze.",
                    "Consulta il pediatra se l'angoscia è così intensa da impedire di mangiare o dormire per settimane, o se non reagisce al suo nome né cerca il tuo sguardo.",
                    "Consultez si l'angoisse est si forte qu'elle l'empêche de manger ou de dormir pendant des semaines, ou si votre bébé ne réagit pas à son prénom et ne cherche pas votre regard."
                ),
                color = ColorEtapa.ROSA
            )
        )

    /** La etapa que le toca al bebé ahora mismo, si hay alguna definida para su edad. */
    fun etapaActual(edadEnDias: Int): EtapaBebe? =
        etapas.firstOrNull { edadEnDias in it.edadMinDias..it.edadMaxDias }
}
