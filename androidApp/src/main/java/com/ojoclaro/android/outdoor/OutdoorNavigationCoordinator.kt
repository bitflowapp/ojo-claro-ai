package com.ojoclaro.android.outdoor

import java.util.concurrent.atomic.AtomicLong

/**
 * Coordinador de la sesión exterior. Kotlin puro (TTS/ubicación/ruta/escena
 * inyectados); el OutdoorForegroundService le da vida en Android.
 *
 * Todo texto hablado pasa por [OutdoorSafetyPolicy]: ninguna afirmación de
 * seguridad sobrevive, venga de GPT, del backend de visión o de un bug local.
 * Los logs usan SOLO buckets y estados: jamás coordenadas ni destinos.
 */
class OutdoorNavigationCoordinator(
    private val locationReader: OutdoorLocationReader,
    private val routeProvider: OutdoorRouteProvider,
    private val sceneDescriber: OutdoorSceneDescriber,
    private val speak: (String) -> Unit,
    private val log: (String) -> Unit = {}
) {
    private val generation = AtomicLong(0L)

    @Volatile
    var state: OutdoorState = OutdoorState.IDLE
        private set

    @Volatile
    private var engine: OutdoorGuidanceEngine? = null

    /** Último destino HABLADO por el usuario, solo para recalcular a pedido. */
    @Volatile
    private var lastDestinationQuery: String? = null

    val isNavigating: Boolean get() = state == OutdoorState.NAVIGATING

    fun currentProgress(): OutdoorRouteProgress? = engine?.currentProgress()

    private fun say(text: String) = speak(OutdoorSafetyPolicy.sanitizeForSpeech(text))

    suspend fun whereAmI() {
        val gen = generation.incrementAndGet()
        // V1.9 — con ruta activa NO se toca el estado de navegación: pasar por
        // LOCATING→IDLE dejaba la guía muda y el service se apagaba solo.
        val wasNavigating = isNavigating
        if (!wasNavigating) transition(OutdoorState.LOCATING, "where_am_i")
        val result = locationReader.read()
        if (gen != generation.get()) return logStale("where_am_i")
        logFix("where_am_i", result)

        // V1.4 — referencia útil: con fix válido intentamos calle/barrio por
        // reverse geocoding. El label NUNCA se loguea (solo presencia) y si
        // el backend no resuelve, cae al texto honesto de siempre.
        val fix = (result as? OutdoorFixResult.Valid)?.fix
        val spoken = if (fix != null) {
            val label = runCatching {
                routeProvider.reverseLabel(fix.latitude, fix.longitude)
            }.getOrNull()
            log("where_am_i reverseLabelPresent=${label != null}")
            val base = if (!label.isNullOrBlank()) {
                val precision = fix.accuracyMeters?.toInt()
                    ?.let { " La precisión es de unos $it metros." }
                    .orEmpty()
                "Estás cerca de $label.$precision"
            } else {
                locationReader.spokenLocationText(result) +
                    " No pude obtener la calle ahora."
            }
            // V1.9 — "me perdí": con ruta activa decimos cómo retomar; sin
            // ruta, ofrecemos iniciar una. Nunca terminar en seco.
            base + routeStatusSuffix(wasNavigating)
        } else {
            locationReader.spokenLocationText(result)
        }
        say(spoken)
        if (!wasNavigating) transition(OutdoorState.IDLE, "where_am_i_done")
    }

    private fun routeStatusSuffix(wasNavigating: Boolean): String {
        val progress = engine?.takeIf { wasNavigating }?.currentProgress()
        return if (progress != null) {
            " Tu ruta hacia ${progress.destinationName} sigue activa. " +
                "La próxima indicación es: ${progress.nextInstruction}. " +
                "Si te desviaste, podés decir: recalculá."
        } else {
            " Si querés, te ayudo a iniciar una ruta: decí, llevame a, y el destino."
        }
    }

    /** V1.9 — "¿cuánto falta?" con respuesta propia (antes repetía la indicación). */
    fun howFar() {
        val activeEngine = engine
        if (activeEngine == null || !isNavigating) {
            say(
                "No tengo una ruta activa. Podés decir: llevame a la plaza, " +
                    "o llevame a una dirección."
            )
            return
        }
        val progress = activeEngine.currentProgress()
        say(
            "Faltan aproximadamente ${progress.remainingDistanceMeters} metros. " +
                "La próxima indicación es: ${progress.nextInstruction}."
        )
    }

    /**
     * V1.9 — recalcular SOLO a pedido del usuario (jamás automático): vuelve
     * a pedir la ruta hacia el último destino hablado, avisando antes.
     * @return true si la guía quedó activa con la ruta nueva.
     */
    suspend fun recalculate(): Boolean {
        val destination = lastDestinationQuery
        if (destination == null || engine == null) {
            say("No hay una ruta activa para recalcular. Decime: llevame a, y el destino.")
            return false
        }
        say("Recalculando la ruta hacia $destination.")
        return startGuidance(destination)
    }

    /**
     * @return true si la guía quedó activa (para que el caller mantenga el service).
     *
     * V1.10.2 — [allowImprecise] solo llega de una confirmación explícita del
     * usuario ("intentá igual"): acepta un fix impreciso avisando que la ruta
     * es aproximada. Jamás se activa solo.
     */
    suspend fun startGuidance(destination: String, allowImprecise: Boolean = false): Boolean {
        // "llevame a casa" sin memoria de casa: pedir la dirección real,
        // nunca geocodificar "casa" (inventaría un destino).
        if (OutdoorDestinationNormalizer.isHomeReference(destination)) {
            OutdoorMobilityFallbackHub.post(MobilityFallbackKind.ADDRESS_RETRY, null)
            say(
                "Todavía no tengo guardada la dirección de tu casa. " +
                    "Decime la calle y la altura, o una esquina cercana."
            )
            transition(OutdoorState.IDLE, "start_home_unknown")
            return false
        }

        val gen = generation.incrementAndGet()
        transition(OutdoorState.LOCATING, "start_guidance")

        val fixResult = locationReader.read()
        if (gen != generation.get()) {
            logStale("start_guidance")
            return false
        }
        logFix("start_guidance", fixResult)
        val fix = when {
            fixResult is OutdoorFixResult.Valid -> fixResult.fix
            // V1.10.2 — GPS impreciso ya no termina en seco: ofrece intentar
            // igual (con confirmación) o esperar mejor señal.
            fixResult is OutdoorFixResult.TooInaccurate && allowImprecise -> {
                say("Voy a intentar con tu ubicación aproximada. La ruta puede tener menos precisión.")
                fixResult.fix
            }
            fixResult is OutdoorFixResult.TooInaccurate -> {
                OutdoorMobilityFallbackHub.post(MobilityFallbackKind.ROUTE_RETRY_IMPRECISE, destination)
                say(
                    "Tu ubicación está imprecisa ahora. Puedo intentar igual con " +
                        "una ruta aproximada, o esperás un momento en un lugar más " +
                        "abierto y me volvés a pedir la ruta. Si querés que intente " +
                        "igual, decí: intentá igual."
                )
                transition(OutdoorState.IDLE, "start_fix_imprecise")
                return false
            }
            else -> {
                say(locationReader.spokenLocationText(fixResult))
                transition(OutdoorState.IDLE, "start_no_fix")
                return false
            }
        }

        say("Buscando una ruta a pie hacia $destination.")
        // Limpieza determinística ("la calle san martín al 500" → "san martin
        // 500"): el geocoder entiende la query, la voz conserva la frase humana.
        val geocodeQuery = OutdoorDestinationNormalizer.normalizeQuery(destination)
        val outcome = routeProvider.walkingRoute(fix.latitude, fix.longitude, geocodeQuery)
        if (gen != generation.get()) {
            logStale("route_result")
            return false
        }

        return when (outcome) {
            is OutdoorRouteOutcome.Route -> {
                val newEngine = OutdoorGuidanceEngine(outcome.data)
                engine = newEngine
                lastDestinationQuery = destination
                // Una ruta viva invalida las ofertas de fallback anteriores:
                // un "sí" tardío no debe abrir Maps con la guía ya andando.
                OutdoorMobilityFallbackHub.clear()
                transition(OutdoorState.NAVIGATING, "route_ready")
                // UN solo anuncio hablado: dos seguidos hacen que el segundo
                // pise al primero (flush) y la persona nunca escucha la ruta.
                // Fallo físico real 2026-06-11: "dijo buscando y se cortó".
                // V1.10.2 — ruta larga: sugerir abrir Uber/Maps SIN pedir nada
                // real (Estela jamás pide ni confirma viajes).
                val transportNote =
                    if (outcome.data.totalDistanceMeters > OutdoorBudgets.LONG_WALK_SUGGEST_METERS) {
                        " Aviso: es un trayecto largo a pie. Si preferís un " +
                            "transporte, decí: abrí uber, o: abrí maps, y te " +
                            "ayudo a prepararlo sin pedir el viaje por vos. " +
                            "Mientras tanto sigo guiándote a pie."
                    } else {
                        ""
                    }
                say(
                    newEngine.startAnnouncement() + " " +
                        "Recordá: soy una ayuda complementaria. " +
                        "Usá tu bastón o tu método habitual de movilidad." +
                        transportNote
                )
                true
            }
            OutdoorRouteOutcome.Unconfigured -> {
                say(
                    "Todavía no puedo calcular rutas porque el servicio de mapas " +
                        "no está configurado. Sí puedo decirte dónde estás."
                )
                transition(OutdoorState.IDLE, "route_unconfigured")
                false
            }
            OutdoorRouteOutcome.NotFound -> {
                // Destino ambiguo o inexistente: re-preguntar con guía
                // concreta, nunca adivinar. La próxima frase puede ser la
                // dirección directamente (hub ADDRESS_RETRY).
                OutdoorMobilityFallbackHub.post(MobilityFallbackKind.ADDRESS_RETRY, destination)
                say(
                    "No encontré bien esa dirección. Decime la calle y la " +
                        "altura, o una esquina cercana. Por ejemplo: San Martín " +
                        "quinientos, o: San Martín y Roca."
                )
                transition(OutdoorState.IDLE, "route_not_found")
                false
            }
            OutdoorRouteOutcome.TooFar -> {
                // V1.10.2 — lejos para ir a pie: degradación útil, sin pedir
                // ningún viaje. Maps con confirmación; Uber si la persona lo pide.
                OutdoorMobilityFallbackHub.post(MobilityFallbackKind.ROUTE_TOO_FAR_MAPS, destination)
                say(
                    "Ese destino queda muy lejos para una ruta a pie. Puedo " +
                        "abrir Google Maps con el destino para que veas opciones " +
                        "de transporte, o decí: abrí uber, y te ayudo a " +
                        "prepararlo sin pedir el viaje por vos. ¿Querés que " +
                        "abra Maps? Decí: sí, o no."
                )
                transition(OutdoorState.IDLE, "route_too_far")
                false
            }
            is OutdoorRouteOutcome.Error -> {
                // V1.10.2 — nunca más el cierre seco "no puedo calcular la
                // ruta": ofrecer reintento o Google Maps con ese destino.
                OutdoorMobilityFallbackHub.post(MobilityFallbackKind.ROUTE_RETRY_OR_MAPS, destination)
                say(
                    "No pude calcular la ruta con mi servicio. Puedo intentar " +
                        "de nuevo, o abrir Google Maps con ese destino. ¿Querés " +
                        "que abra Maps? Decí: sí, o no. Para reintentar, decí: " +
                        "probá de nuevo."
                )
                transition(OutdoorState.IDLE, "route_error_${outcome.code.take(24)}")
                false
            }
        }
    }

    /** @return true si la sesión de guía terminó (llegada) y el service puede parar. */
    fun onLocationUpdate(fix: OutdoorLocationFix): Boolean {
        val activeEngine = engine ?: return false
        if (state != OutdoorState.NAVIGATING) return false
        // V1.9 — precisión mala no alimenta el odómetro: el ruido inventa
        // metros caminados y maniobras falsas. Silencioso a propósito (el
        // aviso de quietud del service cubre el caso de GPS degradado largo).
        val accuracy = fix.accuracyMeters
        if (accuracy != null && accuracy > OutdoorBudgets.NAV_MAX_ACCURACY_METERS) {
            log("navEvent=fix_rejected_inaccurate accuracyBucket=${fix.accuracyBucket}")
            return false
        }
        val decision = activeEngine.onLocation(fix)
        log(
            "navEvent=${decision.event} accuracyBucket=${fix.accuracyBucket} " +
                "ageBucket=${fix.ageBucket} offRoute=${activeEngine.currentProgress().offRoute}"
        )
        if (decision.speak) say(decision.text)
        if (activeEngine.isArrived) {
            engine = null
            transition(OutdoorState.IDLE, "arrival")
            return true
        }
        return false
    }

    fun repeatInstruction() {
        val activeEngine = engine
        if (activeEngine == null) {
            say("No hay una navegación activa. Decime: llevame a, y el destino.")
            return
        }
        say(activeEngine.progressSpokenText())
    }

    fun cancelGuidance(reason: String) {
        generation.incrementAndGet()
        val wasNavigating = engine != null || state != OutdoorState.IDLE
        engine = null
        transition(OutdoorState.IDLE, "cancel_${reason.take(24)}")
        if (wasNavigating) {
            say("Navegación cancelada.")
        } else if (reason == "user_stop") {
            // V1.9 — nunca silencio: si la persona pidió cancelar y no había
            // ruta, lo decimos (los cierres internos siguen mudos).
            say("No había una ruta activa. Si querés una, decí: llevame a, y el destino.")
        }
    }

    suspend fun describeAhead() {
        val gen = generation.incrementAndGet()
        val previousState = state
        transition(OutdoorState.DESCRIBING, "describe_request")
        log("cameraStarted=true explicitRequest=true")
        val outcome = sceneDescriber.describeAhead(explicitUserRequest = true)
        if (gen != generation.get()) return logStale("describe_result")
        log("analysisCompleted=${outcome is OutdoorSceneOutcome.Described} safetyPolicyApplied=true")
        when (outcome) {
            is OutdoorSceneOutcome.Described -> say(outcome.spokenText)
            OutdoorSceneOutcome.CameraPermissionMissing ->
                say("No tengo permiso de cámara. Activalo en Ajustes si querés que describa el entorno.")
            OutdoorSceneOutcome.CaptureFailed ->
                say("No pude capturar la imagen. Probá de nuevo.")
            OutdoorSceneOutcome.Timeout ->
                say("La cámara tardó demasiado. Probá de nuevo.")
            is OutdoorSceneOutcome.Error ->
                say("No pude describir el entorno ahora. Probá de nuevo en un momento.")
        }
        transition(
            if (previousState == OutdoorState.NAVIGATING && engine != null) {
                OutdoorState.NAVIGATING
            } else {
                OutdoorState.IDLE
            },
            "describe_done"
        )
    }

    private fun transition(newState: OutdoorState, cause: String) {
        state = newState
        log("navigationState=$newState cause=$cause finalState=$newState")
    }

    private fun logFix(stage: String, result: OutdoorFixResult) {
        val label = when (result) {
            is OutdoorFixResult.Valid ->
                "valid accuracyBucket=${result.fix.accuracyBucket} ageBucket=${result.fix.ageBucket}"
            OutdoorFixResult.PermissionMissing -> "permission_missing"
            OutdoorFixResult.ServicesDisabled -> "services_disabled"
            OutdoorFixResult.Unavailable -> "unavailable"
            is OutdoorFixResult.TooOld -> "too_old ageBucket=${result.fix.ageBucket}"
            is OutdoorFixResult.TooInaccurate ->
                "too_inaccurate accuracyBucket=${result.fix.accuracyBucket}"
        }
        log("stage=$stage fix=$label")
    }

    private fun logStale(stage: String) {
        log("stage=$stage staleResultIgnored=true")
    }
}
